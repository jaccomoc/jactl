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
 *
 */

package io.jactl.benchmarks;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.jactl.JactlContext;
import io.jactl.JactlScript;
import io.jactl.compiler.Compiler;
import org.apache.commons.jexl3.*;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Side-by-side JMH benchmarks comparing Jactl, JEXL, Groovy, and MVEL on
 * equivalent scripts.
 *
 * Prerequisites:
 *   cd ../commons-jexl && mvn install -DskipTests
 *
 * Run with:
 *   ./gradlew jmh && java -jar build/libs/jactl-*-jmh.jar JactlVsJexlBenchmarks
 *
 * Each benchmark scenario has _jactl, _jexl, _groovy, and _mvel variants
 * running the same logic. Execution benchmarks pre-compile scripts in @Setup
 * so only interpreter/runtime overhead is measured. Compilation benchmarks
 * measure parse + codegen cost.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1)
//@Fork(value = 0)
public class JactlVsJexlBenchmarks {

  // =========================================================================
  // JEXL state
  // =========================================================================

  private JexlEngine jexlEngine;

  // Pre-compiled JEXL expressions/scripts
  private JexlExpression jexlArithmeticExpr;
  private JexlScript     jexlScriptWithVars;
  private JexlScript     jexlCollectionLoop;
  private JexlScript     jexlStringOps;
  private JexlScript     jexlConditional;
  private JexlScript     jexlFibonacci;
  private JexlScript     jexlClosureCurrying;
  private JexlScript     jexlComplexScript;

  // JEXL contexts (pre-built, reused across iterations)
  private MapContext jexlArithCtx;
  private MapContext jexlStringCtx;
  private MapContext jexlOrdersCtx;
  private MapContext jexlEmptyCtx = new  MapContext();

  // =========================================================================
  // Jactl state
  // =========================================================================

  private JactlContext jactlContext;

  // Pre-compiled Jactl scripts
  private JactlScript jactlArithmeticScript;
  private JactlScript jactlScriptWithVars;
  private JactlScript jactlCollectionLoop;
  private JactlScript jactlStringOps;
  private JactlScript jactlConditionalHit;
  private JactlScript jactlConditionalFallthrough;
  private JactlScript jactlFibonacci;
  private JactlScript jactlClosureCurrying;
  private JactlScript jactlComplexScript;

  // Pre-built Jactl binding maps (reused across iterations)
  private Map<String, Object> jactlOrderBindings;
  private Map<String, Object> jactlArithBindings;
  private Map<String, Object> jactlVarBindings;
  private Map<String, Object> jactlListBindings;
  private Map<String, Object> jactlStringBindings;
  private Map<String, Object> jactlScoreHitBindings;
  private Map<String, Object> jactlScoreFallthroughBindings;
  private Map<String, Object> jactlFibBindings;
  private Map<String, Object> jactlCurryBindings;

  // Counter for generating unique class names in compile benchmarks
  private int compileCounter;

  // =========================================================================
  // Groovy state
  // =========================================================================

  private GroovyShell groovyShell;

  // Pre-compiled Groovy script classes (a fresh Script + Binding is created per benchmark call)
  private Class<?> groovyArithmeticClass;
  private Class<?> groovyScriptWithVarsClass;
  private Class<?> groovyCollectionLoopClass;
  private Class<?> groovyStringOpsClass;
  private Class<?> groovyConditionalClass;
  private Class<?> groovyFibonacciClass;
  private Class<?> groovyClosureCurryingClass;
  private Class<?> groovyComplexClass;

  // Pre-built Groovy variable maps (reused across iterations; wrapped in a fresh Binding per call)
  private Map<String, Object> groovyArithVars;
  private Map<String, Object> groovyVarVars;
  private Map<String, Object> groovyListVars;
  private Map<String, Object> groovyStringVars;
  private Map<String, Object> groovyScoreHitVars;
  private Map<String, Object> groovyScoreFallthroughVars;
  private Map<String, Object> groovyFibVars;
  private Map<String, Object> groovyCurryVars;
  private Map<String, Object> groovyOrdersVars;

  // =========================================================================
  // MVEL state
  // =========================================================================

  // Pre-compiled MVEL expressions/scripts
  private Serializable mvelArithmeticExpr;
  private Serializable mvelScriptWithVars;
  private Serializable mvelCollectionLoop;
  private Serializable mvelStringOps;
  private Serializable mvelConditional;
  private Serializable mvelFibonacci;
  private Serializable mvelClosureCurrying;
  private Serializable mvelComplexScript;

