/*
 * Copyright © 2022-2026  James Crawford
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

package io.jactl.benchmark;

import java.io.PrintStream;

public class MonteCarloPI {
  static class PRandom {
    static long rotateLeft(long i, int amt) {
      return (i << amt) | (i >>> -amt);
    }
    
    long[] s = new long[] { -2152535657050944081L, 7960286522194355700L, 487617019471545679L, -537132696929009172L };

    final long nextLong() {
      long result = rotateLeft(s[1] * 5, 7) * 9;
      long t = s[1] << 17;
      s[2] ^= s[0]; s[3] ^= s[1];
      s[1] ^= s[2]; s[0] ^= s[3];
      s[2] ^= t;
      s[3] = rotateLeft(s[3], 45);
      return result;
    }

    final long nextInt(int bound) {
      return ((nextLong() % bound) + bound) % bound;
    }
  }

  public static void run(String source, PrintStream out) {
    int nSuccess = 0;
    double x, y;
    final int loops = 100_000_000;
    final int N = 1_000_000_000;
    PRandom r = new PRandom();
    for (int i = 0; i < loops; i++) {
      x = r.nextInt(N)/(double)N;
      y = r.nextInt(N)/(double)N;
      if (x*x + y*y <= 1) nSuccess++;
    }
    out.println("PI=" + 4*(double)nSuccess/(double)loops);
  }
}
