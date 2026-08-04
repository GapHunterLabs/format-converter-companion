package dev.gaphunter.formatconvertercompanion.xml

import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.model.FormatConversionException
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML <-> [DataNode] conversion using the JDK's own `javax.xml.parsers`
 * (DOM) -- not a new dependency, just the standard library, unlike JSON/YAML
 * which have no JDK-builtin equivalent and are hand-rolled instead (see
 * JsonParser.kt/YamlParser.kt for that rationale).
 *
 * XML has no canonical JSON-shaped representation, so this uses the same
 * convention as most JSON<->XML converters (xmltodict, Jackson XmlMapper):
 * attributes become "@name" keys, direct text content becomes a "#text" key
 * (or the element's own scalar value when it has no attributes/children),
 * and repeated same-name child elements become a JSON array. Documented in
 * README.md under "XML mapping convention" since, unlike JSON<->YAML, this
 * direction is inherently convention-based, not a lossless universal
 * mapping.
 */
object XmlConverter {
    private const val TEXT_KEY = "#text"
    private const val ATTR_PREFIX = "@"

    fun parse(xmlText: String): DataNode {
        val factory = DocumentBuilderFactory.newInstance()
        // XXE hardening: never resolve external entities/DTDs from untyped
        // editor content (OWASP XXE prevention cheat sheet).
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isExpandEntityReferences = false
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val document = try {
            builder.parse(InputSource(StringReader(xmlText)))
        } catch (e: SAXParseException) {
            throw FormatConversionException(e.message ?: "Malformed XML", e.lineNumber, e.columnNumber)
        } catch (e: Exception) {
            throw FormatConversionException(e.message ?: "Malformed XML")
        }
        val root = document.documentElement ?: throw FormatConversionException("XML document has no root element")
        val entries = LinkedHashMap<String, DataNode>()
        entries[root.tagName] = elementToNode(root)
        return DataNode.Obj(entries)
    }

    private fun elementToNode(element: Element): DataNode {
        val entries = LinkedHashMap<String, DataNode>()

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            entries["$ATTR_PREFIX${attr.nodeName}"] = DataNode.Str(attr.nodeValue)
        }

        val childrenByTag = LinkedHashMap<String, MutableList<Element>>()
        val textBuilder = StringBuilder()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> childrenByTag.getOrPut((child as Element).tagName) { mutableListOf() }.add(child)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> textBuilder.append(child.nodeValue)
                else -> {}
            }
        }
        val text = textBuilder.toString().trim()

        if (childrenByTag.isEmpty() && attributes.length == 0) {
            return if (text.isEmpty()) DataNode.Null else DataNode.Str(text)
        }

        if (text.isNotEmpty()) entries[TEXT_KEY] = DataNode.Str(text)
        for ((tag, elements) in childrenByTag) {
            entries[tag] = if (elements.size == 1) {
                elementToNode(elements[0])
            } else {
                DataNode.Arr(elements.map { elementToNode(it) })
            }
        }
        return DataNode.Obj(entries)
    }

    fun write(node: DataNode): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        when (node) {
            is DataNode.Obj -> if (node.entries.size == 1) {
                val (tag, content) = node.entries.entries.first()
                writeElement(tag, content, sb, 0)
            } else {
                writeElement("root", node, sb, 0)
            }
            is DataNode.Arr -> {
                sb.append("<root>\n")
                for (item in node.items) writeElement("item", item, sb, 1)
                sb.append("</root>\n")
            }
            else -> writeElement("root", node, sb, 0)
        }
        return sb.toString()
    }

    private fun writeElement(tagName: String, content: DataNode, sb: StringBuilder, indent: Int) {
        // DataNode.Obj handles its own leading indent (writeObjectElement) since
        // its attribute string must be computed before the opening tag is
        // written -- every other branch writes a single self-contained line/block
        // and indents up front.
        if (content is DataNode.Obj) {
            writeObjectElement(tagName, content, sb, indent)
            return
        }
        appendIndent(sb, indent)
        when (content) {
            DataNode.Null -> sb.append("<$tagName/>\n")
            is DataNode.Str -> sb.append("<$tagName>${escapeText(content.value)}</$tagName>\n")
            is DataNode.Num -> sb.append("<$tagName>${content.raw}</$tagName>\n")
            is DataNode.Bool -> sb.append("<$tagName>${content.value}</$tagName>\n")
            is DataNode.Arr -> {
                // A bare array as an element's content has no natural single-element
                // shape; wrap each item as a repeated "item" child (documented
                // convention, mirrors the top-level array case in write()).
                if (content.items.isEmpty()) {
                    sb.append("<$tagName/>\n")
                } else {
                    sb.append("<$tagName>\n")
                    for (item in content.items) writeElement("item", item, sb, indent + 1)
                    appendIndent(sb, indent)
                    sb.append("</$tagName>\n")
                }
            }
            is DataNode.Obj -> {} // unreachable, handled above
        }
    }

    private fun writeObjectElement(tagName: String, obj: DataNode.Obj, sb: StringBuilder, indent: Int) {
        val attrs = obj.entries.filterKeys { it.startsWith(ATTR_PREFIX) }
        val text = (obj.entries[TEXT_KEY] as? DataNode.Str)?.value
        val childEntries = obj.entries.filterKeys { it != TEXT_KEY && !it.startsWith(ATTR_PREFIX) }

        val attrString = attrs.entries.joinToString("") { (key, value) ->
            val name = key.removePrefix(ATTR_PREFIX)
            val rawValue = when (value) {
                is DataNode.Str -> value.value
                is DataNode.Num -> value.raw
                is DataNode.Bool -> value.value.toString()
                else -> ""
            }
            " $name=\"${escapeAttribute(rawValue)}\""
        }

        appendIndent(sb, indent)

        if (childEntries.isEmpty() && text == null) {
            sb.append("<$tagName$attrString/>\n")
            return
        }
        if (childEntries.isEmpty()) {
            sb.append("<$tagName$attrString>${escapeText(text ?: "")}</$tagName>\n")
            return
        }

        sb.append("<$tagName$attrString>\n")
        if (text != null) {
            appendIndent(sb, indent + 1)
            sb.append(escapeText(text)).append('\n')
        }
        for ((key, value) in childEntries) {
            if (value is DataNode.Arr) {
                for (item in value.items) writeElement(key, item, sb, indent + 1)
            } else {
                writeElement(key, value, sb, indent + 1)
            }
        }
        appendIndent(sb, indent)
        sb.append("</$tagName>\n")
    }

    private fun appendIndent(sb: StringBuilder, indent: Int) {
        repeat(indent * 2) { sb.append(' ') }
    }

    private fun escapeText(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttribute(value: String): String =
        escapeText(value).replace("\"", "&quot;")
}
