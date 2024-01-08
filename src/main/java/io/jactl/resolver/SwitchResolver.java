package io.jactl.resolver;

import io.jactl.*;
import io.jactl.runtime.ClassDescriptor;
import io.jactl.runtime.RuntimeUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.jactl.JactlType.*;
import static io.jactl.JactlType.BYTE;
import static io.jactl.JactlType.CLASS;
import static io.jactl.JactlType.INT;
import static io.jactl.JactlType.STRING;
import static io.jactl.TokenType.*;

public class SwitchResolver {

  public static JactlType visitSwitch(Resolver resolver, Expr.Switch expr) {
    resolver.resolve(expr.subject);

    // Verify that the only non-literals are identifiers (for capture vars) or '_' or '*'
    // and create VarDecl expressions for first use of each identifier.
    expr.cases.forEach(c -> c.patterns = validateSwitchPatterns(resolver, c.patterns));

    JactlType    subjectType = expr.subject.type;
    Stmt.VarDecl itVar       = resolver.createVarDecl(expr.matchToken, Utils.IT_VAR, subjectType.unboxed(), expr.subject);
    expr.itVar = itVar.declExpr;
    // Block to hold our it var
    Stmt.Block block = new Stmt.Block(expr.location, null);
    expr.block = block;
    block.stmts = new Stmt.Stmts(expr.matchToken);
    block.stmts.stmts.add(itVar);
    // Create a statement wrapper for each case in the match so we can resolve them
    block.stmts.stmts.addAll(expr.cases.stream().map(c -> {
      // Create a block in case we have regex/destructured capture vars
      c.block = new Stmt.Block(null, null);
      Token location = c.patterns.get(0).first.location;
      c.block.stmts = new Stmt.Stmts(location);
      c.block.stmts.stmts.add(new Stmt.ExprStmt(location, c));
      return c.block;
    }).collect(Collectors.toList()));
    resolver.resolve(block);
    expr.cases.forEach(c -> c.patterns.forEach(pat -> isCompatible(resolver, subjectType, pat.first)));
    resolver.resolve(expr.defaultCase);

    // Check that there if there is a pattern covering all cases that there are no subsequent patterns and no default
    Set<JactlType> coveredTypes = new HashSet<>();
    expr.cases.forEach(c -> c.patterns.stream().map(pair -> pair.first).forEach(p -> {
      JactlType type = coveringType(p);
      if (coveredTypes.stream().anyMatch(ct -> ct.is(ANY) || ct.is(subjectType) || ct.isAssignableFrom(p.type) || (type != null && ct.isAssignableFrom(type)))) {
        resolver.error("Unreachable switch case (covered by a previous case)", p.location);
      }
      if (type != null) {
        coveredTypes.add(type);
      }
    }));
    if (expr.defaultCase != null && coveredTypes.stream().anyMatch(ct -> ct.is(ANY) || ct.isAssignableFrom(subjectType))) {
      resolver.error("Default case is never applicable due to switch case that matches all input", expr.defaultCase.location);
    }
    validateNotCovered(resolver, expr.cases.stream().flatMap(c -> c.patterns.stream().filter(pair -> pair.second == null).map(pair -> pair.first)).collect(Collectors.toList()));

    if (expr.defaultCase == null) {
      expr.defaultCase = new Expr.Literal(new Token(NULL, expr.location));
      resolver.resolve(expr.defaultCase);
    }

    // Find common type for all results (including default case)
    return expr.type = Stream.concat(expr.cases.stream().map(c -> c.result.type), Stream.of(expr.defaultCase.type))
                             .reduce(JactlType::commonSuperType)
                             .orElse(null);
  }

  private static JactlType coveringType(Expr pattern) {
    if (pattern instanceof Expr.TypeExpr) {
      return ((Expr.TypeExpr) pattern).typeVal;
    }
    if ((pattern instanceof Expr.Identifier)) {
      Expr.Identifier identifier = (Expr.Identifier) pattern;
      if (identifier.identifier.is(UNDERSCORE) || identifier.varDecl.isBindingVar) {
        return pattern.type;
      }
    }
    if (pattern instanceof Expr.VarDecl) {
      return pattern.type;
    }
    return null;
  }

  public static boolean isCompatible(Resolver resolver, JactlType subjectType, Expr pat) {
    if (!doIsCompatible(resolver, subjectType, pat)) {
      resolver.error("Type " + subjectType + " can never match type " + pat.patternType() + " with value " + pat.constValue, pat.location);
      return false;
    }
    return true;
  }

