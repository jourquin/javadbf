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

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Compatibility checks for buffered file output and reusable value formatters. */
public class DBFWriterBufferingTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private Charset originalCharset;

  @Before
  public void setCharset() {
    originalCharset = DBFBase.getCharset();
    DBFBase.setCharset(StandardCharsets.UTF_8);
  }

  @After
  public void restoreCharset() {
    DBFBase.setCharset(originalCharset);
  }

  private DBFField[] fields() {
    return new DBFField[] {
      new DBFField("ID", DBFDataType.NUMERIC, 10, 0),
      new DBFField("LABEL", DBFDataType.CHARACTER, 20),
      new DBFField("AMOUNT", DBFDataType.NUMERIC, 14, 4),
      new DBFField("RATE", DBFDataType.FLOATING_POINT, 12, 2),
      new DBFField("SHIPDATE", DBFDataType.DATE),
      new DBFField("ACTIVE", DBFDataType.LOGICAL)
    };
  }

  private Object[] row(int id) {
    return new Object[] {
      id,
      id % 3 == 0 ? null : "été - a long label to truncate",
      id % 5 == 0 ? null : new BigDecimal("-12345.67895"),
      id % 4 == 0 ? null : new BigDecimal("12.345"),
      id % 7 == 0 ? null : Date.valueOf("2024-02-29"),
      id % 3 == 0 ? null : id % 2 == 0
    };
  }

  @Test
  public void fileMatchesStreamAcrossBufferBoundaries() throws Exception {
    File file = temporary.newFile();
    Files.write(file.toPath(), new byte[1024 * 1024]);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DBFWriter buffered = new DBFWriter(file);
        DBFWriter stream = new DBFWriter(bytes)) {
      buffered.setFields(fields());
      stream.setFields(fields());
      Object[] reusable = new Object[6];
      for (int i = 0; i < 5000; i++) {
        Object[] values = row(i);
        System.arraycopy(values, 0, reusable, 0, values.length);
        buffered.addRecord(reusable);
        stream.addRecord(values);
      }
    }
    assertArrayEquals(bytes.toByteArray(), Files.readAllBytes(file.toPath()));
    try (DBFReader reader = new DBFReader(file.getPath())) {
      assertEquals(5000, reader.getRecordCount());
      for (int i = 0; i < 5000; i++) {
        assertEquals(i, ((Number) reader.nextRecord()[0]).intValue());
      }
      assertNull(reader.nextRecord());
    }
  }

  @Test
  public void appendAndEmptyReopenPreserveRecordsAndSingleEof() throws Exception {
    File file = temporary.newFile();
    ByteArrayOutputStream expected = new ByteArrayOutputStream();
    try (DBFWriter writer = new DBFWriter(file)) {
      writer.setFields(fields());
    }
    byte[] empty = Files.readAllBytes(file.toPath());
    try (DBFWriter writer = new DBFWriter(file, false)) {
      // Opening and closing an empty table must not append another EOF marker.
    }
    assertArrayEquals(empty, Files.readAllBytes(file.toPath()));
    try (DBFWriter stream = new DBFWriter(expected)) {
      stream.setFields(fields());
      for (int batch = 0; batch < 3; batch++) {
        try (DBFWriter writer = new DBFWriter(file, false)) {
          for (int i = batch * 1500; i < (batch + 1) * 1500; i++) {
            writer.addRecord(row(i));
            stream.addRecord(row(i));
          }
        }
      }
    }
    byte[] completed = Files.readAllBytes(file.toPath());
    assertArrayEquals(expected.toByteArray(), completed);
    DBFWriter writer = new DBFWriter(file, false);
    writer.close();
    writer.close();
    assertArrayEquals(completed, Files.readAllBytes(file.toPath()));
  }

  @Test
  public void numericRoundingAndNullsKeepTheirRepresentation() throws Exception {
    File file = temporary.newFile();
    DBFField[] numeric = {
      new DBFField("A", DBFDataType.NUMERIC, 8, 2),
      new DBFField("B", DBFDataType.FLOATING_POINT, 8, 4)
    };
    try (DBFWriter writer = new DBFWriter(file)) {
      writer.setFields(numeric);
      writer.addRecord(new Object[] {new BigDecimal("12.345"), new BigDecimal("-0.12505")});
      writer.addRecord(new Object[] {new BigDecimal("12.355"), new BigDecimal("-0.12515")});
      writer.addRecord(new Object[] {null, 0});
    }
    byte[] data = Files.readAllBytes(file.toPath());
    assertEquals(
        "    12.34 -0.1250    12.36 -0.1252           0.0000\u001a",
        new String(data, 97, data.length - 97, StandardCharsets.US_ASCII));
  }

  @Test
  public void invalidRecordDoesNotCorruptBufferedRows() throws Exception {
    File file = temporary.newFile();
    try (DBFWriter writer = new DBFWriter(file)) {
      writer.setFields(fields());
      writer.addRecord(row(1));
      try {
        writer.addRecord(new Object[] {2, "text", "not a number", null, null, null});
        fail("Expected record validation to fail");
      } catch (DBFException expected) {
        // A subsequent valid row must still be writable.
      }
      writer.addRecord(row(3));
    }
    try (DBFReader reader = new DBFReader(file.getPath())) {
      assertEquals(2, reader.getRecordCount());
      assertEquals(1, ((Number) reader.nextRecord()[0]).intValue());
      assertEquals(3, ((Number) reader.nextRecord()[0]).intValue());
      assertNull(reader.nextRecord());
    }
  }

  @Test
  public void lockedWriterFlushesBeforeUnlockingAndReleasesLockAfterValidationFailure()
      throws Exception {
    File file = temporary.newFile();
    try (DBFLockWriter writer = new DBFLockWriter(file)) {
      writer.setFields(fields());
      long headerLength = file.length();
      writer.addRecord(row(1));
      assertEquals(headerLength + 66, file.length());
      try {
        writer.addRecord(new Object[0]);
        fail("Expected record validation to fail");
      } catch (DBFException expected) {
        // The lock must be released even when addRecord throws a runtime exception.
      }
      try (RandomAccessFile probe = new RandomAccessFile(file, "rw");
          FileLock lock = probe.getChannel().tryLock()) {
        assertNotNull(lock);
      }
      writer.addRecord(row(2));
    }
    try (DBFReader reader = new DBFReader(file.getPath())) {
      assertEquals(2, reader.getRecordCount());
    }
  }

  @Test
  public void closeReportsBufferedWriteFailures() throws Exception {
    File file = temporary.newFile();
    DBFWriter writer = new DBFWriter(file);
    writer.setFields(fields());
    writer.addRecord(row(1));
    writer.getRamdonAccessFile().close();
    try {
      writer.close();
      fail("Expected buffered output failure");
    } catch (DBFException expected) {
      assertTrue(expected.getCause() instanceof IOException);
    }
    writer.close();
  }
}
