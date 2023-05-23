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

package io.jactl.runtime;

import java.util.List;

public class Location implements SourceLocation {
  protected String source;       // Source code of the script being compiled
  protected int    offset;       // The position/offset in source where token starts
  protected String line;         // The source line
  protected int    lineNum = -1; // Line number where token is
  protected int    column  = -1; // Column where token is

  protected Location(String source, int offset) {
    this.source = source;
    this.offset = offset;
  }

  protected Location(String source, int offset, String line, int lineNum, int column) {
    this.source  = source;
    this.offset  = offset;
    this.line    = line;
    this.lineNum = lineNum;
    this.column  = column;
  }

  public Location(Location location) {
    this(location.source, location.offset, location.line, location.lineNum, location.column);
  }

  public String  getSource()     { return source; }
  public int     getOffset()     { return offset; }

  public String getLine() {
    calculateLineAndColumn();
    return line;
  }

  public int getLineNum() {
    calculateLineAndColumn();
    return lineNum;
  }

  public int getColumn() {
    calculateLineAndColumn();
    return column;
  }

  /**
   * Get the line of source code with an additional line showing where token starts (used for errors)
   * @return the source code showing token location
   */
  public String getMarkedSourceLine() {
    calculateLineAndColumn();
    return String.format("%s%n%s^", line, " ".repeat(column - 1));
  }

  private void calculateLineAndColumn() {
    if (lineNum != -1 || source == null) {
      return;
    }
    List<String> lines = RuntimeUtils.lines(source);
    int pos   = 0;
    int i;
    for (i = 0; i < lines.size(); i++) {
      pos += lines.get(i).length() + 1;  // Include extra char for the newline
      if (offset < pos) {
        break;
      }
    }
    if (i == lines.size()) {
      throw new IllegalStateException("Internal error: offset of " + offset + " too large for source of length " + source.length());
    }

    // Remember the line and the line number/column number (which both start at 1, not 0)
    line = lines.get(i);
    lineNum = i + 1;
    // Column is offset - start pos of line + 1
    column = offset - (pos - line.length() - 1) + 1;
  }
}