  // Pre-built MVEL variable maps (reused across iterations)
  private Map<String, Object> mvelArithVars;
  private Map<String, Object> mvelVarVars;
  private Map<String, Object> mvelListVars;
  private Map<String, Object> mvelStringVars;
  private Map<String, Object> mvelScoreHitVars;
  private Map<String, Object> mvelScoreFallthroughVars;
  private Map<String, Object> mvelFibVars;
  private Map<String, Object> mvelCurryVars;
  private Map<String, Object> mvelOrdersVars;

  // =========================================================================
  // Shared data
  // =========================================================================

  private List<Integer>              numberList;
  private List<Map<String, Object>>  ordersList;

  final String JEXL_COMPLEX_SCRIPT =
    "var totals    = {:};\n" +
    "var itemCount = 0;\n" +
    "var grandTotal = 0.0;\n" +
    "var topCategory = '';\n" +
    "var topAmount = -1.0;\n" +
    "\n" +
    "for (order : orders) {\n" +
    "    var price    = order.price;\n" +
    "    var qty      = order.quantity;\n" +
    "    var category = order.category;\n" +
    "\n" +
    "    var discount = 0.0;\n" +
    "    if      (qty >= 100) { discount = 0.20; }\n" +
    "    else if (qty >=  50) { discount = 0.10; }\n" +
    "    else if (qty >=  20) { discount = 0.05; }\n" +
    "\n" +
    "    var lineTotal = price * qty * (1.0 - discount);\n" +
    "\n" +
    "    if (totals[category] == null) {\n" +
    "      totals[category] = 0.0;\n" +
    "    }\n" +
    "    totals[category] = totals[category] + lineTotal;\n" +
    "    grandTotal       = grandTotal + lineTotal;\n" +
    "    itemCount        = itemCount + 1;\n" +
    "\n" +
    "    if (totals[category] > topAmount) {\n" +
    "        topAmount   = totals[category];\n" +
    "        topCategory = category;\n" +
    "    }\n" +
    "}\n" +
    "\n" +
    "'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory";

  private static final String JACTL_COMPLEX_SCRIPT =
    "var totals    = [:];\n" +
    "var itemCount = 0;\n" +
    "var grandTotal = 0.0;\n" +
    "var topCategory = '';\n" +
    "var topAmount = -1.0;\n" +
    "\n" +
    "for (order : orders) {\n" +
    "    var price    = order.price;\n" +
    "    var qty      = order.quantity;\n" +
    "    var category = order.category;\n" +
    "\n" +
    "    var discount = 0.0;\n" +
    "    if      (qty >= 100) { discount = 0.20; }\n" +
    "    else if (qty >=  50) { discount = 0.10; }\n" +
    "    else if (qty >=  20) { discount = 0.05; }\n" +
    "\n" +
    "    var lineTotal = price * qty * (1.0 - discount);\n" +
    "\n" +
    "    if (totals[category] == null) {\n" +
    "      totals[category] = 0.0;\n" +
    "    }\n" +
    "    totals[category] = totals[category] + lineTotal;\n" +
    "    grandTotal       = grandTotal + lineTotal;\n" +
    "    itemCount        = itemCount + 1;\n" +
    "\n" +
    "    if (totals[category] > topAmount) {\n" +
    "        topAmount   = totals[category];\n" +
    "        topCategory = category;\n" +
    "    }\n" +
    "}\n" +
    "\n" +
    "'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory";

  private static final String GROOVY_COMPLEX_SCRIPT =
    "def totals    = [:]\n" +
    "def itemCount = 0\n" +
    "def grandTotal = 0.0\n" +
    "def topCategory = ''\n" +
    "def topAmount = -1.0\n" +
    "\n" +
    "for (order in orders) {\n" +
    "    def price    = order.price\n" +
    "    def qty      = order.quantity\n" +
    "    def category = order.category\n" +
    "\n" +
    "    def discount = 0.0\n" +
    "    if      (qty >= 100) { discount = 0.20 }\n" +
    "    else if (qty >=  50) { discount = 0.10 }\n" +
    "    else if (qty >=  20) { discount = 0.05 }\n" +
    "\n" +
    "    def lineTotal = price * qty * (1.0 - discount)\n" +
    "\n" +
    "    if (totals[category] == null) {\n" +
    "      totals[category] = 0.0\n" +
    "    }\n" +
    "    totals[category] = totals[category] + lineTotal\n" +
    "    grandTotal       = grandTotal + lineTotal\n" +
    "    itemCount        = itemCount + 1\n" +
    "\n" +
    "    if (totals[category] > topAmount) {\n" +
    "        topAmount   = totals[category]\n" +
    "        topCategory = category\n" +
    "    }\n" +
    "}\n" +
    "\n" +
    "'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory";

