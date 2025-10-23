package viberag.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import viberag.data.QdrantPoint
import viberag.data.QdrantPointHit
import viberag.data.QdrantSearchReq
import viberag.data.QdrantSearchResp
import viberag.data.QdrantUpsertReq

/**
 * Qdrant 벡터 데이터베이스 서비스
 */
object QdrantService {
    const val DEFAULT_QDRANT_URL = "http://localhost:6333"
    const val DEFAULT_COLLECTION = "docs"
    const val DEFAULT_DISTANCE = "Cosine"

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
    }

    /**
     * 컬렉션 생성/보장 (idempotent)
     *
     * @param collection 컬렉션 이름
     * @param dims 벡터 차원
     * @param distance 거리 측정 방식 (기본값: Cosine)
     */
    suspend fun ensureCollection(
        collection: String = DEFAULT_COLLECTION,
        dims: Int,
        distance: String = DEFAULT_DISTANCE
    ) {
        // 1) 존재 확인
        val get = httpClient.get("$DEFAULT_QDRANT_URL/collections/$collection")
        when (get.status.value) {
            in 200..299 -> {
                return // 이미 있으니 끝
            }

            404 -> {
                // 2) 없으면 생성
                val create = httpClient.put("$DEFAULT_QDRANT_URL/collections/$collection") {
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("vectors", buildJsonObject {
                            put("size", JsonPrimitive(dims))
                            put("distance", JsonPrimitive(distance))
                        })
                    })
                }
                if (create.status.value !in 200..299) {
                    error("컬렉션 생성 실패: ${create.status} ${create.bodyAsText()}")
                }
            }
            else -> error("컬렉션 조회 실패: ${get.status} ${get.bodyAsText()}")
        }
    }

    /**
     * 포인트를 Qdrant에 업서트
     *
     * @param points 업서트할 포인트 리스트
     * @param collection 컬렉션 이름
     */
    suspend fun upsertPoints(
        points: List<QdrantPoint>,
        collection: String = DEFAULT_COLLECTION
    ) {
        val resp = httpClient.put("$DEFAULT_QDRANT_URL/collections/$collection/points") {
            contentType(ContentType.Application.Json)
            setBody(QdrantUpsertReq(points = points))
        }
        if (resp.status.value !in 200..299) {
            error("Qdrant 업서트 실패: ${resp.status} ${resp.bodyAsText()}")
        }
    }

    /**
     * 단일 포인트를 Qdrant에 업서트
     *
     * @param point 업서트할 포인트
     * @param collection 컬렉션 이름
     */
    suspend fun upsertPoint(
        point: QdrantPoint,
        collection: String = DEFAULT_COLLECTION
    ) {
        upsertPoints(listOf(point), collection)
    }

    /**
     * 벡터 검색
     *
     * @param queryVector 쿼리 벡터
     * @param topK 반환할 결과 개수
     * @param collection 컬렉션 이름
     * @param docId 필터링할 문서 ID (선택)
     * @param hnswEf HNSW 파라미터 (선택)
     * @return 검색 결과 리스트
     */
    suspend fun search(
        queryVector: List<Float>,
        topK: Int = 5,
        collection: String = DEFAULT_COLLECTION,
        docId: String? = null,
        hnswEf: Int? = null
    ): List<QdrantPointHit> {
        val filter = docId?.let {
            buildJsonObject {
                put("must", buildJsonArray {
                    add(buildJsonObject {
                        put("key", JsonPrimitive("doc_id"))
                        put("match", buildJsonObject { put("value", JsonPrimitive(it)) })
                    })
                })
            }
        }

        val params = hnswEf?.let {
            buildJsonObject { put("hnsw_ef", JsonPrimitive(it)) }
        }

        val req = QdrantSearchReq(
            vector = queryVector,
            limit = topK,
            withPayload = true,
            filter = filter,
            params = params
        )

        val resp = httpClient.post("$DEFAULT_QDRANT_URL/collections/$collection/points/search") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }

        if (resp.status.value !in 200..299) {
            error("Qdrant 검색 실패: ${resp.status} ${resp.bodyAsText()}")
        }

        val body = resp.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<QdrantSearchResp>(body)
        return parsed.result.orEmpty()
    }

    fun release() {
        httpClient.close()
    }
}
