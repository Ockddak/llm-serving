# LLM SERVING GATEWAY
본 프로젝트는 LLM 호출 비용을 제어하고, 스트리밍 응답을 안정적으로 중계하기 위한 API Gateway를 구현한 예제입니다.

Spring Cloud Gateway + WebFlux를 기반으로
- Ollama LLM SSE(Server Sent Events) 스트리밍 프록시
- Redis Lua Script 기반 TPM(Token Per Minute) 제한
- 클라이언트/글로벌 단위 비용 제어

를 구현했습니다.

## 주요 기능 요약

| 기능                     | 설명                                |
|:-----------------------|:----------------------------------|
| SSE Streaming Proxy    | Ollama SSE 응답을 끊김 없이 클라이언트로 전달    |
| Cancellation Handling  | 클라이언트 연결 종료 시 Ollama 요청 즉시 중단     |
| Keep-Alive Ping        | 장시간 연결 유지 (LB / Proxy timeout 방지) |
| TPM Rate Limiting      | Redis + Lua 기반 토큰 단위 비용 제한        |
| Client / Global Limit  | 클라이언트별 + 전체 시스템 TPM 분리 관리         |

## 아키텍처
```
Client
  │
  │  (HTTP / SSE)
  ▼
Gateway
  │
  │ 1 Request Body 읽기
  │ 2 JTokkit으로 토큰 수 계산
  │ 3 Redis Lua Script 호출
  │     - global TPM 차감
  │     - client TPM 차감
  │
  │ 4 통과 시
  ▼
Ollama
  │
  │ 5 SSE Streaming 응답
  ▼
Gateway
  │
  │ 6 스트림 그대로 전달
  ▼
Client
```

## 요청 흐름 상세
### 1. 클라이언트 요청
```
POST /llm/stream
X-Client-Id: clientA
```
```json
{
  "model": "llama3.2",
  "prompt": "Spring Cloud Gateway를 설명해줘",
  "stream": true
}
```
### 2. Gateway 내부 처리 순서
```
요청 수신
  ↓
Client ID 검증
  ↓
Request Body 읽기
  ↓
JTokkit 기반 토큰 수 추정
  ↓
Redis Global TPM 차감
  ↓
Redis Client TPM 차감
  ↓
통과 → Ollama 호출
차단 → 429 응답
```

### 3. TPM(Token Per Minute) 제한 설계

#### 왜 RPS(Request Per Second)가 아닌 TPM인가?
- LLM 비용은 요청 수가 아니라 토큰 수에 비례. 따라서, 토큰 단어 제어가 필수

#### 토큰 단위 처리(Redis Lua Script 기반)
```
local current = redis.call("GET", KEYS[1])
if not current then current = "0" end

if (tonumber(current) + tonumber(ARGV[1])) > tonumber(ARGV[2]) then
  return -1
end

local new = redis.call("INCRBY", KEYS[1], ARGV[1])
redis.call("EXPIRE", KEYS[1], ARGV[3])
return new
```
**특징**
- 단일 Redis 명령으로 원자성 보장
- 동시 요청에서도 정확한 제한
- TTL 기반 자동 리셋

#### TPM Key 구조
```
tpm:global:202601152230
tpm:client:clientA:202601152230
```

| 구분     | 의미        |
|:-------|:----------|
| global | 전체 시스템 한도 |
| client | 개별 사용자 한도 |
| minute | 분 단위 윈도우  |


### SSE 스트리밍 프록시 구조
#### 기본 아이디어
```
Ollama → Gateway → Client
중간에서 데이터를 저장하지 않고 즉시 전달
```
#### 커넥션 유지 전략
##### Keep-Alive Ping
- SSE 연결은 장시간 유지됨
- 중간 LB / Proxy가 idle timeout으로 끊을 수 있음
- `: ping\n\n` 주기 전송으로 연결 유지

##### Cancellation Handling
- 브라우저 종료 / 네트워크 끊김
- doOnCancel() 호출
- Ollama 요청 즉시 중단

##### Error Event Mapping
```
event: error
data: {"message":"TPM limit exceeded"}
```