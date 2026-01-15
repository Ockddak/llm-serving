# LLM SERVING GATEWAY
이 프로젝트는 Spring Cloud Gateway를 사용해 Ollama의 SSE(Server-Sent Events) 스트리밍 응답을 그대로 프록시하는 최소 구현을 목표로 한다.
- 클라이언트는 Gateway에 단 한 번 요청
- Gateway는 Ollama 서버와 스트리밍 연결을 유지
- Ollama가 생성하는 토큰을 가공 없이 즉시 클라이언트로 전달

## 아키텍처
```
[Client (curl / browser)]
        |
        |  HTTP (SSE)
        ▼
[Spring Cloud Gateway]
        |
        |  HTTP (SSE, stream=true)
        ▼
[Ollama Server]
```

## Gateway 내부 동작
1. /llm/stream 요청 수신
2. Gateway는 WebClient를 통해 Ollama에 POST 요청
3. stream=true 옵션으로 스트리밍 응답 요청
4. Ollama가 토큰을 생성하는 즉시:
5. Gateway는 데이터를 버퍼링하지 않고
6. 즉시 클라이언트로 전달

## Ollama와의 연결
1. Gateway ↔ Ollama는 하나의 HTTP 연결을 계속 유지
2. Ollama가 응답을 끝내기 전까지 연결은 닫히지 않음
3. 생성이 끝나면:
   - Ollama가 스트림 종료
   - Gateway도 자동으로 종료
   - 클라이언트 연결 종료