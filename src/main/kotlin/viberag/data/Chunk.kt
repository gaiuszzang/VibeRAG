package viberag.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chunk(
    val id: String,
    val text: String,
    val source: String? = null,
    val section: String? = null,
    val chunk: Int? = null,
    @SerialName("char_start") val charStart: Int? = null,
    @SerialName("char_end") val charEnd: Int? = null,
)
