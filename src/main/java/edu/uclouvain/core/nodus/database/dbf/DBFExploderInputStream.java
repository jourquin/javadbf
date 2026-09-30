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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** A DBC decompression stream backed by one byte array, without a boxed-byte queue. */
class DBFExploderInputStream extends InputStream {
  private InputStream in;
  private final int estimatedUncompressedSize;
  private byte[] data;
  private int position;
  private boolean closed;

  DBFExploderInputStream(InputStream in) {
    this(in, 0);
  }

  DBFExploderInputStream(InputStream in, int uncompressedSize) {
    this.in = in;
    this.estimatedUncompressedSize = uncompressedSize;
  }

  private void load() throws IOException {
    if (closed) {
      throw new IOException("Decompression stream is closed");
    }
    if (data == null) {
      byte[] compressed = getCompressedByteStream().toByteArray();
      ByteArrayOutputStream output = new ByteArrayOutputStream(4096);
      if (compressed.length > 0) {
        DBFExploder.pkexplode(
            compressed,
            DBFExploder.createOutputStreamStorage(output),
            getAdjustedOutputSize(compressed));
      }
      data = output.toByteArray();
    }
  }

  @Override
  public int read() throws IOException {
    load();
    return position < data.length ? data[position++] & 0xff : -1;
  }

  @Override
  public int read(byte[] bytes, int offset, int length) throws IOException {
    if (bytes == null) {
      throw new NullPointerException("bytes");
    }
    if (offset < 0 || length < 0 || offset > bytes.length - length) {
      throw new IndexOutOfBoundsException();
    }
    if (length == 0) {
      return 0;
    }
    load();
    if (position == data.length) {
      return -1;
    }
    int count = Math.min(length, data.length - position);
    System.arraycopy(data, position, bytes, offset, count);
    position += count;
    return count;
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      data = null;
      try {
        in.close();
      } finally {
        in = null;
      }
    }
  }

  protected int getAdjustedOutputSize(byte[] compressedData) {
    // A guessed compression ratio can truncate valid, highly compressed files.
    // Storage grows with actual output; this limit does not preallocate memory.
    return estimatedUncompressedSize > 0 ? estimatedUncompressedSize : Integer.MAX_VALUE;
  }

  protected ByteArrayOutputStream getCompressedByteStream() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
    byte[] buffer = new byte[4096];
    int count;
    while ((count = in.read(buffer)) != -1) {
      if (count == 0) {
        int value = in.read();
        if (value == -1) {
          break;
        }
        bytes.write(value);
      } else {
        bytes.write(buffer, 0, count);
      }
    }
    return bytes;
  }
}
