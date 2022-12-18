#!/bin/sh

perl ./src/main/tools/GenerateClasses.pl src/main/tools/Stmt.java > src/main/java/jacsal/Stmt.java; perl ./src/main/tools/GenerateClasses.pl src/main/tools/Stmt.java > src/test/resources/jacsal/Stmt.java.generated
perl ./src/main/tools/GenerateClasses.pl src/main/tools/Expr.java > src/main/java/jacsal/Expr.java; perl ./src/main/tools/GenerateClasses.pl src/main/tools/Expr.java > src/test/resources/jacsal/Expr.java.generated

