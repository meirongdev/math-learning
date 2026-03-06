package com.mathlearning.shared.api

import com.mathlearning.shared.model.CreateStudentRequest
import com.mathlearning.shared.model.LoginRequest
import com.mathlearning.shared.model.LoginResponse
import com.mathlearning.shared.model.RegisterRequest
import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.shared.model.SolveResponse
import com.mathlearning.shared.model.Student
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MathApi(
    private val baseUrl: String = "http://localhost:8080",
    httpClient: HttpClient? = null,
) {
    internal val client =
        httpClient ?: HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000 // 3 minutes
                connectTimeoutMillis = 10_000
            }
        }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var token: String? = null

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
        return loginResponse
    }

    suspend fun solve(request: SolveRequest): SolveResponse {
        val response: HttpResponse = client.post("$baseUrl/api/v1/solve") {
            contentType(ContentType.Application.Json)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(json.encodeToString(request))
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Server returned ${response.status}")
        }

        val body = response.bodyAsText()
        return json.decodeFromString<SolveResponse>(body)
    }

    suspend fun createStudent(name: String, grade: Int): Student {
        val response: HttpResponse = client.post("$baseUrl/api/v1/students") {
            contentType(ContentType.Application.Json)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(json.encodeToString(CreateStudentRequest(name, grade)))
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to create student: ${response.status}")
        }
        return json.decodeFromString<Student>(response.bodyAsText())
    }

    suspend fun listStudents(): List<Student> {
        val response: HttpResponse = client.get("$baseUrl/api/v1/students") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Failed to list students: ${response.status}")
        }
        return json.decodeFromString<List<Student>>(response.bodyAsText())
    }
}
