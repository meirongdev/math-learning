package com.mathlearning.shared.api

import com.mathlearning.shared.model.AssessmentQuestionResponse
import com.mathlearning.shared.model.CreateStudentRequest
import com.mathlearning.shared.model.KnowledgeNodeResponse
import com.mathlearning.shared.model.KnowledgeProgressResponse
import com.mathlearning.shared.model.LoginRequest
import com.mathlearning.shared.model.LoginResponse
import com.mathlearning.shared.model.PagedRecordResponse
import com.mathlearning.shared.model.RatingRequest
import com.mathlearning.shared.model.RatingResponse
import com.mathlearning.shared.model.RegisterRequest
import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.shared.model.SolveResponse
import com.mathlearning.shared.model.Student
import com.mathlearning.shared.model.UpdateMasteryRequest
import com.mathlearning.shared.storage.clearToken
import com.mathlearning.shared.storage.saveToken
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UnauthorizedException : RuntimeException("Session expired. Please log in again.")

class MathApi(
    private val baseUrl: String = "http://localhost:8080",
    httpClient: HttpClient? = null,
) {
    internal val client =
        httpClient ?: HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                connectTimeoutMillis = 10_000
            }
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        clearToken()
                        token = null
                        throw UnauthorizedException()
                    }
                }
            }
        }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var token: String? = null

    private fun HttpRequestBuilder.authHeader() {
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    suspend fun register(email: String, password: String): Boolean {
        val response: HttpResponse = client.post("$baseUrl/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest(email, password)))
        }
        return response.status == HttpStatusCode.Created
    }

    suspend fun login(email: String, password: String): LoginResponse {
        val response: HttpResponse = client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(email, password)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Login failed: ${response.status}")
        }
        val loginResponse = json.decodeFromString<LoginResponse>(response.bodyAsText())
        token = loginResponse.token
        saveToken(loginResponse.token, loginResponse.expiresAt)
        return loginResponse
    }

    suspend fun solve(request: SolveRequest): SolveResponse {
        val response: HttpResponse = client.post("$baseUrl/api/v1/solve") {
            contentType(ContentType.Application.Json)
            authHeader()
            setBody(json.encodeToString(request))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Server returned ${response.status}")
        }
        return json.decodeFromString<SolveResponse>(response.bodyAsText())
    }

    suspend fun createStudent(name: String, grade: Int): Student {
        val response: HttpResponse = client.post("$baseUrl/api/v1/students") {
            contentType(ContentType.Application.Json)
            authHeader()
            setBody(json.encodeToString(CreateStudentRequest(name, grade)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to create student: ${response.status}")
        }
        return json.decodeFromString<Student>(response.bodyAsText())
    }

    suspend fun listStudents(): List<Student> {
        val response: HttpResponse = client.get("$baseUrl/api/v1/students") {
            authHeader()
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to list students: ${response.status}")
        }
        return json.decodeFromString<List<Student>>(response.bodyAsText())
    }

    suspend fun deleteStudent(id: String) {
        val response: HttpResponse = client.delete("$baseUrl/api/v1/students/$id") {
            authHeader()
        }
        if (response.status != HttpStatusCode.NoContent && !response.status.isSuccess()) {
            throw RuntimeException("Failed to delete student: ${response.status}")
        }
    }

    // Knowledge graph
    suspend fun getKnowledgeGraph(): List<KnowledgeNodeResponse> {
        val response: HttpResponse = client.get("$baseUrl/api/v1/knowledge/graph")
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to get knowledge graph: ${response.status}")
        }
        return json.decodeFromString<List<KnowledgeNodeResponse>>(response.bodyAsText())
    }

    suspend fun getKnowledgeProgress(studentId: String): List<KnowledgeProgressResponse> {
        val response: HttpResponse = client.get("$baseUrl/api/v1/knowledge/$studentId/progress") {
            authHeader()
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to get knowledge progress: ${response.status}")
        }
        return json.decodeFromString<List<KnowledgeProgressResponse>>(response.bodyAsText())
    }

    suspend fun updateMastery(studentId: String, nodeCode: String, masteryLevel: String) {
        val response: HttpResponse = client.put("$baseUrl/api/v1/knowledge/$studentId/progress/$nodeCode") {
            contentType(ContentType.Application.Json)
            authHeader()
            setBody(json.encodeToString(UpdateMasteryRequest(masteryLevel)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to update mastery: ${response.status}")
        }
    }

    // Records
    suspend fun getRecords(studentId: String, page: Int = 0, size: Int = 20): PagedRecordResponse {
        val response: HttpResponse = client.get("$baseUrl/api/v1/records/$studentId") {
            authHeader()
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to get records: ${response.status}")
        }
        return json.decodeFromString<PagedRecordResponse>(response.bodyAsText())
    }

    suspend fun rateRecord(recordId: String, rating: Int): RatingResponse {
        val response: HttpResponse = client.patch("$baseUrl/api/v1/records/$recordId/rating") {
            contentType(ContentType.Application.Json)
            authHeader()
            setBody(json.encodeToString(RatingRequest(rating)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to rate record: ${response.status}")
        }
        return json.decodeFromString<RatingResponse>(response.bodyAsText())
    }

    // Assessment questions
    suspend fun getAssessmentQuestions(
        tag: String? = null,
        grade: Int? = null,
        limit: Int = 10,
    ): List<AssessmentQuestionResponse> {
        val response: HttpResponse = client.get("$baseUrl/api/v1/questions") {
            authHeader()
            tag?.let { parameter("tag", it) }
            grade?.let { parameter("grade", it) }
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to get questions: ${response.status}")
        }
        return json.decodeFromString<List<AssessmentQuestionResponse>>(response.bodyAsText())
    }
}
