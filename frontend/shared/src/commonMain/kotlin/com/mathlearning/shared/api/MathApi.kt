package com.mathlearning.shared.api

import com.mathlearning.shared.model.SolveEvent
import com.mathlearning.shared.model.SolveRequest
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MathApi(
    private val baseUrl: String = "http://localhost:8080"
) {
    private val client = HttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Sends a solve request to the streaming endpoint and returns a Flow of SolveEvents
     * parsed from SSE data lines. Each event contains a type (parent_guide, child_script,
     * bar_model, knowledge_tags, error) and content string.
     */
    fun solveStream(request: SolveRequest): Flow<SolveEvent> = flow {
        val response: HttpResponse = client.post("$baseUrl/api/v1/solve/stream") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(json.encodeToString(request))
        }

        val channel: ByteReadChannel = response.bodyAsChannel()

        while (!channel.isClosedForRead) {
            val line = try {
                channel.readUTF8Line()
            } catch (_: Exception) {
                null
            } ?: break

            if (line.startsWith("data:")) {
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                try {
                    val event = json.decodeFromString<SolveEvent>(data)
                    emit(event)
                } catch (_: Exception) {
                    // Skip malformed events
                }
            }
        }
    }
}
