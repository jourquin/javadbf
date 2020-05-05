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
package edu.uclouvain.core.nodus.database.dbf.testutils;

import edu.uclouvain.core.nodus.database.dbf.DBFField;
import edu.uclouvain.core.nodus.database.dbf.DBFReader;
import edu.uclouvain.core.nodus.database.dbf.DBFUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

public final class DbfToTxtTest {

  public static void export(DBFReader reader, File file) {

    PrintWriter writer = null;
    try {
      writer = new PrintWriter(file, "UTF-8");
      Object[] row = null;

      while ((row = reader.nextRecord()) != null) {
        for (Object o : row) {
          writer.print(o + ";");
        }
        writer.println("");
      }
    } catch (IOException e) {
      // nop
    } finally {
      DBFUtils.close(writer);
    }
  }

  public static void writeToConsole(File file) throws FileNotFoundException {
    DBFReader reader = null;
    try {

      // create a DBFReader object
      reader = new DBFReader(new FileInputStream(file));

      // get the field count if you want for some reasons like the following

      int numberOfFields = reader.getFieldCount();

      // use this count to fetch all field information
      // if required

      for (int i = 0; i < numberOfFields; i++) {

        DBFField field = reader.getField(i);

        // do something with it if you want
        // refer the JavaDoc API reference for more details
        //
        System.out.println(field.getDBFType() + " (" + field.getLength() + ") " + field.getName());
      }

      // Now, lets us start reading the rows

      Object[] rowObjects;

      System.out.println("-------------------");
      while ((rowObjects = reader.nextRecord()) != null) {
        for (int i = 0; i < rowObjects.length; i++) {
          System.out.println(rowObjects[i]);
        }
        System.out.println("-------------------");
      }

      // By now, we have iterated through all of the rows

    } finally {
      DBFUtils.close(reader);
    }
  }

  private DbfToTxtTest() {
    throw new AssertionError("no instances");
  }
}
