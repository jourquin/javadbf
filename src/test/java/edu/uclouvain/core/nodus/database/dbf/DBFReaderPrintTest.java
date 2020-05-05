/*

(C) Copyright 2017 Alberto Fernández <infjaf@gmail.com>

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

import edu.uclouvain.core.nodus.database.dbf.testutils.DbfToTxtTest;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

public class DBFReaderPrintTest {

  @Test
  public void printFile() throws Exception {
    File file = new File("src/test/resources/books.dbf");
    DBFReader reader = null;
    try {
      reader = new DBFReader(new FileInputStream(file));
      DbfToTxtTest.export(reader, File.createTempFile("javadbf-test", ".txt"));

    } finally {
      DBFUtils.close(reader);
    }
  }
}
