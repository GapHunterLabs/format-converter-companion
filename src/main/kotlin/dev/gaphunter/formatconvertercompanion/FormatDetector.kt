package dev.gaphunter.formatconvertercompanion

/**
 * Detects the source format from editor CONTENT, never a file extension --
 * the whole point is converting a selection or an untitled buffer with no
 * reliable extension at all, and generic extensions (`.txt`, no extension)
 * would misdetect anyway (same "detect by content, not extension" principle
 * as NginxDirectiveIndex/AnsibleFileTypeOverrider elsewhere in this
 * workspace).
 */
enum class Format(val label: String) {
    JSON("JSON"),
    YAML("YAML"),
    XML("XML"),
}

object FormatDetector {
    fun detect(text: String): Format {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("<") -> Format.XML
            trimmed.startsWith("{") || trimmed.startsWith("[") -> Format.JSON
            else -> Format.YAML
        }
    }
}