  private static final String MVEL_COMPLEX_SCRIPT =
    "totals    = new java.util.HashMap();\n" +
    "itemCount = 0;\n" +
    "grandTotal = 0.0;\n" +
    "topCategory = '';\n" +
    "topAmount = -1.0;\n" +
    "\n" +
    "foreach (order : orders) {\n" +
    "    price    = order.price;\n" +
    "    qty      = order.quantity;\n" +
    "    category = order.category;\n" +
    "\n" +
    "    discount = 0.0;\n" +
    "    if      (qty >= 100) { discount = 0.20; }\n" +
    "    else if (qty >=  50) { discount = 0.10; }\n" +
    "    else if (qty >=  20) { discount = 0.05; }\n" +
    "\n" +
    "    lineTotal = price * qty * (1.0 - discount);\n" +
    "\n" +
    "    if (totals[category] == null) {\n" +
    "      totals[category] = 0.0;\n" +
    "    }\n" +
    "    totals[category] = totals[category] + lineTotal;\n" +
    "    grandTotal       = grandTotal + lineTotal;\n" +
    "    itemCount        = itemCount + 1;\n" +
    "\n" +
    "    if (totals[category] > topAmount) {\n" +
    "        topAmount   = totals[category];\n" +
    "        topCategory = category;\n" +
    "    }\n" +
    "}\n" +
    "\n" +
    "'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory";

  // =========================================================================
  // Setup
  // =========================================================================

  @Setup(Level.Iteration)
  public void setup() {
    setupSharedData();
    setupJexl();
    setupJactl();
    setupGroovy();
    setupMvel();
    compileCounter = 0;
  }

  private void setupSharedData() {
    numberList = new ArrayList<>(100);
    for (int i = 1; i <= 100; i++) {
      numberList.add(i);
    }

    String[] categories = {"Electronics", "Clothing", "Food", "Books", "Sports"};
    int[] prices     = {50, 25, 10, 15, 40, 80, 12, 30,  5, 60, 20, 45,  8, 35, 55, 18, 70, 22, 14, 90};
    int[] quantities = {10, 45, 80, 25,110, 60, 30, 15, 95,  5, 55, 20, 75, 40, 12, 65, 35,100, 50,  8};
    ordersList = new ArrayList<>(20);
    for (int i = 0; i < 20; i++) {
      Map<String, Object> order = new HashMap<>();
      order.put("category", categories[i % categories.length]);
      order.put("price",    prices[i]);
      order.put("quantity", quantities[i]);
      ordersList.add(order);
    }
  }

  private void setupJexl() {
    jexlEngine = new JexlBuilder().cache(0).strict(false).silent(false).create();

    // Pre-compiled expressions/scripts
    jexlArithmeticExpr  = jexlEngine.createExpression("(a + b) * c - d / 2 + e % 3");
    jexlScriptWithVars  = jexlEngine.createScript("var result = x * x + y * y; result + z", "x", "y", "z");
    jexlCollectionLoop  = jexlEngine.createScript("var sum = 0; for (item : list) { sum += item; } sum", "list");
    jexlStringOps       = jexlEngine.createScript(
        "var s = first + ' ' + last; s.toUpperCase() + ' (' + s.length() + ')'", "first", "last");
    jexlConditional     = jexlEngine.createScript(
        "if (score >= 90) { 'A'; }"
      + "else if (score >= 80) { 'B'; }"
      + "else if (score >= 70) { 'C'; }"
      + "else if (score >= 60) { 'D'; }"
      + "else { 'F'; }", "score");
    jexlFibonacci       = jexlEngine.createScript(
        "function fib(n) { if (n <= 1) { return n; } return fib(n - 1) + fib(n - 2); } fib(n)", "n");
    jexlClosureCurrying = jexlEngine.createScript(
        "var multiplier = factor -> x -> x * factor;"
      + "var double = multiplier(2);"
      + "var triple = multiplier(3);"
      + "for (var i = 0; i < 100; i++) { triple(double(value)) }", "value");

    jexlComplexScript = jexlEngine.createScript(JEXL_COMPLEX_SCRIPT, "orders");

    // Pre-built contexts
    jexlArithCtx = new MapContext();
    jexlArithCtx.set("a", 10);
    jexlArithCtx.set("b", 5);
    jexlArithCtx.set("c", 3);
    jexlArithCtx.set("d", 8);
    jexlArithCtx.set("e", 7);

    jexlStringCtx = new MapContext();
    jexlStringCtx.set("first", "John");
    jexlStringCtx.set("last", "Doe");
    
    jexlOrdersCtx = new MapContext();
    jexlOrdersCtx.set("order", ordersList);
  }