  public static boolean doIsCompatible(Resolver resolver, JactlType subjectType, Expr pat) {
    JactlType patType = pat.patternType();
    if (subjectType.is(ANY) || patType.is(ANY)) {
      return true;
    }
    if (pat instanceof Expr.RegexMatch) {
      return subjectType.is(STRING);
    }
    if (pat.isTypePattern()) {
      if ((!subjectType.is(INSTANCE) || !patType.is(INSTANCE)) && !subjectType.equals(patType)) {
        return false;
      }
      return subjectType.isConvertibleTo(patType, true);
    }
    if (subjectType.isNumeric() && !patType.isNumeric() || patType.isNumeric() && !subjectType.isNumeric()) {
      return false;
    }
    if (subjectType.isNumeric() && patType.isNumeric()) {
      if (!subjectType.boxed().is(patType.boxed())) {
        // Only implicit conversion allowed is between byte and int
        if (patType.is(INT) && subjectType.unboxed().is(BYTE)) {
          return (int) pat.constValue >= 0 && (int) pat.constValue < 256;
        }
        return false;
      }
      else {
        return true;
      }
    }
    if (subjectType.is(ARRAY)) {
      if (!(pat instanceof Expr.ListLiteral)) { return false; }
      return ((Expr.ListLiteral) pat).exprs.stream().allMatch(subPat -> isCompatible(resolver, subjectType.getArrayElemType(), subPat));
    }
    return subjectType.isConvertibleTo(patType, true);
  }

  private static void validateNotCovered(Resolver resolver, List<Expr> patterns) {
    // For each pattern after the first one check there are no previous ones that are a superset
    // of the current pattern
    for (int i = 1; i < patterns.size(); i++) {
      Expr pattern = patterns.get(i);
      for (int j = 0; j < i; j++) {
        if (covers(patterns.get(j), pattern)) {
          resolver.error("Switch pattern will never be evaluated (covered by a previous pattern)", pattern.location);
        }
      }
    }
  }

  private static boolean isUnderscore(Expr e) { return e instanceof Expr.Identifier && ((Expr.Identifier)e).identifier.is(UNDERSCORE); }

  private static boolean covers(Expr pattern1, Expr pattern2) {
    return covers(pattern1, pattern2, new HashMap<>());
  }

