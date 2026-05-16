# Changelog

All notable changes to SAI will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Interrupt Handling**: Press Ctrl-C during agent execution to cancel running tasks
  - Uses portable JLine-based terminal monitoring
  - Works across Linux, macOS, and Windows
  - Gracefully cancels the current agent task and returns to input prompt
  - No need to kill the process or wait for completion
  
- **File Read Optimization**: Checksum-based change detection for file reads
  - `read()` tool now accepts a `knownChecksum` parameter
  - Returns `changed=false` and no content when file is unchanged since last read
  - Returns `changed=true` with full content when file is new or modified
  - Significantly reduces token usage by avoiding re-transmission of unchanged files
  - Agent can reuse previously known content when `changed=false`

- **File Write Safety**: Checksum validation to prevent accidental overwrites
  - `write()` tool now accepts an `expectedChecksum` parameter
  - Requires checksum to overwrite existing files
  - Detects concurrent modifications and prevents data loss
  - Returns descriptive errors when checksums don't match
  - Ensures safe file operations in multi-agent or manual edit scenarios

### Changed
- Enhanced `MessagePrinter` to provide better feedback for file read operations
  - Shows "File read successfully" or "File is empty" for changed files
  - Shows "File has not changed since last read" for unchanged files
  - Improved error message display

### Fixed
- Fixed critical bug in `read()` method where first reads would return null content
  - Original logic: `isModified = !isEmpty(known) && !equals(known, current)`
  - Fixed logic: `isModified = isEmpty(known) || !equals(known, current)`
  - First reads now correctly return content instead of null

### Technical Details
- Added `InterruptMonitor` class for portable Ctrl-C detection using JLine Terminal
- Modified `CommandProcessor` to expose `cancelRunningTask()` method
- Added `changed` field to `ToolIO.ReadResponse` 
- Added `knownChecksum` parameter to `ToolIO.ReadRequest`
- Added `expectedChecksum` parameter to `ToolIO.WriteRequest`
- Exposed `Terminal` in `Printer` class via `@Getter` annotation

## [1.0-SNAPSHOT] - Initial Release

### Added
- Initial release with core functionality
- Support for OpenAI, Azure OpenAI, and GitHub Copilot proxy providers
- Interactive and headless modes
- Session persistence and management
- Agent Skills support
- Command-line interface with multiple subcommands
- Persona system for agent configuration
- File operations (read, write, search/replace, line edit, patch)
- Shell command execution
- Streaming output with event display
