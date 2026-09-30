/*

(C) Copyright 2017 Alberto Fernández <infjaf@gmail.com>
(C) Copyright 2017 ialek36

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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

/** Created by ialek36 on 11/8/17. */
public class DBFtoDBC {
  public static void main(String args[]) {

    System.out.println("Started...");
    System.out.println(Calendar.getInstance().getTime());

    if (args.length != 2) {
      System.err.println("You must indicate source and target files");
      System.exit(1);
    }
    convert(args[0], args[1]);

    System.out.println(Calendar.getInstance().getTime());
    System.out.println("finished...");
  }

  static void convert(String source, String target) {
    convert(new File(source), new File(target));
  }

  static void convert(File source, File target) {
    try (InputStream inStream = new BufferedInputStream(new FileInputStream(source));
        DBCDATASUSReader reader = new DBCDATASUSReader(inStream);
        DataOutputStream output =
            new DataOutputStream(new BufferedOutputStream(new FileOutputStream(target)))) {
      reader.getHeader().write(output);
      // initDBC has consumed the header and CRC, but decompression is still lazy.
      try (DBFExploderInputStream compressed = new DBFExploderInputStream(inStream)) {
        byte[] input = compressed.getCompressedByteStream().toByteArray();
        DBFExploder.pkexplode(
            input, DBFExploder.createOutputStreamStorage(output), Integer.MAX_VALUE);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
