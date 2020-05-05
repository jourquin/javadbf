package edu.uclouvain.core.nodus.database.dbf.limit255fields;

import edu.uclouvain.core.nodus.database.dbf.DBFDataType;
import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFRow;
import edu.uclouvain.core.nodus.database.dbf.DBFWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class LimitOf255FieldsPerRecord {

  private static int NUMBER_OF_COLUMNS_TO_CREATE = 315;
  private static int NUMBER_OF_ROWS_TO_CREATE = 5000;

  public LimitOf255FieldsPerRecord() {
    super();
  }

  /**
   * Test number of columns supported.
   *
   * <p>https://github.com/albfernandez/javadbf/issues/53
   *
   * <p>engines only has to support 255. Javadbf can create and read more than 255 columns, but
   * cannot be viewed in all engines
   */
  @Test
  @Ignore("Now this is disable to generate compatible DBF files")
  public void testLimit() throws IOException {
    byte[] data = writeFile();
    Assert.assertNotNull(data);
    Assert.assertTrue(data.length > 0);
    try (DBFReader reader = new DBFReader(new ByteArrayInputStream(data))) {
      Assert.assertEquals(NUMBER_OF_COLUMNS_TO_CREATE, reader.getFieldCount());
      Assert.assertEquals(NUMBER_OF_ROWS_TO_CREATE, reader.getRecordCount());
      DBFRow row;
      int countRows = 0;
      while ((row = reader.nextRow()) != null) {
        int columns = 0;
        for (int i = 0; i < reader.getFieldCount(); i++) {
          Assert.assertEquals("data_" + i, row.getString(i));
          columns++;
        }
        Assert.assertEquals(NUMBER_OF_COLUMNS_TO_CREATE, columns);

        countRows++;
      }
      Assert.assertEquals(NUMBER_OF_ROWS_TO_CREATE, countRows);
    }
  }

  private byte[] writeFile() throws IOException {
    DBFField[] fields = new DBFField[NUMBER_OF_COLUMNS_TO_CREATE];
    for (int i = 0; i < fields.length; i++) {
      DBFField field = new DBFField("field_" + i, DBFDataType.CHARACTER);
      field.setLength(15);
      fields[i] = field;
    }
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      DBFWriter writer = new DBFWriter(baos);
      writer.setFields(fields);
      Object[] record = new Object[NUMBER_OF_COLUMNS_TO_CREATE];
      for (int i = 0; i < record.length; i++) {
        record[i] = "data_" + i;
      }
      for (int i = 0; i < NUMBER_OF_ROWS_TO_CREATE; i++) {
        writer.addRecord(record);
      }
      writer.close();
      return baos.toByteArray();
    }
  }
}
