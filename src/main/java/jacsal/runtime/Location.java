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

package jacsal.runtime;

public class Location {
  protected String source;       // Source code of the script being compiled
  protected int    offset;       // The position/offset in source where token starts
  protected String line;         // The source line
  protected int    lineNum = -1; // Line number where token is
  protected int    column  = -1; // Column where token is

  public Location(String source, int offset) {
    this.source = source;
    this.offset = offset;
  }

  public Location(Location location) {
    this(location.source, location.offset);
    this.line      = location.line;
    this.lineNum   = location.lineNum;
    this.column    = location.column;
  }

  public String  getSource()     { return source; }
  public int     getOffset()     { return offset; }

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
    if (lineNum != -1) {
      return;
    }
    String[] lines = source.split("\n");
    int pos = 0;
    int i;
    for (i = 0; i < lines.length; i++) {
      pos += lines[i].length() + 1;  // Include extra char for the newline
      if (offset < pos) {
        break;
      }
    }
    if (i == lines.length) {
      throw new IllegalStateException("Internal error: offset of " + offset + " too large for source of length " +  source.length());
    }

    // Remember the line and the line number/column number (which both start at 1, not 0)
    line = lines[i];
    lineNum = i + 1;
    // Column is offset - start pos of line + 1
    column = offset - (pos - line.length() - 1) + 1;
  }
}
