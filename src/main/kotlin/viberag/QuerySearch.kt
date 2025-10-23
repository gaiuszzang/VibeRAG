package viberag

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import viberag.data.QdrantPointHit
import viberag.service.OllamaService
import viberag.service.QdrantService

// ---------- Pretty print ----------
fun prettyHit(hit: QdrantPointHit): String {
    val id = hit.id ?: "N/A"
    val score = hit.score?.let { String.format("%.4f", it) } ?: "N/A"
    val payload = hit.payload
    val docId = payload?.get("doc_id")?.jsonPrimitive?.contentOrNull ?: ""
    val section = payload?.get("section")?.jsonPrimitive?.contentOrNull ?: ""
    val extId = payload?.get("ext_id")?.jsonPrimitive?.contentOrNull ?: ""
    val text = payload?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
    val preview = if (text.length > 200) text.take(200) + "..." else text
    return buildString {
        appendLine("id=$id  score=$score  doc_id=$docId  section=$section  ext_id=$extId")
        appendLine(preview)
    }
}

// ---------- main ----------
suspend fun querySearch(
    query: String,
    docId: String? = null,
    topK: Int = 5,
    collection: String = "docs"
) {
    println("Query: $query")

    // 1) 임베딩
    val embedding = OllamaService.embed(text = query, maxChars = 2000)
    val qVec = embedding.map { it.toFloat() }

    // 2) 검색
    val hits = QdrantService.search(
        queryVector = qVec,
        topK = topK,
        collection = collection,
        docId = docId
    )

    // 3) 출력
    if (hits.isEmpty()) {
        println("No results.")
    } else {
        println("Top $topK results:")
        hits.forEachIndexed { i, h ->
            println("[$i]\n" + prettyHit(h))
        }
    }
}
