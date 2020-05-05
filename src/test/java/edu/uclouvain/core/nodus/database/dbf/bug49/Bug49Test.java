package edu.uclouvain.core.nodus.database.dbf.bug49;

import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFRow;
import edu.uclouvain.core.nodus.database.dbf.DBFUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.junit.Test;

public class Bug49Test {

  public Bug49Test() {
    super();
  }

  private void print(DBFRow row, int fieldCount) {
    for (int i = 0; i < fieldCount; i++) {
      System.out.println(row.getObject(i));
    }
  }

  @Test
  public void testBug49() throws Exception {
    DBFReader dbfReader = null;
    InputStream in = null;
    File input = new File("src/test/resources/bug-49/student.dbf");
    try {
      in = new BufferedInputStream(new FileInputStream(input));
      dbfReader = new DBFReader(in);
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
