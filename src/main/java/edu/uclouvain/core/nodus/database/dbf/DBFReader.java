/*

(C) Copyright 2015-2017 Alberto Fernández <infjaf@gmail.com>
(C) Copyright 2004,2014 Jan Schlößin
(C) Copyright 2003-2004 Anil Kumar K <anil@linuxense.com>

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
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * DBFReader class can creates objects to represent DBF data.
 *
 * <p>This Class is used to read data from a DBF file. Meta data and records can be queried against
 * this document.
 *
 * <p>DBFReader cannot write to a DBF file. For creating DBF files use DBFWriter.
 *
 * <p>Fetching records is possible only in the forward direction and cannot be re-wound. In such
 * situations, a suggested approach is to reconstruct the object.
 *
 * <p>The nextRecord() method returns an array of Objects and the types of these Object are as
 * follows:
 *
 * <table>
 * <caption>Types mapping</caption>
 * <thead>
 * <tr>
 * <th>xBase Type</th>
 * <th>Java Type</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 * <td>C</td>
 * <td>String</td>
 * </tr>
 * <tr>
 * <td>N</td>
 * <td>java.math.BigDecimal</td>
 * </tr>
 * <tr>
 * <td>F</td>
 * <td>java.math.BigDecimal</td>
 * </tr>
 * <tr>
 * <td>L</td>
 * <td>Boolean</td>
 * </tr>
 * <tr>
 * <td>D</td>
 * <td>java.util.Date</td>
 * </tr>
 * <tr>
 * <td>Y</td>
 * <td>java.math.BigDecimal</td>
 * </tr>
 * <tr>
 * <td>I</td>
 * <td>Integer</td>
 * </tr>
 * <tr>
 * <td>T</td>
 * <td>java.util.Date</td>
 * </tr>
 * <tr>
 * <td>@</td>
 * <td>java.util.Date</td>
 * </tr>
 * <tr>
 * <td></td>
 * <td>Integer</td>
 * </tr>
 * <tr>
 * <td>V</td>
 * <td>String</td>
 * </tr>
 * <tr>
 * <td>O</td>
 * <td>Double</td>
 * </tr>
 * <tr>
 * <td>M</td>
 * <td>java.lang.String or byte[]</td>
 * </tr>
 * <tr>
 * <td>B</td>
 * <td>byte[] or java.lang.Double</td>
 * </tr>
 * <tr>
 * <td>G</td>
 * <td>byte[]</td>
 * </tr>
 * <tr>
 * <td>P</td>
 * <td>byte[]</td>
 * </tr>
 * <tr>
 * <td>Q</td>
 * <td>byte[]</td>
 * </tr>
 * </tbody>
 * </table>
 */
public class DBFReader extends DBFBase implements Closeable {

  private static final long MILLISECS_PER_DAY = 24 * 60 * 60 * 1000;
  private static final long TIME_MILLIS_1_1_4713_BC = -210866803200000L;

  protected InputStream inputStream;
  protected DataInputStream dataInputStream;
  private DBFHeader header;
  private boolean trimRightSpaces = true;

  private DBFMemoFile memoFile = null;

  private boolean closed = false;

  private Map<String, Integer> mapFieldNames = new HashMap<String, Integer>();

  private boolean showDeletedRows = false;

  private int currentRecord = 0;
  private boolean endOfData;

  /*
   * Nodus API compatibility note : the constructors must also accept a canonical
   * file name instead of an InputStream. Therefore, the type of the first parameter
   * is changed from "InputStream" to "Object". The nature of the object is tested
   * and a DBFException is thrown if it is not a String or an InputStream.
   */

  /**
   * Intializes a DBFReader object.
   *
   * <p>Tries to detect charset from file, if failed uses default charset ISO-8859-1 When this
   * constructor returns the object will have completed reading the header (meta date) and header
   * information can be queried there on. And it will be ready to return the first row.
   *
   * @param in the InputStream or canonical file name where the data is read from.
   */
  public DBFReader(Object in) {
    this(in, defaultCharset, false);
  }

