<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Format Converter Companion Changelog

## [Unreleased]

## [0.1.2]

### Added

- Review/star CTA: after 5 successful conversions, a one-time
  notification asks whether to rate the plugin on Marketplace, with a
  permanent "Don't ask again" option.

## [0.1.1]

### Added

- YAML block-level anchors and aliases (`&name`/`*name`) — an alias
  always expands to a deep copy of its anchor's value, never a shared
  reference. Anchors must be defined before they're used.

### Fixed

- A second real `---` document boundary after content used to be
  silently dropped, merging both documents' content into a single
  parse with no warning. Now rejected with a clear
  `FormatConversionException` instead — a single leading `---` (the
  conventional "start of the document" marker) still works exactly as
  before.

## [0.1.0]

### Added

- Convert the current selection (or whole file) between JSON, YAML, and
  XML from the editor right-click menu, no file-path dialog.
- Hand-rolled JSON and YAML parsers/writers; XML via the JDK's
  `javax.xml` DOM APIs, hardened against XXE.
- Source format detection by content, not file extension.
- Conversion runs off the EDT so large documents don't freeze the IDE.

[Unreleased]: https://github.com/GapHunterLabs/format-converter-companion/compare/0.1.2...HEAD
[0.1.2]: https://github.com/GapHunterLabs/format-converter-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/format-converter-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/format-converter-companion/commits/0.1.0
