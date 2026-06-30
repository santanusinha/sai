# File Operations

SAI provides several file operation tools for reading, writing, and modifying files. These tools include advanced features like checksum-based change detection and write safety.

## Reading Files

The `read()` tool reads file content with intelligent change detection to optimize token usage.

### Basic Usage

```plaintext
Read a file:
  Tool: read
  Parameters:
    - filePath: "/path/to/file.txt"
    - requestReason: "Read configuration"
    - knownChecksum: ""  (empty for first read)
```

### Checksum-Based Optimization

When you read a file for the first time, provide an empty `knownChecksum`. The response includes:
- `content`: The full file content
- `checksum`: SHA-256 hash of the content
- `changed`: true (indicating new/modified content)

On subsequent reads, provide the previous checksum:

**If the file hasn't changed:**
- `content`: null (no content returned - saves tokens!)
- `checksum`: Same as before
- `changed`: false

**If the file has changed:**
- `content`: New file content
- `checksum`: New SHA-256 hash
- `changed`: true

### Example Workflow

```
1. First read:
   Input:  knownChecksum = ""
   Output: content = "Hello", checksum = "abc123", changed = true

2. Re-read (unchanged):
   Input:  knownChecksum = "abc123"
   Output: content = null, checksum = "abc123", changed = false
   → Use previously known "Hello" content

3. Re-read (modified):
   Input:  knownChecksum = "abc123"
   Output: content = "World", checksum = "def456", changed = true
```

### Benefits

- **Token Efficiency**: Unchanged files don't re-send content
- **Bandwidth Optimization**: Large files only transmitted when modified
- **Change Detection**: Automatically detects file modifications
- **Session Continuity**: Track file state across multiple operations

## Writing Files

The `write()` tool includes safety checks to prevent accidental overwrites and data loss.

### Basic Usage

```plaintext
Write to a new file:
  Tool: write
  Parameters:
    - filePath: "/path/to/new-file.txt"
    - content: "File content here"
    - requestReason: "Create new file"
    - expectedChecksum: ""  (empty for new files)
```

### Checksum Validation

When overwriting an existing file, you **must** provide the `expectedChecksum`:

```plaintext
Update existing file:
  Tool: write
  Parameters:
    - filePath: "/path/to/existing-file.txt"
    - content: "Updated content"
    - requestReason: "Update configuration"
    - expectedChecksum: "abc123"  (from previous read)
```

### Safety Checks

**New Files**: Can write with empty `expectedChecksum`

**Existing Files Without Checksum**:
```
Error: "File already exists at /path/to/file.txt. 
        Re-read the file to get the correct content and checksum, 
        then provide the checksum in the write request to overwrite."
```

**Existing Files With Wrong Checksum**:
```
Error: "Checksum mismatch. Expected: abc123, Actual: def456. 
        Re-read the file to get the current checksum."
```

### Benefits

- **Prevents Accidental Overwrites**: Can't overwrite without confirmation
- **Detects Concurrent Modifications**: Catches changes made by other processes
- **Data Loss Prevention**: Ensures you're working with the latest content
- **Explicit Intent**: Forces acknowledgment of file state before modification

## Best Practices

### For Reading Files

1. **First Read**: Always use empty checksum
   ```
   read(filePath, "", reason)
   ```

2. **Store Checksums**: Keep track of checksums for files you're monitoring
   ```
   fileChecksums["config.yaml"] = "abc123"
   ```

3. **Subsequent Reads**: Provide known checksum
   ```
   read(filePath, fileChecksums["config.yaml"], reason)
   ```

4. **Handle Changes**: When `changed=true`, update your stored checksum
   ```
   if response.changed:
       fileChecksums[filePath] = response.checksum
       content = response.content
   else:
       content = previouslyKnownContent
   ```

### For Writing Files

1. **New Files**: Use empty checksum
   ```
   write(filePath, content, reason, "")
   ```

2. **Updates**: Always read first, then write with checksum
   ```
   response = read(filePath, "", "read before update")
   checksum = response.checksum
   write(filePath, newContent, reason, checksum)
   ```

3. **Handle Conflicts**: If write fails with checksum mismatch
   ```
   1. Re-read the file to see what changed
   2. Decide whether to:
      - Merge your changes with the new content
      - Overwrite with your version
      - Abort the operation
   3. Write with the new checksum
   ```

## Other File Operations

### Search and Replace

Precisely replace text in a file:

```plaintext
Tool: search_replace
Parameters:
  - filePath: "/path/to/file.txt"
  - searchText: "old text"
  - replaceText: "new text"
  - requestReason: "Update configuration"
  - expectedChecksum: "abc123"
  - occurrence: 1
```

### Line Editing

Edit files by line number:

```plaintext
Tool: line_edit
Parameters:
  - filePath: "/path/to/file.txt"
  - startLine: 10
  - endLine: 15
  - operation: "REPLACE"
  - content: "new content"
  - requestReason: "Update section"
  - expectedChecksum: "abc123"
```

Operations: INSERT_BEFORE, INSERT_AFTER, REPLACE, DELETE

### Patch Application

Apply unified diff patches:

```plaintext
Tool: patch
Parameters:
  - filePath: "/path/to/file.txt"
  - patchContent: "diff -u ..."
  - requestReason: "Apply fix"
  - expectedChecksum: "abc123"
```

## Error Handling

All file operations return structured responses with error information:

```json
{
  "success": false,
  "error": "File not found: /path/to/file.txt"
}
```

Common errors:
- `File not found`: File doesn't exist
- `Checksum mismatch`: File modified since last read
- `Permission denied`: Insufficient permissions
- `Invalid path`: Path is not valid

Always check the response for errors before proceeding with dependent operations.
