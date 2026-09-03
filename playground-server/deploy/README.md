# Jactl Playground Backend — Deploy & Security Reference

The backend for **jactl.io/playground**: a browser frontend on `jactl.io` (GitHub
Pages) calls a code-eval service at **`https://api.jactl.io`** running on a single VM.
This directory holds everything needed to stand that VM up, versioned with the service
code in `playground-server/`.

## Architecture

```
Browser (jactl.io/playground visitor)
   |
   v  POST https://api.jactl.io/run   {"script": "...", "input"?: "..."}
nginx  (same VM, public 80/443)
   - TLS termination (Let's Encrypt / certbot)
   - Per-IP rate limiting (limit_req)
   - Request size cap (client_max_body_size)
   - Short proxy_read_timeout
   |
   v  proxy_pass -> 127.0.0.1:8080  (localhost only)
Jactl eval service  (one long-running JVM; io.jactl.playground.PlaygroundServer)
   - Sandboxed script execution (Jactl deny-all host access)
   - Per-request cooperative timeout + loop cap
   - External wall-clock watchdog (interrupt on overrun)
   - Bounded output; bounded concurrency
```

Single VM. nginx and the eval service share the box; the service binds `127.0.0.1`
only, so it is unreachable except through nginx. The firewall opens only 22/80/443.

## Endpoints (served by the service)

| Method | Path      | Body / result |
|--------|-----------|---------------|
| `GET`  | `/health` | `{"status":"ok"}` |
| `POST` | `/run`    | in: `{"script": string, "input"?: string}` · out: `{"output", "result", "error", "timedOut", "truncated"}` |

## Isolation model — decision

For a public untrusted endpoint the stronger posture is process/container isolation
(a subprocess or container escape has a smaller blast radius than an in-process
sandbox escape). We nonetheless run **in-process** (one warm JVM, thread-per-request
+ watchdog) because on a single free-tier VM it is far faster (no ~300ms JVM start
per run) and much simpler to operate. The residual risk — a tight non-looping CPU
burn the cooperative timeout can't preempt, or an in-JVM sandbox escape — is
backstopped at the VM level by the systemd unit: `MemoryMax=1G`, `CPUQuota=80%`,
`Restart=always`. **Revisit** (subprocess- or container-per-request) if abuse or a
sandbox-escape attempt is ever observed in the logs.

## Security layers (in request order)

1. **Firewall** — only 22/80/443 open externally; service bound to `127.0.0.1`.
   On Oracle Cloud the VCN **Security List / NSG is a separate layer** from the host
   `ufw` and must also open 80/443, or nothing reaches the VM.
