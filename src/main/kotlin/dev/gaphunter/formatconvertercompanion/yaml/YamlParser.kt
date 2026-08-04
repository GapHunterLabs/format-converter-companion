package dev.gaphunter.formatconvertercompanion.yaml

import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.model.FormatConversionException

private val PLAIN_NUMBER = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")
private val LINE_SPLIT = Regex("\r\n|\r|\n")

private fun classifyPlainScalar(token: String): DataNode {
    if (token.isEmpty() || token == "~" || token == "null") return DataNode.Null
    if (token == "true") return DataNode.Bool(true)
    if (token == "false") return DataNode.Bool(false)
    if (PLAIN_NUMBER.matches(token)) return DataNode.Num(token)
    return DataNode.Str(token)
}

/**
 * Parses a single-line flow value: `{...}`, `[...]`, a quoted scalar, or a
 * bare plain scalar/token. Shared by top-level bare-scalar documents,
 * mapping values, and sequence items -- YAML flow style is close enough to
 * JSON grammar that one small recursive-descent parser covers all of it.
 */
private class FlowParser(private val text: String, private val lineNumber: Int) {
    private var pos = 0

    fun parseAndConsumeAll(): DataNode {
        skipSpaces()
        val value = parseValue()
        skipSpaces()
        if (pos != text.length) fail("Unexpected trailing content after value")
        return value
    }

    private fun fail(message: String): Nothing = throw FormatConversionException(message, lineNumber, pos + 1)

    private fun skipSpaces() {
        while (pos < text.length && text[pos] == ' ') pos++
    }

    private fun parseValue(): DataNode {
        skipSpaces()
        if (pos >= text.length) fail("Unexpected end of value")
        return when (text[pos]) {
            '{' -> parseFlowMapping()
            '[' -> parseFlowSequence()
            '"' -> DataNode.Str(consumeDoubleQuoted())
            '\'' -> DataNode.Str(consumeSingleQuoted())
            else -> classifyPlainScalar(consumePlainToken())
        }
    }

    private fun parseFlowMapping(): DataNode.Obj {
        pos++
        val entries = LinkedHashMap<String, DataNode>()
        skipSpaces()
        if (pos < text.length && text[pos] == '}') {
            pos++
            return DataNode.Obj(entries)
        }
        while (true) {
            skipSpaces()
            val key = when {
                pos < text.length && text[pos] == '"' -> consumeDoubleQuoted()
                pos < text.length && text[pos] == '\'' -> consumeSingleQuoted()
                else -> consumePlainKey()
            }
            skipSpaces()
            if (pos >= text.length || text[pos] != ':') fail("Expected ':' in flow mapping")
            pos++
            skipSpaces()
            entries[key] = parseValue()
            skipSpaces()
            if (pos >= text.length) fail("Unterminated flow mapping")
            when (text[pos]) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return DataNode.Obj(entries)
                }
                else -> fail("Expected ',' or '}' in flow mapping")
            }
        }
    }

    private fun parseFlowSequence(): DataNode.Arr {
        pos++
        val items = mutableListOf<DataNode>()
        skipSpaces()
        if (pos < text.length && text[pos] == ']') {
            pos++
            return DataNode.Arr(items)
        }
        while (true) {
            skipSpaces()
            items.add(parseValue())
            skipSpaces()
            if (pos >= text.length) fail("Unterminated flow sequence")
            when (text[pos]) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return DataNode.Arr(items)
                }
                else -> fail("Expected ',' or ']' in flow sequence")
            }
        }
    }

    private fun consumeDoubleQuoted(): String {
        pos++
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated double-quoted scalar")
            val c = text[pos]
            pos++
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (pos >= text.length) fail("Unterminated escape sequence")
                    val escaped = text[pos]
                    pos++
                    when (escaped) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '0' -> sb.append('\u0000')
                        'u' -> {
                            if (pos + 4 > text.length) fail("Invalid unicode escape")
                            val hex = text.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16) ?: fail("Invalid unicode escape '\\u$hex'")
                            pos += 4
                            sb.append(code.toChar())
                        }
                        else -> fail("Invalid escape character '\\$escaped'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun consumeSingleQuoted(): String {
        pos++
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated single-quoted scalar")
            val c = text[pos]
            pos++
            if (c == '\'') {
                if (pos < text.length && text[pos] == '\'') {
                    sb.append('\'')
                    pos++
                } else {
                    return sb.toString()
                }
            } else {
                sb.append(c)
            }
        }
    }

    private fun consumePlainKey(): String {
        val start = pos
        while (pos < text.length && text[pos] != ':' && text[pos] != ',' && text[pos] != '}' && text[pos] != ']') pos++
        return text.substring(start, pos).trim()
    }

    private fun consumePlainToken(): String {
        val start = pos
        while (pos < text.length && text[pos] != ',' && text[pos] != ']' && text[pos] != '}') pos++
        return text.substring(start, pos).trim()
    }
}