  private void setupJactl() {
    jactlContext = JactlContext.create().async(false).build();

    // Binding maps: values determine the type Jactl infers for each global
    jactlArithBindings        = new HashMap<>();
    jactlArithBindings.put("a", 10);  jactlArithBindings.put("b", 5);
    jactlArithBindings.put("c", 3);   jactlArithBindings.put("d", 8);
    jactlArithBindings.put("e", 7);
    jactlArithBindings.put("i", 0);

    jactlVarBindings          = new HashMap<>();
    jactlVarBindings.put("x", 3);  jactlVarBindings.put("y", 4);  jactlVarBindings.put("z", 10);

    jactlListBindings         = new HashMap<>();
    jactlListBindings.put("list", numberList);

    jactlStringBindings       = new HashMap<>();
    jactlStringBindings.put("first", "John");
    jactlStringBindings.put("last", "Doe");

    jactlScoreHitBindings         = Collections.singletonMap("score", 95);
    jactlScoreFallthroughBindings = Collections.singletonMap("score", 50);

    jactlFibBindings          = Collections.singletonMap("n", 15);

    jactlCurryBindings        = new HashMap<>();
    jactlCurryBindings.put("value", 7);

    // Pre-compile all Jactl scripts
    jactlContext.debugLevel = 0;
    jactlArithmeticScript = Compiler.compileScript(
        "(a + b) * c - d / 2 + e % 3",
        jactlContext, jactlArithBindings);
    jactlContext.debugLevel = 0;

    jactlScriptWithVars = Compiler.compileScript(
        "var result = x * x + y * y; result + z",
        jactlContext, jactlVarBindings);

    jactlCollectionLoop = Compiler.compileScript("var sum = 0; for (item in list) { sum += item }; sum",
                                                 jactlContext, jactlListBindings);

    jactlStringOps = Compiler.compileScript(
        "var s = first + ' ' + last; s.toUpperCase() + ' (' + s.size() + ')'",
        jactlContext, jactlStringBindings);

    // One compiled script handles both score cases (the binding changes at eval time)
    String conditionalSrc =
        "if (score >= 90) { 'A' }"
      + "else if (score >= 80) { 'B' }"
      + "else if (score >= 70) { 'C' }"
      + "else if (score >= 60) { 'D' }"
      + "else { 'F' }";
    jactlConditionalHit         = Compiler.compileScript(conditionalSrc, jactlContext, jactlScoreHitBindings);
    jactlConditionalFallthrough = Compiler.compileScript(conditionalSrc, jactlContext, jactlScoreFallthroughBindings);

    jactlFibonacci = Compiler.compileScript(
        "long fib(int x) { x <= 1 ? x : fib(x - 1) + fib(x - 2) }; fib(n)",
        jactlContext, jactlFibBindings);

    jactlClosureCurrying = Compiler.compileScript(
        "var multiplier = { factor -> { x -> x * factor } };"
        + "var twice = multiplier(2);"
        + "var triple = multiplier(3);"
        + "for (int i = 0; i < 100; i++) { triple(twice(value)) }",
        jactlContext, jactlCurryBindings);

    jactlOrderBindings = Collections.singletonMap("orders", ordersList);
    jactlComplexScript = Compiler.compileScript(JACTL_COMPLEX_SCRIPT, jactlContext, jactlOrderBindings);
  }