  private static boolean covers(Expr pattern1, Expr pattern2, Map<String,Expr> bindings) {
    if (pattern1 instanceof Expr.TypeExpr || pattern1 instanceof Expr.VarDecl || isUnderscore(pattern1)) {
      JactlType t1 = pattern1 instanceof Expr.TypeExpr ? ((Expr.TypeExpr) pattern1).typeVal : pattern1.type;
      JactlType t2 = pattern2.patternType();
      if (pattern1 instanceof Expr.VarDecl) {
        bindings.put(((Expr.VarDecl) pattern1).name.getStringValue(), pattern2);
      }
      return t1.is(ANY) || t1.is(t2) || t1.isAssignableFrom(t2);
    }
    if (pattern1.isLiteral() && pattern1.isConst && pattern2.isLiteral() && pattern2.isConst) {
      return RuntimeUtils.switchEquals(pattern1.constValue, pattern2.constValue);
    }
    if (pattern1 instanceof Expr.ExprString || pattern2 instanceof Expr.ExprString) {
      return false;
    }
    // We have either List/Map/Constructor pattern or Identifier
    if (pattern1 instanceof Expr.ListLiteral) {
      if (!(pattern2 instanceof Expr.ListLiteral))           { return false; }
      List<Expr> l1 = ((Expr.ListLiteral) pattern1).exprs;
      List<Expr> l2 = ((Expr.ListLiteral) pattern2).exprs;
      if (l2.stream().anyMatch(Expr::isStar))            { return false; }
      boolean l1HasStar = l1.stream().anyMatch(Expr::isStar);
      if (l1.size() - (l1HasStar ? 1 : 0) > l2.size())   { return false; }
      if (l2.size() > l1.size() && !l1HasStar)           { return false; }
      for (int i = 0, star = 0; i < l1.size(); i++) {
        Expr e1 = l1.get(i);
        Expr e2 = l2.get(star>0 ? i-l1.size()+l2.size() : i);
        star += e1.isStar()?1:0;
        if (!covers(e1,e2,bindings))                      { return false; }
      }
      return true;
    }
    if (pattern1 instanceof Expr.MapLiteral) {
      if (!(pattern2 instanceof Expr.MapLiteral))                   { return false; }
      List<Pair<Expr,Expr>> entries1 = ((Expr.MapLiteral) pattern1).entries;
      List<Pair<Expr,Expr>> entries2 = ((Expr.MapLiteral) pattern2).entries;
      if (entries2.stream().anyMatch(p -> p.first.isStar()))         { return false; }
      boolean l1HasStar = entries1.stream().anyMatch(p -> p.first.isStar());
      if (entries1.size() - (l1HasStar ? 1 : 0) > entries2.size())   { return false; }
      if (entries2.size() > entries1.size() && !l1HasStar)           { return false; }
      Map<String,Expr> m1 = entries1.stream().filter(p -> !p.first.isStar()).collect(Collectors.toMap(p -> ((Expr.Literal)p.first).value.getStringValue(), p -> p.second));
      Map<String,Expr> m2 = entries2.stream().collect(Collectors.toMap(p -> ((Expr.Literal)p.first).value.getStringValue(), p -> p.second));
      return m1.keySet().stream().allMatch(k -> m2.containsKey(k) && covers(m1.get(k), m2.get(k), bindings));
    }
    if (pattern1 instanceof Expr.ConstructorPattern) {
      if (!(pattern2 instanceof Expr.ConstructorPattern))                      { return false; }
      if (!pattern1.patternType().isAssignableFrom(pattern2.patternType()))    { return false; }
      Map<String, Expr> keyMap1 = ((Expr.MapLiteral)((Expr.ConstructorPattern)pattern1).args).literalKeyMap;
      Map<String, Expr> keyMap2 = ((Expr.MapLiteral)((Expr.ConstructorPattern)pattern2).args).literalKeyMap;
      if (!keyMap1.keySet().equals(keyMap2.keySet()))                          { return false; }
      return keyMap1.keySet().stream().allMatch(k -> keyMap2.containsKey(k) && covers(keyMap1.get(k), keyMap2.get(k), bindings));
    }
    if (pattern1 instanceof Expr.Identifier && pattern1.isTypePattern()) {
      return covers(bindings.get(((Expr.Identifier) pattern1).identifier.getStringValue()), pattern2, bindings);
    }
    return false;
  }

  public static JactlType visitConstructorPattern(Resolver resolver, Expr.ConstructorPattern expr) {
    resolver.resolve(expr.typeExpr);
    resolver.resolve(expr.args);
    ClassDescriptor                   descriptor      = expr.typeExpr.patternType().getClassDescriptor();
    List<Map.Entry<String,JactlType>> constructorArgs = descriptor.getAllMandatoryFields().entrySet().stream().collect(Collectors.toList());
    // Transform unnamed args into named args
    if (expr.args instanceof Expr.ListLiteral) {
      Expr.MapLiteral mapLiteral = new Expr.MapLiteral(expr.args.location);
      mapLiteral.literalKeyMap = new HashMap<>();
      List<Expr> exprs = ((Expr.ListLiteral) expr.args).exprs;
      if (exprs.size() != constructorArgs.size()) {
        resolver.error("Argument count for constructor pattern does not match mandatory field count of " + constructorArgs.size(), expr.args.location);
      }
      for (int i = 0; i < exprs.size(); i++) {
        Expr arg = exprs.get(i);
        Map.Entry<String, JactlType> constructorParam = constructorArgs.get(i);
        if (!arg.type.isConvertibleTo(constructorParam.getValue())) {
          resolver.error("Argument value of type " + arg.type + " incompatible with type " + constructorParam.getValue() + " of field " + constructorParam.getKey(), arg.location);
        }
        mapLiteral.literalKeyMap.put(constructorParam.getKey(), arg);
        Expr.Literal fieldName = new Expr.Literal(arg.location.newIdent(constructorParam.getKey()));
        resolver.resolve(fieldName);
        mapLiteral.entries.add(Pair.create(fieldName, arg));
      }
      expr.args = mapLiteral;
    }
    else {
      // Validate that the fields listed actually exist
      ((Expr.MapLiteral)expr.args).entries.stream()
                                          .map(pair -> pair.first)
                                          .filter(field -> !descriptor.getAllFields().containsKey(field.constValue))
                                          .findFirst()
                                          .ifPresent(field -> resolver.error("Field " + field.constValue + " does not exist in class " + expr.typeExpr.patternType(), field.location));
    }
    return expr.type = expr.typeExpr.patternType();
  }

