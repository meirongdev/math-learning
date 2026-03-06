package com.mathlearning.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class SolveRequestTest {
    @Test
    fun serializes_all_fields() {
        val request = SolveRequest(question = "5 + 3 = ?", grade = 1, studentId = "abc-123")
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SolveRequest>(encoded)
        assertEquals("5 + 3 = ?", decoded.question)
        assertEquals(1, decoded.grade)
        assertEquals("abc-123", decoded.studentId)
    }

    @Test
    fun serializes_with_null_studentId() {
        val request = SolveRequest(question = "x + 3 = 8", grade = 6)
        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SolveRequest>(encoded)
        assertEquals("x + 3 = 8", decoded.question)
        assertEquals(6, decoded.grade)
        assertNull(decoded.studentId)
    }
}

class SolveResponseTest {
    @Test
    fun deserializes_full_response() {
        val raw = """
            {
              "parentGuide": "This tests ratio.",
              "childScript": "Let's count sweets!",
              "barModelJson": "{}",
              "knowledgeTags": ["ratio.basic", "whole_numbers"]
            }
        """.trimIndent()
        val response = json.decodeFromString<SolveResponse>(raw)
        assertEquals("This tests ratio.", response.parentGuide)
        assertEquals("Let's count sweets!", response.childScript)
        assertEquals("{}", response.barModelJson)
        assertEquals(listOf("ratio.basic", "whole_numbers"), response.knowledgeTags)
    }

    @Test
    fun deserializes_partial_response_with_nulls() {
        val raw = """{"parentGuide": "Some guide"}"""
        val response = json.decodeFromString<SolveResponse>(raw)
        assertEquals("Some guide", response.parentGuide)
        assertNull(response.childScript)
        assertNull(response.barModelJson)
        assertNull(response.knowledgeTags)
    }

    @Test
    fun ignores_unknown_fields() {
        val raw = """
            {
              "parentGuide": "Guide",
              "unknownField": "should be ignored",
              "anotherField": 42
            }
        """.trimIndent()
        val response = json.decodeFromString<SolveResponse>(raw)
        assertEquals("Guide", response.parentGuide)
    }
}

class SolveEventTest {
    @Test
    fun deserializes_event() {
        val raw = """{"type":"parent_guide","content":"Hello parent"}"""
        val event = json.decodeFromString<SolveEvent>(raw)
        assertEquals("parent_guide", event.type)
        assertEquals("Hello parent", event.content)
    }

    @Test
    fun roundtrip_serialization() {
        val original = SolveEvent(type = "knowledge_tags", content = "ratio.basic, whole_numbers")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SolveEvent>(encoded)
        assertEquals(original, decoded)
    }
}