  private void setupGroovy() {
    groovyShell = new GroovyShell();

    // Pre-compile script classes once; each benchmark call creates a fresh
    // Script instance with a fresh Binding wrapping the per-script vars map.
    groovyArithmeticClass     = groovyShell.getClassLoader().parseClass("(a + b) * c - d / 2 + e % 3");
    groovyScriptWithVarsClass = groovyShell.getClassLoader().parseClass("def result = x * x + y * y; result + z");
    groovyCollectionLoopClass = groovyShell.getClassLoader().parseClass("def sum = 0; for (item in list) { sum += item }; sum");
    groovyStringOpsClass      = groovyShell.getClassLoader().parseClass(
        "def s = first + ' ' + last; s.toUpperCase() + ' (' + s.length() + ')'");

    String groovyConditionalSrc =
        "if (score >= 90) { 'A' }"
      + "else if (score >= 80) { 'B' }"
      + "else if (score >= 70) { 'C' }"
      + "else if (score >= 60) { 'D' }"
      + "else { 'F' }";
    groovyConditionalClass = groovyShell.getClassLoader().parseClass(groovyConditionalSrc);

    groovyFibonacciClass = groovyShell.getClassLoader().parseClass(
        "long fib(int n) { if (n <= 1) return n; return fib(n - 1) + fib(n - 2) }; fib(n)");

    groovyClosureCurryingClass = groovyShell.getClassLoader().parseClass(
        "def multiplier = { factor -> { x -> x * factor } };"
        + "def twice = multiplier(2);"
        + "def triple = multiplier(3);"
        + "for (int i = 0; i < 100; i++) { triple(twice(value)) }");
    
    groovyComplexClass = groovyShell.getClassLoader().parseClass(GROOVY_COMPLEX_SCRIPT);

    // Pre-built variable maps (reused across iterations; wrapped in a fresh Binding per call)
    groovyArithVars = new HashMap<>();
    groovyArithVars.put("a", 10);
    groovyArithVars.put("b", 5);
    groovyArithVars.put("c", 3);
    groovyArithVars.put("d", 8);
    groovyArithVars.put("e", 7);

    groovyVarVars = new HashMap<>();
    groovyVarVars.put("x", 3);
    groovyVarVars.put("y", 4);
    groovyVarVars.put("z", 10);

    groovyListVars = new HashMap<>();
    groovyListVars.put("list", numberList);

    groovyStringVars = new HashMap<>();
    groovyStringVars.put("first", "John");
    groovyStringVars.put("last", "Doe");

    groovyScoreHitVars = new HashMap<>();
    groovyScoreHitVars.put("score", 95);
    groovyScoreFallthroughVars = new HashMap<>();
    groovyScoreFallthroughVars.put("score", 50);

    groovyFibVars = new HashMap<>();
    groovyFibVars.put("n", 15);

    groovyCurryVars = new HashMap<>();
    groovyCurryVars.put("value", 7);

    groovyOrdersVars = new HashMap<>();
    groovyOrdersVars.put("orders", ordersList);
  }

  private void setupMvel() {
    // Pre-compile all MVEL expressions/scripts
    mvelArithmeticExpr  = MVEL.compileExpression("(a + b) * c - d / 2 + e % 3");
    mvelScriptWithVars  = MVEL.compileExpression("result = x * x + y * y; result + z");
    mvelCollectionLoop  = MVEL.compileExpression("sum = 0; foreach (item : list) { sum += item; } sum");
    mvelStringOps       = MVEL.compileExpression(
        "s = first + ' ' + last; s.toUpperCase() + ' (' + s.length() + ')'");

    String mvelConditionalSrc =
        "if (score >= 90) { 'A'; }"
      + "else if (score >= 80) { 'B'; }"
      + "else if (score >= 70) { 'C'; }"
      + "else if (score >= 60) { 'D'; }"
      + "else { 'F'; }";
    mvelConditional = MVEL.compileExpression(mvelConditionalSrc);

    mvelFibonacci = MVEL.compileExpression(
        "def fib(n) { if (n <= 1) return n; return fib(n - 1) + fib(n - 2); }; fib(n);");

    mvelComplexScript = MVEL.compileExpression(MVEL_COMPLEX_SCRIPT);

    // Pre-built variable maps (reused across iterations)
    mvelArithVars = new HashMap<>();
    mvelArithVars.put("a", 10);  mvelArithVars.put("b", 5);
    mvelArithVars.put("c", 3);   mvelArithVars.put("d", 8);
    mvelArithVars.put("e", 7);

    mvelVarVars = new HashMap<>();
    mvelVarVars.put("x", 3);  mvelVarVars.put("y", 4);  mvelVarVars.put("z", 10);

    mvelListVars = new HashMap<>();
    mvelListVars.put("list", numberList);

    mvelStringVars = new HashMap<>();
    mvelStringVars.put("first", "John");
    mvelStringVars.put("last", "Doe");

    mvelScoreHitVars = new HashMap<>();
    mvelScoreHitVars.put("score", 95);
    mvelScoreFallthroughVars = new HashMap<>();
    mvelScoreFallthroughVars.put("score", 50);

    mvelFibVars = new HashMap<>();
    mvelFibVars.put("n", 15);

    mvelCurryVars = new HashMap<>();
    mvelCurryVars.put("value", 7);

    mvelOrdersVars = new HashMap<>();
    mvelOrdersVars.put("orders", ordersList);
  }

