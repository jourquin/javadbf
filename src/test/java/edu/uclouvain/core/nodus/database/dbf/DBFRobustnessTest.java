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

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Regression cases from the complete-library robustness audit. */
public class DBFRobustnessTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private Charset originalCharset;
  private Locale originalLocale;

  @Before
  public void before() {
    originalCharset = DBFBase.getCharset();
    originalLocale = Locale.getDefault();
    DBFBase.setCharset(StandardCharsets.UTF_8);
  }

  @After
  public void after() {
    DBFBase.setCharset(originalCharset);
    Locale.setDefault(originalLocale);
  }

  private byte[] table(int count) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DBFWriter writer = new DBFWriter(bytes, StandardCharsets.UTF_8)) {
      writer.setFields(
          new DBFField[] {
            new DBFField("ID", DBFDataType.NUMERIC, 4, 0),
            new DBFField("TEXT", DBFDataType.CHARACTER, 12),
            new DBFField("DATE", DBFDataType.DATE)
          });
      for (int i = 0; i < count; i++) {
        writer.addRecord(new Object[] {i, "été", java.sql.Date.valueOf("2024-02-29")});
      }
    }
    return bytes.toByteArray();
  }

  private static class TrackedInput extends ByteArrayInputStream {
    int closes;

    TrackedInput(byte[] data) {
      super(data);
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) {
      return super.read(b, off, Math.min(1, len));
    }

    @Override
    public long skip(long n) {
      return 0;
    }

    @Override
    public void close() {
      closes++;
    }
  }

  @Test
  public void shortReadsAreCompletedForTextDatesAndNumbers() throws Exception {
    TrackedInput input = new TrackedInput(table(3));
    DBFReader reader = new DBFReader(input);
    for (int i = 0; i < 3; i++) {
      Object[] row = reader.nextRecord();
      assertEquals(i, ((Number) row[0]).intValue());
      assertEquals("été", row[1]);
      assertEquals(java.sql.Date.valueOf("2024-02-29"), row[2]);
    }
    assertFalse(reader.hasNextRecord());
    assertNull(reader.nextRecord());
    reader.close();
    reader.close();
    assertEquals(1, input.closes);
  }

  @Test
  public void deletedRecordsAndSkippingKeepThePhysicalPosition() throws Exception {
    byte[] data = table(3);
    data[129] = '*';
    try (DBFReader reader = new DBFReader(new ByteArrayInputStream(data))) {
      assertEquals(1, ((Number) reader.nextRecord()[0]).intValue());
      reader.skipRecords(1);
      assertFalse(reader.hasNextRecord());
    }
    try (DBFReader reader = new DBFReader(new ByteArrayInputStream(data), true)) {
      DBFRow row = reader.nextRow();
      assertTrue(row.isDeleted());
      assertEquals(0, row.getInt("ID"));
      reader.skipRecords(2);
      assertFalse(reader.hasNextRecord());
    }
  }

  @Test
  public void eofAndCloseMakeHasNextFalseEvenWithWrongHeaderCount() throws Exception {
    byte[] data = table(1);
    data[4] = 4;
    DBFReader reader = new DBFReader(new ByteArrayInputStream(data));
    reader.nextRecord();
    assertNull(reader.nextRecord());
    assertFalse(reader.hasNextRecord());
    reader.close();
    assertFalse(reader.hasNextRecord());
  }

  @Test
  public void runtimeConstructorFailureClosesItsInput() throws Exception {
    byte[] data = table(0);
    data[8] = 0;
    data[9] = 0;
    TrackedInput input = new TrackedInput(data);
    try (DBFReader reader = new DBFReader(input)) {
      fail();
    } catch (DBFException expected) {
    }
    assertEquals(1, input.closes);
  }

  @Test
  public void skipRejectsPrematureEofAndMakesProgressWhenSkipReturnsZero() throws Exception {
    TrackedInput input = new TrackedInput(new byte[] {1, 2, 3});
    DBFUtils.skip(input, 2);
    assertEquals(3, input.read());
    try {
      DBFUtils.skip(input, 10);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test
  public void endianConversionsPreserveHighBitsAndHeaderState() throws Exception {
    assertEquals(0x00000080, DBFUtils.littleEndian(0x80000000));
    assertEquals((short) 0x0080, DBFUtils.littleEndian((short) 0x8000));
    DBFHeader header = new DBFHeader();
    header.setUsedCharset(StandardCharsets.UTF_8);
    header.fieldArray = new DBFField[] {new DBFField("N", DBFDataType.NUMERIC, 1, 0)};
    header.numberOfRecords = 0x12345678;
    ByteArrayOutputStream a = new ByteArrayOutputStream();
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    header.write(new DataOutputStream(a));
    header.write(new DataOutputStream(b));
    assertEquals(0x12345678, header.numberOfRecords);
    assertArrayEquals(a.toByteArray(), b.toByteArray());
  }

  @Test
  public void modificationDateUsesUnsignedYearAndZeroBasedMonth() throws Exception {
    byte[] data = table(0);
    data[1] = (byte) 130;
    data[2] = 2;
    data[3] = 28;
    try (DBFReader reader = new DBFReader(new ByteArrayInputStream(data))) {
      assertEquals(java.sql.Date.valueOf("2030-02-28"), reader.getLastModificationDate());
    }
  }

  @Test
  public void namesAreCaseInsensitiveUnderTurkishLocale() throws Exception {
    Locale.setDefault(new Locale("tr", "TR"));
    try (DBFReader reader = new DBFReader(new ByteArrayInputStream(table(1)))) {
      assertEquals(0, reader.nextRow().getInt("id"));
    }
  }

  private byte[] raw(char[] types, int[] lengths, int[] flags, byte[] records, int rows)
      throws Exception {
    int headerSize = 33 + types.length * 32;
    int recordSize = 1;
    for (int length : lengths) recordSize += length;
    ByteBuffer bytes =
        ByteBuffer.allocate(headerSize + records.length + 1).order(ByteOrder.LITTLE_ENDIAN);
    bytes.put((byte) 0x30).put((byte) 126).put((byte) 9).put((byte) 30);
    bytes.putInt(rows).putShort((short) headerSize).putShort((short) recordSize);
    bytes.position(32);
    for (int i = 0; i < types.length; i++) {
      int offset = bytes.position();
      bytes.put((byte) ('A' + i));
      bytes.position(offset + 11);
      bytes.put((byte) types[i]);
      bytes.putInt(0).put((byte) lengths[i]);
      bytes.put((byte) (types[i] == 'C' ? lengths[i] >> 8 : 0));
      bytes.put((byte) flags[i]);
      bytes.position(offset + 32);
    }
    bytes.put((byte) 13).put(records).put((byte) 26);
    return bytes.array();
  }

  @Test
  public void currencyKeepsAll64BitsIncludingNegativeValues() throws Exception {
    long[] values = {4294967297L, -12345L, Long.MAX_VALUE, Long.MIN_VALUE};
    ByteBuffer records = ByteBuffer.allocate(values.length * 9).order(ByteOrder.LITTLE_ENDIAN);
    for (long value : values) records.put((byte) ' ').putLong(value);
    try (DBFReader reader =
        new DBFReader(
            new TrackedInput(
                raw(
                    new char[] {'Y'},
                    new int[] {8},
                    new int[] {0},
                    records.array(),
                    values.length)))) {
      for (long value : values) assertEquals(BigDecimal.valueOf(value, 4), reader.nextRecord()[0]);
    }
  }

  @Test
  public void longCharacterFieldAndDeletedSkipUseUnsignedLengths() throws Exception {
    byte[] records = new byte[2 * 32769];
    Arrays.fill(records, (byte) 'x');
    records[0] = '*';
    records[32769] = ' ';
    try (DBFReader reader =
        new DBFReader(
            new ByteArrayInputStream(
                raw(new char[] {'C'}, new int[] {32768}, new int[] {0}, records, 2)))) {
      assertEquals(32768, reader.getField(0).getLength());
      assertEquals(32768, ((String) reader.nextRecord()[0]).length());
      assertFalse(reader.hasNextRecord());
    }
  }

  @Test
  public void nullableFieldsAccountForHiddenAndDeletedColumns() throws Exception {
    byte[] record = {'*', 1, 0, 0, 0, 'a', 'b', 'c', 1};
    byte[] bytes =
        raw(new char[] {'I', 'C', '0'}, new int[] {4, 3, 1}, new int[] {1, 2, 1}, record, 1);
    try (DBFReader reader = new DBFReader(new TrackedInput(bytes), true)) {
      DBFRow row = reader.nextRow();
      assertTrue(row.isDeleted());
      assertNull(row.getString("B"));
    }
  }

  @Test
  public void variableFieldLengthsAreUnsigned() throws Exception {
    byte[] record = new byte[203];
    record[0] = ' ';
    Arrays.fill(record, 1, 201, (byte) 'x');
    record[201] = (byte) 200;
    try (DBFReader reader =
        new DBFReader(
            new TrackedInput(
                raw(new char[] {'V', '0'}, new int[] {201, 1}, new int[] {0, 1}, record, 1)))) {
      assertEquals(200, ((String) reader.nextRecord()[0]).length());
    }
  }

  @Test
  public void explicitFileWriterCharsetIsHonored() throws Exception {
    File file = folder.newFile();
    Charset charset = Charset.forName("windows-1251");
    try (DBFWriter writer = new DBFWriter(file, charset)) {
      writer.setFields(new DBFField[] {new DBFField("TEXT", DBFDataType.CHARACTER, 10)});
      writer.addRecord(new Object[] {"Привет"});
    }
    assertEquals((byte) 0xc9, Files.readAllBytes(file.toPath())[29]);
    try (DBFReader reader = new DBFReader(file.getPath(), (Charset) null)) {
      assertEquals("Привет", reader.nextRecord()[0]);
    }
  }

  @Test
  public void closedStreamWriterReleasesRowsAndReportsCloseFailures() throws Exception {
    OutputStream output =
        new ByteArrayOutputStream() {
          @Override
          public void close() throws IOException {
            throw new IOException("close failure");
          }
        };
    DBFWriter writer = new DBFWriter(output);
    writer.setFields(new DBFField[] {new DBFField("TEXT", DBFDataType.CHARACTER, 10)});
    writer.addRecord(new Object[] {"payload"});
    try {
      writer.close();
      fail();
    } catch (DBFException expected) {
      assertEquals("close failure", expected.getCause().getMessage());
    }
    Field rows = DBFWriter.class.getDeclaredField("v_records");
    rows.setAccessible(true);
    assertTrue(((List<?>) rows.get(writer)).isEmpty());
    writer.close();
  }

  @Test
  public void longTextTruncationMatchesLegacyBytesWithoutRecursion() {
    String[] texts = {"été東京😀", "abc", "\ud800z"};
    Charset[] charsets = {
      StandardCharsets.UTF_8,
      StandardCharsets.ISO_8859_1,
      StandardCharsets.UTF_16,
      Charset.forName("windows-1251")
    };
    for (Charset charset : charsets)
      for (String text : texts)
        for (int length = 0; length < 20; length++) {
          String prefix = text;
          while (prefix.getBytes(charset).length > length)
            prefix = prefix.substring(0, prefix.length() - 1);
          byte[] expected = new byte[length];
          Arrays.fill(expected, (byte) ' ');
          byte[] encoded = prefix.getBytes(charset);
          System.arraycopy(encoded, 0, expected, 0, encoded.length);
          assertArrayEquals(expected, DBFUtils.textPadding(text, charset, length));
        }
    char[] longText = new char[100000];
    Arrays.fill(longText, 'x');
    assertEquals(
        "xxx",
        new String(
            DBFUtils.textPadding(new String(longText), StandardCharsets.UTF_8, 3),
            StandardCharsets.UTF_8));
  }

  @Test
  public void appendPreservesDescriptorsAndFilesWithoutEofMarker() throws Exception {
    for (boolean hasEof : new boolean[] {true, false}) {
      byte[] data = table(1);
      data[55] = 42; // Dialect-specific field metadata must survive append unchanged.
      if (!hasEof) data = Arrays.copyOf(data, data.length - 1);
      File file = folder.newFile();
      Files.write(file.toPath(), data);
      try (DBFWriter writer = new DBFWriter(file, false)) {
        writer.addRecord(new Object[] {9, "new", java.sql.Date.valueOf("2025-01-01")});
      }
      byte[] actual = Files.readAllBytes(file.toPath());
      assertArrayEquals(Arrays.copyOfRange(data, 8, 129), Arrays.copyOfRange(actual, 8, 129));
      try (DBFReader reader = new DBFReader(file.getPath())) {
        assertEquals(2, reader.getRecordCount());
        assertEquals(0, ((Number) reader.nextRecord()[0]).intValue());
        assertEquals(9, ((Number) reader.nextRecord()[0]).intValue());
        assertNull(reader.nextRecord());
      }
    }
  }

  @Test
  public void skipCannotSeekPastPhysicalEof() throws Exception {
    File file = folder.newFile();
    Files.write(file.toPath(), new byte[] {1});
    try (InputStream input = new FileInputStream(file)) {
      try {
        DBFUtils.skip(input, 1000);
        fail();
      } catch (EOFException expected) {
      }
    }
  }
}
