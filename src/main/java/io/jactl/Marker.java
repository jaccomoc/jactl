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

import java.util.List;

public interface Marker {
  Marker  precede();
  void    rollback();
  void    error(CompileError err);
  boolean isError();
  void    drop();
  void    done(JactlName name);
  void    done(JactlType type, Token location);
  void    done(Stmt stmt);
  void    done(Expr expr);
  void    doneExpr(Expr expr);   // For complete expressions (e.g. rhs of assignment)
  void    done(List list);
}
