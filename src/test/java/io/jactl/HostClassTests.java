/*
 * Copyright © 2022,2023,2024  James Crawford
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

package io.jactl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class HostClassTests extends BaseTest {
  
  public static class NewBaseType {
    static boolean throwException = false;
    public NewBaseType() {}
    public NewBaseType(int i) {}
    public NewBaseType(String throwException) { throw new RuntimeException("throwing test exception"); }
    public String baseMethod2(NewBaseType x) { if (throwException) throw new RuntimeException("throwing test exception"); return "baseMethod";}
    public String baseMethod2(String x) { if (throwException) throw new RuntimeException("throwing test exception"); return "baseMethod: " + x;}
    public String baseMethod(String x) { if (throwException) throw new RuntimeException("throwing test exception"); return "baseMethod: " + x;}
    public static String baseStaticMethod() { if (throwException) throw new RuntimeException("throwing test exception"); return "baseStaticMethod";}
    public static String staticMethod() {  if (throwException) throw new RuntimeException("throwing test exception"); return "static in base"; }
  }

  public static class NewType extends NewBaseType {
    static boolean throwException = false;
    String prefix;
    public NewType(String prefix) {this.prefix = prefix;}
    public NewType(String prefix, int i) { throw new RuntimeException("throwing test exception"); }
    public String process(String x) { if (throwException) throw new RuntimeException("throwing test exception"); return prefix + ": " + x; }
    public void processMap(Map x) { if (throwException) throw new RuntimeException("throwing test exception"); x.put("x", "xxx"); }
    public static void staticProcessMap(Map x) { if (throwException) throw new RuntimeException("throwing test exception"); x.put("x", "xxx"); }
    public String process2(String x) { if (throwException) throw new RuntimeException("throwing test exception"); return prefix + ": " + x; }
    public String process2(int x, int y) { if (throwException) throw new RuntimeException("throwing test exception"); return prefix + ": " + x + ": " + y; }
    public static String staticMethod() { if (throwException) throw new RuntimeException("throwing test exception"); return "static"; }
    public static String staticMethod2(String x) { if (throwException) throw new RuntimeException("throwing test exception"); return "static: " + x; }
  }

  @BeforeEach
  public void beforeEach() {
    NewType.throwException = false;
    NewBaseType.throwException = false;
  }
  
  @Test public void hostAccess() {
    // Checkpointing not currently supported due to risk of malicious injection attacks
    skipCheckpointTests = true;

    globals.put("bindingVar", new NewType("prefix"));
    testError("bindingVar.process('abc')", "access to host classes not allowed");
    allowHostAccess = true;
    allowHostClassLookup = n -> n.equals(NewType.class.getName());
    test("bindingVar.process('abc')", "prefix: abc");
    test("def m = [:]; bindingVar.processMap(m); m.x", "xxx");
    test("def m = [:]; bindingVar.staticProcessMap(m); m.x", "xxx");
    test("def m = [x:'abc']; bindingVar.processMap(m)", null);
    test("def m = [x:'abc']; bindingVar.processMap(m); m.x", "xxx");
    testError("bindingVar.process(x:'abc')", "does not support named arguments");
    testError("def x = bindingVar; x.process(x:'abc')", "does not support named arguments");
    testError("def f = bindingVar.process; f(x:'abc')", "does not support named arguments");
    test("bindingVar.process2('abc')", "prefix: abc");
    test("bindingVar.staticMethod()", "static");
    test("def x = bindingVar.process; x('abc')", "prefix: abc");
    test("bindingVar.'process'('abc')", "prefix: abc");
    test("def x = 'process'; bindingVar.\"$x\"('abc')", "prefix: abc");
    test("def x = 'process'; def y = bindingVar; y.\"$x\"('abc')", "prefix: abc");
    test("def x = 'process'; def y = bindingVar; def f = y.\"$x\"; f('abc')", "prefix: abc");
    test("def m = [:]; def f = bindingVar.staticProcessMap; f(m); m.x", "xxx");
    test("def m = [:]; def f = bindingVar.\"${'staticProcessMap'}\"; f(m); m.x", "xxx");

    classAccessToGlobals = true;
    test("class X { static def fff() { def x = 'process'; def y = bindingVar; def f = y.\"$x\"; f('abc') } }; X.fff()", "prefix: abc");
    test("class X { static def fff() { def x = 'process'; def y = bindingVar; def f = y.\"$x\"; f('abc') } }; def g = X.fff; g()", "prefix: abc");
    test("class X { def fff() { def x = 'process'; def y = bindingVar; def f = y.\"$x\"; f('abc') } }; new X().fff()", "prefix: abc");
    test("class X { def fff() { def x = 'process'; def y = bindingVar; def f = y.\"$x\"; f('abc') } }; def g = new X().fff; g()", "prefix: abc");
    
    NewType.throwException = true;
    NewBaseType.throwException = true;
    testError("bindingVar.process('abc')", "error invoking host class method");
    testError("def m = [:]; bindingVar.processMap(m); m.x", "error invoking host class method");
    testError("def m = [:]; bindingVar.staticProcessMap(m); m.x", "error invoking host class method");
  }

  @Test public void hostClassLookup() {
    skipCheckpointTests = true;

    testError("io.jactl.ComplilerTest.NewType.staticMethod()", "unknown variable 'io'");

    allowHostAccess = true;
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests");
    test("io.jactl.HostClassTests.NewType.staticMethod()", "static");
    testError("io.jactl.HostClassTests.NewType.process('abc')", "could not find static public method");
    test("class X { static def fff() { io.jactl.HostClassTests.NewType.staticMethod() } }; X.fff()", "static");
    test("class X { def fff() { io.jactl.HostClassTests.NewType.staticMethod() } }; new X().fff()", "static");
    test("new io.jactl.HostClassTests.NewType('pre').process('abc')", "pre: abc");
    test("new io.jactl.HostClassTests.NewType(sleep(1,'pre')).process('abc')", "pre: abc");
    test("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.process('abc')", "pre: abc");
    test("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.'process'('abc')", "pre: abc");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); x.process('abc')", "pre: abc");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); def y = 'abc'; x.process(y)", "pre: abc");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.process; f('abc')", "pre: abc");
    test("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.staticMethod()", "static");
    test("def x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); x[0].process('abc')", "newprefix: abc");
    test("def x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); x[0].staticMethod()", "static");
    test("io.jactl.HostClassTests.NewType[] x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); x[0].staticMethod()", "static");
    test("io.jactl.HostClassTests.NewType[] x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); def y = x[0]; y.staticMethod()", "static");
    testError("class X extends io.jactl.HostClassTests.NewType { }", "cannot extend host classes (io.jactl.HostClassTests$NewType)");
    test("switch (new io.jactl.HostClassTests.NewType('pre')) { io.jactl.HostClassTests.NewType -> 'NewType' }", "NewType");
    testError("([:] as io.jactl.HostClassTests.NewType).process('abc')", "cannot convert from type map");
    testError("def x = [:]; (x as io.jactl.HostClassTests.NewType).process('abc')", "map cannot be cast");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.process; f('abc')", "pre: abc");
    test("def f = new io.jactl.HostClassTests.NewType('pre').process; f('abc')", "pre: abc");
    test("io.jactl.HostClassTests.NewType.staticMethod()", "static");
    test("def f = io.jactl.HostClassTests.NewType.staticMethod; f()", "static");
    test("def f = io.jactl.HostClassTests.NewType.staticMethod2; f('abc')", "static: abc");
    testError("def x = new io.jactl.HostClassTests.NewType(); x.process('abc')", "could not find public constructor");
    testError("import io.jactl.HostClassTests.NewType; def x = new NewType(); x.process('abc')", "could not find public constructor");
    testError("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('abc'); x.process()", "could not find public method");
    testError("def x = new io.jactl.HostClassTests.NewType('abc'); x.process()", "could not find public method");
    testError("def x = new io.jactl.HostClassTests.NewType(); x.process('abc')", "could not find public constructor");

    NewType.throwException = true;
    NewBaseType.throwException = true;
    testError("class X { static def fff() { io.jactl.HostClassTests.NewType.staticMethod() } }; X.fff()", "error invoking host class method");
    testError("class X { def fff() { io.jactl.HostClassTests.NewType.staticMethod() } }; new X().fff()", "error invoking host class method");
    testError("new io.jactl.HostClassTests.NewType('pre').process('abc')", "error invoking host class method");
    testError("new io.jactl.HostClassTests.NewType('pre', 1).process('abc')", "error invoking constructor for host class");
    testError("new io.jactl.HostClassTests.NewType(sleep(1,'pre')).process('abc')", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.process('abc')", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.'process'('abc')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); x.process('abc')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); def y = 'abc'; x.process(y)", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.process; f('abc')", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.staticMethod()", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); x[0].process('abc')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); x[0].staticMethod()", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType[] x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); x[0].staticMethod()", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType[] x = new io.jactl.HostClassTests.NewType[1]; x[0] = new io.jactl.HostClassTests.NewType('newprefix'); def y = x[0]; y.staticMethod()", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.process; f('abc')", "error invoking host class method");
    testError("def f = new io.jactl.HostClassTests.NewType('pre').process; f('abc')", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType.staticMethod()", "error invoking host class method");
    testError("def f = io.jactl.HostClassTests.NewType.staticMethod; f()", "error invoking host class method");
    testError("def f = io.jactl.HostClassTests.NewType.staticMethod2; f('abc')", "error invoking host class method");
  }

  @Test public void baseClassAccess() {
    skipCheckpointTests = true;
    allowHostAccess = true;
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests$NewType");
    testError("new io.jactl.HostClassTests.NewType('xxx').getClass() != null", "not an allowed host class");
    testError("new io.jactl.HostClassTests.NewType('xxx').baseMethod('xxx')", "not an allowed host class");
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests");
    test("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.baseMethod2(x)", "baseMethod");
    test("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.baseMethod2(new io.jactl.HostClassTests.NewBaseType())", "baseMethod");
    test("new io.jactl.HostClassTests.NewType('xxx').baseMethod('xxx')", "baseMethod: xxx");
    test("def x = 'xxx'; new io.jactl.HostClassTests.NewType('xxx').baseMethod(x)", "baseMethod: xxx");
    test("def x = new io.jactl.HostClassTests.NewType('xxx'); x.baseMethod('xxx')", "baseMethod: xxx");
    test("def f = new io.jactl.HostClassTests.NewType('pre').baseMethod; f('abc')", "baseMethod: abc");
    test("def x = new io.jactl.HostClassTests.NewType('xxx'); x.baseMethod2('xxx')", "baseMethod: xxx");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.baseMethod; f('abc')", "baseMethod: abc");
    test("def f = new io.jactl.HostClassTests.NewType('pre').\"${'baseMethod'}\"; f('abc')", "baseMethod: abc");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.baseMethod; f('abc')", "baseMethod: abc");
    test("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.\"${'baseMethod'}\"; f('abc')", "baseMethod: abc");
    testError("def f = new io.jactl.HostClassTests.NewType('xxx').baseMethod2; f('xxx')", "multiple methods");
    testError("def f = new io.jactl.HostClassTests.NewType('xxx').\"${'baseMethod2'}\"; f('xxx')", "multiple methods");
    testError("def x = new io.jactl.HostClassTests.NewType('xxx'); def f = x.baseMethod2; f('xxx')", "multiple methods");
    testError("def x = new io.jactl.HostClassTests.NewType('xxx'); def f = x.\"${'baseMethod2'}\"; f('xxx')", "multiple methods");
    
    NewType.throwException = true;
    NewBaseType.throwException = true;
    testError("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.baseMethod2(x)", "error invoking host class method");
    testError("io.jactl.HostClassTests.NewType x = new io.jactl.HostClassTests.NewType('pre'); x.baseMethod2(new io.jactl.HostClassTests.NewBaseType())", "error invoking host class method");
    testError("new io.jactl.HostClassTests.NewType('xxx').baseMethod('xxx')", "error invoking host class method");
    testError("def x = 'xxx'; new io.jactl.HostClassTests.NewType('xxx').baseMethod(x)", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('xxx'); x.baseMethod('xxx')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('xxx'); x.baseMethod2('xxx')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.baseMethod; f('abc')", "error invoking host class method");
    testError("def f = new io.jactl.HostClassTests.NewType('pre').baseMethod; f('abc')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.baseMethod; f('abc')", "error invoking host class method");
    testError("def x = new io.jactl.HostClassTests.NewType('pre'); def f = x.\"${'baseMethod'}\"; f('abc')", "error invoking host class method");
  }

  @Test public void importTests() {
    skipCheckpointTests = true;
    allowHostAccess = true;
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests");
    testError("import io.jactl.HostClassTests.XXXNewType; new NewType('xyz').process('abc')", "host class not found: io.jactl.HostClassTests$XXXNewType");
    testError("import x.y.z.*; new XYZ('xyz')", "no such package");
    testError(Utils.listOf("package a.b; class X{}"), "import jactl.pkg.a.b.X; new X()", "expecting class name or valid package");
    test("import io.jactl.HostClassTests.NewType; new NewType('xyz').process('abc')", "xyz: abc");
    testError("import io.jactl.HostClassTests.*; def x = new NewType('xyz'); switch (x) { NewType -> 'NewType' }", "unexpected token '*'");
    test("import io.jactl.HostClassTests.NewBaseType; def x = new NewBaseType(); switch (x) { NewBaseType -> 'NewBaseType' }", "NewBaseType");
    testError("import io.jactl.*; def x = new HostClassTests.NewType()", "could not find public constructor");
    test("import io.jactl.*; def x = new HostClassTests.NewType('abc'); switch (x) { HostClassTests.NewBaseType -> 'NewBaseType' }", "NewBaseType");
    testError("import static io.jactl.HostClassTests.NewType.staticMethod as mmm; mmm()", "cannot use import static with a host class");
    testError("import io.jactl.*; new Jactl('xyz')", "unknown class");
    
    allowHostClassLookup = n -> true;
    testError("import io.jactl.*; new Jactl('xyz')", "could not find public constructor");
  }
  
  @Test public void switchExpressions() {
    skipCheckpointTests = true;
    allowHostAccess = true;
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests");
    test("import io.jactl.HostClassTests.NewType; switch (new io.jactl.HostClassTests.NewType('pre')) { NewType -> 'NewType' }", "NewType");
    test("import io.jactl.HostClassTests.NewType; switch (new NewType('pre')) { NewType -> 'NewType' }", "NewType");
    test("import io.jactl.HostClassTests.NewType; switch (new NewType('pre')) { NewType -> 'NewType' }", "NewType");
    test("import io.jactl.HostClassTests.NewType; def x = new NewType('xyz'); switch (x) { NewType -> 'NewType' }", "NewType");
    test("import io.jactl.HostClassTests.NewType; def x = new NewType('xyz'); switch (x) { NewType a -> a.baseMethod('xxx') }", "baseMethod: xxx");
    test("import io.jactl.HostClassTests.NewType; def x = new NewType('xyz'); switch (x) { NewType a if a.baseMethod('xxx') == 'baseMethod: xxx' -> it.baseMethod('xxx') }", "baseMethod: xxx");
    test("import io.jactl.HostClassTests.NewType; def x = new NewType('xxx'); switch (x) { io.jactl.HostClassTests.NewBaseType -> 'NewBaseType' }", "NewBaseType");
  }
  
  @Test public void fields() {
    skipCheckpointTests = true;
    allowHostAccess = true;
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests");
    testError("class X { io.jactl.HostClassTests.NewType x }; new X(new io.jactl.HostClassTest.NewType('abc')).x.process('xyz')", "unknown class 'io.jactl.HostClassTest'");
    testError("class X { io.jactl.HostClassTest.NewType x }; new X(new io.jactl.HostClassTest.NewType('abc')).x.process('xyz')", "unknown class 'io.jactl.HostClassTest'");
    testError("class X { io.jactl.HostClassTests.NewType x }; new X(new io.jactlxxxx.HostClassTest.NewType('abc')).x.process('xyz')", "unknown package 'io.jactlxxxx'");
    testError("class X { io.jactlxxxx.HostClassTests.NewType x }; new X(new io.jactl.HostClassTests.NewType('abc')).x.process('xyz')", "unknown package 'io.jactlxxxx'");
    test("class X { io.jactl.HostClassTests.NewType x }; new X(new io.jactl.HostClassTests.NewType('prefix')).x.process('xyz')", "prefix: xyz");
    testError("class X { io.jactl.HostClassTests.NewType x }; new X([x:new io.jactl.HostClassTests.NewType('prefix')]).x.process('xyz')", "cannot convert from type Map to Instance");
    test("class X { io.jactl.HostClassTests.NewType x }; new X(x:new io.jactl.HostClassTests.NewType('prefix')).x.process('xyz')", "prefix: xyz");
    test("class X { io.jactl.HostClassTests.NewType x }; def x = new X(x:new io.jactl.HostClassTests.NewType('prefix')); x.x.process('xyz')", "prefix: xyz");
    test("class X { io.jactl.HostClassTests.NewType x }; ([x:new io.jactl.HostClassTests.NewType('prefix')] as X).x.process('xyz')", "prefix: xyz");
    test("class X { io.jactl.HostClassTests.NewType x }; def x = [x:new io.jactl.HostClassTests.NewType('prefix')]; (x as X).x.process('xyz')", "prefix: xyz");
    test("class X { io.jactl.HostClassTests.NewType x }; class Y extends X { int i }; def y = new Y(new io.jactl.HostClassTests.NewType('prefix'), 3).x.process('xyz')", "prefix: xyz");
    test("class X { io.jactl.HostClassTests.NewType x }; class Y extends X { int i }; def y = new Y(x:new io.jactl.HostClassTests.NewType('prefix'), i:3).x.process('xyz')", "prefix: xyz");
    test("class X { io.jactl.HostClassTests.NewType x }; class Y extends X { int i }; def y = [x:new io.jactl.HostClassTests.NewType('prefix'), i:3]; (y as Y).x.process('xyz')", "prefix: xyz");

    testError("import io.jactl.HostClassTests; class X { HostClassTests.NewType x }; new X(new HostClassTest.NewType('abc')).x.process('xyz')", "unknown class 'HostClassTest'");
    test("import io.jactl.HostClassTests.NewType; class X { NewType x }; new X(new NewType('abc')).x.process('xyz')", "abc: xyz");
    test("import io.jactl.HostClassTests; class X { HostClassTests.NewType x }; new X(new HostClassTests.NewType('abc')).x.process('xyz')", "abc: xyz");
    test("import io.jactl.*; class X { HostClassTests.NewType x }; new X(new HostClassTests.NewType('abc')).x.process('xyz')", "abc: xyz");
    testError("import io.jactl.HostClassTests; class X { HostClassTests.NewType x }; new X([x:new HostClassTests.NewType('prefix')]).x.process('xyz')", "cannot convert from type Map to Instance");
    testError("import io.jactl.HostClassTests.NewType; class X { NewType x }; new X([x:new NewType('prefix')]).x.process('xyz')", "cannot convert from type Map to Instance");
    test("import io.jactl.HostClassTests; class X { HostClassTests.NewType x }; new X(x:new HostClassTests.NewType('prefix')).x.process('xyz')", "prefix: xyz");
    test("import io.jactl.HostClassTests.NewType; class X { NewType x }; new X(x:new NewType('prefix')).x.process('xyz')", "prefix: xyz");
    test("import io.jactl.HostClassTests.NewType; class X { NewType x }; def x = [x:new NewType('prefix')]; (x as X).x.process('xyz')", "prefix: xyz");
    test("import io.jactl.*; class X { HostClassTests.NewType x }; class Y extends X { int i }; def y = new Y(new HostClassTests.NewType('prefix'), 3).x.process('xyz')", "prefix: xyz");
    test("import io.jactl.HostClassTests.NewType; class X { NewType x }; class Y extends X { int i }; def y = new Y(x:new NewType('prefix'), i:3).x.process('xyz')", "prefix: xyz");
    test("import io.jactl.*; class X { HostClassTests.NewBaseType x }; class Y extends X { int i }; def y = new Y(x:new HostClassTests.NewType('prefix'), i:3).x.baseMethod('xyz')", "baseMethod: xyz");
    
    NewType.throwException = true;
    NewBaseType.throwException = true;
    testError("import io.jactl.HostClassTests; class X { HostClassTests.NewType x }; new X(x:new HostClassTests.NewType('prefix')).x.process('xyz')", "error invoking host class method");
    testError("import io.jactl.HostClassTests.NewType; class X { NewType x }; new X(x:new NewType('prefix')).x.process('xyz')", "error invoking host class method");
    testError("import io.jactl.HostClassTests.NewType; class X { NewType x }; def x = [x:new NewType('prefix')]; (x as X).x.process('xyz')", "error invoking host class method");
    testError("import io.jactl.*; class X { HostClassTests.NewType x }; class Y extends X { int i }; def y = new Y(new HostClassTests.NewType('prefix'), 3).x.process('xyz')", "error invoking host class method");
    testError("import io.jactl.HostClassTests.NewType; class X { NewType x }; class Y extends X { int i }; def y = new Y(x:new NewType('prefix'), i:3).x.process('xyz')", "error invoking host class method");
    testError("import io.jactl.*; class X { HostClassTests.NewBaseType x }; class Y extends X { int i }; def y = new Y(x:new HostClassTests.NewType('prefix'), i:3).x.baseMethod('xyz')", "error invoking host class method");
  }
  
  @Test public void parametersAndReturnTypes() {
    skipCheckpointTests = true;
    allowHostAccess = true;
    allowHostClassLookup = n -> n.startsWith("io.jactl.HostClassTests");
    test("io.jactl.HostClassTests.NewType f() { new io.jactl.HostClassTests.NewType('prefix') }; f().process('xyz');",  "prefix: xyz");
    test("import io.jactl.HostClassTests; HostClassTests.NewType f() { new HostClassTests.NewType('prefix') }; f().process('xyz');",  "prefix: xyz");
    test("import io.jactl.HostClassTests.NewType; NewType f() { new NewType('prefix') }; f().process('xyz');",  "prefix: xyz");
    test("String f(io.jactl.HostClassTests.NewType n) { n.process('xyz') }; f(new io.jactl.HostClassTests.NewType('prefix'));",  "prefix: xyz");
    testError("class X{}; X f(io.jactl.HostClassTests.NewType n) { n.process('xyz') }; f(new io.jactl.HostClassTests.NewType('prefix'));",  "String not compatible");
    test("class X { String f(io.jactl.HostClassTests.NewBaseType n) { n.baseMethod('xyz') } }; new X().f(new io.jactl.HostClassTests.NewType('prefix'));",  "baseMethod: xyz");
  }
  
  @Test public void javaTypes() {
    skipCheckpointTests = true;
    allowHostAccess = true;
    allowHostClassLookup = n -> true;
    useAsyncDecorator = false;
    test("import java.lang.String; new java.lang.String('abc')", "abc");
    test("import java.lang.String as X; new X('abc')", "abc");
    test("new java.lang.String('abc')", "abc");
    test("java.lang.Math.min(1,2)", 1);
    test("import java.lang.Math; Math.min(1,2)", 1);
  }
}