package dev.gaphunter.formatconvertercompanion.json

import dev.gaphunter.formatconvertercompanion.model.DataNode

/** Pretty-printed (2-space indent) JSON serializer. Never escapes non-ASCII
 * characters to `\uXXXX` -- the output is UTF-8 text, and the whole point
 * of this plugin is round-tripping non-ASCII content correctly. */
object JsonWriter {
    fun write(node: DataNode): String {
        val sb = StringBuilder()
        writeValue(node, sb, 0)
        return sb.toString()
    }

    private fun indent(sb: StringBuilder, depth: Int) {
        repeat(depth) { sb.append("  ") }
    }

    private fun writeValue(node: DataNode, sb: StringBuilder, depth: Int) {
        when (node) {
            is DataNode.Null -> sb.append("null")
            is DataNode.Bool -> sb.append(if (node.value) "true" else "false")
            is DataNode.Num -> sb.append(node.raw)
            is DataNode.Str -> writeString(node.value, sb)
            is DataNode.Arr -> writeArray(node, sb, depth)
            is DataNode.Obj -> writeObject(node, sb, depth)
        }
    }

    private fun writeArray(node: DataNode.Arr, sb: StringBuilder, depth: Int) {
        if (node.items.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[\n")
        node.items.forEachIndexed { index, item ->
            indent(sb, depth + 1)
            writeValue(item, sb, depth + 1)
            if (index != node.items.lastIndex) sb.append(',')
            sb.append('\n')
        }
        indent(sb, depth)
        sb.append(']')
    }

    private fun writeObject(node: DataNode.Obj, sb: StringBuilder, depth: Int) {
        if (node.entries.isEmpty()) {
            sb.append("{}")
            return
        }
        sb.append("{\n")
        val keys = node.entries.keys.toList()
        keys.forEachIndexed { index, key ->
            indent(sb, depth + 1)
            writeString(key, sb)
            sb.append(": ")
            writeValue(node.entries.getValue(key), sb, depth + 1)
            if (index != keys.lastIndex) sb.append(',')
            sb.append('\n')
        }
        indent(sb, depth)
        sb.append('}')
    }

    private fun writeString(value: String, sb: StringBuilder) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}