  public static JactlType visitSwitchCase(Resolver resolver, Expr.SwitchCase caseExpr) {
    caseExpr.patterns = caseExpr.patterns.stream().map(pair -> {
      Expr pattern = pair.first;
      if (pattern instanceof Expr.Identifier && ((Expr.Identifier) pattern).identifier.is(UNDERSCORE)) {
        pattern.type = ANY;
      }
      else if (pattern instanceof Expr.VarDecl) {
        // Work out type from switch expression type if unknown
        if (pattern.type.is(UNKNOWN)) {
          pattern.type = caseExpr.switchSubject.type;
        }
        else if (!caseExpr.switchSubject.type.isConvertibleTo(pattern.type)) {
          resolver.error("Type of binding variable not compatible with switch expression type", pattern.location);
        }
        Stmt.VarDecl varDecl = resolver.createVarDecl((Expr.VarDecl)pattern);
        resolver.resolve(varDecl);
        resolver.insertStmt(varDecl);
      }
      else {
        pattern = resolvePattern(resolver, pattern);
        if (pattern instanceof Expr.TypeExpr && ((Expr.TypeExpr) pattern).typeVal.is(CLASS)) {
          ((Expr.TypeExpr) pattern).typeVal = ((Expr.TypeExpr) pattern).typeVal.createInstanceType();
        }
      }
      resolver.resolve(pair.second);
      return Pair.create(pattern,pair.second);
    }).collect(Collectors.toList());
    resolver.resolve(caseExpr.result);
    return caseExpr.type = ANY;
  }

  private static Expr resolvePattern(Resolver resolver, Expr pattern) {
    if (!(pattern instanceof Expr.TypeExpr) || !pattern.patternType().is(INSTANCE,CLASS)) {
      resolver.resolve(pattern);
      return pattern;
    }
    // If pattern is a TypeExpr and potentially a class name then we need to check to make
    // sure that the last "class name" in the dotted list is actually a class and if it is
    // actually a static field turn the pattern into a literal (based on the field value)
    Expr.TypeExpr typeExpr  = (Expr.TypeExpr)pattern;
    List<Expr>    className = typeExpr.typeVal.getClassName();
    if (className.size() == 1 && className.get(0) instanceof Expr.Identifier) {
      // Check for a constant
      Expr.Identifier identifier = (Expr.Identifier)className.get(0);
      Expr.VarDecl    constant   = resolver.constants.get(identifier.identifier.getStringValue());
      if (constant != null) {
        Expr.Literal literal = (Expr.Literal)resolver.literalDefaultValue(pattern.location, constant.type);
        literal.value.setValue(constant.constValue);
        resolver.resolve(literal);
        return literal;
      }
    }
    if (className.size() == 1 || resolver.lookupClass(className, true) != null) {
      resolver.resolve(typeExpr);
      return typeExpr;
    }
    Expr.Identifier identifier = (Expr.Identifier) className.get(className.size() - 1);
    String          fieldName  = identifier.identifier.getStringValue();
    className = className.subList(0,className.size() - 1);
    // We must have a proper class name or we will throw an resolver.error now
    ClassDescriptor descriptor = resolver.lookupClass(className, false);
    JactlType fieldType = descriptor.getStaticField(fieldName);
    if (fieldType == null) {
      resolver.error("No class static field '" + fieldName + "' for class " + descriptor.getClassName(), identifier.location);
    }
    Expr.Literal literal = (Expr.Literal)resolver.literalDefaultValue(pattern.location, fieldType);
    literal.value.setValue(descriptor.getStaticFieldValue(fieldName));
    resolver.resolve(literal);
    return literal;
  }

  private static List<Pair<Expr,Expr>> validateSwitchPatterns(Resolver resolver, List<Pair<Expr,Expr>> exprs) {
    return _validateSwitchPattern(resolver, exprs, new HashSet<>());
  }

  private static List<Pair<Expr,Expr>> _validateSwitchPattern(Resolver resolver, List<Pair<Expr,Expr>> exprs, Set<String> bindingVars) {
    return exprs.stream()
                .map(p -> Pair.create(_validateSwitchPattern(resolver, p.first, bindingVars, new HashSet<>()), p.second))
                .collect(Collectors.toList());
  }

