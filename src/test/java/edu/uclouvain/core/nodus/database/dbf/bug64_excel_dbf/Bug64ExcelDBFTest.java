package edu.uclouvain.core.nodus.database.dbf.bug64_excel_dbf;

import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFRow;
import edu.uclouvain.core.nodus.database.dbf.DBFUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class Bug64ExcelDBFTest {

  public Bug64ExcelDBFTest() {
    super();
  }

  @Test
  public void testRead() throws IOException {
    DBFReader dbfReader = null;
    InputStream in = null;
    File input = new File("src/test/resources/bug_64_excel_dbf/wrong-exented-character-field.dbf");
    try {
      in = new BufferedInputStream(new FileInputStream(input));
      dbfReader = new DBFReader(in, null, false, false);
      int fieldCount = dbfReader.getFieldCount();
      DBFRow row = null;
      int count = 0;
      while ((row = dbfReader.nextRow()) != null) {
        count++;
        System.out.println("record:" + count);
        for (int i = 0; i < fieldCount; i++) {
          System.out.print(dbfReader.getField(i).getName() + " = ");
          System.out.println(row.getObject(i));
        }
        System.out.println("");
      }
    } finally {
      DBFUtils.close(dbfReader);
      DBFUtils.close(in);
    }
  }
}
