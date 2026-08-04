package dev.gaphunter.formatconvertercompanion

import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.xml.XmlConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlConverterTest {
    @Test
    fun `converts attributes and child elements`() {
        val xml = """<order id="42"><customer>Acme Corp</customer><total>19.50</total></order>"""
        val node = XmlConverter.parse(xml) as DataNode.Obj
        val order = node.entries.getValue("order") as DataNode.Obj
        assertEquals(DataNode.Str("42"), order.entries["@id"])
        assertEquals(DataNode.Str("Acme Corp"), order.entries["customer"])
        assertEquals(DataNode.Str("19.50"), order.entries["total"])
    }

    @Test
    fun `groups repeated child elements into an array`() {
        val xml = "<items><item>A1</item><item>B2</item></items>"
        val node = XmlConverter.parse(xml) as DataNode.Obj
        val items = node.entries.getValue("items") as DataNode.Obj
        val itemArray = items.entries.getValue("item") as DataNode.Arr
        assertEquals(2, itemArray.items.size)
    }

    @Test
    fun `write then parse round trips an object tree`() {
        val entries = LinkedHashMap<String, DataNode>()
        entries["@id"] = DataNode.Str("7")
        entries["name"] = DataNode.Str("Widget")
        val root = LinkedHashMap<String, DataNode>()
        root["product"] = DataNode.Obj(entries)
        val original = DataNode.Obj(root)

        val xml = XmlConverter.write(original)
        val reparsed = XmlConverter.parse(xml) as DataNode.Obj
        val product = reparsed.entries.getValue("product") as DataNode.Obj
        assertEquals(DataNode.Str("7"), product.entries["@id"])
        assertEquals(DataNode.Str("Widget"), product.entries["name"])
    }

    @Test
    fun `rejects a doctype declaration to prevent XXE`() {
        val malicious = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root>&xxe;</root>
        """.trimIndent()
        var threw = false
        try {
            XmlConverter.parse(malicious)
        } catch (e: dev.gaphunter.formatconvertercompanion.model.FormatConversionException) {
            threw = true
        }
        assertTrue("expected a DOCTYPE declaration to be rejected, not silently resolved", threw)
    }
}
