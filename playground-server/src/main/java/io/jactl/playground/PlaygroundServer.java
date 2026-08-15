/*
 * Copyright © 2022-2026 James Crawford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jactl.playground;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.jactl.CompileError;
import io.jactl.Jactl;
import io.jactl.JactlContext;
import io.jactl.JactlScript;
import io.jactl.runtime.DieError;
import io.jactl.runtime.Json;
import io.jactl.runtime.RuntimeError;
import io.jactl.runtime.TimeoutError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A small, dependency-free HTTP service that compiles and runs untrusted Jactl
 * snippets submitted from the jactl.io "Try it now" playground page.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET  /health} &rarr; {@code {"status":"ok"}}</li>
 *   <li>{@code POST /run} with body {@code {"script": "...", "input"?: "..."}}
 *       &rarr; {@code {"output": str, "result": str|null, "error": str|null,
 *       "timedOut": bool, "truncated": bool}}</li>
 * </ul>
 *
 * <p>Safety model (see also the playground plan / README):</p>
 * <ul>
 *   <li>Jactl's sandbox is left at its restrictive defaults: {@code allowHostAccess=false}
 *       and {@code allowHostClassLookup=false}, so scripts can only touch built-in
 *       types and Jactl-defined classes &mdash; not arbitrary host/JDK classes.</li>
 *   <li>{@code eval()} is disabled and cooperative limits ({@code maxExecutionTime},
 *       {@code maxLoopIterations}) catch runaway loops and async scripts.</li>
 *   <li>An external wall-clock watchdog ({@link Future#get(long, TimeUnit)} +
 *       {@code cancel(true)}) interrupts a run that overruns. NOTE: JVM interruption
 *       is cooperative &mdash; {@code sleep()} (a {@code Thread.sleep} under
 *       {@code async(false)}) is interruptible, but a tight non-looping CPU burn
 *       cannot be preempted cleanly. That residual case is backstopped by running
 *       the service under a systemd unit with a memory/CPU cap and {@code Restart=always}.</li>
 *   <li>Output is bounded ({@link BoundedWriter}) and request bodies are size-capped.</li>
 *   <li>Concurrency is capped by a semaphore; excess requests get {@code 429}.</li>
 * </ul>
 *
 * <p>All configuration is via system properties (each with a sensible default),
 * so the systemd unit can override with {@code -Djactl.playground.*=...}.</p>
 */
public class PlaygroundServer implements HttpHandler {

  // ---- Configuration (system properties with defaults) ---------------------

  private static final int    PORT             = intProp("jactl.playground.port", 8080);
  private static final String BIND             = strProp("jactl.playground.bind", "127.0.0.1");
  /** Cooperative Jactl timeout (checked at loop iterations / async boundaries). */
  private static final int    MAX_EXEC_MS      = intProp("jactl.playground.maxExecMs", 5000);
  /** Wall-clock kill for the worker thread; must be &gt; MAX_EXEC_MS to let the cooperative check fire first. */
  private static final long   WALL_CLOCK_MS    = longProp("jactl.playground.wallClockMs", 8000);
  private static final long   MAX_LOOPS        = longProp("jactl.playground.maxLoops", 10_000_000L);
  private static final int    MAX_OUTPUT_CHARS = intProp("jactl.playground.maxOutputChars", 65536);
  private static final int    MAX_SCRIPT_LEN   = intProp("jactl.playground.maxScriptLen", 100_000);
  private static final int    MAX_BODY_BYTES   = intProp("jactl.playground.maxBodyBytes", 256 * 1024);
  private static final int    MAX_CONCURRENT   = intProp("jactl.playground.maxConcurrent", 4);
  /**
   * Allowed CORS origin. Set to "*" to allow any (dev only). Set to "off"/"none"/""
   * to disable in-service CORS entirely — do that only if the reverse proxy (nginx)
   * is set to add CORS instead, so the two don't emit duplicate Access-Control-Allow-Origin
   * headers (which browsers reject).
   */
  private static final String  CORS_ORIGIN      = strProp("jactl.playground.corsOrigin", "https://jactl.io");
  private static final boolean CORS_ENABLED     = !(CORS_ORIGIN.isEmpty()
                                                    || CORS_ORIGIN.equalsIgnoreCase("off")
                                                    || CORS_ORIGIN.equalsIgnoreCase("none"));

