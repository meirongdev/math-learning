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
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Sends a solve request and returns a Flow of SolveEvents parsed from SSE.
     */
    fun solveStream(request: SolveRequest): Flow<SolveEvent> = flow {
        val response: HttpResponse = client.post("$baseUrl/api/solve") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(json.encodeToString(request))
        }

        val channel: ByteReadChannel = response.bodyAsChannel()
        val buffer = StringBuilder()

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