/**
 * Hand-rolled YAML 1.1 SUBSET parser: indentation-driven block mappings and
 * sequences, flow collections (`{...}`/`[...]`), plain/single/double-quoted
 * scalars, "- key: value" compact sequence items, block-level anchors
 * (`&name`) and aliases (`*name`). Deliberately not the full YAML spec --
 * tags and flow-level anchors/aliases (inside `{...}`/`[...]`) are still out
 * of scope (same "don't promise more than a hand-rolled subset can deliver
 * correctly" call already made for other v1 scopes in this workspace).
 * Multiple documents in one stream (a second real `---` after content) are
 * rejected with a clear error rather than silently merged -- see
 * [preprocessLines]. Covers everything [YamlWriter] emits plus the common
 * real-world subset needed to read YAML written by other tools.
 *
 * An alias always expands to a deep copy of its anchor's value, never a
 * shared reference -- converting to JSON/XML has no way to represent a
 * shared/aliased structure anyway, so full expansion is the only
 * universally correct output. Anchors must be defined before they're
 * aliased (top-to-bottom, single pass) -- forward references aren't
 * supported, matching how anchors are used in practice.
 */
class YamlParser(rawText: String) {
    private data class Line(val indent: Int, val content: String, val number: Int)

    private val lines: List<Line> = preprocessLines(rawText)
    private var cursor = 0
    private val anchors = LinkedHashMap<String, DataNode>()

    fun parse(): DataNode {
        if (lines.isEmpty()) return DataNode.Null
        val node = parseBlockAt(lines[0].indent)
        if (cursor != lines.size) fail("Unexpected content at this indentation level", lines[cursor].number)
        return node
    }

    private fun fail(message: String, lineNumber: Int): Nothing =
        throw FormatConversionException(message, lineNumber, 1)

    private fun isSequenceMarker(content: String): Boolean = content == "-" || content.startsWith("- ")

    private fun findMappingColon(content: String): Int {
        var inSingle = false
        var inDouble = false
        var depth = 0
        var i = 0
        while (i < content.length) {
            val c = content[i]
            when {
                inSingle -> if (c == '\'') inSingle = false
                inDouble -> if (c == '\\') i++ else if (c == '"') inDouble = false
                c == '\'' -> inSingle = true
                c == '"' -> inDouble = true
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> depth--
                c == ':' && depth == 0 && (i + 1 == content.length || content[i + 1] == ' ') -> return i
            }
            i++
        }
        return -1
    }

    private fun parseBlockAt(indent: Int): DataNode {
        val first = lines[cursor]
        return when {
            isSequenceMarker(first.content) -> parseSequence(indent)
            findMappingColon(first.content) >= 0 -> parseMapping(indent)
            else -> {
                cursor++
                parseInlineValue(first.content, first.number)
            }
        }
    }

    private fun parseSequence(indent: Int): DataNode.Arr {
        val items = mutableListOf<DataNode>()
        while (cursor < lines.size && lines[cursor].indent == indent && isSequenceMarker(lines[cursor].content)) {
            val line = lines[cursor]
            var idx = 1
            while (idx < line.content.length && line.content[idx] == ' ') idx++
            var rest = if (idx >= line.content.length) "" else line.content.substring(idx)

            val aliasValue = resolveAlias(rest, line.number)
            if (aliasValue != null) {
                cursor++
                items.add(aliasValue)
                continue
            }
            val (anchorName, afterAnchor, consumedLength) = extractAnchor(rest)
            rest = afterAnchor
            val effectiveIndent = indent + idx + consumedLength

            val value: DataNode = when {
                rest.isBlank() -> {
                    cursor++
                    if (cursor < lines.size && lines[cursor].indent > indent) parseBlockAt(lines[cursor].indent)
                    else DataNode.Null
                }
                findMappingColon(rest) >= 0 -> {
                    cursor++
                    parseMappingStartingWith(rest, line.number, effectiveIndent)
                }
                else -> {
                    cursor++
                    parseInlineValue(rest, line.number)
                }
            }
            if (anchorName != null) anchors[anchorName] = value
            items.add(value)
        }
        return DataNode.Arr(items)
    }

    private fun parseMapping(indent: Int): DataNode.Obj {
        val entries = LinkedHashMap<String, DataNode>()
        while (cursor < lines.size && lines[cursor].indent == indent && !isSequenceMarker(lines[cursor].content)) {
            val line = lines[cursor]
            addMappingEntry(entries, line.content, line.number, indent)
        }
        return DataNode.Obj(entries)
    }

    /** A "- key: value" compact sequence item: [firstContent] is the text
     * after "- ", already known to contain a top-level mapping colon. The
     * caller has already advanced [cursor] past that line; this consumes
     * any further lines indented exactly at [ownIndent] as sibling keys of
     * the same mapping. */
    private fun parseMappingStartingWith(firstContent: String, firstLineNumber: Int, ownIndent: Int): DataNode.Obj {
        val entries = LinkedHashMap<String, DataNode>()
        addMappingEntry(entries, firstContent, firstLineNumber, ownIndent, consumeCurrentLine = false)
        while (cursor < lines.size && lines[cursor].indent == ownIndent && !isSequenceMarker(lines[cursor].content)) {
            val line = lines[cursor]
            addMappingEntry(entries, line.content, line.number, ownIndent)
        }
        return DataNode.Obj(entries)
    }