  // =========================================================================
  // Benchmark 1: Expression/script compilation
  //
  // Measures the cost of parsing + code generation for a fresh script.
  // JEXL: builds an AST. Jactl: generates JVM bytecode via ASM.
  // Each call uses a unique class name to avoid context-level caching.
  // =========================================================================

  @Benchmark
  public Object compileExpression_jexl() {
    return jexlEngine.createExpression("(a + b) * c - d / 2 + e % 3");
  }

  @Benchmark
  public Object compileExpression_jactl() {
    return Compiler.compileScript("(a + b) * c - d / 2 + e % 3", jactlContext, "ArithExpr" + compileCounter++, jactlArithBindings);
  }

  @Benchmark
  public Object compileExpression_groovy() {
    return groovyShell.parse("(a + b) * c - d / 2 + e % 3");
  }

  @Benchmark
  public Object compileExpression_mvel() {
    return MVEL.compileExpression("(a + b) * c - d / 2 + e % 3");
  }

  @Benchmark
  public Object compileScript_jexl() {
    return jexlEngine.createScript(JEXL_COMPLEX_SCRIPT);
  }

  @Benchmark
  public Object compileScript_jactl() {
    return Compiler.compileScript(JACTL_COMPLEX_SCRIPT, jactlContext, "Script", "", jactlVarBindings);
  }

  @Benchmark
  public Object compileScript_groovy() {
    return groovyShell.parse(GROOVY_COMPLEX_SCRIPT);
  }

  @Benchmark
  public Object compileScript_mvel() {
    return MVEL.compileExpression(MVEL_COMPLEX_SCRIPT);
  }

  // =========================================================================
  // Benchmark 2: Simple arithmetic (pre-compiled)
  //
  // Pure interpreter/runtime throughput — no compilation cost.
  // Tests numeric operations and context/binding variable lookup.
  // =========================================================================

  @Benchmark
  public Object arithmetic_jexl() {
    return jexlArithmeticExpr.evaluate(jexlArithCtx);
  }

  @Benchmark
  public Object arithmetic_jactl() {
    return jactlArithmeticScript.eval(jactlArithBindings);
  }

  @Benchmark
  public Object arithmetic_groovy() {
    return InvokerHelper.createScript(groovyArithmeticClass, new Binding(groovyArithVars)).run();
  }

  @Benchmark
  public Object arithmetic_mvel() {
    return MVEL.executeExpression(mvelArithmeticExpr, mvelArithVars);
  }

  // =========================================================================
  // Benchmark 3: Script with variables
  //
  // Short multi-statement script using externally-supplied values.
  // Tests parameter/global passing overhead and local variable handling.
  // =========================================================================

  @Benchmark
  public Object scriptWithVars_jexl() {
    return jexlScriptWithVars.execute(null, 3, 4, 10);
  }

  @Benchmark
  public Object scriptWithVars_jactl() {
    return jactlScriptWithVars.eval(jactlVarBindings);
  }

  @Benchmark
  public Object scriptWithVars_groovy() {
    return InvokerHelper.createScript(groovyScriptWithVarsClass, new Binding(groovyVarVars)).run();
  }

  @Benchmark
  public Object scriptWithVars_mvel() {
    return MVEL.executeExpression(mvelScriptWithVars, mvelVarVars);
  }

  // =========================================================================
  // Benchmark 4: Collection iteration
  //
  // for-each over a 100-element List, accumulating a sum.
  // Tests loop overhead and Java collection interop.
  // =========================================================================

  @Benchmark
  public Object collectionLoop_jexl() {
    return jexlCollectionLoop.execute(jexlEmptyCtx, numberList);
  }

  @Benchmark public Object collectionLoop_jactl() { return jactlCollectionLoop.eval(jactlListBindings); }

  @Benchmark
  public Object collectionLoop_groovy() {
    return InvokerHelper.createScript(groovyCollectionLoopClass, new Binding(groovyListVars)).run();
  }