  /**
   * Validate variables in pattern of a switch case and replace Identifier with VarDecl where appropriate.
   *
   * @param resolver
   * @param expr        the pattern
   * @param bindingVars the binding variables for the entire case
   * @param varsSeen    binding variables seen in this pattern
   * @return transformed expression
   */
  private static Expr _validateSwitchPattern(Resolver resolver, Expr expr, Set<String> bindingVars, Set<String> varsSeen) {
    if (expr instanceof Expr.ListLiteral) {
      Expr.ListLiteral listExpr = (Expr.ListLiteral) expr;
      listExpr.exprs = listExpr.exprs.stream().map(e -> _validateSwitchPattern(resolver, e, bindingVars, varsSeen)).collect(Collectors.toList());
      listExpr.exprs.stream()
                    .filter(e -> e instanceof Expr.Identifier && ((Expr.Identifier) e).identifier.is(STAR))
                    .skip(1)
                    .findFirst()
                    .ifPresent(value -> resolver.error("'" + STAR.asString + "' can only appear once in a list/map pattern", value.location));
      return listExpr;
    }
    if (expr instanceof Expr.MapLiteral) {
      Expr.MapLiteral mapLiteral = (Expr.MapLiteral)expr;
      if (mapLiteral.literalKeyMap == null) {
        resolver.error("Field names must be constant strings",
              mapLiteral.entries.stream().filter(pair -> !(pair.first instanceof Expr.Literal)).map(p -> p.first).findFirst().orElse(expr).location);
      }
      mapLiteral.entries = mapLiteral.entries.stream().map(pair -> {
        if (!(pair.first instanceof Expr.Literal)) {
          resolver.error("Expected string constant for map key", pair.first.location);
        }
        return Pair.create(pair.first, _validateSwitchPattern(resolver, pair.second, bindingVars, varsSeen));
      }).collect(Collectors.toList());
      return mapLiteral;
    }
    if (expr instanceof Expr.ConstructorPattern) {
      Expr.ConstructorPattern constructorPattern = (Expr.ConstructorPattern) expr;
      if (constructorPattern.args instanceof Expr.MapLiteral) {
        Expr.MapLiteral args = (Expr.MapLiteral) constructorPattern.args;
        args.entries = args.entries.stream().map(a -> Pair.create(a.first,_validateSwitchPattern(resolver, a.second, bindingVars, varsSeen))).collect(Collectors.toList());
        args.literalKeyMap = args.entries.stream().collect(Collectors.toMap(p -> ((Expr.Literal)p.first).value.toString(), p -> p.second));
      }
      else {
        Expr.ListLiteral args = (Expr.ListLiteral) constructorPattern.args;
        args.exprs = args.exprs.stream().map(a -> _validateSwitchPattern(resolver, a, bindingVars, varsSeen)).collect(Collectors.toList());
      }
      return constructorPattern;
    }
    if (expr instanceof Expr.Literal || expr instanceof Expr.TypeExpr) {
      return expr;
    }
    if (expr instanceof Expr.VarDecl) {
      String name = ((Expr.VarDecl) expr).name.getStringValue();
      if (bindingVars.contains(name)) {
        resolver.error("Binding variable '" + name + "' already declared in switch pattern", expr.location);
      }
      bindingVars.add(name);
      varsSeen.add(name);
      return expr;
    }
    if (expr instanceof Expr.Identifier && !((Expr.Identifier) expr).identifier.is(DOLLAR_IDENTIFIER)) {
      Token name = ((Expr.Identifier) expr).identifier;
      String varName = name.getStringValue();

      // Turn into a Literal value if we have a constant
      Expr.VarDecl varDecl = resolver.lookup(varName, name, false, true);
      if (varDecl != null && varDecl.isConstVar) {
        Expr.Literal literal = (Expr.Literal)Resolver.literalDefaultValue(name, varDecl.type);
        literal.value.setValue(varDecl.constValue);
        return literal;
      }

      // Otherwise, if first occurrence then create a VarDecl for the binding var.
      // We will check for clash with other variables later.
      if (!name.is(UNDERSCORE,STAR)) {
        if (!bindingVars.contains(varName)) {
          bindingVars.add(varName);
          expr = Utils.createVarDeclExpr(name, ANY, new Token(EQUAL, name), null, false, false, true);
        }
        else {
          // Check if we are first use in this pattern. Previous patterns for the case (separate by commas)
          // might have the VarDecl in which case we need to know to reinitialise value first time bound in
          // this pattern.
          if (!varsSeen.contains(varName)) {
            ((Expr.Identifier)expr).firstTimeInPattern = true;
            varsSeen.add(varName);
          }
        }
      }
      return expr;
    }
    return expr;
  }

}
