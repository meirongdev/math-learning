package com.mathlearning.shared.api

import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.shared.model.SolveResponse
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MathApi(
    private val baseUrl: String = "http://localhost:8080",
) {
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 180_000 // 3 minutes
            connectTimeoutMillis = 10_000
        }
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Sends a solve request and returns the structured result.
     * Uses the non-streaming endpoint for reliable Wasm compatibility.
     */
    suspend fun solve(request: SolveRequest): SolveResponse {
        val response: HttpResponse = client.post("$baseUrl/api/v1/solve") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Server returned ${response.status}")
        }

        val body = response.bodyAsText()
        return json.decodeFromString<SolveResponse>(body)
    }
}
