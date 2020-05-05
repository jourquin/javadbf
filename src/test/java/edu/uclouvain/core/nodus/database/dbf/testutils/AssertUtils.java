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

import edu.uclouvain.core.nodus.database.dbf.DBFDataType;
import edu.uclouvain.core.nodus.database.dbf.DBFField;

import org.junit.Assert;

public class AssertUtils {

  public static void assertColumnDefinition(
      DBFField field, String columnName, DBFDataType type, int length, int decimal) {
    Assert.assertEquals(columnName, field.getName());
    Assert.assertEquals(type, field.getDBFType());
    Assert.assertEquals(length, field.getLength());
    Assert.assertEquals(decimal, field.getDecimalCount());
  }
}
