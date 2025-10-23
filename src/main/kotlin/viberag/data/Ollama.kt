package viberag.data

import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbReq(
    val model: String,
    val prompt: String
)

@Serializable data class OllamaEmbResp(
    val embedding: List<Double>
)
