/*
 * Copyright © 2022,2023 James Crawford
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

import io.jactl.runtime.ClassDescriptor;
import org.objectweb.asm.Type;

import java.util.List;

/**
 * Objects of this class are used to provide a layer of indirection. They "point" to the type of a given
 * Expr. Initially this type is unknown but once the Expr has been resolved this DelegatingJactlType will
 * delegate all calls to it. This allows us to pass around objects of this type before we actually know what
 * the underlying type will be.
 * <p>Before the Expr has been resolved it instead delegates to its "delegate" which will always be a JactlType
 * of UNKNOWN.</p>
 */
public class DelegatingJactlType extends JactlType {

  JactlType delegate;
  Expr       expr;

  public DelegatingJactlType(JactlType delegate) {
    this.delegate = delegate;
  }

  public void typeDependsOn(Expr expr) {
    this.expr = expr;
  }

  protected JactlType getDelegate() {
    if (expr == null || expr.type == null) {
      return delegate;
    }
    return expr.type;
  }

  // JactlType
  @Override public JactlType createInstanceType()                      { return getDelegate().createInstanceType();    }
  @Override public TypeEnum getType()                                  { return getDelegate().getType();               }
  @Override public boolean isBoxed()                                   { return getDelegate().isBoxed();               }
  @Override public TokenType tokenType()                               { return getDelegate().tokenType();             }
  @Override public boolean isRef()                                     { return getDelegate().isRef();                 }
  @Override public boolean isNumeric()                                 { return getDelegate().isNumeric();             }
  @Override public boolean isPrimitive()                               { return getDelegate().isPrimitive();           }
  @Override public JactlType boxed()                                   { return getDelegate().boxed();                 }
  @Override public JactlType unboxed()                                 { return getDelegate().unboxed();               }
  @Override public boolean is(JactlType... types)                      { return getDelegate().is(types);               }
  @Override public boolean isBoxedOrUnboxed(JactlType... types)        { return getDelegate().isBoxedOrUnboxed(types); }
  @Override public boolean isCastableTo(JactlType otherType)           { return getDelegate().isCastableTo(otherType); }
  @Override public String descriptor()                                 { return getDelegate().descriptor();            }
  @Override public Type descriptorType()                               { return getDelegate().descriptorType();        }
  @Override public String getInternalName()                            { return getDelegate().getInternalName();       }
  @Override public Class classFromType()                               { return getDelegate().classFromType();         }
  @Override public String typeName()                                   { return getDelegate().typeName();              }
  @Override public List<Expr> getClassName()                           { return getDelegate().getClassName();          }
  @Override public String toString()                                   { return getDelegate().toString();              }
  @Override public void setClassDescriptor(ClassDescriptor descriptor) { getDelegate().setClassDescriptor(descriptor); }
  @Override public ClassDescriptor getClassDescriptor()                { return getDelegate().getClassDescriptor();    }
  @Override public JactlType getArrayElemType()                        { return getDelegate().getArrayElemType();          }
  @Override public boolean isAssignableFrom(JactlType otherType)       { return getDelegate().isAssignableFrom(otherType); }
  @Override public boolean equals(Object obj)                          { return getDelegate().equals(obj);             }

  // JactlUserDataHolder
  @Override public Stmt.Block getBlock()           { return delegate.getBlock(); }
  @Override public void setBlock(Stmt.Block block) { delegate.setBlock(block);   }
}