  // Rolling request/error logs. Each file rotates at LOG_MAX_BYTES; at most LOG_COUNT
  // files are kept (older generations are overwritten), so disk use is bounded at
  // roughly LOG_MAX_BYTES * LOG_COUNT.
  private static final String LOG_DIR       = strProp("jactl.playground.logDir", "logs");
  private static final int    LOG_MAX_BYTES = intProp("jactl.playground.logMaxBytes", 1024 * 1024 * 1024); // 1 GB
  private static final int    LOG_COUNT     = intProp("jactl.playground.logCount", 10);
  private static final Logger LOG           = Logger.getLogger("io.jactl.playground");

  // ---- Shared state --------------------------------------------------------

  // Compilation is serialised because we do not rely on the Jactl compiler being
  // safe to run concurrently across its shared static tables; script execution
  // (which may sleep) still runs concurrently on the worker pool.
  private final Object compileLock = new Object();

  private final Semaphore       slots  = new Semaphore(MAX_CONCURRENT);
  private final ExecutorService runners;

  /**
   * Build a fresh sandboxed context for a single request.
   *
   * <p>Deliberately NOT reused across requests: a JactlContext owns a
   * DynamicClassLoader that strong-references every class it defines, and each
   * distinct script compiles to a new class (named by md5 of its source). A
   * long-lived shared context would therefore pin one class per distinct
   * submission forever and eventually exhaust metaspace. Building a new context
   * per request lets the classloader and its generated class become unreachable
   * once the request completes, so memory is bounded by in-flight requests
   * rather than total requests. It's cheap: with async(false) no event-loop
   * thread pool is created, and the builtin function tables are shared statics
   * (not re-registered per context).</p>
   */
  private static JactlContext newContext() {
    return JactlContext.create()
                       .async(false)                 // run on our worker thread so we can interrupt it
                       .maxExecutionTime(MAX_EXEC_MS) // cooperative wall-clock limit
                       .maxLoopIterations(MAX_LOOPS)  // cooperative loop limit
                       .disableEval(true)             // no dynamic eval()
                       .build();
    // allowHostAccess / allowHostClassLookup are left at their (denying) defaults.
  }

