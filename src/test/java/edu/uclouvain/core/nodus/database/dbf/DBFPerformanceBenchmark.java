/*

(C) Copyright 2015-2017 Alberto Fernández <infjaf@gmail.com>
(C) Copyright 2014 Jan Schlößin
(C) Copyright 2003-2004 Anil Kumar K <anil@linuxense.com>

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3.0 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library.  If not, see <http://www.gnu.org/licenses/>.

 */

package edu.uclouvain.core.nodus.database.dbf;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;

/** Manual benchmark, deliberately excluded from the timed assertions of the test suite. */
public final class DBFPerformanceBenchmark {
  private DBFPerformanceBenchmark() {}

  public static void main(String[] args) throws Exception {
    int rows = args.length == 0 ? 100000 : Integer.parseInt(args[0]);
    File file = File.createTempFile("javadbf-benchmark-", ".dbf");
    DBFBase.setCharset(StandardCharsets.UTF_8);
    try {
      run(file, 2000, false);
      run(file, 2000, false);
      for (int i = 0; i < 3; i++) run(file, rows, true);
    } finally {
      Files.deleteIfExists(file.toPath());
    }
  }

  private static void run(File file, int rows, boolean report) throws Exception {
    DBFField[] fields = {
      new DBFField("ID", DBFDataType.NUMERIC, 10, 0),
      new DBFField("TEXT", DBFDataType.CHARACTER, 80),
      new DBFField("AMOUNT", DBFDataType.NUMERIC, 14, 4),
      new DBFField("DATE", DBFDataType.DATE),
      new DBFField("ACTIVE", DBFDataType.LOGICAL)
    };
    long start = System.nanoTime();
    try (DBFWriter writer = new DBFWriter(file.getPath(), fields)) {
      Object[] row = {0, "Bruxelles été", -12345.6789, new Date(1609459200000L), true};
      for (int i = 0; i < rows; i++) {
        row[0] = i;
        writer.addRecord(row);
      }
    }
    long written = System.nanoTime();
    long sum = 0;
    int count = 0;
    try (DBFReader reader = new DBFReader(file.getPath())) {
      Object[] row;
      while ((row = reader.nextRecord()) != null) {
        sum += ((Number) row[0]).longValue();
        count++;
      }
    }
    long read = System.nanoTime();
    if (count != rows || sum != (long) rows * (rows - 1) / 2)
      throw new AssertionError("Wrong rows");
    if (report)
      System.out.printf(
          java.util.Locale.ROOT,
          "%d rows: write %.3f s, read %.3f s, %d bytes%n",
          rows,
          (written - start) / 1e9,
          (read - written) / 1e9,
          file.length());
  }
}
