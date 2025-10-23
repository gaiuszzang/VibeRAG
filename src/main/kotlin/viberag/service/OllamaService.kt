package viberag.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import viberag.data.OllamaEmbReq
import viberag.data.OllamaEmbResp

/**
 * Ollama 임베딩 서비스
 */
object OllamaService {
    const val DEFAULT_OLLAMA_URL = "http://localhost:11434"
    const val DEFAULT_MODEL = "bge-m3"
    const val DEFAULT_VECTOR_DIMENSION = 1024

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    /**
     * 텍스트를 임베딩 벡터로 변환
     *
     * @param text 임베딩할 텍스트
     * @param maxChars 최대 문자 수 제한 (너무 긴 입력 방지, null이면 제한 없음)
     * @return 임베딩 벡터 (Double 리스트)
     */
    suspend fun embed(
        text: String,
        maxChars: Int? = null
    ): List<Double> {
        val cleanText = if (maxChars != null) {
            text.trim().take(maxChars)
        } else {
            text.trim()
        }

        require(cleanText.isNotEmpty()) { "임베딩할 텍스트가 비어있습니다" }

        val resp = httpClient.post("$DEFAULT_OLLAMA_URL/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbReq(DEFAULT_MODEL, cleanText))
        }

        if (resp.status.value !in 200..299) {
            error("Ollama 임베딩 실패: ${resp.status} ${resp.bodyAsText()}")
        }

        val body: OllamaEmbResp = resp.body()
        require(body.embedding.isNotEmpty()) { "임베딩 결과가 비어있습니다" }

        return body.embedding
    }

    fun release() {
        httpClient.close()
    }
}
