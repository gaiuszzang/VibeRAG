package viberag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import viberag.data.Chunk
import viberag.data.QdrantPoint
import viberag.service.OllamaService
import viberag.service.QdrantService
import java.io.File
import java.util.UUID

// ---------- 실행 엔트리 ----------
suspend fun embedAndUpsert(
    chunksFile: File,
    docId: String,
    collection: String,
    provider: String,
    model: String,
    dims: Int
) {
    // 1) JSONL 읽기
    val lines = File(chunksFile.absolutePath).useLines { seq ->
        seq.filter { it.isNotBlank() }.toList()
    }
    val json = Json { ignoreUnknownKeys = true }
    val chunks = lines.mapIndexed { i, line ->
        try {
            json.decodeFromString<Chunk>(line)
        } catch (e: Exception) {
            error("JSONL 파싱 실패 (line ${i+1}): ${e.message}\n내용: $line")
        }
    }
    println("Loaded ${chunks.size} chunks from ${chunksFile.absolutePath} (docId=$docId)")

    // 2) 컬렉션 보장
    QdrantService.ensureCollection(collection = collection, dims = dims)

    // 3) 시리얼 처리: 각 청크를 임베딩 → 즉시 업서트
    var ok = 0
    for (c in chunks) {
        val vec = OllamaService.embed(text = c.text)
        println("Embedding chunk\n - text : ${c.text}\n - vector : $vec")
        require(vec.size == dims) { "임베딩 차원 불일치: got ${vec.size}, expected $dims (chunk id=${c.id})" }

        QdrantService.upsertPoint(
            point = QdrantPoint(
                id = qdrantIdFromExt(c.id),
                vector = vec.map { it.toFloat() },
                payload = buildJsonObject {
                    put("doc_id", JsonPrimitive(docId))
                    put("text", JsonPrimitive(c.text))
                    c.source?.let { put("source", JsonPrimitive(it)) }
                    c.section?.let { put("section", JsonPrimitive(it)) }
                    c.chunk?.let { put("chunk", JsonPrimitive(it)) }
                    c.charStart?.let { put("char_start", JsonPrimitive(it)) }
                    c.charEnd?.let { put("char_end", JsonPrimitive(it)) }
                    put("embed", buildJsonObject {
                        put("provider", JsonPrimitive(provider))
                        put("model", JsonPrimitive(model))
                        put("dims", JsonPrimitive(dims))
                        put("normalized", JsonPrimitive(true))
                    })
                }
            ),
            collection = collection
        )
        ok++
        if (ok % 50 == 0) println("Upserted $ok / ${chunks.size}")
    }

    println("Done. Upserted $ok points (serial) into '$collection' for docId='$docId'.")
}

// ---------- Helpers ----------

fun qdrantIdFromExt(ext: String): String =
    UUID.nameUUIDFromBytes(ext.toByteArray(Charsets.UTF_8)).toString() // 결정적(UUID v3 스타일)
