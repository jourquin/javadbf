package edu.uclouvain.core.nodus.database.dbf.bug60fieldlength;

import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFRow;
import edu.uclouvain.core.nodus.database.dbf.DBFUtils;
import edu.uclouvain.core.nodus.database.dbf.testutils.DbfToTxtTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.junit.Assert;
import org.junit.Test;

public class Bug60FieldLengthTest {
  File testFile = new File("src/test/resources/bug-60-fieldlength/060.dbf");

  public Bug60FieldLengthTest() {
    super();
  }

  @Test
  public void test1() throws Exception {
    File testFile = new File("src/test/resources/bug-60-fieldlength/060.dbf");
    DBFReader reader = null;
    try {
      reader = new DBFReader(new FileInputStream(testFile));
      reader.setTrimRightSpaces(false);
      DBFRow row = reader.nextRow();
      String result = row.getString("DESCRIPTIO");
      Assert.assertEquals(100, result.length());
    } finally {
      DBFUtils.close(reader);
    }
  }

  @Test
  public void testPrint() throws FileNotFoundException {
    File testFile = new File("src/test/resources/bug-60-fieldlength/060.dbf");
    DbfToTxtTest.writeToConsole(testFile);
  }
}
