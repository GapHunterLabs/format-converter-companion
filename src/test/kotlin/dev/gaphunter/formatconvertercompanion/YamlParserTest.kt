package dev.gaphunter.formatconvertercompanion

import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.yaml.YamlParser
import org.junit.Assert.assertEquals
import org.junit.Test

class YamlParserTest {
    @Test
    fun `parses a block mapping with scalars`() {
        val yaml = """
            name: Acme Corp
            active: true
            score: 4.5
            meta: null
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        assertEquals(DataNode.Str("Acme Corp"), node.entries["name"])
        assertEquals(DataNode.Bool(true), node.entries["active"])
        assertEquals(DataNode.Num("4.5"), node.entries["score"])
        assertEquals(DataNode.Null, node.entries["meta"])
    }

    @Test
    fun `parses a block sequence of compact mapping items`() {
        val yaml = """
            - name: Alice
              age: 30
            - name: Bob
              age: 25
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Arr
        assertEquals(2, node.items.size)
        val first = node.items[0] as DataNode.Obj
        assertEquals(DataNode.Str("Alice"), first.entries["name"])
        assertEquals(DataNode.Num("30"), first.entries["age"])
    }

    @Test
    fun `parses nested mappings and sequences`() {
        val yaml = """
            order:
              id: 42
              items:
                - sku: "A1"
                  qty: 2
                - sku: "B2"
                  qty: 1
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        val order = node.entries.getValue("order") as DataNode.Obj
        assertEquals(DataNode.Num("42"), order.entries["id"])
        val items = order.entries.getValue("items") as DataNode.Arr
        assertEquals(2, items.items.size)
    }

    @Test
    fun `ignores comments outside quoted strings`() {
        val yaml = """
            # top comment
            name: "value # not a comment"  # trailing comment
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        assertEquals(DataNode.Str("value # not a comment"), node.entries["name"])
    }

    @Test
    fun `parses flow collections`() {
        val yaml = "point: {x: 1, y: 2}"
        val node = YamlParser(yaml).parse() as DataNode.Obj
        val point = node.entries.getValue("point") as DataNode.Obj
        assertEquals(DataNode.Num("1"), point.entries["x"])
        assertEquals(DataNode.Num("2"), point.entries["y"])
    }

    @Test
    fun `parses empty collections`() {
        val yaml = "a: {}\nb: []\n"
        val node = YamlParser(yaml).parse() as DataNode.Obj
        assertEquals(DataNode.Obj(LinkedHashMap()), node.entries["a"])
        assertEquals(DataNode.Arr(emptyList()), node.entries["b"])
    }
}
