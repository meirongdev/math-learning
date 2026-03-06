package com.mathlearning.shared.api

import com.mathlearning.shared.model.SolveRequest
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MathApiTest {
    private fun mockApi(handler: MockRequestHandler): MathApi {
        val client = HttpClient(MockEngine) {
            engine { addHandler(handler) }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
            }
        }
        return MathApi(baseUrl = "http://mock", httpClient = client)
    }

    @Test
    fun solve_success_returns_parsed_response() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """
                    {
                      "parentGuide": "Great job!",
                      "childScript": "Let's solve together!",
                      "barModelJson": "{}",
                      "knowledgeTags": ["whole_numbers"]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = api.solve(SolveRequest("5 + 3 = ?", grade = 1))

        assertEquals("Great job!", result.parentGuide)
        assertEquals("Let's solve together!", result.childScript)
        assertEquals("{}", result.barModelJson)
        assertEquals(listOf("whole_numbers"), result.knowledgeTags)
    }

    @Test
    fun solve_sends_correct_request_body() = runTest {
        var capturedBody = ""
        val api = mockApi { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"parentGuide":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        api.solve(SolveRequest("Amy has 24 sweets", grade = 3))

        assertTrue(capturedBody.contains("\"grade\":3"), "grade must be in request body")
        assertTrue(capturedBody.contains("Amy has 24 sweets"), "question must be in request body")
    }

    @Test
    fun solve_sends_to_correct_endpoint() = runTest {
        var capturedUrl = ""
        val api = mockApi { request ->
            capturedUrl = request.url.fullPath
            respond(
                content = """{}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        api.solve(SolveRequest("5 + 3", grade = 1))

        assertEquals("/api/v1/solve", capturedUrl)
    }

    @Test
    fun solve_400_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"code":"VALIDATION_ERROR","message":"must not be blank"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val ex = assertFailsWith<RuntimeException> {
            api.solve(SolveRequest("", grade = 3))
        }
        assertTrue(ex.message!!.contains("400"), "error message should include status code")
    }

    @Test
    fun solve_504_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"code":"LLM_TIMEOUT","message":"LLM timed out"}""",
                status = HttpStatusCode.GatewayTimeout,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val ex = assertFailsWith<RuntimeException> {
            api.solve(SolveRequest("x + 3 = 8", grade = 6))
        }
        assertTrue(ex.message!!.contains("504"), "error message should include status code")
    }

    @Test
    fun solve_with_null_studentId_excluded_from_body() = runTest {
        var capturedBody = ""
        val api = mockApi { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"parentGuide":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        api.solve(SolveRequest("5 + 3", grade = 1, studentId = null))

        // null studentId should serialize as JSON null (encodeDefaults = true)
        assertTrue(capturedBody.contains("studentId"), "studentId key should be present")
    }
}
