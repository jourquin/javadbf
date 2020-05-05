package edu.uclouvain.core.nodus.database.dbf.bug46_zero_field_count;

import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

public class ZeroFieldCountTest {

  public ZeroFieldCountTest() {
    super();
  }

  @Test
  public void test() throws Exception {
    DBFReader dbfReader = null;
    InputStream in = null;
    File input = new File("src/test/resources/bug-46-zero_field_count/a3.dbf");
    try {
      in = new BufferedInputStream(new FileInputStream(input));
      dbfReader = new DBFReader(in);
      int count = dbfReader.getFieldCount();
      System.out.println("" + dbfReader.toString());
      Assert.assertEquals(17, count);
    } finally {
      DBFUtils.close(dbfReader);
      DBFUtils.close(in);
    }
  }
}
