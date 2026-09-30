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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Locale;

/** Class for read memo files (DBT and FPT) */
public class DBFMemoFile implements Closeable {

  private DataInputStream fileInMemory = null;
  private ByteArrayInputStream baisMemory = null;
  private Charset charset = null;
  private int blockSize = 512;
  private boolean fpt = false;
  private RandomAccessFile file;
  private long fileLength;
  private boolean closed;

  protected DBFMemoFile(File memoFile, Charset charset, boolean inMemory) {
    this.charset = charset;
    this.fpt = memoFile.getName().toLowerCase(Locale.ROOT).endsWith(".fpt");
    try {
      initReader(memoFile, inMemory);
      this.fileLength = inMemory ? baisMemory.available() : memoFile.length();
      this.blockSize = readBlockSize();
    } catch (RuntimeException e) {
      close();
      throw e;
    }
  }

  private void initReader(File memoFile, boolean inMemory) {
    try {
      if (!inMemory) {
        this.file = new RandomAccessFile(memoFile, "r");
      } else {
        this.baisMemory = new ByteArrayInputStream(Files.readAllBytes(memoFile.toPath()));
        this.fileInMemory = new DataInputStream(this.baisMemory);
      }
    } catch (IOException ex) {
      throw new DBFException(ex.getMessage(), ex);
    }
  }

  protected DBFMemoFile(File memoFile, Charset charset) {
    this(memoFile, charset, memoFile.length() < (8 * 1024 * 1024));
  }

  private void seek(long pos) throws IOException {
    if (closed) {
      throw new IOException("Memo file is closed");
    }
    if (pos < 0 || pos > fileLength) {
      throw new IOException("Invalid memo offset: " + pos);
    }
    if (fileInMemory != null) {
      fileInMemory.reset();
      DBFUtils.skip(fileInMemory, pos);
    } else {
      file.seek(pos);
    }
  }

  public int read(byte b[]) throws IOException {
    if (closed) {
      throw new IOException("Memo file is closed");
    }
    if (baisMemory != null) {
      return baisMemory.read(b);
    }
    return file.read(b);
  }

  private DataInput getDataInput() {
    if (fileInMemory != null) {
      return fileInMemory;
    }
    return file;
  }

  private short readShort() throws IOException {
    return getDataInput().readShort();
  }

  private short readLittleEndianShort() throws IOException {
    return DBFUtils.readLittleEndianShort(getDataInput());
  }

  private int readBlockSize() {

    try {
      int size = 0;
      if (isFPT()) {
        seek(6);
        size = readShort() & 0xffff;
      } else {
        seek(20);
        size = readLittleEndianShort() & 0xffff;
      }
      if (size == 0) {
        size = 512;
      }
      return size;

    } catch (IOException ex) {
      throw new DBFException(ex.getMessage(), ex);
    }
  }

  private boolean isFPT() {
    return this.fpt;
  }
  /**
   * Only for testing purposes
   *
   * @param block position of first block of this field
   * @return text contained in the block
   */
  protected String readText(int block) {
    return (String) readData(block, DBFDataType.MEMO);
  }
  /**
   * Only for testing purposes
   *
   * @param block postition of first block of this field
   * @return data in the block as byte
   */
  protected byte[] readBinary(int block) {
    return (byte[]) readData(block, DBFDataType.BINARY);
  }

  protected Object readData(int block, DBFDataType type) {
    if (closed) {
      throw new IllegalStateException("Memo file is closed");
    }
    if (block == 0) {
      return null;
    }
    long blockStart = (long) blockSize * block;
    DBFDataType usedType = type;
    try {
      seek(blockStart);
      int prefixLength = (int) Math.min(8, fileLength - blockStart);
      byte[] prefix = new byte[prefixLength];
      getDataInput().readFully(prefix);
      byte[] data;
      if (isFPT() || (prefixLength >= 4 && isMagicDBase4(prefix))) {
        if (prefixLength < 8) {
          throw new IOException("Truncated memo header");
        }
        ByteBuffer header = ByteBuffer.wrap(prefix);
        int itemSize;
        if (isFPT()) {
          int itemType = header.getInt();
          if (itemType == 1) {
            usedType = DBFDataType.MEMO;
          } else if (itemType == 2) {
            usedType = DBFDataType.BINARY;
          } else if (itemType == 0) {
            usedType = DBFDataType.PICTURE;
          }
          itemSize = header.getInt();
        } else {
          itemSize = header.order(ByteOrder.LITTLE_ENDIAN).getInt(4) - 8;
        }
        if (itemSize < 0 || itemSize > fileLength - blockStart - 8) {
          throw new IOException("Invalid memo length: " + itemSize);
        }
        data = new byte[itemSize];
        getDataInput().readFully(data);
      } else {
        seek(blockStart);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(blockSize);
        byte[] buffer = new byte[blockSize];
        boolean pendingEndByte = false;
        boolean end = false;
        int count;
        while (!end && (count = read(buffer)) > 0) {
          for (int i = 0; i < count; i++) {
            int value = buffer[i] & 0xff;
            if (pendingEndByte) {
              if (value == 0x1a) {
                end = true;
                break;
              }
              bytes.write(0x1a);
            }
            pendingEndByte = value == 0x1a;
            if (!pendingEndByte) {
              bytes.write(value);
            }
          }
        }
        if (pendingEndByte && !end) {
          bytes.write(0x1a);
        }
        data = bytes.toByteArray();
      }
      return usedType == DBFDataType.MEMO ? new String(data, charset) : data;
    } catch (IOException ex) {
      throw new DBFException(ex.getMessage(), ex);
    }
  }

  private boolean isMagicDBase4(byte[] blockData) {
    return blockData[0] == (byte) 0xFF
        && blockData[1] == (byte) 0xFF
        && blockData[2] == 0x08
        && blockData[3] == 0x00;
  }

  public void close() {
    if (!closed) {
      closed = true;
      DBFUtils.close(this.file);
      DBFUtils.close(this.fileInMemory);
      this.file = null;
      this.fileInMemory = null;
      this.baisMemory = null;
    }
  }
}
