# TODO

## 개선 필요 항목

- [ ] OllamaService, QdrantService의 HttpClient release() 로직 호출 개선 필요
  - 현재 lazy로 생성된 HttpClient가 프로세스 종료 시까지 닫히지 않음
  - embed/search 모드 실행 후 약 1분간 프로세스가 대기하는 문제 발생
  - runBlocking 종료 전 또는 프로세스 종료 전에 httpClient.close() 호출 필요