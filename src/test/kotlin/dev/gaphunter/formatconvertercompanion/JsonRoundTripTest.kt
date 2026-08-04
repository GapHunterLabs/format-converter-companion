package dev.gaphunter.formatconvertercompanion

import dev.gaphunter.formatconvertercompanion.json.JsonParser
import dev.gaphunter.formatconvertercompanion.json.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonRoundTripTest {
    @Test
    fun `parses and re-serializes nested objects and arrays`() {
        val source = """
            {"name": "Acme Corp", "active": true, "score": 4.5, "tags": ["a", "b"], "meta": null, "count": 0}
        """.trimIndent()
        val tree = JsonParser(source).parse()
        val written = JsonWriter.write(tree)
        val reparsed = JsonParser(written).parse()
        assertEquals(JsonWriter.write(tree), JsonWriter.write(reparsed))
    }

    @Test
    fun `preserves exact numeric text through a round trip`() {
        val source = """{"price": 19.50, "big": 12345678901234, "exp": 1.5e10}"""
        val tree = JsonParser(source).parse()
        val written = JsonWriter.write(tree)
        assert(written.contains("19.50")) { "expected raw numeric text '19.50' preserved, got: $written" }
        assert(written.contains("12345678901234"))
        assert(written.contains("1.5e10"))
    }

    @Test
    fun `decodes standard escape sequences`() {
        val source = """{"text": "line1\nline2\ttabbed\\backslash\"quoted\""}"""
        val tree = JsonParser(source).parse() as dev.gaphunter.formatconvertercompanion.model.DataNode.Obj
        val value = (tree.entries.getValue("text") as dev.gaphunter.formatconvertercompanion.model.DataNode.Str).value
        assertEquals("line1\nline2\ttabbed\\backslash\"quoted\"", value)
    }

    @Test
    fun `rejects malformed json with a clear error`() {
        assertThrows(dev.gaphunter.formatconvertercompanion.model.FormatConversionException::class.java) {
            JsonParser("""{"a": }""").parse()
        }
    }
}