  @Benchmark
  public Object collectionLoop_mvel() {
    return MVEL.executeExpression(mvelCollectionLoop, mvelListVars);
  }

  // =========================================================================
  // Benchmark 5: String operations
  //
  // String concatenation plus reflective/built-in method dispatch
  // (toUpperCase, length/size). Tests string handling overhead.
  // =========================================================================

  @Benchmark
  public Object stringOperations_jexl() {
    return jexlStringOps.execute(null, "John", "Doe");
  }

  @Benchmark
  public Object stringOperations_jactl() {
    return jactlStringOps.eval(jactlStringBindings);
  }

  @Benchmark
  public Object stringOperations_groovy() {
    return InvokerHelper.createScript(groovyStringOpsClass, new Binding(groovyStringVars)).run();
  }

  @Benchmark
  public Object stringOperations_mvel() {
    return MVEL.executeExpression(mvelStringOps, mvelStringVars);
  }

  // =========================================================================
  // Benchmark 6: Conditional branching — first branch taken
  //
  // score=95 matches the first if-branch immediately.
  // Tests AST/bytecode branch evaluation with an early exit.
  // =========================================================================

  @Benchmark
  public Object conditionalHit_jexl() {
    return jexlConditional.execute(null, 95);
  }

  @Benchmark
  public Object conditionalHit_jactl() {
    return jactlConditionalHit.eval(jactlScoreHitBindings);
  }

  @Benchmark
  public Object conditionalHit_groovy() {
    return InvokerHelper.createScript(groovyConditionalClass, new Binding(groovyScoreHitVars)).run();
  }

  @Benchmark
  public Object conditionalHit_mvel() {
    return MVEL.executeExpression(mvelConditional, mvelScoreHitVars);
  }

  // =========================================================================
  // Benchmark 7: Conditional branching — fall through to else
  //
  // score=50 falls through all four if/else-if guards to the else clause.
  // Tests the cost of evaluating all branch conditions before finding a match.
  // =========================================================================

  @Benchmark
  public Object conditionalFallthrough_jexl() {
    return jexlConditional.execute(null, 50);
  }

  @Benchmark
  public Object conditionalFallthrough_jactl() {
    return jactlConditionalFallthrough.eval(jactlScoreFallthroughBindings);
  }

  @Benchmark
  public Object conditionalFallthrough_groovy() {
    return InvokerHelper.createScript(groovyConditionalClass, new Binding(groovyScoreFallthroughVars)).run();
  }

  @Benchmark
  public Object conditionalFallthrough_mvel() {
    return MVEL.executeExpression(mvelConditional, mvelScoreFallthroughVars);
  }

  // =========================================================================
  // Benchmark 8: Recursive function call — fib(15)
  //
  // fib(15) = 610, requiring 1,973 recursive calls.
  // Tests call-frame allocation and function dispatch.
  // JEXL resolves function references dynamically; Jactl emits bytecode calls.
  // =========================================================================

  @Benchmark
  public Object recursiveFib_jexl() {
    return jexlFibonacci.execute(null, 15);
  }

  @Benchmark
  public Object recursiveFib_jactl() {
    return jactlFibonacci.eval(jactlFibBindings);
  }

  @Benchmark
  public Object recursiveFib_groovy() {
    return InvokerHelper.createScript(groovyFibonacciClass, new Binding(groovyFibVars)).run();
  }

  @Benchmark
  public Object recursiveFib_mvel() {
    Map vars = new HashMap(mvelFibVars);
    return MVEL.executeExpression(mvelFibonacci, vars);
  }

  // =========================================================================
  // Benchmark 9: Closure currying
  //
  // A function that returns a function (multiplier), partially applied to
  // produce a "triple" closure, which is then invoked on a value.
  // Tests closure/lambda allocation and captured-variable access.
  // =========================================================================

  @Benchmark
  public Object closureCurrying_jexl() {
    return jexlClosureCurrying.execute(null, 7);
  }

  @Benchmark
  public Object closureCurrying_jactl() {
    return jactlClosureCurrying.eval(jactlCurryBindings);
  }

  @Benchmark
  public Object closureCurrying_groovy() {
    return InvokerHelper.createScript(groovyClosureCurryingClass, new Binding(groovyCurryVars)).run();
  }

//  @Benchmark
//  public Object closureCurrying_mvel() {
//    return MVEL.executeExpression(mvelClosureCurrying, mvelCurryVars);
//  }

