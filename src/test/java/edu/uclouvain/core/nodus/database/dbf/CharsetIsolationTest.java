package edu.uclouvain.core.nodus.database.dbf;

import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.*;
import org.junit.Test;

/** Mixed encodings must not alter any other open reader or writer. */
public class CharsetIsolationTest {
  private static final Charset CP1252 = Charset.forName("windows-1252");
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final String TEXT = "Été – coût €";

  private byte[] bytes(Charset charset) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (DBFWriter writer = new DBFWriter(output, charset)) {
      writer.setFields(new DBFField[] {new DBFField("LABEL", 'C', 80, 0)});
      writer.addRecord(new Object[] {TEXT});
    }
    return output.toByteArray();
  }

  @Test public void simultaneouslyOpenReadersKeepTheirEncodings() {
    byte[] utf = bytes(UTF8), cp = bytes(CP1252);
    Charset previous = DBFBase.getCharset();
    try (DBFReader a = new DBFReader(new ByteArrayInputStream(utf), UTF8);
         DBFReader b = new DBFReader(new ByteArrayInputStream(cp), CP1252)) {
      DBFBase.setCharset(StandardCharsets.US_ASCII);
      assertEquals(TEXT, a.nextRecord()[0]);
      assertEquals(TEXT, b.nextRecord()[0]);
      assertEquals(UTF8, a.getEffectiveCharset());
      assertEquals(CP1252, b.getEffectiveCharset());
    } finally { DBFBase.setCharset(previous); }
  }

  @Test public void simultaneouslyOpenWritersKeepTheirEncodings() {
    ByteArrayOutputStream utf = new ByteArrayOutputStream(), cp = new ByteArrayOutputStream();
    Charset previous = DBFBase.getCharset();
    try {
      try (DBFWriter a = new DBFWriter(utf, UTF8); DBFWriter b = new DBFWriter(cp, CP1252)) {
        DBFField[] fields = {new DBFField("LABEL", 'C', 80, 0)};
        a.setFields(fields); b.setFields(fields);
        a.addRecord(new Object[] {TEXT}); b.addRecord(new Object[] {TEXT});
        DBFBase.setCharset(StandardCharsets.US_ASCII);
      }
      try (DBFReader a = new DBFReader(new ByteArrayInputStream(utf.toByteArray()), UTF8);
           DBFReader b = new DBFReader(new ByteArrayInputStream(cp.toByteArray()), CP1252)) {
        assertEquals(TEXT, a.nextRecord()[0]); assertEquals(TEXT, b.nextRecord()[0]);
      }
    } finally { DBFBase.setCharset(previous); }
  }

  @Test public void defaultReaderDetectsMarkedCodePageAndExplicitOverrideWins() {
    Charset previous = DBFBase.getCharset();
    try {
      byte[] cp = bytes(CP1252);
      DBFBase.setCharset(UTF8);
      try (DBFReader reader = new DBFReader(new ByteArrayInputStream(cp))) {
        assertEquals(CP1252, reader.getEffectiveCharset());
        assertEquals(TEXT, reader.nextRecord()[0]);
        assertEquals(UTF8, DBFBase.getCharset());
      }
      byte[] utf = bytes(UTF8);
      utf[29] = (byte) DBFCharsetHelper.getDBFCodeForCharset(CP1252);
      try (DBFReader reader = new DBFReader(new ByteArrayInputStream(utf), UTF8)) {
        assertEquals(TEXT, reader.nextRecord()[0]);
      }
    } finally { DBFBase.setCharset(previous); }
  }

  @SuppressWarnings("deprecation")
  @Test public void legacyInstanceSetterUpdatesWriterHeaderWithoutChangingOtherFiles() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    Charset defaultBefore = DBFBase.getCharset();
    try (DBFWriter writer = new DBFWriter(output, UTF8)) {
      writer.setCharactersetName("windows-1252");
      assertEquals(CP1252.displayName(), writer.getCharactersetName());
      writer.setFields(new DBFField[]{new DBFField("LABEL", 'C', 80, 0)});
      writer.addRecord(new Object[]{TEXT});
    }
    assertEquals(defaultBefore, DBFBase.getCharset());
    try (DBFReader reader = new DBFReader(new ByteArrayInputStream(output.toByteArray()))) {
      assertEquals(CP1252, reader.getEffectiveCharset());
      assertEquals(TEXT, reader.nextRecord()[0]);
    }
  }

  @Test public void strictTextAndSupplementaryCharactersRespectFieldByteWidths() {
    try { DBFUtils.encodeText("漢", CP1252); fail("Unrepresentable output must fail"); }
    catch (DBFException expected) { assertTrue(expected.getMessage().contains("windows-1252")); }
    try { DBFUtils.decodeText(new byte[]{(byte)0xc3}, UTF8); fail("Malformed input must fail"); }
    catch (DBFException expected) { assertTrue(expected.getMessage().contains("UTF-8")); }
    assertEquals("😀", DBFUtils.decodeText(DBFUtils.textPadding("😀é", UTF8, 4), UTF8));
    assertEquals("   ", DBFUtils.decodeText(DBFUtils.textPadding("😀", UTF8, 3), UTF8));
  }
}