  /**
   * Intializes a DBFReader object.
   *
   * <p>Tries to detect charset from file, if failed uses default charset ISO-8859-1 When this
   * constructor returns the object will have completed reading the header (meta date) and header
   * information can be queried there on. And it will be ready to return the first row.
   *
   * @param in the InputStream or canonical file name where the data is read from.
   * @param showDeletedRows can be used to identify records that have been deleted.
   */
  // TODO Change to boolean in 2.0
  public DBFReader(Object in, Boolean showDeletedRows) {
    this(in, defaultCharset, showDeletedRows);
  }

  /**
   * Initializes a DBFReader object.
   *
   * <p>When this constructor returns the object will have completed reading the header (meta date)
   * and header information can be queried there on. And it will be ready to return the first row.
   *
   * @param in the InputStream or canonical file name where the data is read from.
   * @param charset charset used to decode field names and field contents. If null, then is
   *     autedetected from dbf file
   */
  public DBFReader(Object in, Charset charset) {
    this(in, charset, false);
  }

  /**
   * Initializes a DBFReader object.
   *
   * <p>When this constructor returns the object will have completed reading the header (meta date)
   * and header information can be queried there on. And it will be ready to return the first row.
   *
   * @param in the InputStream or canonical file name where the data is read from.
   * @param charset charset used to decode field names and field contents. If null, then is
   *     autedetected from dbf file
   * @param showDeletedRows can be used to identify records that have been deleted.
   */
  public DBFReader(Object in, Charset charset, boolean showDeletedRows) {
    this(in, charset, showDeletedRows, true);
  }

  /**
   * Initializes a DBFReader object.
   *
   * <p>When this constructor returns the object will have completed reading the header (meta date)
   * and header information can be queried there on. And it will be ready to return the first row.
   *
   * @param in the InputStream or canonical file name where the data is read from.
   * @param charset charset used to decode field names and field contents. If null, then is
   *     autedetected from dbf file
   * @param showDeletedRows can be used to identify records that have been deleted.
   * @param supportExtendedCharacterFields Defines whether 2-byte (extended) length character fields
   *     should be supported (see DBFField.adjustLengthForLongCharSupport(), default: true).
   */
  public DBFReader(
      Object in, Charset charset, boolean showDeletedRows, boolean supportExtendedCharacterFields) {

    if (!(in instanceof String) && !(in instanceof InputStream)) {
      throw new DBFException(
          "First parameter must be a canonical file name (String) or an InputStream.");
    }
    InputStream is = null;
    try {

      if (in instanceof String) {
        is = new BufferedInputStream(new FileInputStream((String) in), 64 * 1024);
      } else {
        is = (InputStream) in;
      }
      this.showDeletedRows = showDeletedRows;
      this.inputStream = is;
      this.dataInputStream = new DataInputStream(this.inputStream);
      this.header = new DBFHeader();
      this.header.setSupportExtendedCharacterFields(supportExtendedCharacterFields);
      this.header.read(this.dataInputStream, charset, showDeletedRows);
      setCharset(this.header.getUsedCharset());
      /* it might be required to leap to the start of records at times */
      int fieldSize = this.header.getFieldDescriptorSize();
      int tableSize = this.header.getTableHeaderSize();
      int t_dataStartIndex =
          this.header.headerLength - (tableSize + (fieldSize * this.header.fieldArray.length)) - 1;
      skip(t_dataStartIndex);

      this.mapFieldNames = createMapFieldNames(this.header.userFieldArray);
    } catch (IOException e) {
      DBFUtils.close(is);
      throw new DBFException(e.getMessage(), e);
    } catch (RuntimeException e) {
      DBFUtils.close(is);
      throw e;
    }
  }

  private Map<String, Integer> createMapFieldNames(DBFField[] fieldArray) {
    Map<String, Integer> fieldNames = new HashMap<String, Integer>();
    for (int i = 0; i < fieldArray.length; i++) {
      String name = fieldArray[i].getName();
      fieldNames.put(name.toLowerCase(Locale.ROOT), i);
    }
    return Collections.unmodifiableMap(fieldNames);
  }

