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

import io.jactl.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static io.jactl.TokenType.*;

public class BuilderImpl implements Builder {

  protected Tokeniser                   tokeniser;
  protected Token                       previousToken = null;
  protected LinkedHashSet<CompileError> errors        = new LinkedHashSet<>();

  public BuilderImpl(Tokeniser tokeniser) {
    this.tokeniser = tokeniser;
  }

  protected BuilderImpl() {}

  public void done() {
    if (errors.size() == 1) {
      throw errors.iterator().next();
    }
    if (!errors.isEmpty()) {
      throw new CompileError(new ArrayList<>(errors));
    }
  }

  @Override public Marker mark() {
    return new MarkerImpl();
  }

  void rollbackTo(Marker marker) {
    MarkerImpl impl = (MarkerImpl)marker;
    tokeniser.rewind(impl.savedState);
    previousToken = impl.ourPreviousToken;
  }

  @Override
  public Token previous() {
    return previousToken;
  }

  /**
   * Return the next token without consuming it.
   * Skip comments and whitespace.
   * @return the next token
   */
  @Override
  public Token peek() {
    skipCommentsAndWhiteSpace();
    return tokeniser.peek();
  }

  /**
   * Consume the next token and return it.
   * Turn multiple EOLs into one (including skipping comments and whitespace).
   * @return the next token
   */
  @Override
  public Token advance() {
    if (peek().is(EOL)) {
      do {
        previousToken = _advance();
        skipCommentsAndWhiteSpace();
      } while (peek().is(EOL));
    }
    else {
      previousToken = _advance();
    }
    return previousToken;
  }

  @Override
  public Token _advance() {
    return tokeniser.next();
  }

  @Override
  public void startRegex() {
    tokeniser.startRegex();
  }

  private void skipCommentsAndWhiteSpace() {
    while (tokeniser.peek().is(COMMENT,WHITESPACE)) {
      _advance();
    }
  }

  protected class MarkerImpl implements Marker {
    Token           ourPreviousToken;
    Tokeniser.State savedState;
    boolean         isError;

    MarkerImpl() {
      this.ourPreviousToken = previousToken;
      this.savedState       = tokeniser.saveState();
    }

    @Override
    public Marker precede() {
      return new MarkerImpl();
    }

    @Override
    public void rollback() {
      rollbackTo(this);
    }

    @Override public void drop() {}

    @Override public void error(CompileError err) {
      if (!errors.add(err)) {
        drop();
      }
      else {
        isError = true;
      }
    }

    @Override public boolean isError() {
      return isError;
    }

    @Override public void done(JactlName name) {}
    @Override public void done(JactlType type, Token location) {}
    @Override public void done(Stmt stmt) {}
    @Override public void done(Expr expr) {}
  }

  private static class DummyMarker implements Marker {
    boolean isError = false;
    @Override public Marker precede() { return new DummyMarker(); }
    @Override public void rollback() {}
    @Override public void error(CompileError err) { isError = true; }
    @Override public boolean isError() { return isError; }
    @Override public void drop() {}
    @Override public void done(JactlName name) {}
    @Override public void done(JactlType type, Token location) {}
    @Override public void done(Stmt stmt) {}
    @Override public void done(Expr expr) {}
  }
}