2. **nginx** — TLS; `limit_req` per-IP rate limit; `client_max_body_size`; short
   `proxy_read_timeout` (> the service's `wallClockMs` so nginx doesn't cut off the
   service's own timeout response).
3. **Execution sandbox** (the layer that matters most — this runs arbitrary user code):
   - Jactl `allowHostAccess=false` + `allowHostClassLookup=false` (defaults): scripts
     reach only built-in types and Jactl-defined classes — no filesystem, no network,
     no arbitrary class instantiation.
   - `disableEval(true)`; cooperative `maxExecutionTime` + `maxLoopIterations`.
   - External wall-clock watchdog interrupts overruns; output size capped; per-request
     globals map (no shared mutable state across requests).
   - **Fresh `JactlContext` per request.** A context owns a classloader that strong-
     references every class it compiles (one per distinct script, named by source md5),
     so a reused context would pin classes forever and exhaust metaspace. A new context
     per request lets each generated class unload once the request finishes — memory is
     bounded by in-flight requests, not total requests.
4. **Monitoring** — the service logs **every `/run` request** (client IP, timing,
   the submitted script, and outcome: `ok` / `error` / `timeout`, plus rejects) and any
   unexpected internal errors with full stack traces. Logs go to rolling files under
   `/var/log/jactl-playground/` (`jactl-playground.<gen>.log`) **and** the console
   (captured by the journal, so `journalctl -u jactl-playground` also works). Each file
   rotates at `logMaxBytes` (default 1 GB) and at most `logCount` (default 10) files are
   kept — older generations are overwritten, so disk use is bounded at ~10 GB. Watch for
   CPU/memory spikes as a signal of abuse or an escape attempt.

### On CORS — not real access control
CORS only restricts which *websites' browser JS* can read the response; it does nothing
against direct `curl`/bot calls. Set it (it stops lazy cross-site embeds) but don't
treat it as security. **Here CORS lives in the service** (exact-origin
`https://jactl.io`, plus the OPTIONS preflight) — not in nginx — so the two never emit
duplicate `Access-Control-Allow-Origin` headers. To move it to nginx instead, start the
service with `-Djactl.playground.corsOrigin=off` and uncomment the CORS block in
`nginx/api.jactl.io.conf`.

## Files here

| File | Purpose |
|------|---------|
| `provision.sh` | One-shot VM setup: packages, service user, jar, systemd, nginx, ufw, certbot. |
| `systemd/jactl-playground.service` | The eval service unit (resource caps + JVM-safe hardening). |
| `nginx/api.jactl.io.conf` | nginx site: TLS (via certbot), rate limit, size cap, proxy to `127.0.0.1:8080`. |

## Deploy

```bash
# 1. Build the self-contained deployment archive (from repo root).
#    Bundles the fat jar + provision.sh + systemd/nginx config into one file.
./gradlew :playground-server:deployTar     # or deployZip / deployDist
#    -> playground-server/build/deploy-dist/jactl-playground-deploy-<version>.tar.gz

# 2. DNS: add A record  api.jactl.io -> VM's reserved public IP  (Namecheap Advanced DNS)
#    and open 80/443 in the Oracle VCN Security List. Verify: dig +short api.jactl.io

# 3. Copy the one archive to the VM, unpack, and run provision.sh from inside it.
scp playground-server/build/deploy-dist/jactl-playground-deploy-<version>.tar.gz  vm:
ssh vm
tar xzf jactl-playground-deploy-<version>.tar.gz
cd jactl-playground-<version>
sudo CERTBOT_EMAIL=you@example.com ./provision.sh
```

The archive unpacks to `jactl-playground-<version>/` laid out exactly how
`provision.sh` expects — the jar as a sibling `jactl-playground.jar`, plus the
`systemd/` and `nginx/` config — so `./provision.sh` needs no arguments. (A `.zip`
with identical contents is produced by `deployZip` if you prefer.)

## Local development

Run the service locally for testing:

```bash
./gradlew :playground-server:shadowJar
java -Djactl.playground.port=18080 -Djactl.playground.corsOrigin='*' \
     -jar playground-server/build/libs/jactl-playground-<version>.jar
```

**Gotcha — always restart the JVM after rebuilding the jar.** A `-jar` JVM opens
the archive at startup and loads classes *lazily*, so a rebuild that overwrites the
jar file while the old process is still running corrupts any class not yet loaded.
The failure is delayed and misleading: e.g. the server runs fine until the first
regex script (`'abc' =~ /a/`), then throws `NoClassDefFoundError:
io/jactl/runtime/RegexMatcher$1` — because that inner class is only loaded on the
first regex, and by then the jar underneath the process has changed. The class is
present in the jar; the running JVM just can't read it any more. Fix: `pkill -f
jactl-playground` and start a fresh JVM on the rebuilt jar.

(Production is immune: systemd runs an installed jar that isn't rebuilt in place —
a redeploy is "copy new jar → `systemctl restart jactl-playground`", i.e. a fresh JVM.)

## Config knobs (service system properties)

All optional; defaults shown. Overridden in the systemd `ExecStart`.

| Property | Default | Meaning |
|----------|---------|---------|
| `jactl.playground.port` | `8080` | Listen port |
| `jactl.playground.bind` | `127.0.0.1` | Bind address |
| `jactl.playground.corsOrigin` | `https://jactl.io` | Allowed origin; `off`/`none`/`` disables in-service CORS |
| `jactl.playground.maxExecMs` | `5000` | Cooperative Jactl timeout (ms) |
| `jactl.playground.wallClockMs` | `8000` | Hard wall-clock kill (ms); keep > maxExecMs and < nginx proxy_read_timeout |
| `jactl.playground.maxLoops` | `10000000` | Cooperative loop-iteration cap |
| `jactl.playground.maxConcurrent` | `4` | Concurrent executions before `429` |
| `jactl.playground.maxOutputChars` | `65536` | Captured-output cap |
| `jactl.playground.logDir` | `logs` | Directory for rolling log files (systemd sets `/var/log/jactl-playground`) |
| `jactl.playground.logMaxBytes` | `1073741824` | Rotate a log file once it exceeds this size (1 GB) |
| `jactl.playground.logCount` | `10` | Max log files kept; older generations overwritten |

## Rebuild-from-scratch

Spin up a fresh minimal Ubuntu VM → reserve/attach its static public IP → point the
`api.jactl.io` A record at it → open 80/443 in the Oracle Security List → copy `deploy/`
+ the jar → run `provision.sh`. Keep this dir in git so the whole VM is reproducible.

## When to level up
- **nginx on a separate VM / managed LB** — once a long-running script risks starving
  nginx of CPU, or once there's more than one eval instance behind a shared gateway.
- **Ansible / Packer instead of `provision.sh`** — once there's more than one VM or
  staged rollouts / secrets management are needed. Overkill for a single VM.
- **Subprocess/container-per-request** — if abuse or sandbox-escape attempts appear.