  public PlaygroundServer() {
    final AtomicLong n = new AtomicLong();
    ThreadFactory tf = r -> {
      Thread t = new Thread(r, "jactl-run-" + n.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
    // Cached pool: a thread wedged by a non-interruptible run is abandoned rather
    // than blocking future requests; the semaphore still bounds real concurrency.
    this.runners = Executors.newCachedThreadPool(tf);
  }

  public static void main(String[] args) throws IOException {
    setupLogging();
    PlaygroundServer handler = new PlaygroundServer();
    HttpServer server = HttpServer.create(new InetSocketAddress(BIND, PORT), 0);
    server.createContext("/run", handler);
    server.createContext("/health", handler);
    // Use a small executor for HTTP dispatch; script runs happen on the separate runner pool.
    server.setExecutor(Executors.newFixedThreadPool(Math.max(2, MAX_CONCURRENT + 2)));
    server.start();
    LOG.info("Jactl playground server listening on " + BIND + ":" + PORT
             + " (maxExecMs=" + MAX_EXEC_MS + ", wallClockMs=" + WALL_CLOCK_MS
             + ", maxLoops=" + MAX_LOOPS + ", maxConcurrent=" + MAX_CONCURRENT
             + ", corsOrigin=" + CORS_ORIGIN + ", logDir=" + LOG_DIR
             + ", logMaxBytes=" + LOG_MAX_BYTES + ", logCount=" + LOG_COUNT + ")");
  }

  /**
   * Configure the "io.jactl.playground" logger with a rolling {@link FileHandler}
   * ({@code LOG_MAX_BYTES} per file, {@code LOG_COUNT} generations) plus a console
   * handler (which under systemd is captured by the journal). If the log directory
   * cannot be opened the service still starts, logging to the console only.
   */
  private static void setupLogging() {
    LOG.setUseParentHandlers(false);   // don't also fire the default root ConsoleHandler
    LOG.setLevel(Level.INFO);
    Formatter fmt = new CompactFormatter();

    ConsoleHandler console = new ConsoleHandler();
    console.setFormatter(fmt);
    console.setLevel(Level.INFO);
    LOG.addHandler(console);

    try {
      Files.createDirectories(Paths.get(LOG_DIR));
      // %g = generation number (0..LOG_COUNT-1); append across restarts.
      FileHandler file = new FileHandler(LOG_DIR + "/jactl-playground.%g.log", LOG_MAX_BYTES, LOG_COUNT, true);
      file.setFormatter(fmt);
      file.setLevel(Level.INFO);
      LOG.addHandler(file);
    }
    catch (IOException e) {
      LOG.log(Level.WARNING, "Could not open log files in '" + LOG_DIR + "' — logging to console only", e);
    }
  }

  // ---- HTTP dispatch --------------------------------------------------------

  @Override
  public void handle(HttpExchange ex) throws IOException {
    try {
      addCors(ex);
      String method = ex.getRequestMethod();
      String path   = ex.getHttpContext().getPath();

      if ("OPTIONS".equalsIgnoreCase(method)) {
        // CORS preflight.
        ex.sendResponseHeaders(204, -1);
        return;
      }
      if ("/health".equals(path)) {
        if (!"GET".equalsIgnoreCase(method)) { sendError(ex, 405, "GET only"); return; }
        Map<String,Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        sendJson(ex, 200, ok);
        return;
      }
      if ("/run".equals(path)) {
        if (!"POST".equalsIgnoreCase(method)) { sendError(ex, 405, "POST only"); return; }
        handleRun(ex);
        return;
      }
      sendError(ex, 404, "Not found");
    }
    catch (Throwable t) {
      // Last-resort guard so the handler never leaks a stack trace to the client.
      try { sendError(ex, 500, "Internal error"); } catch (IOException ignore) {}
    }
    finally {
      ex.close();
    }
  }

  private void handleRun(HttpExchange ex) throws IOException {
    byte[] body = readBody(ex, MAX_BODY_BYTES);
    if (body == null) { logReject(ex, 413, "body-too-large"); sendError(ex, 413, "Request body too large"); return; }

    String script;
    String input;
    try {
      Object parsed = Json.fromJson(new String(body, StandardCharsets.UTF_8), "request", 0);
      if (!(parsed instanceof Map)) { logReject(ex, 400, "not-json-object"); sendError(ex, 400, "Body must be a JSON object"); return; }
      @SuppressWarnings("unchecked")
      Map<String,Object> req = (Map<String,Object>) parsed;
      Object s = req.get("script");
      Object i = req.get("input");
      if (!(s instanceof String)) { logReject(ex, 400, "missing-script"); sendError(ex, 400, "Missing string field: script"); return; }
      script = (String) s;
      input  = (i instanceof String) ? (String) i : "";
    }
    catch (Exception e) {
      logReject(ex, 400, "invalid-json");
      sendError(ex, 400, "Invalid JSON request");
      return;
    }

    if (script.length() > MAX_SCRIPT_LEN) { logReject(ex, 413, "script-too-long"); sendError(ex, 413, "Script too long"); return; }

    // Bound concurrent executions; reject fast when saturated.
    if (!slots.tryAcquire()) { logReject(ex, 429, "busy"); sendError(ex, 429, "Server busy, please retry"); return; }
    try {
      long startNs = System.nanoTime();
      Map<String,Object> resp = runScript(script, input);
      long ms = (System.nanoTime() - startNs) / 1_000_000L;
      logRun(ex, script, resp, ms);
      sendJson(ex, 200, resp);
    }
    finally {
      slots.release();
    }
  }

  // ---- Script execution -----------------------------------------------------

  /** Compile + run a single snippet, capturing output and classifying any error. */
  private Map<String,Object> runScript(String script, String input) {
    BoundedWriter out = new BoundedWriter(MAX_OUTPUT_CHARS);

    Callable<Object> task = () -> {
      // A fresh, single-use context per request (see newContext()): its
      // DynamicClassLoader and the class it compiles become unreachable when this
      // task returns, so generated classes can be reclaimed rather than leaking.
      JactlContext context = newContext();
      JactlScript compiled;
      synchronized (compileLock) {
        // Compilation counts toward the wall-clock budget since it runs inside the task.
        compiled = Jactl.compileScript(script, new LinkedHashMap<>(), context);
      }
      return compiled.eval(new LinkedHashMap<>(), new StringReader(input == null ? "" : input), out);
    };

    Map<String,Object> resp = new LinkedHashMap<>();
    resp.put("output", null);
    resp.put("result", null);
    resp.put("error", null);
    resp.put("timedOut", Boolean.FALSE);
    resp.put("truncated", Boolean.FALSE);

    Future<Object> future = runners.submit(task);
    try {
      Object result = future.get(WALL_CLOCK_MS, TimeUnit.MILLISECONDS);
      resp.put("result", stringify(result));
    }
    catch (TimeoutException te) {
      future.cancel(true);                 // interrupt the worker (interruptible sleeps, etc.)
      resp.put("timedOut", Boolean.TRUE);
      resp.put("error", "Execution timed out after " + WALL_CLOCK_MS + "ms");
    }
    catch (ExecutionException ee) {
      Throwable cause = ee.getCause() == null ? ee : ee.getCause();
      if (cause instanceof TimeoutError) {
        resp.put("timedOut", Boolean.TRUE);
        resp.put("error", cause.getMessage());
      }
      else if (cause instanceof DieError || cause instanceof RuntimeError || cause instanceof CompileError) {
        // getMessage() on a JactlError includes the single-line message plus the marked source line.
        resp.put("error", messageOrClass(cause));
      }
      else {
        // Unexpected (non-Jactl) throwable: log it server-side (with the script and
        // full stack trace) for diagnosis but never leak a raw/blank message to the client.
        LOG.log(Level.WARNING, "Unexpected error running snippet: script=" + escape(script), cause);
        resp.put("error", "Internal error (" + cause.getClass().getSimpleName() + ")");
      }
    }
    catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      resp.put("error", "Server interrupted");
    }

    resp.put("output", out.toString());
    if (out.isTruncated()) { resp.put("truncated", Boolean.TRUE); }
    return resp;
  }

  /** Render a script's return value the way the playground should display it. */
  private static String stringify(Object result) {
    return result == null ? "null" : String.valueOf(result);
  }

  /** A JactlError's message, falling back to its class name if (unexpectedly) null. */
  private static String messageOrClass(Throwable t) {
    String m = t.getMessage();
    return m != null ? m : t.getClass().getSimpleName();
  }

  // ---- Request logging ------------------------------------------------------

  /** Log a completed /run: client, timing, the submitted script, and the outcome. */
  private static void logRun(HttpExchange ex, String script, Map<String,Object> resp, long ms) {
    boolean timedOut  = Boolean.TRUE.equals(resp.get("timedOut"));
    boolean truncated = Boolean.TRUE.equals(resp.get("truncated"));
    Object  error     = resp.get("error");
    String  outcome   = timedOut ? "timeout" : (error != null ? "error" : "ok");
    String  detail    = error != null ? "error=" + escape(String.valueOf(error))
                                      : "result=" + escape(String.valueOf(resp.get("result")));
    LOG.info("run ip=" + clientIp(ex) + " ms=" + ms + " scriptLen=" + script.length()
             + " outcome=" + outcome + " truncated=" + truncated
             + " :: script=" + escape(script) + " :: " + detail);
  }

  /** Log a /run request rejected before execution (bad request, too large, rate-limited). */
  private static void logReject(HttpExchange ex, int status, String reason) {
    LOG.info("run ip=" + clientIp(ex) + " reject=" + status + " reason=" + reason);
  }

  /** Real client IP: first X-Forwarded-For entry (set by nginx) if present, else the peer. */
  private static String clientIp(HttpExchange ex) {
    String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      int comma = xff.indexOf(',');
      return (comma > 0 ? xff.substring(0, comma) : xff).trim();
    }
    return ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress().getHostAddress();
  }

