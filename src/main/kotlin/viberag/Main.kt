package viberag

import kotlinx.coroutines.runBlocking
import viberag.service.OllamaService
import viberag.service.QdrantService
import viberag.service.QdrantService.DEFAULT_COLLECTION
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // 파라메터 파싱
    val params = parseArgs(args)

    when (params["mode"]) {
        "chunk" -> {
            // --mode=chunk --input=input.txt --output=output.jsonl [--docId=myDocument] [--targetLen=1000] [--overlapSize=150]
            val input = params["input"] ?: run {
                println("Error: --input is required for chunk mode")
                exitProcess(1)
            }
            val output = params["output"] ?: run {
                println("Error: --output is required for chunk mode")
                exitProcess(1)
            }

            val docId = params["docId"] ?: "myDocument"
            val targetLen = params["targetLen"]?.toIntOrNull() ?: 1000
            val overlapSize = params["overlapSize"]?.toIntOrNull() ?: 150

            val inputFile = validateFilePath(input) ?: run {
                println("Error: Invalid input file path: $input")
                exitProcess(1)
            }
            val outputFile = validateFilePath(output) ?: run {
                println("Error: Invalid output file path: $output")
                exitProcess(1)
            }

            if (!inputFile.exists()) {
                println("Error: Input file does not exist: $input")
                exitProcess(1)
            }

            chunkText(inputFile, outputFile, docId, targetLen, overlapSize)
        }
        "embed" -> {
            // --mode=embed --chunks=chunks.jsonl --docId=myDocument
            val chunks = params["chunks"] ?: run {
                println("Error: --chunks is required for embed mode")
                exitProcess(1)
            }
            val docId = params["docId"] ?: run {
                println("Error: --docId is required for embed mode")
                exitProcess(1)
            }

            val chunksFile = validateFilePath(chunks) ?: run {
                println("Error: Invalid chunks file path: $chunks")
                exitProcess(1)
            }

            if (!chunksFile.exists()) {
                println("Error: Chunks file does not exist: $chunks")
                exitProcess(1)
            }

            runBlocking {
                embedAndUpsert(
                    chunksFile = chunksFile,
                    docId = docId,
                    collection = DEFAULT_COLLECTION,
                    provider = "ollama",
                    model = OllamaService.DEFAULT_MODEL,
                    dims = OllamaService.DEFAULT_VECTOR_DIMENSION
                )
                OllamaService.release()
                QdrantService.release()
            }
        }
        "search" -> {
            // --mode=search --query="query text" [--docId=myDocument] [--topK=5]
            val query = params["query"] ?: run {
                println("Error: --query is required for search mode")
                exitProcess(1)
            }

            val docId = params["docId"] // optional
            val topK = params["topK"]?.toIntOrNull() ?: 5

            runBlocking {
                querySearch(query, docId, topK)
                OllamaService.release()
                QdrantService.release()
            }
        }
        else -> {
            println("Error: Unknown mode. Use --mode=chunk, --mode=embed, or --mode=search")
            exitProcess(1)
        }
    }
}

fun parseArgs(args: Array<String>): Map<String, String> {
    val params = mutableMapOf<String, String>()

    for (arg in args) {
        if (arg.startsWith("--")) {
            val parts = arg.substring(2).split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0]
                var value = parts[1]
                // 따옴표로 감싸진 경우 제거
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1)
                }
                params[key] = value
            }
        }
    }

    return params
}

@SuppressWarnings
fun validateFilePath(path: String): File? {
    return try {
        // Path traversal 방지: 위험한 문자 및 패턴 체크
        if (path.contains('\u0000') ||
            path.contains("..") ||
            path.isEmpty()) {
            return null
        }

        // 정규화된 경로로 변환하여 실제 경로 검증
        val file = File(path).canonicalFile
        file
    } catch (_: Exception) {
        null
    }
}
