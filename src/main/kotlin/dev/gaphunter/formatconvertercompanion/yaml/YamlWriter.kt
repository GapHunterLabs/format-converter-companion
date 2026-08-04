package dev.gaphunter.formatconvertercompanion.yaml

import dev.gaphunter.formatconvertercompanion.model.DataNode

/**
 * Block-style (2-space indent) YAML serializer. Always double-quotes string
 * scalars and keys (never emits an unquoted plain scalar for a string) --
 * this trades a little readability for a guarantee that matters more for a
 * *converter*: [YamlParser] can read the output back byte-for-byte
 * unambiguously, with no risk of a string like "true" or "12" or "- x"
 * silently reinterpreting as a different type on the way back in.
 */
object YamlWriter {
    fun write(node: DataNode): String {
        val sb = StringBuilder()
        when (node) {
            is DataNode.Obj -> if (node.entries.isEmpty()) sb.append("{}\n") else writeMapping(node, sb, 0)
            is DataNode.Arr -> if (node.items.isEmpty()) sb.append("[]\n") else writeSequence(node, sb, 0)
            else -> sb.append(scalarText(node)).append('\n')
        }
        return sb.toString()
    }

    private fun appendIndent(sb: StringBuilder, indent: Int) {
        repeat(indent) { sb.append(' ') }
    }

    private fun writeMapping(obj: DataNode.Obj, sb: StringBuilder, indent: Int) {
        for ((key, value) in obj.entries) {
            appendIndent(sb, indent)
            writeMappingEntryLine(key, value, sb, indent)
        }
    }

    private fun writeMappingEntryLine(key: String, value: DataNode, sb: StringBuilder, keyColumnIndent: Int) {
        sb.append(escapeDoubleQuoted(key))
        sb.append(':')
        when (value) {
            is DataNode.Obj -> if (value.entries.isEmpty()) {
                sb.append(" {}\n")
            } else {
                sb.append('\n')
                writeMapping(value, sb, keyColumnIndent + 2)
            }
            is DataNode.Arr -> if (value.items.isEmpty()) {
                sb.append(" []\n")
            } else {
                sb.append('\n')
                writeSequence(value, sb, keyColumnIndent + 2)
            }
            else -> {
                sb.append(' ')
                sb.append(scalarText(value))
                sb.append('\n')
            }
        }
    }

    private fun writeSequence(arr: DataNode.Arr, sb: StringBuilder, indent: Int) {
        for (item in arr.items) {
            when (item) {
                is DataNode.Obj -> if (item.entries.isEmpty()) {
                    appendIndent(sb, indent)
                    sb.append("- {}\n")
                } else {
                    writeCompactMappingItem(item, sb, indent)
                }
                is DataNode.Arr -> if (item.items.isEmpty()) {
                    appendIndent(sb, indent)
                    sb.append("- []\n")
                } else {
                    appendIndent(sb, indent)
                    sb.append("-\n")
                    writeSequence(item, sb, indent + 2)
                }
                else -> {
                    appendIndent(sb, indent)
                    sb.append("- ")
                    sb.append(scalarText(item))
                    sb.append('\n')
                }
            }
        }
    }

    private fun writeCompactMappingItem(obj: DataNode.Obj, sb: StringBuilder, indent: Int) {
        val keys = obj.entries.keys.toList()
        keys.forEachIndexed { index, key ->
            val value = obj.entries.getValue(key)
            if (index == 0) {
                appendIndent(sb, indent)
                sb.append("- ")
            } else {
                appendIndent(sb, indent + 2)
            }
            writeMappingEntryLine(key, value, sb, indent + 2)
        }
    }

    private fun scalarText(node: DataNode): String = when (node) {
        DataNode.Null -> "null"
        is DataNode.Bool -> if (node.value) "true" else "false"
        is DataNode.Num -> node.raw
        is DataNode.Str -> escapeDoubleQuoted(node.value)
        is DataNode.Obj, is DataNode.Arr -> throw IllegalStateException("scalarText called on a container node")
    }

    private fun escapeDoubleQuoted(value: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
