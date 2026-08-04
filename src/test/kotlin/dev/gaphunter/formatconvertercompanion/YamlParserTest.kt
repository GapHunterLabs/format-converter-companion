package dev.gaphunter.formatconvertercompanion

import dev.gaphunter.formatconvertercompanion.model.DataNode
import dev.gaphunter.formatconvertercompanion.model.FormatConversionException
import dev.gaphunter.formatconvertercompanion.yaml.YamlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun `expands a scalar alias to the anchored value`() {
        val yaml = """
            default_region: &region us-east-1
            backup_region: *region
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        assertEquals(DataNode.Str("us-east-1"), node.entries["default_region"])
        assertEquals(DataNode.Str("us-east-1"), node.entries["backup_region"])
    }

    @Test
    fun `expands a block mapping alias to a deep copy, not a shared reference`() {
        val yaml = """
            defaults: &defaults
              timeout: 30
              retries: 3
            service_a:
              config: *defaults
            service_b:
              config: *defaults
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        val serviceAConfig = ((node.entries["service_a"] as DataNode.Obj).entries["config"]) as DataNode.Obj
        val serviceBConfig = ((node.entries["service_b"] as DataNode.Obj).entries["config"]) as DataNode.Obj
        assertEquals(DataNode.Num("30"), serviceAConfig.entries["timeout"])
        assertEquals(DataNode.Num("30"), serviceBConfig.entries["timeout"])
        // Deep copies, not the same instance -- mutating one map must never affect the other.
        assertNotSame(serviceAConfig.entries, serviceBConfig.entries)
    }

    @Test
    fun `expands a sequence-item alias`() {
        val yaml = """
            base: &base
              role: worker
            nodes:
              - *base
              - role: manager
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        val nodes = (node.entries["nodes"] as DataNode.Arr).items
        assertEquals(2, nodes.size)
        assertEquals(DataNode.Str("worker"), (nodes[0] as DataNode.Obj).entries["role"])
        assertEquals(DataNode.Str("manager"), (nodes[1] as DataNode.Obj).entries["role"])
    }

    @Test
    fun `an anchor on a compact sequence-item mapping still finds its sibling keys`() {
        val yaml = """
            items:
              - &first
                sku: A1
                qty: 2
              - sku: B2
                qty: 1
            copy_of_first: *first
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        val items = (node.entries["items"] as DataNode.Arr).items
        val first = items[0] as DataNode.Obj
        assertEquals(DataNode.Str("A1"), first.entries["sku"])
        assertEquals(DataNode.Num("2"), first.entries["qty"])
        val copy = node.entries["copy_of_first"] as DataNode.Obj
        assertEquals(first, copy)
    }

    @Test
    fun `referencing an anchor that was never defined fails clearly`() {
        val yaml = "value: *missing"
        val exception = assertThrows(FormatConversionException::class.java) { YamlParser(yaml).parse() }
        assertTrue(exception.message!!.contains("missing"))
    }

    @Test
    fun `a single leading document marker is not treated as multiple documents`() {
        val yaml = """
            ---
            name: Acme Corp
        """.trimIndent()
        val node = YamlParser(yaml).parse() as DataNode.Obj
        assertEquals(DataNode.Str("Acme Corp"), node.entries["name"])
    }

    @Test
    fun `a second document marker after real content is rejected, not silently merged`() {
        val yaml = """
            name: Acme Corp
            ---
            name: Other Corp
        """.trimIndent()
        val exception = assertThrows(FormatConversionException::class.java) { YamlParser(yaml).parse() }
        assertTrue(exception.message!!.contains("Multiple YAML documents"))
    }
}
