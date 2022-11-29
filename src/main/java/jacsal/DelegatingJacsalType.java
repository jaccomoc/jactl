/*
 * Copyright 2022 James Crawford
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

package jacsal;

import jacsal.runtime.ClassDescriptor;
import org.objectweb.asm.Type;

import java.util.List;

/**
 * Objects of this class are used to provide a layer of indirection. They "point" to the type of a given
 * Expr. Initially this type is unknown but once the Expr has been resolved this DelegatingJacsalType will
 * delegate all calls to it. This allows us to pass around objects of this type before we actually know what
 * the underlying type will be.
 * <p>Before the Expr has been resolved it instead delegates to its "delegate" which will always be a JacsalType
 * of UNKNOWN.</p>
 */
public class DelegatingJacsalType extends JacsalType {

  JacsalType delegate;
  Expr       expr;

  DelegatingJacsalType(JacsalType delegate) {
    this.delegate = delegate;
  }

  public void typeDependsOn(Expr expr) {
    this.expr = expr;
  }

  protected JacsalType getDelegate() {
    if (expr == null || expr.type == null) {
      return delegate;
    }
    return expr.type;
  }

  @Override public JacsalType createInstance()                         { return getDelegate().createInstance();           }
  @Override public TypeEnum getType()                                  { return getDelegate().getType();                  }
  @Override public boolean isBoxed()                                   { return getDelegate().isBoxed();                  }
  @Override public TokenType tokenType()                               { return getDelegate().tokenType();                }
  @Override public boolean isRef()                                     { return getDelegate().isRef();                    }
  @Override public boolean isNumeric()                                 { return getDelegate().isNumeric();                }
  @Override public boolean isPrimitive()                               { return getDelegate().isPrimitive();              }
  @Override public JacsalType boxed()                                  { return getDelegate().boxed();                    }
  @Override public JacsalType unboxed()                                { return getDelegate().unboxed();                  }
  @Override public boolean is(JacsalType... types)                     { return getDelegate().is(types);                  }
  @Override public boolean isBoxedOrUnboxed(JacsalType... types)       { return getDelegate().isBoxedOrUnboxed(types);    }
  @Override public boolean isConvertibleTo(JacsalType otherType)       { return getDelegate().isConvertibleTo(otherType); }
  @Override public String descriptor()                                 { return getDelegate().descriptor();               }
  @Override public Type descriptorType()                               { return getDelegate().descriptorType();           }
  @Override public String getInternalName()                            { return getDelegate().getInternalName();          }
  @Override public Class classFromType()                               { return getDelegate().classFromType();            }
  @Override public String typeName()                                   { return getDelegate().typeName();                 }
  @Override public List<Expr> getClassName()                           { return getDelegate().getClassName();             }
  @Override public String toString()                                   { return getDelegate().toString();                 }
  @Override public void setClassDescriptor(ClassDescriptor descriptor) { getDelegate().setClassDescriptor(descriptor);    }
  @Override public ClassDescriptor getClassDescriptor()                { return getDelegate().getClassDescriptor();       }

}