  /**
   * Returns the number of records in the DBF. This number includes deleted (hidden) records
   *
   * @return number of records in the DBF file.
   */
  public int getRecordCount() {
    return this.header.numberOfRecords;
  }
  /**
   * Returns the last time the file was modified
   *
   * @return the las time the file was modified
   */
  public Date getLastModificationDate() {
    if (this.header != null) {
      return this.header.getLastModificationDate();
    }
    return null;
  }

  /**
   * Returns the asked Field. In case of an invalid index, it returns a
   * ArrayIndexOutofboundsException.
   *
   * @param index Index of the field. Index of the first field is zero.
   * @return Field definition for selected field
   */
  public DBFField getField(int index) {
    if (index < 0 || index >= this.header.userFieldArray.length) {
      throw new IllegalArgumentException(
          "Invalid index field: ("
              + index
              + "). Valid range is 0 to "
              + (this.header.userFieldArray.length - 1));
    }
    return new DBFField(this.header.userFieldArray[index]);
  }

  /**
   * Returns the number of field in the DBF.
   *
   * @return number of fields in the DBF file
   */
  public int getFieldCount() {
    return this.header.userFieldArray.length;
  }

  /**
   * Reads the returns the next row in the DBF stream.
   *
   * @return The next row as an Object array. Types of the elements these arrays follow the
   *     convention mentioned in the class description.
   */
  public Object[] nextRecord() {
    if (this.closed) {
      throw new IllegalArgumentException("this DBFReader is closed");
    }
    if (endOfData) {
      return null;
    }
    Object[] record = new Object[getFieldCount()];
    try {
      boolean deleted;
      do {
        int marker = dataInputStream.read();
        if (marker == END_OF_DATA || marker == -1) {
          endOfData = true;
          return null;
        }
        currentRecord++;
        deleted = marker == '*';
        if (deleted && !showDeletedRows) {
          skip(header.recordLength - 1);
        }
      } while (deleted && !showDeletedRows);

      int column = 0;
      if (showDeletedRows) {
        record[column++] = deleted;
      }
      BitSet nullFlags = null;
      for (DBFField field : header.fieldArray) {
        Object value = getFieldValue(field);
        if (field.getDBFType() == DBFDataType.NULL_FLAGS && value instanceof BitSet) {
          nullFlags = (BitSet) value;
        } else if (!field.isSystem()) {
          record[column++] = value;
        }
      }
      if (nullFlags != null) {
        column = showDeletedRows ? 1 : 0;
        int bit = 0;
        for (DBFField field : header.fieldArray) {
          if (field.isSystem() || field.getDBFType() == DBFDataType.NULL_FLAGS) {
            continue;
          }
          if (field.isNullable() && nullFlags.get(bit++)) {
            record[column] = null;
          }
          if (field.getDBFType() == DBFDataType.VARBINARY
              || field.getDBFType() == DBFDataType.VARCHAR) {
            boolean full = nullFlags.get(bit++);
            if (record[column] instanceof byte[]) {
              byte[] data = (byte[]) record[column];
              int length = full ? data.length : data[data.length - 1] & 0xff;
              if (length > data.length) {
                throw new DBFException("Invalid variable field length: " + length);
              }
              byte[] value = java.util.Arrays.copyOf(data, length);
              record[column] =
                  field.getDBFType() == DBFDataType.VARCHAR
                      ? new String(value, getCharset())
                      : value;
            }
          }
          column++;
        }
      }
      return record;
    } catch (IOException e) {
      endOfData = true;
      throw new DBFException(e.getMessage(), e);
    }
  }

  /**
   * Reads the returns the next row in the DBF stream.
   *
   * @return The next row as an DBFRow
   */
  public DBFRow nextRow() {
    Object[] record = nextRecord();
    if (record == null) {
      return null;
    }
    return new DBFRow(record, mapFieldNames, this.header.userFieldArray);
  }

