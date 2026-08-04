<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Format Converter Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- Convert the current selection (or whole file) between JSON, YAML, and
  XML from the editor right-click menu, no file-path dialog.
- Hand-rolled JSON and YAML parsers/writers; XML via the JDK's
  `javax.xml` DOM APIs, hardened against XXE.
- Source format detection by content, not file extension.
- Conversion runs off the EDT so large documents don't freeze the IDE.

[Unreleased]: https://github.com/GapHunterLabs/format-converter-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/format-converter-companion/commits/0.1.0