  // =========================================================================
  // Benchmark 10: Complex script — order processing with tiered discounts
  //
  // ~30-line script iterating over 20 order records. Applies a three-tier
  // volume discount per order, accumulates per-category revenue into a map,
  // and tracks the top-earning category — all in a single loop.
  // Exercises: map literal, for-each, conditionals, map reads/writes,
  // floating-point arithmetic, and string concatenation.
  // =========================================================================

  @Benchmark
  public Object complexScript_jexl() {
    String result = (String)jexlComplexScript.execute(jexlEmptyCtx, ordersList);
    assert result.equals("Processed 20 orders. Grand total: 22870.0. Top category: Electronics") : "Result error: " + result;
    return result;
  }

  @Benchmark
  public Object complexScript_jactl() {
    String result = (String)jactlComplexScript.eval(jactlOrderBindings);
    assert result.equals("Processed 20 orders. Grand total: 22870.00. Top category: Electronics") : "Result error: " + result;
    return result;
  }

  @Benchmark
  public Object complexScript_groovy() {
    String result = (String)InvokerHelper.createScript(groovyComplexClass, new Binding(groovyOrdersVars)).run();
    assert result.equals("Processed 20 orders. Grand total: 22870.0. Top category: Electronics") : "Result error: " + result;
    return result;
  }

  @Benchmark
  public Object complexScript_mvel() {
    String result = (String)MVEL.executeExpression(mvelComplexScript, mvelOrdersVars);
    assert result.equals("Processed 20 orders. Grand total: 22870.0. Top category: Electronics") : "Result error: " + result;
    return result;
  }

  // =========================================================================
  // main() — run all benchmarks from the command line
  // =========================================================================

  public static void main(String[] args) throws RunnerException {
    Options opts = new OptionsBuilder()
                     .include(JactlVsJexlBenchmarks.class.getSimpleName())
                     //.include(JactlVsJexlBenchmarks.class.getSimpleName() + "." + "complexScript_jexl")
                     //.include(JactlVsJexlBenchmarks.class.getSimpleName() + "." + "complexScript_jactl")
                     .build();
    new Runner(opts).run();
  }
  
  void testJexl() {
    JexlEngine engine = new JexlBuilder()
                          .cache(256)
                          .strict(false)
                          .silent(false)
                          .namespaces(Collections.singletonMap("debug", System.out))
                          .create();
    MapContext context = new MapContext();
    context.set("result", "");
    JexlScript script = engine.createScript(
      "var x = 1;\n" +
      "var y = 2;\n" +
      "for (i : [1,2,3]) {\n" +
      "  if (x + y > 1) {\n" +
      "    x = 3;\n" +
      "    y = 4;\n" +
      "  }\n" +
      "}\n" +
      "result = x + y;\n");
    script.execute(context);
    System.out.println(context.get("result"));

    final String JEXL_COMPLEX_SCRIPT =
      "var totals    = {:};\n" +
      "var itemCount = 0;\n" +
      "var grandTotal = 0.0;\n" +
      "var topCategory = '';\n" +
      "var topAmount = -1.0;\n" +
      "\n" +
      "for (order : orders) {\n" +
      "    var price    = order.price;\n" +
      "    var qty      = order.quantity;\n" +
      "    var category = order.category;\n" +
      "\n" +
      "    var discount = 0.0;\n" +
      "    if      (qty >= 100) { discount = 0.20; }\n" +
      "    else if (qty >=  50) { discount = 0.10; }\n" +
      "    else if (qty >=  20) { discount = 0.05; }\n" +
      "\n" +
      "    var lineTotal = price * qty * (1.0 - discount);\n" +
      "\n" +
      "    if (totals[category] == null) {\n" +
      "      totals[category] = 0.0;\n" +
      "    }\n" +
      "    totals[category] = totals[category] + lineTotal;\n" +
      "    grandTotal       = grandTotal + lineTotal;\n" +
      "    itemCount        = itemCount + 1;\n" +
      "\n" +
      "    if (totals[category] > topAmount) {\n" +
      "        topAmount   = totals[category];\n" +
      "        topCategory = category;\n" +
      "    }\n" +
      "}\n" +
      "\n" +
      "'Processed ' + itemCount + ' orders. Grand total: ' + grandTotal + '. Top category: ' + topCategory";
    
    setupSharedData();
    
    script = engine.createScript(JEXL_COMPLEX_SCRIPT, "orders");
    System.out.println(script.execute(jexlEmptyCtx,  ordersList));
  }
}