  protected Object getFieldValue(DBFField field) throws IOException {
    switch (field.getDBFType()) {
      case CHARACTER:
        byte b_array[] = new byte[field.getLength()];
        this.dataInputStream.readFully(b_array);
        if (this.trimRightSpaces) {
          return new String(DBFUtils.trimRightSpaces(b_array), getCharset());
        } else {
          return new String(b_array, getCharset());
        }

      case VARCHAR:
      case VARBINARY:
        byte b_array_var[] = new byte[field.getLength()];
        this.dataInputStream.readFully(b_array_var);
        return b_array_var;
      case DATE:
        byte t_byte_year[] = new byte[4];
        this.dataInputStream.readFully(t_byte_year);

        byte t_byte_month[] = new byte[2];
        this.dataInputStream.readFully(t_byte_month);

        byte t_byte_day[] = new byte[2];
        this.dataInputStream.readFully(t_byte_day);

        try {
          GregorianCalendar calendar =
              new GregorianCalendar(
                  Integer.parseInt(new String(t_byte_year, StandardCharsets.US_ASCII)),
                  Integer.parseInt(new String(t_byte_month, StandardCharsets.US_ASCII)) - 1,
                  Integer.parseInt(new String(t_byte_day, StandardCharsets.US_ASCII)));
          return calendar.getTime();
        } catch (NumberFormatException e) {
          // this field may be empty or may have improper value set
          return null;
        }

      case FLOATING_POINT:
      case NUMERIC:
        return DBFUtils.readNumericStoredAsText(this.dataInputStream, field.getLength());

      case LOGICAL:
        byte t_logical = this.dataInputStream.readByte();
        return DBFUtils.toBoolean(t_logical);
      case LONG:
      case AUTOINCREMENT:
        int data = DBFUtils.readLittleEndianInt(this.dataInputStream);
        return data;
      case CURRENCY:
        byte[] currency = new byte[8];
        dataInputStream.readFully(currency);
        return BigDecimal.valueOf(
            ByteBuffer.wrap(currency).order(ByteOrder.LITTLE_ENDIAN).getLong(), 4);
      case TIMESTAMP:
      case TIMESTAMP_DBASE7:
        int days = DBFUtils.readLittleEndianInt(this.dataInputStream);
        int time = DBFUtils.readLittleEndianInt(this.dataInputStream);

        if (days == 0 && time == 0) {
          return null;
        } else {
          Calendar calendar = new GregorianCalendar();
          calendar.setTimeInMillis(days * MILLISECS_PER_DAY + TIME_MILLIS_1_1_4713_BC + time);
          calendar.add(
              Calendar.MILLISECOND, -TimeZone.getDefault().getOffset(calendar.getTimeInMillis()));
          return calendar.getTime();
        }
      case MEMO:
      case GENERAL_OLE:
      case PICTURE:
      case BLOB:
        return readMemoField(field);
      case BINARY:
        if (field.getLength() == 8) {
          return readDoubleField(field);
        } else {
          return readMemoField(field);
        }
      case DOUBLE:
        return readDoubleField(field);
      case NULL_FLAGS:
        byte[] data1 = new byte[field.getLength()];
        dataInputStream.readFully(data1);
        return BitSet.valueOf(data1);
      default:
        skip(field.getLength());
        return null;
    }
  }

  private Object readDoubleField(DBFField field) throws IOException {
    byte[] data = new byte[field.getLength()];
    this.dataInputStream.readFully(data);
    if (data.length < 8) {
      throw new DBFException("Invalid double field length: " + data.length);
    }
    return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getDouble();
  }

  private Object readMemoField(DBFField field) throws IOException {
    Number nBlock = null;
    if (field.getLength() == 10) {
      nBlock = DBFUtils.readNumericStoredAsText(this.dataInputStream, field.getLength());
    } else {
      nBlock = DBFUtils.readLittleEndianInt(this.dataInputStream);
    }
    if (this.memoFile != null && nBlock != null) {
      return memoFile.readData(nBlock.intValue(), field.getDBFType());
    }
    return null;
  }

