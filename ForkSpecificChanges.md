# Fork specific changes

## Global changes
- Compiled library name changed to `javadbf4nodus-xxx.jar`, in order to identify the jar as a fork.
- Package name changed from `com.linuxense.javadbf` to `edu.uclouvain.core.nodus.database.dbf`. This change was introduced to allow a 
simple replacement of the old library without code change. Indeed, changing the original package name would break many 
Groovy scripts developed by Nodus users.
- Some code reformatting.

# DBFBase
- Set default Charset to StandardCharsets.UTF_8 (since version 1.12.1).
- Make `setCharset(Charset charset)`static. Used to change the default Charset for DBFWriter and DBFReader.

# DBFReader.java

- In the constructors, the first parameter is now an `Object` instead of an `InputStream`. The `Object` can be a `String` or 
an `InputStream`. If it is a `String`, it must be the canonical file name of a DBF file. This allows creating a `DBFReader` using
a file name. 
- Add the `public boolean hasNextRecord()` method.
- Add the `public boolean isOpen()` method.


# DBFWriter.java

- Add a `public DBFWriter(String fileName, DBFField[] fields)` constructor.
- Add a `public DBFWriter(File dbfFile, boolean overwrite)` constructor. 
- Add a `public DBFWriter(File dbfFile, boolean overwrite, Charset charset)` constructor.
- Add a `public DBFWriter(String fileName, DBFField[] fields, Charset charset)` constructor.
- Don't throw an exception in `dbfFileDBFWriter(File dbfFile ...)` if the Charset is set to UTF-8. 

# DBFField.java

- Rename `public DBFType getType()` to public `DBFDataType getDBFType()`.
- Rename `public void setType(DBFDataType type)` to public `void setDBFType(DBFDataType type)`.
- Add the `public DBFField(String name, char type, int length, int decimalCount)`  constructor.
- Add the `public char getType()` method, that returns the char code of a type.
