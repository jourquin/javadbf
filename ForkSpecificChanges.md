# Fork specific changes

## Global changes
- Compiled library name changed to `javadbf4nodus-xxx.jar`, in order to identify the jar as a fork.
- Package name changed from `com.linuxense.javadbf` to `edu.uclouvain.core.nodus.database.dbf`. This change was introduced to allow a 
simple replacement of the old library without code change. Indeed, changing the original package name would break many 
Groovy scripts developed by Nodus users.
- Some code reformatting.

# DBFBase
- Set default Charset to StandardCharsets.UTF_8 (since version 1.12.1).
- Keep `setCharset(Charset charset)` and `getCharset()` static. They configure or
  report the default for future readers and writers; changing it does not affect
  existing instances. Null charsets are rejected.
- Add `getEffectiveCharset()` to report the charset of an individual reader or
  writer.
- The deprecated `getCharactersetName()` and `setCharactersetName(String)` now
  operate on the instance charset, independently of the static default. Prefer
  selecting the charset in the constructor. For writers, the legacy setter also
  updates the header charset.

# DBFReader.java

- In the constructors, the first parameter is now an `Object` instead of an `InputStream`. The `Object` can be a `String` or 
an `InputStream`. If it is a `String`, it must be the canonical file name of a DBF file. This allows creating a `DBFReader` using
a file name. 
- Add the `public boolean hasNextRecord()` method.
- Add the `public boolean isOpen()` method.
- When no charset is supplied, detect it from the DBF header and use the
  configured default as a fallback. An explicit charset overrides the header.
- Use the instance charset for character fields, VARCHAR values and attached
  textual memo files.


# DBFWriter.java

- Add a `public DBFWriter(String fileName, DBFField[] fields)` constructor.
- Add a `public DBFWriter(File dbfFile, boolean overwrite)` constructor. 
- Add a `public DBFWriter(File dbfFile, boolean overwrite, Charset charset)` constructor.
- Add a `public DBFWriter(String fileName, DBFField[] fields, Charset charset)` constructor.
- Don't throw an exception in `dbfFileDBFWriter(File dbfFile ...)` if the Charset is set to UTF-8.
- Use the instance charset when serializing records and writing field descriptors.
  Opening a writer does not change the static default or another open instance.

- Buffer file-backed record writes in 64 KiB, flushing before header updates at close.
  Records are serialized immediately; writer memory does not grow with the row count.
- Reuse numeric formatters per writer/column and a calendar per writer for date values.
- Preserve append order and a single EOF marker, including reopening without adding rows.
- `DBFLockWriter` flushes records while holding the file lock and releases the lock when
  record validation fails. Subclasses that reposition the underlying file can call
  `flushRecords()` first.
- OutputStream constructors retain their existing in-memory record behavior.

# DBFField.java

- Rename `public DBFType getType()` to public `DBFDataType getDBFType()`.
- Rename `public void setType(DBFDataType type)` to public `void setDBFType(DBFDataType type)`.
- Add the `public DBFField(String name, char type, int length, int decimalCount)`  constructor.
- Add the `public char getType()` method, that returns the char code of a type.


# DBFHeader.java, DBFMemoFile.java and DBFUtils.java

- Add a `DBFHeader.read` overload with an explicit fallback charset so a reader or
  append writer uses the default captured when that instance was created.
- Decode textual memo values strictly with the reader's effective charset.
- Add strict `DBFUtils.decodeText` and `DBFUtils.encodeText` helpers and truncate
  padded character values at whole Unicode code points.

  
  ## Encoding changes in version 1.12.4

- Each reader and writer retains its own effective charset. Opening a file with a
  different charset no longer changes another open reader or writer.
- An explicit constructor charset takes precedence over the DBF header. Without
  an explicit charset, readers and writers appending to an existing file detect
  its language-driver/code-page byte, then use the configured fallback if that
  byte is not recognized. New writers use the configured default (UTF-8 unless
  changed).
- Text decoding and encoding report malformed input and unrepresentable
  characters with `DBFException`, instead of silently inserting replacement
  characters. This also applies to field names and textual memo values.
- Character-field widths remain byte-based. Overlong text is still truncated,
  but only at whole Unicode code points, so a surrogate pair is not split.
- Regression tests cover simultaneous readers and writers with different
  charsets, changes to the default while instances remain open, header detection
  and explicit overrides, strict conversion, and Unicode truncation. Legacy
  fixtures now declare their intended charset rather than depending on charset
  changes made by another test.

### CPG files and the Nodus integration

A `.cpg` file is a plain-text companion to a DBF, with the same filename stem,
that declares the DBF text encoding (for example, `roads.cpg` containing `UTF-8`
for `roads.dbf`). CPG discovery and creation belong to Nodus; this library does
not automatically read or write these files. Nodus resolves project overrides,
CPG metadata, the DBF language driver, then UTF-8, and supplies the resulting
charset explicitly to the library. Layer saves and exports preserve the resolved
encoding and write matching CPG metadata.
  