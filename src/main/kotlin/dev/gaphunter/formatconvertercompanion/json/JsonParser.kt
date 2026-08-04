package dev.gaphunter.formatconvertercompanion.json

import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.model.FormatConversionException

/**
 * Hand-rolled recursive-descent JSON parser (RFC 8259). Linear time, no
 * regex backtracking, single pass over the input -- deliberately not a
 * dependency on a bundled Jackson/Gson class, since relying on an
 * IntelliJ Platform-internal library that isn't a declared plugin
 * dependency is exactly the kind of classpath fragility
 * AUTOMATION_PLAYBOOK.md/CONSTITUTION.md SS6 already steers away from
 * ("hand-roll over new dependency when the surface is small and
 * stable" -- same call already made for XlsxReader and
 * NginxDirectiveIndex in this workspace).
 */
class JsonParser(private val text: String) {
    private var pos = 0
    private var line = 1
    private var col = 1

    fun parse(): DataNode {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (pos != text.length) fail("Unexpected trailing content after JSON value")
        return value
    }

    private fun fail(message: String): Nothing = throw FormatConversionException(message, line, col)

    private fun peek(): Char = if (pos < text.length) text[pos] else ' '

    private fun advance(): Char {
        val c = text[pos]
        pos++
        if (c == '\n') {
            line++
            col = 1
        } else {
            col++
        }
        return c
    }

    private fun skipWhitespace() {
        while (pos < text.length && peek().let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) {
            advance()
        }
    }

    private fun expect(c: Char) {
        if (pos >= text.length || peek() != c) fail("Expected '$c'")
        advance()
    }

    private fun parseValue(): DataNode {
        if (pos >= text.length) fail("Unexpected end of input")
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> DataNode.Str(parseStringLiteral())
            't' -> parseLiteral("true", DataNode.Bool(true))
            'f' -> parseLiteral("false", DataNode.Bool(false))
            'n' -> parseLiteral("null", DataNode.Null)
            '-', in '0'..'9' -> parseNumber()
            else -> fail("Unexpected character '${peek()}'")
        }
    }

    private fun parseLiteral(literal: String, value: DataNode): DataNode {
        for (expected in literal) {
            if (pos >= text.length || peek() != expected) fail("Invalid literal, expected '$literal'")
            advance()
        }
        return value
    }

    private fun parseObject(): DataNode.Obj {
        expect('{')
        val entries = LinkedHashMap<String, DataNode>()
        skipWhitespace()
        if (peek() == '}') {
            advance()
            return DataNode.Obj(entries)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Expected string key")
            val key = parseStringLiteral()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            entries[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    advance()
                }
                '}' -> {
                    advance()
                    return DataNode.Obj(entries)
                }
                else -> fail("Expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): DataNode.Arr {
        expect('[')
        val items = mutableListOf<DataNode>()
        skipWhitespace()
        if (peek() == ']') {
            advance()
            return DataNode.Arr(items)
        }
        while (true) {
            skipWhitespace()
            items.add(parseValue())
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    advance()
                }
                ']' -> {
                    advance()
                    return DataNode.Arr(items)
                }
                else -> fail("Expected ',' or ']'")
            }
        }
    }

    private fun parseStringLiteral(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) fail("Unterminated string")
            val c = advance()
            when {
                c == '"' -> return sb.toString()
                c == '\\' -> {
                    if (pos >= text.length) fail("Unterminated escape sequence")
                    when (val escaped = advance()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail("Invalid unicode escape")
                            val hex = text.substring(pos, pos + 4)
                            val code = hex.toIntOrNull(16) ?: fail("Invalid unicode escape '\\u$hex'")
                            repeat(4) { advance() }
                            sb.append(code.toChar())
                        }
                        else -> fail("Invalid escape character '\\$escaped'")
                    }
                }
                c.code < 0x20 -> fail("Unescaped control character in string")
                else -> sb.append(c)
            }
        }
    }

    private fun parseNumber(): DataNode.Num {
        val start = pos
        if (peek() == '-') advance()
        if (pos >= text.length || peek() !in '0'..'9') fail("Invalid number")
        if (peek() == '0') {
            advance()
        } else {
            while (pos < text.length && peek() in '0'..'9') advance()
        }
        if (pos < text.length && peek() == '.') {
            advance()
            if (pos >= text.length || peek() !in '0'..'9') fail("Invalid number, expected digit after '.'")
            while (pos < text.length && peek() in '0'..'9') advance()
        }
        if (pos < text.length && (peek() == 'e' || peek() == 'E')) {
            advance()
            if (pos < text.length && (peek() == '+' || peek() == '-')) advance()
            if (pos >= text.length || peek() !in '0'..'9') fail("Invalid number exponent")
            while (pos < text.length && peek() in '0'..'9') advance()
        }
        return DataNode.Num(text.substring(start, pos))
    }
}
