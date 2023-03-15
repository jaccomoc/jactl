#!/bin/sh

#
# Copyright © 2022,2023 James Crawford
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

perl ./src/main/tools/GenerateClasses.pl src/main/tools/Stmt.java > src/main/java/jacsal/Stmt.java; perl ./src/main/tools/GenerateClasses.pl src/main/tools/Stmt.java > src/test/resources/jacsal/Stmt.java.generated
perl ./src/main/tools/GenerateClasses.pl src/main/tools/Expr.java > src/main/java/jacsal/Expr.java; perl ./src/main/tools/GenerateClasses.pl src/main/tools/Expr.java > src/test/resources/jacsal/Expr.java.generated

