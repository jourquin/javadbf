package edu.uclouvain.core.nodus.database.dbf.bug34_char_encoding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import edu.uclouvain.core.nodus.database.dbf.DBFDataType;
import edu.uclouvain.core.nodus.database.dbf.DBFException;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFUtils;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class Bug34CharacterEncodingTest {

  public Bug34CharacterEncodingTest() {
    super();
  }

  @Test
  public void testBug34EncodingFile() throws DBFException {
    System.out.println(StandardCharsets.UTF_8.toString());
  }

  @Test
  public void testBug34EncodingStream() throws DBFException {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DBFReader reader = null;
    DBFWriter wr = null;
    try {
      wr = new DBFWriter(baos, StandardCharsets.UTF_8);
      // wr.setCharset(Charset.forName("windows-1253"));
      DBFField fields[] = new DBFField[1];

      fields[0] = new DBFField();
      fields[0].setName("emp_name");
      fields[0].setDBFType(DBFDataType.CHARACTER);
      fields[0].setLength(10);
      wr.setFields(fields);
      wr.addRecord(new Object[] {"Simón"});
      wr.addRecord(new Object[] {"Julián"});

      DBFUtils.close(wr);
      byte[] data = baos.toByteArray();

      Assert.assertNotEquals(3, data[29]);
      System.out.println("" + data[29]);

      ByteArrayInputStream bais = new ByteArrayInputStream(data);

      List<String> names = new ArrayList<String>();
      reader = new DBFReader(bais);
      reader.setCharset(StandardCharsets.UTF_8);
      Object[] rowObject;
      while ((rowObject = reader.nextRecord()) != null) {
        names.add((String) rowObject[0]);
      }
      assertNotNull(names.get(0));
      assertEquals("Simón", names.get(0).trim());

      assertNotNull(names.get(1));
      assertEquals("Julián", names.get(1).trim());
    } finally {
      DBFUtils.close(reader);
      DBFUtils.close(wr);
    }
  }
}
