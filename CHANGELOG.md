# Changelog

## 1.12.3 — 2026-09-30

This release improves robustness and performance without changing public or protected
API signatures. The minimum supported runtime is now Java 11, matching Nodus. See [AUDIT.md](AUDIT.md) for coverage, compatibility decisions and validation.

### Reading and metadata

- Complete short stream reads instead of treating a partial read as end-of-file.
- Buffer filename-based DBF reads and avoid temporary lists and extra numeric-text copies.
- Keep record position correct when skipping deleted rows or calling `skipRecords`; report
  exhausted and closed readers correctly through `hasNextRecord`.
- Map `DBFRow` metadata and null flags to visible columns, including the synthetic deleted column.
- Decode unsigned DBF header, record, character and variable-field lengths correctly.
- Read the full signed 64-bit currency value, including negative values and large amounts.
- Correct little-endian conversion of values with their high bit set.
- Correct modification dates (year offset, unsigned year and month numbering), and avoid
  mutating the record count when serializing a header.
- Recognize high-bit language-driver and field-flag codes and full-length field names.
- Use locale-independent case folding for field lookup and memo filename extensions.
- Reject malformed header dimensions, invalid memo sizes and truncated skips predictably.

### Writing and resource ownership

- Stream file-backed record writes through a fixed 64 KiB buffer, reuse numeric formatters
  per writer/field and reuse the date calendar. No table-sized record collection is needed.
- Flush locked writes while holding the file lock; release locks on validation failure.
- Honor the explicit charset in `DBFWriter(File, Charset)`.
- Close file descriptors when reader, writer, memo or DBC initialization fails.
- Update only date/count metadata when finalizing a file, preserving existing field
  descriptors during append; also handle files lacking an EOF marker and empty reopen/close.
- Release retained rows, buffers and streams on close. Report output-close failures.
- Reject writes after close and incomplete field definitions. Stop if overwriting a file fails.
- Replace recursive text truncation with a bounded search, retaining existing encoding,
  alignment and padding behavior while avoiding stack overflow on long strings.

### Memo and compressed files

- Honor `setMemoFile(file, inMemory)` and reject memo attachment after reader close.
- Read sized FPT/DBT payloads in bulk; validate lengths before allocating payload storage.
- Handle empty memos and DBT terminators crossing a block boundary.
- Replace the decompressor's boxed-byte linked list with contiguous storage and bulk reads.
- Return unsigned bytes from decompression streams, close their source streams and release
  decompressed storage on close.
- Reject invalid empty-dictionary references instead of looping indefinitely; avoid guessed
  compression-ratio limits when the uncompressed size is unknown.
- Close conversion resources on all exit paths.

### Validation and development

- Upgrade the Maven Javadoc plugin from 3.0.0 to 3.12.0 for Java 11 packaging
  (requires Maven 3.6.3+), and replace a legacy HTML table attribute with a caption.

- Add regression tests for fragmented streams, malformed input, descriptor leaks, memo memory
  modes, append compatibility, null flags, numeric boundaries and decompression.
- Align inherited upstream tests with this fork's explicit charset and overwrite defaults.
- Add a repeatable manual read/write benchmark and narrowly scoped SpotBugs exclusions for
  retained API visibility and compiler-generated resource cleanup.
- Require Java 11 for compilation and runtime; configure Maven with `--release 11`,
  align local Eclipse settings and the CI JDK, and ignore generated `bin/` output.
- Replace deprecated numeric wrapper constructors in tests with equivalent `valueOf` calls.
- Keep Eclipse compilation compatible by querying optional descriptor counts through
  standard JMX; close unexpected constructor results in tests, clarify writer-buffer
  ownership, qualify static charset calls and remove an unused test helper.
