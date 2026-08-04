package dev.gaphunter.formatconvertercompanion

import dev.gaphunter.formatconvertercompanion.json.JsonParser
import dev.gaphunter.formatconvertercompanion.json.JsonWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three tests NEXT_BATCH_PLAN.md calls for explicitly: a JSON->YAML->JSON
 * round trip producing the same data tree, real non-ASCII UTF-8 content, and
 * a synthetic large-file latency check confirming conversion doesn't hang.
 */
class ConversionServiceTest {
    @Test
    fun `json to yaml to json round trip produces the same data tree`() {
        val json = """
            {
              "company": "Acme Corp",
              "employees": [
                {"name": "Alice", "active": true, "salary": 95000.50},
                {"name": "Bob", "active": false, "salary": 81234.00}
              ],
              "founded": 1998,
              "public": null,
              "tags": ["b2b", "saas"],
              "address": {"city": "Springfield", "zip": "00000"}
            }
        """.trimIndent()

        val originalTree = JsonParser(json).parse()
        val canonicalOriginal = JsonWriter.write(originalTree)

        val yaml = ConversionService.convert(json, Format.YAML)
        val backToJson = ConversionService.convert(yaml, Format.JSON)
        val canonicalRoundTripped = JsonWriter.write(JsonParser(backToJson).parse())

        assertEquals(canonicalOriginal, canonicalRoundTripped)
    }

    @Test
    fun `non-ascii utf-8 content survives json to yaml to json`() {
        val json = """{"greeting": "héllo wörld", "city": "München", "emoji": "🚀", "cjk": "日本語", "arrow": "á subgraph"}"""

        val yaml = ConversionService.convert(json, Format.YAML)
        val backToJson = ConversionService.convert(yaml, Format.JSON)

        val original = JsonParser(json).parse()
        val roundTripped = JsonParser(backToJson).parse()
        assertEquals(JsonWriter.write(original), JsonWriter.write(roundTripped))
        assertTrue(yaml.contains("München"))
        assertTrue(yaml.contains("🚀"))
        assertTrue(yaml.contains("日本語"))
    }

    @Test
    fun `non-ascii utf-8 content survives json to xml to json`() {
        val json = """{"root": {"greeting": "héllo wörld", "city": "München", "cjk": "日本語"}}"""
        val xml = ConversionService.convert(json, Format.XML)
        val backToJson = ConversionService.convert(xml, Format.JSON)
        assertTrue(xml.contains("München"))
        assertTrue(backToJson.contains("München"))
        assertTrue(backToJson.contains("日本語"))
    }

    @Test
    fun `converting a large document does not hang`() {
        // Synthetic >10MB JSON document (competitor complaint: "not able to
        // convert BIG Files"). This isn't a literal file on disk -- the
        // whole point of this plugin is converting editor/selection content
        // directly, so the realistic large-input case is a big in-memory
        // string, which is what this generates.
        val sb = StringBuilder()
        sb.append("{\"records\": [")
        val recordCount = 150_000
        val padding = "x".repeat(50)
        for (i in 0 until recordCount) {
            if (i > 0) sb.append(',')
            sb.append("{\"id\": ").append(i)
                .append(", \"name\": \"Record number ").append(i)
                .append("\", \"active\": ").append(i % 2 == 0)
                .append(", \"score\": ").append(i).append(".5")
                .append(", \"padding\": \"").append(padding).append("\"}")
        }
        sb.append("]}")
        val largeJson = sb.toString()
        assertTrue("test fixture should exceed 10MB to be meaningful", largeJson.length > 10_000_000)

        val start = System.nanoTime()
        val yaml = ConversionService.convert(largeJson, Format.YAML)
        val backToJson = ConversionService.convert(yaml, Format.JSON)
        val elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000.0

        assertTrue("expected some YAML output", yaml.isNotEmpty())
        assertTrue("expected some JSON output", backToJson.isNotEmpty())
        assertTrue(
            "conversion of a >10MB document took ${elapsedSeconds}s -- too slow, likely quadratic behavior",
            elapsedSeconds < 30.0
        )
    }
}
