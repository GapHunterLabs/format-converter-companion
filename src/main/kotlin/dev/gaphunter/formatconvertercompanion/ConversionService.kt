package dev.gaphunter.formatconvertercompanion

import dev.gaphunter.formatconvertercompanion.json.JsonParser
import dev.gaphunter.formatconvertercompanion.json.JsonWriter
import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.xml.XmlConverter
import dev.gaphunter.formatconvertercompanion.yaml.YamlParser
import dev.gaphunter.formatconvertercompanion.yaml.YamlWriter

/**
 * Detects the source format, parses it into the shared [DataNode] tree, and
 * serializes it as [target]. The only entry point actions call -- keeps
 * format-specific parser/writer selection in one place.
 */
object ConversionService {
    fun convert(sourceText: String, target: Format): String {
        val source = FormatDetector.detect(sourceText)
        val tree = parse(sourceText, source)
        return write(tree, target)
    }

    fun parse(text: String, format: Format): DataNode = when (format) {
        Format.JSON -> JsonParser(text).parse()
        Format.YAML -> YamlParser(text).parse()
        Format.XML -> XmlConverter.parse(text)
    }

    fun write(node: DataNode, format: Format): String = when (format) {
        Format.JSON -> JsonWriter.write(node)
        Format.YAML -> YamlWriter.write(node)
        Format.XML -> XmlConverter.write(node)
    }
}
