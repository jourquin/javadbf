package edu.uclouvain.core.nodus.database.dbf.bug67windows1252;

import edu.uclouvain.core.nodus.database.dbf.DBFDataType;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Test;

public class Bug67windows1252 {

  private static final String CITY_NAME = "Vandœuvre-lès-Nancy";

  public Bug67windows1252() {
    super();
  }

  @Test
  public void test1() throws Exception {
    File dbfFile = File.createTempFile("exampewindows1252", ".dbf");
    DBFWriter dbfWriter =
        new DBFWriter(new FileOutputStream(dbfFile), Charset.forName("windows-1252"));
    DBFField field = new DBFField();
    field.setName("city"); // give a name to the field
    field.setDBFType(DBFDataType.CHARACTER); // and set its type
    field.setLength(25); // and length of the field
    dbfWriter.setFields(new DBFField[] {field});
    dbfWriter.addRecord(new Object[] {CITY_NAME});
    dbfWriter.close();

    DBFReader reader = new DBFReader(new FileInputStream(dbfFile));
    Object[] o = reader.nextRecord();
    String value = (String) o[0];
    reader.close();
    System.out.println(value);
    Assert.assertEquals(CITY_NAME, value);
  }
}
