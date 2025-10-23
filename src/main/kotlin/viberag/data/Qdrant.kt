package viberag.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class QdrantPoint(
    val id: String,
    val vector: List<Float>,
    val payload: JsonObject
)

@Serializable data class QdrantUpsertReq(
    val points: List<QdrantPoint>
)


@Serializable data class QdrantSearchReq(
    // 단일 벡터 스키마 기준
    val vector: List<Float>,
    val limit: Int = 5,
    @SerialName("with_payload") val withPayload: Boolean = true,
    val filter: JsonObject? = null,
    val params: JsonObject? = null
)
@Serializable data class QdrantPointHit(
    val id: String? = null,
    val score: Double? = null,
    val payload: JsonObject? = null
)
@Serializable data class QdrantSearchResp(
    val result: List<QdrantPointHit>? = null
)