  /**
   * Safely skip bytesToSkip bytes (in some bufferd scenarios skip doesn't really skip all requested
   * bytes)
   *
   * @param bytesToSkip number of bytes to skip
   * @throws IOException if some IO error happens
   */
  protected void skip(int bytesToSkip) throws IOException {
    DBFUtils.skip(this.dataInputStream, bytesToSkip);
  }
  /**
   * Skip records from reading. Treat "deleted" records as normal records.
   *
   * @param recordsToSkip Number of records to skip.
   * @throws IOException if some IO error happens
   */
  public void skipRecords(int recordsToSkip) throws IOException {
    if (recordsToSkip < 0) {
      throw new IllegalArgumentException("Cannot skip a negative number of records");
    }
    if (showDeletedRows) {
      DBFUtils.skip(dataInputStream, (long) recordsToSkip * header.recordLength);
      currentRecord = (int) Math.min(Integer.MAX_VALUE, (long) currentRecord + recordsToSkip);
    } else {
      for (int i = 0; i < recordsToSkip; i++) {
        if (nextRecord() == null) {
          break;
        }
      }
    }
  }

  protected DBFHeader getHeader() {
    return this.header;
  }
  /**
   * Determine if character fields should be right trimmed (default true)
   *
   * @return true if data is right trimmed
   */
  public boolean isTrimRightSpaces() {
    return this.trimRightSpaces;
  }

  /**
   * Determine if character fields should be right trimmed (default true)
   *
   * @param trimRightSpaces if reading fields should trim right spaces
   */
  public void setTrimRightSpaces(boolean trimRightSpaces) {
    this.trimRightSpaces = trimRightSpaces;
  }

  /**
   * Sets the memo file (DBT or FPT) where memo fields will be readed. If no file is provided, then
   * this fields will be null.
   *
   * @param file the file containing the memo data
   */
  public void setMemoFile(File file) {
    if (this.memoFile != null) {
      throw new IllegalStateException("Memo file is already setted");
    }
    setMemoFile(file, file.length() < (8 * 1024 * 1024));
  }

  /**
   * Sets the memo file (DBT or FPT) where memo fields will be readed. If no file is provided, then
   * this fields will be null.
   *
   * @param file the file containing the memo data
   * @param inMemory if the memoFile shoud be loaded in memory (caution, it may hang your jvm if
   *     memo file is too big)
   */
  public void setMemoFile(File file, boolean inMemory) {
    if (this.memoFile != null) {
      throw new IllegalStateException("Memo file is already setted");
    }
    if (!file.exists()) {
      throw new DBFException("Memo file " + file.getName() + " not exists");
    }
    if (!file.canRead()) {
      throw new DBFException("Cannot read Memo file " + file.getName());
    }
    if (closed) {
      throw new IllegalStateException("Cannot attach a memo file to a closed reader");
    }
    this.memoFile = new DBFMemoFile(file, getCharset(), inMemory);
  }

  @Override
  public void close() {
    if (!closed) {
      this.closed = true;
      DBFUtils.close(this.dataInputStream);
      DBFUtils.close(this.memoFile);
      this.memoFile = null;
      this.dataInputStream = null;
      this.inputStream = null;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(128);
    sb.append(this.header.getYear()).append("/");
    sb.append(this.header.getMonth()).append("/");
    sb.append(this.header.getDay()).append("\n");
    sb.append("Total records: ").append(this.header.numberOfRecords).append("\n");
    sb.append("Header length: ").append(this.header.headerLength).append("\n");
    sb.append("Columns:\n");
    for (DBFField field : this.header.fieldArray) {
      sb.append(field.getName());
      sb.append("\n");
    }
    return sb.toString();
  }

  protected int getEstimatedOutputSize() {
    return (int) Math.min(Integer.MAX_VALUE, (long) header.numberOfRecords * header.recordLength);
  }

  /**
   * ********************************************** New methods added for Nodus API compatibility
   * **********************************************
   */

  /**
   * Returns true if there is at least one more record to read.
   *
   * @return true unless end of file.
   */
  public boolean hasNextRecord() {
    return !closed && !endOfData && currentRecord < this.header.numberOfRecords;
  }

  /**
   * Test if the DBF file is open.
   *
   * @return true if open.
   */
  public boolean isOpen() {
    return !this.closed;
  }
}
