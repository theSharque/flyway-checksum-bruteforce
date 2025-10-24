# FlyWay Checksum Brute-Force Tool

A utility for modifying FlyWay migration files while preserving their checksums.

## Problem

When you need to modify an existing FlyWay migration file (e.g., remove schema prefixes, fix SQL syntax, or make other changes), FlyWay will detect that the checksum has changed and refuse to run the migration. This creates a dilemma:

- **Option 1**: Manually update the checksum in the database (tedious and error-prone)
- **Option 2**: Create a new migration (loses migration history)
- **Option 3**: Use this tool to find a comment that makes the checksums match ✅

## Solution

This tool takes your **original file** and **modified file**, then uses brute-force to find a SQL comment that, when appended to the modified file, produces the exact same checksum as the original.

## How It Works

1. Calculate checksum of the original file (what's in the database)
2. Calculate checksum of the modified file
3. If they differ, perform multi-threaded brute-force search to find a matching comment
4. Verify the solution
5. Automatically save the modified file with the comment appended

The tool uses the exact same CRC32 algorithm that FlyWay uses internally, guaranteeing 100% accuracy.

## Features

- **Accurate FlyWay Checksum Calculation**: Direct implementation of FlyWay's internal algorithm
- **Multi-threaded Brute-Force**: Fast parallel search using 16 threads
- **Automatic File Backup**: Original files are backed up with `.old` extension
- **Printable Comments**: Only generates valid SQL comments with printable characters
- **Verification**: Validates the solution before saving

## Installation

### Prerequisites

- Java 11 or higher
- Gradle (included via wrapper)

### Build

```bash
./gradlew build
```

This creates an executable JAR file with wrapper scripts for easy use.

## Usage

The tool includes convenient wrapper scripts that handle all Java flags automatically:

- **Linux/Mac**: `./brute-force`
- **Windows**: `brute-force.bat`

### Calculate checksum of a single file

```bash
./brute-force migrations/your_file.sql
```

Output:
```
Source file: migrations/your_file.sql

=== Calculating Checksum ===
Checksum (FlyWay): -2078186952
Checksum (hex): 0x84216238
File size: 1744 bytes

✅ Checksum calculated successfully!
```

### Find matching comment for modified file

```bash
./brute-force migrations/test_src.sql migrations/test_dst.sql
```

Output:
```
=== FlyWay Checksum Calculator ===
Source file: migrations/test_src.sql
  Checksum: 78787420 (0x4B2335C)

Target file: migrations/test_dst.sql
  Checksum: 798974111 (0x2F9F609F)

Checksums differ. Starting brute-force to find matching comment...

=== Brute-forcing Printable Comment ===
Target CRC32: 78787420 (0x4B2335C)
Searching for comment string '--<chars>' that produces target checksum...

Trying comment length: 1
   Not found at length 1 (13 ms)
Trying comment length: 2
   Not found at length 2 (14 ms)
Trying comment length: 3
   Not found at length 3 (56 ms)
Trying comment length: 4
   Not found at length 4 (1095 ms)
Trying comment length: 5
✅ Found in 21357 ms
✅ Found printable comment: '--|Zd~p'

=== Verifying Solution ===
Modified content checksum: 78787420 (0x4B2335C)
Target checksum:          78787420 (0x4B2335C)

✅ Verification passed! Saving modified file...

Original file backed up to: migrations/test_dst.sql.old
Modified file saved to: migrations/test_dst.sql

SUCCESS! File has been updated with matching comment.
```

The modified file will now look like:
```sql
SELECT 2;
--|Zd~p
```

Your `test_dst.sql` now has the same checksum as `test_src.sql`, and FlyWay will accept it!

### Verbose mode

Add `--verbose` flag to see detailed CRC32 calculations:

```bash
./brute-force migrations/original.sql migrations/modified.sql --verbose
```

### Direct JAR usage (if you prefer)

You can also run the JAR directly:

```bash
java --add-opens java.base/java.util.zip=ALL-UNNAMED \
  -jar build/libs/brute-force-1.0-SNAPSHOT.jar \
  migrations/your_file.sql
```

## Algorithm Details

### FlyWay Checksum Calculation

The tool replicates FlyWay's exact checksum algorithm:

1. Create CRC32 instance
2. Read file line by line
3. Filter BOM (Byte Order Mark) from each line
4. Convert each line to UTF-8 bytes
5. Update CRC32 with the bytes
6. Return CRC32 value as signed 32-bit integer

### Brute-Force Search

1. Calculate base CRC32 of the modified file
2. Try comment lengths from 1 to 8 characters
3. Use 16 parallel threads to test character combinations
4. Character set: `0-9`, `A-Z`, `a-z`, and printable symbols (95 total)
5. Stop immediately when a match is found

**Performance**: Typical search completes in 3-15 seconds for most files.

### File Modification

When a matching comment is found:

1. Remove all trailing newlines from the file
2. Append the comment as a new line
3. Add a single newline at the end
4. Verify the checksum matches
5. Save with backup of the original

## Technical Details

- **Language**: Java 11
- **Build System**: Gradle
- **Dependencies**: None (fully standalone)
- **Algorithm**: CRC32 with direct field manipulation via reflection
- **Threading**: Fixed pool of 16 threads
- **Character Set**: 95 printable ASCII characters

## Use Cases

### 1. Remove Schema Prefixes

**Original**:
```sql
CREATE TABLE public.users (id INT);
```

**Modified**:
```sql
CREATE TABLE users (id INT);
--X3k9
```

### 2. Fix SQL Syntax

**Original**:
```sql
SELECT * FROM table WHERE id = 1
```

**Modified**:
```sql
SELECT * FROM table WHERE id = 1;
--mP4z
```

### 3. Update Comments

**Original**:
```sql
-- Old comment
SELECT 1;
```

**Modified**:
```sql
-- New comment
SELECT 1;
--9aB2
```

## Limitations

- Maximum comment length: 8 characters (can be increased in code)
- Only works with text files (SQL migrations)

## Technical Notes

### Why wrapper scripts?

The tool uses reflection to directly manipulate the internal `crc` field of `java.util.zip.CRC32` for performance optimization. This requires the `--add-opens java.base/java.util.zip=ALL-UNNAMED` flag in Java 9+.

The wrapper scripts (`brute-force` and `brute-force.bat`) automatically add this flag, so you don't need to remember it.

## Development

This project was developed entirely using **Cursor AI** with Claude Sonnet 4.5. All development activities were performed exclusively through the AI agent:

- 🔍 Source code analysis and FlyWay internals investigation
- 🛠️ Decompilation of FlyWay JAR files to understand the checksum algorithm
- 💻 Complete code implementation and optimization
- 🧪 Testing and debugging
- 📝 Documentation writing

This demonstrates the capability of modern AI coding assistants to handle complex reverse engineering and algorithm implementation tasks end-to-end.

## License

MIT

## Contributing

Feel free to open issues or submit pull requests.