  /** Flatten newlines/tabs so a logged script or message stays on a single line. */
  private static String escape(String s) {
    if (s == null) return "null";
    return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
  }

  /** Single-line log records ("<timestamp> <LEVEL> <message>"), with any stack trace appended. */
  private static final class CompactFormatter extends Formatter {
    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override public String format(LogRecord r) {
      StringBuilder sb = new StringBuilder(160);
      sb.append(TS.format(Instant.ofEpochMilli(r.getMillis())))
        .append(' ').append(r.getLevel().getName())
        .append(' ').append(formatMessage(r)).append('\n');
      if (r.getThrown() != null) {
        StringWriter sw = new StringWriter();
        r.getThrown().printStackTrace(new PrintWriter(sw));
        sb.append(sw);
      }
      return sb.toString();
    }
  }

  // ---- HTTP helpers ---------------------------------------------------------

  private void addCors(HttpExchange ex) {
    if (!CORS_ENABLED) return;   // reverse proxy owns CORS
    ex.getResponseHeaders().set("Access-Control-Allow-Origin", CORS_ORIGIN);
    ex.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
    ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    ex.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    ex.getResponseHeaders().set("Vary", "Origin");
  }

  /** Read up to {@code max} bytes; return null if the body exceeds {@code max}. */
  private static byte[] readBody(HttpExchange ex, int max) throws IOException {
    try (InputStream in = ex.getRequestBody()) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int total = 0, r;
      while ((r = in.read(chunk)) != -1) {
        total += r;
        if (total > max) { return null; }
        buf.write(chunk, 0, r);
      }
      return buf.toByteArray();
    }
  }

  private void sendJson(HttpExchange ex, int status, Map<String,Object> obj) throws IOException {
    byte[] bytes = Json.toJson(obj, null, 0).getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
  }

  private void sendError(HttpExchange ex, int status, String message) throws IOException {
    Map<String,Object> err = new LinkedHashMap<>();
    err.put("error", message);
    sendJson(ex, status, err);
  }

  // ---- Bounded output writer ------------------------------------------------

  /**
   * A {@link Writer} that accumulates into a {@link StringBuilder} but stops
   * appending once {@code maxChars} is reached (flagging truncation). This caps
   * memory from output-heavy scripts; a script that keeps printing forever is
   * still stopped by the wall-clock/cooperative timeouts.
   */
  private static final class BoundedWriter extends Writer {
    private final StringBuilder sb = new StringBuilder();
    private final int maxChars;
    private boolean truncated;

    BoundedWriter(int maxChars) { this.maxChars = maxChars; }

    @Override public void write(char[] cbuf, int off, int len) {
      int room = maxChars - sb.length();
      if (room <= 0) { truncated = true; return; }
      if (len > room) { sb.append(cbuf, off, room); truncated = true; }
      else            { sb.append(cbuf, off, len); }
    }

    @Override public void write(int c) {
      if (sb.length() >= maxChars) { truncated = true; return; }
      sb.append((char) c);
    }

    @Override public void write(String str) {
      write(str, 0, str.length());
    }

    @Override public void write(String str, int off, int len) {
      int room = maxChars - sb.length();
      if (room <= 0) { truncated = true; return; }
      if (len > room) { sb.append(str, off, off + room); truncated = true; }
      else            { sb.append(str, off, off + len); }
    }

    boolean isTruncated() { return truncated; }
    @Override public void flush() { }
    @Override public void close() { }
    @Override public String toString() { return sb.toString(); }
  }

  // ---- Property helpers -----------------------------------------------------

  private static String strProp(String key, String def) {
    String v = System.getProperty(key);
    return v == null || v.isEmpty() ? def : v;
  }
  private static int intProp(String key, int def) {
    try { return Integer.parseInt(strProp(key, Integer.toString(def))); }
    catch (NumberFormatException e) { return def; }
  }
  private static long longProp(String key, long def) {
    try { return Long.parseLong(strProp(key, Long.toString(def))); }
    catch (NumberFormatException e) { return def; }
  }
}
