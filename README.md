# Format Converter Companion

Converts JSON, YAML, and XML directly in the editor -- select text (or
convert the whole file) and pick a target format from the right-click menu.

## Why it exists

**JSON-YAML-XML Converter** (JetBrains Marketplace id 20297), 10,062
downloads, paid, vendor Syncro Soft -- rated 2.9/5, the lowest of any
competitor evaluated for this workspace. Real, verbatim reviewer
complaints:

- *"Not usable with IntelliJ 2024.2.1 (Ultimate Edition)"* / *"Not usable
  with IntelliJ 2021.2.2"* -- recurring compatibility breaks.
- *"Works okay, but has problems with UTF-8 characters"*
- *"the tool is not able to convert BIG Files (> 1GB)"*
- *"I was expecting that I can convert editor content without extra
  dialog that asks me to enter two file paths... it was easier to open a
  chrome tab with some online converter and copy-paste stuff there"*

## Why built this way

- **No file-path dialog.** Conversion operates directly on the current
  selection, or the whole document if nothing is selected, and replaces
  it in place -- the exact workflow the last complaint above asked for.
- **UTF-8 correct end to end.** Non-ASCII characters are never escaped to
  `\uXXXX`; JSON/YAML/XML parsers and writers all operate on Kotlin
  `String`/`Char` (UTF-16 code units backed by the platform's own
  document text), never raw bytes with an assumed encoding.
- **Hand-rolled JSON and YAML parsers**, not a bundled Jackson/SnakeYAML
  dependency: relying on an IntelliJ Platform-internal library that isn't
  a declared plugin dependency is exactly the kind of classpath fragility
  this workspace already steers away from (same call made for
  `XlsxReader`/`NginxDirectiveIndex` in other plugins here). XML uses the
  JDK's own `javax.xml` DOM APIs (the actual standard library, not an
  added dependency), hardened against XXE.
- **Off the EDT.** Parsing/serialization run on a pooled thread
  (`ApplicationManager.executeOnPooledThread`); only the final document
  write happens on the EDT inside a `WriteCommandAction`. A large paste
  converts without freezing the IDE -- directly answers "not able to
  convert BIG files" without claiming an unbounded size guarantee no
  in-memory converter can actually make.
- **Source format detected from content, not file extension** -- a
  selection or an untitled buffer has no reliable extension at all.

### v1 scope cuts (documented, not silent)

- YAML support covers block/flow mappings and sequences, plain/quoted
  scalars, comments, and block-level anchors/aliases (`&name`/`*name`,
  always expanded to a deep copy -- JSON/XML have no way to represent
  a shared reference) -- not tags, or flow-level anchors/aliases
  (inside `{...}`/`[...]`). A second real `---` document boundary after
  content is rejected with a clear error instead of silently merging
  both documents into one tree.
- XML has no single canonical JSON-shaped representation, so this plugin
  uses the same convention as most JSON&lt;-&gt;XML converters (xmltodict,
  Jackson XmlMapper): attributes become `@name` keys, direct text content
  becomes a `#text` key (or the element's own scalar value if it has no
  attributes/children), and repeated same-name child elements become a
  JSON array.

## Usage

Select some JSON/YAML/XML in the editor (or select nothing to convert the
whole file), right-click, and choose **Convert Format > Convert to
JSON/YAML/XML**.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