    private fun addMappingEntry(
        entries: LinkedHashMap<String, DataNode>,
        content: String,
        lineNumber: Int,
        ownIndent: Int,
        consumeCurrentLine: Boolean = true,
    ) {
        val colonIdx = findMappingColon(content)
        if (colonIdx < 0) fail("Expected 'key: value' mapping entry", lineNumber)
        val key = parseScalarKey(content.substring(0, colonIdx), lineNumber)
        var valuePart = content.substring(colonIdx + 1).trim()
        if (consumeCurrentLine) cursor++

        val aliasValue = resolveAlias(valuePart, lineNumber)
        if (aliasValue != null) {
            entries[key] = aliasValue
            return
        }
        val (anchorName, afterAnchor, _) = extractAnchor(valuePart)
        valuePart = afterAnchor

        val value = if (valuePart.isEmpty()) {
            if (cursor < lines.size && lines[cursor].indent > ownIndent) parseBlockAt(lines[cursor].indent)
            else DataNode.Null
        } else {
            parseInlineValue(valuePart, lineNumber)
        }
        if (anchorName != null) anchors[anchorName] = value
        entries[key] = value
    }

    /** Strips a leading `&name` anchor marker, if present. Returns the anchor
     * name (or null), the remaining text with the marker and any following
     * space removed, and how many characters were consumed -- callers that
     * track column/indent positions need that last part to stay correct. */
    private fun extractAnchor(text: String): Triple<String?, String, Int> {
        if (!text.startsWith("&")) return Triple(null, text, 0)
        var i = 1
        while (i < text.length && !text[i].isWhitespace()) i++
        val name = text.substring(1, i)
        val rest = text.substring(i).trimStart()
        return Triple(name, rest, text.length - rest.length)
    }

    /** Non-null only when [text] is exactly a `*name` alias reference. */
    private fun resolveAlias(text: String, lineNumber: Int): DataNode? {
        if (!text.startsWith("*")) return null
        val name = text.substring(1).trim()
        val anchored = anchors[name] ?: fail("Unknown anchor reference '*$name'", lineNumber)
        return deepCopy(anchored)
    }

    private fun deepCopy(node: DataNode): DataNode = when (node) {
        is DataNode.Obj -> DataNode.Obj(LinkedHashMap(node.entries.mapValues { deepCopy(it.value) }))
        is DataNode.Arr -> DataNode.Arr(node.items.map { deepCopy(it) })
        else -> node
    }

    private fun parseScalarKey(raw: String, lineNumber: Int): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) fail("Empty mapping key", lineNumber)
        return when (val node = FlowParser(trimmed, lineNumber).parseAndConsumeAll()) {
            is DataNode.Str -> node.value
            is DataNode.Num -> node.raw
            is DataNode.Bool -> node.value.toString()
            DataNode.Null -> "null"
            else -> fail("Mapping keys must be scalars", lineNumber)
        }
    }

    private fun parseInlineValue(text: String, lineNumber: Int): DataNode {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return DataNode.Null
        return FlowParser(trimmed, lineNumber).parseAndConsumeAll()
    }

    companion object {
        private fun stripComment(raw: String): String {
            var inSingle = false
            var inDouble = false
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    inSingle -> if (c == '\'') inSingle = false
                    inDouble -> if (c == '\\') i++ else if (c == '"') inDouble = false
                    c == '\'' -> inSingle = true
                    c == '"' -> inDouble = true
                    c == '#' && (i == 0 || raw[i - 1] == ' ' || raw[i - 1] == '\t') -> return raw.substring(0, i)
                }
                i++
            }
            return raw
        }

        /**
         * A leading `---` before any real content is just the conventional
         * "start of the document" marker and is skipped, same as before --
         * but a SECOND `---` after content has already been seen means this
         * stream genuinely has more than one document. That used to be
         * silently dropped too, merging both documents' content into one
         * parse with no warning; now it's a clear, immediate error instead,
         * since converting more than one document into a single JSON/XML
         * tree isn't something this tool can do correctly either way, and
         * silently doing it wrong is worse than refusing.
         */
        private fun preprocessLines(rawText: String): List<Line> {
            val result = mutableListOf<Line>()
            var lineNumber = 0
            var sawContent = false
            for (raw in LINE_SPLIT.split(rawText)) {
                lineNumber++
                val withoutComment = stripComment(raw).trimEnd()
                if (withoutComment.isBlank()) continue
                val content = withoutComment.trimStart(' ')
                val leading = withoutComment.length - content.length
                if (withoutComment.substring(0, leading).contains('\t')) {
                    throw FormatConversionException("YAML indentation must use spaces, not tabs", lineNumber, 1)
                }
                if (content == "---") {
                    if (sawContent) {
                        throw FormatConversionException(
                            "Multiple YAML documents found in one stream -- convert one document at a time",
                            lineNumber,
                            1,
                        )
                    }
                    continue
                }
                if (content == "...") continue
                result.add(Line(leading, content, lineNumber))
                sawContent = true
            }
            return result
        }
    }
}
