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
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class DBFResourceTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private Charset charset;

  @Before
  public void before() {
    charset = DBFBase.getCharset();
    DBFBase.setCharset(StandardCharsets.UTF_8);
  }

  @After
  public void after() {
    DBFBase.setCharset(charset);
  }

  private File memo(int size) throws Exception {
    File file = folder.newFile("memo-" + System.nanoTime() + ".fpt");
    ByteBuffer bytes = ByteBuffer.allocate(523);
    bytes.putShort(6, (short) 512);
    bytes.putInt(512, 1).putInt(516, size);
    bytes.position(520);
    bytes.put(new byte[] {'a', 'b', 'c'});
    Files.write(file.toPath(), bytes.array());
    return file;
  }

  @Test
  public void emptyAndMalformedMemoLengthsAreHandled() throws Exception {
    for (boolean memory : new boolean[] {false, true}) {
      try (DBFMemoFile memo = new DBFMemoFile(memo(0), StandardCharsets.UTF_8, memory)) {
        assertEquals("", memo.readText(1));
      }
      for (int size : new int[] {-1, Integer.MAX_VALUE}) {
        try (DBFMemoFile memo = new DBFMemoFile(memo(size), StandardCharsets.UTF_8, memory)) {
          try {
            memo.readText(1);
            fail();
          } catch (DBFException expected) {
          }
        }
      }
    }
  }

  @Test
  public void memoTerminatorMayStraddleBlocks() throws Exception {
    File file = folder.newFile("split.dbt");
    byte[] bytes = new byte[64];
    bytes[20] = 16;
    Arrays.fill(bytes, 32, 47, (byte) 'x');
    bytes[47] = 26;
    bytes[48] = 26;
    bytes[49] = 'z';
    Files.write(file.toPath(), bytes);
    for (boolean memory : new boolean[] {false, true}) {
      DBFMemoFile memo = new DBFMemoFile(file, StandardCharsets.UTF_8, memory);
      assertEquals("xxxxxxxxxxxxxxx", memo.readText(2));
      memo.close();
      memo.close();
      Field storage = DBFMemoFile.class.getDeclaredField("baisMemory");
      storage.setAccessible(true);
      assertNull(storage.get(memo));
    }
  }

  @Test
  public void readerHonorsMemoMemoryModeAndRejectsAttachAfterClose() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DBFWriter writer = new DBFWriter(bytes)) {
      writer.setFields(new DBFField[] {new DBFField("MEMO", DBFDataType.CHARACTER, 10)});
      writer.addRecord(new Object[] {"         1"});
    }
    byte[] dbf = bytes.toByteArray();
    dbf[43] = 'M';
    for (boolean memory : new boolean[] {false, true}) {
      File file = memo(3);
      DBFReader reader = new DBFReader(new ByteArrayInputStream(dbf));
      reader.setMemoFile(file, memory);
      try (RandomAccessFile edit = new RandomAccessFile(file, "rw")) {
        edit.seek(520);
        edit.writeBytes("xyz");
      }
      assertEquals(memory ? "abc" : "xyz", reader.nextRecord()[0]);
      reader.close();
      try {
        reader.setMemoFile(file, memory);
        fail();
      } catch (IllegalStateException expected) {
      }
    }
  }

  private static class TrackedInput extends ByteArrayInputStream {
    int closes;

    TrackedInput(byte[] bytes) {
      super(bytes);
    }

    @Override
    public void close() {
      closes++;
    }
  }

  @Test
  public void compressedReaderClosesItsSource() throws Exception {
    TrackedInput input =
        new TrackedInput(
            Files.readAllBytes(new File("src/test/resources/dbc-files/storm.dbc").toPath()));
    DBCDATASUSReader reader = new DBCDATASUSReader(input);
    assertNotNull(reader.nextRecord());
    reader.close();
    reader.close();
    assertEquals(1, input.closes);
  }

  @Test
  public void truncatedCompressedHeaderClosesItsSource() throws Exception {
    byte[] data = Files.readAllBytes(new File("src/test/resources/dbc-files/storm.dbc").toPath());
    int headerSize = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(8) & 0xffff;
    TrackedInput input = new TrackedInput(Arrays.copyOf(data, headerSize));
    try (DBCDATASUSReader reader = new DBCDATASUSReader(input)) {
      fail();
    } catch (DBFException expected) {
    }
    assertEquals(1, input.closes);
  }

  @Test
  public void decompressionReturnsUnsignedBytesAndSupportsBulkReads() throws Exception {
    byte[] data = Files.readAllBytes(new File("src/test/resources/dbc-files/storm.dbc").toPath());
    int headerSize = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getShort(8) & 0xffff;
    byte[] compressed = Arrays.copyOfRange(data, headerSize + 4, data.length);
    ByteArrayOutputStream expected = new ByteArrayOutputStream();
    DBFExploder.pkexplode(
        compressed, DBFExploder.createOutputStreamStorage(expected), Integer.MAX_VALUE);
    try (DBFExploderInputStream stream =
        new DBFExploderInputStream(new ByteArrayInputStream(compressed))) {
      for (byte b : expected.toByteArray()) assertEquals(b & 0xff, stream.read());
      assertEquals(-1, stream.read());
    }
    try (DBFExploderInputStream stream =
        new DBFExploderInputStream(new ByteArrayInputStream(compressed))) {
      ByteArrayOutputStream actual = new ByteArrayOutputStream();
      byte[] buffer = new byte[123];
      int count;
      while ((count = stream.read(buffer)) != -1) actual.write(buffer, 0, count);
      assertArrayEquals(expected.toByteArray(), actual.toByteArray());
      assertEquals(0, stream.read(buffer, 0, 0));
    }
  }

  @Test(timeout = 2000)
  public void invalidDictionaryReferenceCannotLoopForever() throws Exception {
    byte[] data = {0, 4, 1, 0, 0, 0, 0, 0};
    try {
      DBFExploder.pkexplode(data, DBFExploder.createInMemoryStorage(new byte[100]), 100);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void repeatedConstructionFailuresDoNotLeakFileDescriptors() throws Exception {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    ObjectName operatingSystem = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
    // This optional attribute is queried through standard JMX, without JDK-internal types.
    try {
      server.getAttribute(operatingSystem, "OpenFileDescriptorCount");
    } catch (AttributeNotFoundException unsupported) {
      Assume.assumeNoException(unsupported);
    }
    File malformed = folder.newFile("malformed.dbf");
    Files.write(malformed.toPath(), new byte[32]);
    File invalidMemo = folder.newFile("invalid.fpt");
    File output = folder.newFile("output.dbf");
    for (int round = 0; round < 2; round++) {
      long before =
          ((Number) server.getAttribute(operatingSystem, "OpenFileDescriptorCount")).longValue();
      for (int i = 0; i < 30; i++) {
        try (DBFWriter unexpected = new DBFWriter(malformed, false)) {
          fail();
        } catch (DBFException expected) {
        }
        try (DBFMemoFile unexpected = new DBFMemoFile(invalidMemo, StandardCharsets.UTF_8, false)) {
          fail();
        } catch (DBFException expected) {
        }
        try (DBFWriter unexpected = new DBFWriter(output.getPath(), new DBFField[0])) {
          fail();
        } catch (DBFException expected) {
        }
        try (DBFWriter unexpected = new DBFWriter(output, true, StandardCharsets.UTF_16)) {
          fail();
        } catch (DBFException expected) {
        }
      }
      if (round == 1)
        assertTrue(
            "File descriptors grew after constructor failures",
            ((Number) server.getAttribute(operatingSystem, "OpenFileDescriptorCount")).longValue()
                <= before + 2);
    }
  }
}
