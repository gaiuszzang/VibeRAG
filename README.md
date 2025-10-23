# VibeRAG

Ollama와 Qdrant 기반의 RAG(Retrieval-Augmented Generation) Tool입니다.

텍스트 문서를 청크로 분할하고, Ollama를 통해 임베딩을 생성한 뒤 Qdrant 벡터 데이터베이스에 저장하여 의미 기반 검색을 수행할 수 있습니다.

## 요구사항

- JDK 21 이상
- Gradle
- Ollama (로컬 실행 중이어야 함)
- Qdrant (로컬 또는 원격 인스턴스)

## 빌드

```bash
./gradlew build
```

## 실행 방법

### 1. 텍스트 청킹 (Chunk)

입력 텍스트 파일을 작은 청크로 분할합니다.

```bash
./gradlew run --args="--mode=chunk --input=input.txt --output=output.jsonl --docId=myDocument --targetLen=1000 --overlapSize=150"
```

**파라미터:**
- `--input`: 입력 텍스트 파일 경로 (필수)
- `--output`: 출력 JSONL 파일 경로 (필수)
- `--docId`: 문서 ID (기본값: myDocument)
- `--targetLen`: 목표 청크 길이 (기본값: 1000)
- `--overlapSize`: 청크 간 오버랩 크기 (기본값: 150)

### 2. 임베딩 및 업서트 (Embed)

청크를 임베딩하여 Qdrant에 저장합니다.

```bash
./gradlew run --args="--mode=embed --chunks=output.jsonl --docId=myDocument"
```

**파라미터:**
- `--chunks`: 청크 JSONL 파일 경로 (필수)
- `--docId`: 문서 ID (필수)

### 3. 검색 (Search)

쿼리를 기반으로 유사한 청크를 검색합니다.

```bash
./gradlew run --args="--mode=search --query=\"검색할 내용\" --docId=myDocument --topK=5"
```

**파라미터:**
- `--query`: 검색 쿼리 (필수)
- `--docId`: 특정 문서에서만 검색 (선택)
- `--topK`: 반환할 결과 개수 (기본값: 5)

## 라이선스

이 프로젝트는 오픈소스입니다.