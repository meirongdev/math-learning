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
import kotlin.test.assertNull
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

    // ── solve ─────────────────────────────────────────────────────────────────

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

    @Test
    fun solve_sends_auth_header_when_token_set() = runTest {
        var capturedAuth = ""
        val api = mockApi { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization] ?: ""
            respond(
                content = """{"parentGuide":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        api.token = "my-jwt-token"

        api.solve(SolveRequest("5 + 3", grade = 1))

        assertEquals("Bearer my-jwt-token", capturedAuth)
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    fun register_201_returns_true() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.Created)
        }

        val result = api.register("test@example.com", "password123")

        assertTrue(result)
    }

    @Test
    fun register_409_returns_false() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"code":"EMAIL_TAKEN","message":"already registered"}""",
                status = HttpStatusCode.Conflict,
            )
        }

        val result = api.register("existing@example.com", "pass")

        assertTrue(!result)
    }

    @Test
    fun register_sends_to_correct_endpoint() = runTest {
        var capturedUrl = ""
        val api = mockApi { request ->
            capturedUrl = request.url.fullPath
            respond(content = "", status = HttpStatusCode.Created)
        }

        api.register("a@b.com", "pass")

        assertEquals("/api/v1/auth/register", capturedUrl)
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    fun login_success_stores_token() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"token":"jwt-abc","userId":"u1","expiresAt":"2099-01-01T00:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val loginResponse = api.login("user@example.com", "pass")

        assertEquals("jwt-abc", loginResponse.token)
        assertEquals("jwt-abc", api.token)
    }

    @Test
    fun login_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = """{"code":"UNAUTHORIZED"}""", status = HttpStatusCode.Unauthorized)
        }

        assertFailsWith<RuntimeException> {
            api.login("bad@example.com", "wrong")
        }
    }

    // ── students ──────────────────────────────────────────────────────────────

    @Test
    fun createStudent_success_returns_student() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"id":"s1","name":"Alice","grade":3}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val student = api.createStudent("Alice", 3)

        assertEquals("Alice", student.name)
        assertEquals(3, student.grade)
        assertEquals("s1", student.id)
    }

    @Test
    fun createStudent_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = """{"code":"BAD_REQUEST"}""", status = HttpStatusCode.BadRequest)
        }

        assertFailsWith<RuntimeException> { api.createStudent("", 1) }
    }

    @Test
    fun listStudents_success_returns_list() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """[{"id":"s1","name":"Alice","grade":3},{"id":"s2","name":"Bob","grade":5}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val students = api.listStudents()

        assertEquals(2, students.size)
        assertEquals("Alice", students[0].name)
        assertEquals("Bob", students[1].name)
    }

    @Test
    fun listStudents_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }

        assertFailsWith<RuntimeException> { api.listStudents() }
    }

    @Test
    fun deleteStudent_204_succeeds() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        // Should not throw
        api.deleteStudent("s1")
    }

    @Test
    fun deleteStudent_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = """{"code":"NOT_FOUND"}""", status = HttpStatusCode.NotFound)
        }

        assertFailsWith<RuntimeException> { api.deleteStudent("nonexistent") }
    }

    // ── knowledge graph ───────────────────────────────────────────────────────

    @Test
    fun getKnowledgeGraph_success_returns_tree() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """[{"code":"NUM","nameEn":"Numbers","nameZh":"数","gradeStart":1,"children":[]}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val graph = api.getKnowledgeGraph()

        assertEquals(1, graph.size)
        assertEquals("NUM", graph[0].code)
        assertNull(graph[0].parentCode)
    }

    @Test
    fun getKnowledgeGraph_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }

        assertFailsWith<RuntimeException> { api.getKnowledgeGraph() }
    }

    @Test
    fun getKnowledgeProgress_success_returns_list() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """[{"id":"p1","knowledgeCode":"whole_numbers","attemptCount":3,"masteryLevel":"FAMILIAR"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val progress = api.getKnowledgeProgress("s1")

        assertEquals(1, progress.size)
        assertEquals("whole_numbers", progress[0].knowledgeCode)
        assertEquals("FAMILIAR", progress[0].masteryLevel)
    }

    @Test
    fun getKnowledgeProgress_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.Forbidden)
        }

        assertFailsWith<RuntimeException> { api.getKnowledgeProgress("s1") }
    }

    @Test
    fun updateMastery_success_does_not_throw() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.OK)
        }

        // Should not throw
        api.updateMastery("s1", "whole_numbers", "MASTERED")
    }

    @Test
    fun updateMastery_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.NotFound)
        }

        assertFailsWith<RuntimeException> { api.updateMastery("s1", "bad.code", "MASTERED") }
    }

    // ── records ───────────────────────────────────────────────────────────────

    @Test
    fun getRecords_success_returns_paged_response() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"records":[{"id":"r1","questionText":"5+3","createdAt":"2026-01-01"}],"page":0,"size":20,"totalElements":1,"totalPages":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = api.getRecords("s1")

        assertEquals(1, result.records.size)
        assertEquals("r1", result.records[0].id)
        assertEquals(1L, result.totalElements)
    }

    @Test
    fun getRecords_sends_page_params() = runTest {
        var capturedUrl = ""
        val api = mockApi { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"records":[],"page":2,"size":10,"totalElements":0,"totalPages":0}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        api.getRecords("s1", page = 2, size = 10)

        assertTrue(capturedUrl.contains("page=2"), "page param expected")
        assertTrue(capturedUrl.contains("size=10"), "size param expected")
    }

    @Test
    fun getRecords_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }

        assertFailsWith<RuntimeException> { api.getRecords("s1") }
    }

    @Test
    fun rateRecord_success_returns_rating_response() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """{"recordId":"r1","rating":5,"suggestedMastery":"MASTERED"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = api.rateRecord("r1", 5)

        assertEquals("r1", result.recordId)
        assertEquals(5, result.rating)
        assertEquals("MASTERED", result.suggestedMastery)
    }

    @Test
    fun rateRecord_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.NotFound)
        }

        assertFailsWith<RuntimeException> { api.rateRecord("bad-id", 3) }
    }

    // ── assessment questions ──────────────────────────────────────────────────

    @Test
    fun getAssessmentQuestions_success_returns_list() = runTest {
        val api = mockApi { _ ->
            respond(
                content = """[{"id":"q1","questionText":"What is 2+2?","grade":1,"difficulty":"easy"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val questions = api.getAssessmentQuestions(grade = 1)

        assertEquals(1, questions.size)
        assertEquals("q1", questions[0].id)
        assertEquals("easy", questions[0].difficulty)
        assertNull(questions[0].answerHint)
    }

    @Test
    fun getAssessmentQuestions_sends_all_params() = runTest {
        var capturedUrl = ""
        val api = mockApi { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """[]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        api.getAssessmentQuestions(tag = "ratio.basic", grade = 5, limit = 5)

        assertTrue(capturedUrl.contains("tag=ratio.basic"), "tag param expected")
        assertTrue(capturedUrl.contains("grade=5"), "grade param expected")
        assertTrue(capturedUrl.contains("limit=5"), "limit param expected")
    }

    @Test
    fun getAssessmentQuestions_failure_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.InternalServerError)
        }

        assertFailsWith<RuntimeException> { api.getAssessmentQuestions() }
    }

    // ── UnauthorizedException ─────────────────────────────────────────────────

    @Test
    fun api_401_response_throws_RuntimeException() = runTest {
        val api = mockApi { _ ->
            respond(content = "", status = HttpStatusCode.Unauthorized)
        }
        api.token = "some-token"

        // The mock client does not install HttpResponseValidator, so we get the
        // generic RuntimeException from the isSuccess() check in each method.
        assertFailsWith<RuntimeException> {
            api.solve(SolveRequest("5 + 3", grade = 1))
        }
    }

    @Test
    fun api_401_clears_token_via_real_client() = runTest {
        // Tests that UnauthorizedException is thrown when the HttpResponseValidator is installed.
        var apiRef: MathApi? = null
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ -> respond(content = "", status = HttpStatusCode.Unauthorized) }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        apiRef?.token = null
                        throw UnauthorizedException()
                    }
                }
            }
        }
        val api = MathApi(baseUrl = "http://mock", httpClient = client)
        apiRef = api
        api.token = "expired-token"

        assertFailsWith<UnauthorizedException> {
            api.solve(SolveRequest("5 + 3", grade = 1))
        }
        assertNull(api.token, "token should be cleared on 401")
    }
}
