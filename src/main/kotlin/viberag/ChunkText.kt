package viberag

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import viberag.data.Chunk
import java.io.File
import java.text.Normalizer
import kotlin.math.max
import kotlin.text.iterator

fun chunkText(inputFile: File, output: File, docId: String = "myDocument", targetLen: Int = 1000, overlapSize: Int = 150) {
    val raw = inputFile.readText(Charsets.UTF_8)
    val normalized = normalize(raw)
    val paragraphs = splitParagraphs(normalized)
    val joined = paragraphs.joinToString("\n\n")

    // 문장과 오프셋
    val (sentences, offsets) = splitSentencesWithOffsets(joined)

    // 헤더 감지(아주 간단한 규칙)
    val sections = detectSections(paragraphs)

    val chunks = makeChunks(
        sentences, offsets, docId, sections,
        targetLen = targetLen, overlap = overlapSize
    )

    output.printWriter(Charsets.UTF_8).use { pw ->
        for (c in chunks) {
            pw.println(toJsonl(c))
        }
    }
    println("Chunks: ${chunks.size} → ${output.absolutePath}")
}

private fun normalize(s: String): String {
    val nfkc = Normalizer.normalize(s, Normalizer.Form.NFKC)
    val lf = nfkc.replace("\r\n", "\n").replace("\r", "\n")
    val noCtrl = buildString {
        for (ch in lf) append(if (ch == '\n' || ch.code >= 0x20) ch else ' ')
    }
    // 라인단 trim + 과도한 공백 압축
    return noCtrl.lines().joinToString("\n") { it.trim().replace(Regex("\\s{3,}"), " ") }.trim()
}

private fun splitParagraphs(s: String): List<String> =
    s.split(Regex("\\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }

private fun splitSentencesWithOffsets(s: String): Pair<List<String>, List<Int>> {
    val out = mutableListOf<String>()
    val ofs = mutableListOf<Int>()
    var i = 0
    var buf = StringBuilder()
    var start = 0
    fun flush(endExclusive: Int) {
        val t = buf.toString().trim()
        if (t.isNotEmpty()) {
            out += t
            ofs += endExclusive - t.length
        }
        buf = StringBuilder()
    }
    while (i < s.length) {
        val ch = s[i]
        buf.append(ch)
        val next = if (i + 1 < s.length) s[i + 1] else '\u0000'
        val boundary = when {
            ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == '。' -> {
                // 간단 보호: 3.14 처럼 숫자.숫자는 경계로 보지 않음
                val prev = if (i > 0) s[i - 1] else '\u0000'
                !(prev.isDigit() && next.isDigit())
            }
            ch == '\n' -> true
            else -> false
        }
        if (boundary) flush(i + 1)
        i++
    }
    flush(s.length)

    // 너무 짧은 문장 합치기 (<= 60자)
    val merged = mutableListOf<String>()
    val mOfs = mutableListOf<Int>()
    var acc = StringBuilder()
    var accStart = if (ofs.isNotEmpty()) ofs[0] else 0
    for (idx in out.indices) {
        val cur = out[idx]
        if (acc.isEmpty()) {
            acc.append(cur)
        } else if (acc.length + 1 + cur.length <= 60) {
            acc.append(' ').append(cur)
        } else {
            merged += acc.toString(); mOfs += accStart
            acc = StringBuilder(cur); accStart = ofs[idx]
        }
    }
    if (acc.isNotEmpty()) { merged += acc.toString(); mOfs += accStart }
    return merged to mOfs
}

private fun detectSections(paragraphs: List<String>): List<Pair<Int, String>> {
    val res = mutableListOf<Pair<Int, String>>()
    var offset = 0
    for (p in paragraphs) {
        val first = p.lines().first().trim()
        val looksHeading =
            first.length in 2..80 &&
                    (first.matches(Regex("^\\d+(\\.\\d+)*\\s+.+$")) || // 1. / 1.2.3
                            first.matches(Regex("^[가-힣A-Za-z0-9].{0,78}$")))
        if (looksHeading) res += offset to first.take(120)
        offset += p.length + 2 // "\n\n" 가정
    }
    return res
}

private fun currentSection(charOffset: Int, sections: List<Pair<Int, String>>): String? {
    var cur: String? = null
    for ((ofs, name) in sections) {
        if (ofs <= charOffset) cur = name else break
    }
    return cur
}

private fun makeChunks(
    sentences: List<String>,
    offsets: List<Int>,
    docId: String,
    sections: List<Pair<Int, String>>,
    targetLen: Int,
    overlap: Int
): List<Chunk> {
    val out = mutableListOf<Chunk>()
    var i = 0
    var cid = 0
    while (i < sentences.size) {
        var cur = StringBuilder()
        val start = offsets[i]
        var end = start
        var j = i
        while (j < sentences.size) {
            val s = sentences[j]
            if (cur.isNotEmpty() && cur.length + 1 + s.length > targetLen) break
            if (cur.isNotEmpty()) cur.append('\n')
            cur.append(s)
            end = offsets[j] + s.length
            j++
        }
        val text = cur.toString().trim()
        if (text.isNotEmpty()) {
            val sec = currentSection(start, sections)
            out += Chunk(
                id = "%s-%05d".format(docId, cid),
                text = text,
                source = "$docId.txt",
                section = sec,
                chunk = cid,
                charStart = start,
                charEnd = end
            )
            cid++
        }
        if (j >= sentences.size) break
        // 오버랩: 뒤로 overlap 문자만큼 겹치게 시작점 이동
        var back = 0
        var k = j - 1
        while (k >= i && back < overlap) {
            back += sentences[k].length + 1
            k--
        }
        i = max(k + 1, i + 1)
    }
    return out
}


private val json = Json { encodeDefaults = true }
private fun toJsonl(c: Chunk): String = json.encodeToString(c)
