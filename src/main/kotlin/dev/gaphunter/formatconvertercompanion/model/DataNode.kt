package dev.gaphunter.formatconvertercompanion.model

/**
 * Format-neutral document tree shared by the JSON/YAML/XML parsers and
 * writers. [Num] keeps the original source text (not a Double) so a
 * round trip never loses precision or reformats "1.50" into "1.5".
 */
sealed class DataNode {
    object Null : DataNode()
    data class Bool(val value: Boolean) : DataNode()
    data class Num(val raw: String) : DataNode()
    data class Str(val value: String) : DataNode()
    data class Arr(val items: List<DataNode>) : DataNode()
    data class Obj(val entries: LinkedHashMap<String, DataNode>) : DataNode()
}

class FormatConversionException(message: String, val line: Int = -1, val column: Int = -1) :
    Exception(if (line >= 0) "$message (line $line, column $column)" else message)
