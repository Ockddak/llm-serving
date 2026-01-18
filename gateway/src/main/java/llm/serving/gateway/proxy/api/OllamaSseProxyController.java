package llm.serving.gateway.proxy.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
public class OllamaSseProxyController {

    private final Log log = LogFactory.getLog(OllamaSseProxyController.class);

    private final WebClient ollamaWebClient;

    public OllamaSseProxyController(
            final WebClient webClient
    ) {
        this.ollamaWebClient = webClient;

    }

    @GetMapping(
            value = "/llm/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE // ollama 에서 응답이 오면 전달받은 응답을 바로 클라이언트에 전달
    )
    public Flux<String> stream() {

        Flux<String> ollamaStream = ollamaWebClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "model": "llama3.2",
                          "prompt": "Spring Cloud Gateway와 SSE를 설명해줘",
                          "stream": true
                        }
                        """)
                // stream: 응답 시, 문자열 전체가 아닌 토큰 단위로 응답
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(30)); //30초 동안 ollama에서 아무 데이터가 안오면 에러 발생

        Flux<String> keepAlive = Flux.interval(Duration.ofSeconds(10))
                .map(tick -> ": ping\n\n") // 클라이언트와 gateway 사이의 연결 유지를 위해 주기적으로 ping을 전달
                .takeUntilOther(ollamaStream.ignoreElements()); //ollamaStream이 완료되는 순간 keepAlive도 같이 종료

        return Flux.merge(ollamaStream, keepAlive)
                .doOnCancel(() -> {
                    log.info("Client disconnected - cancel Ollama request"); //클라이언트가 연결을 끊은 경우 요청 종료
                })
                .onErrorResume(ex -> {
                    log.error("Ollama stream error", ex);

                    String errorEvent = """
                        event: error
                        data: {"message":"Ollama streaming failed"}
                        """;
                    return Flux.just(errorEvent);
                });
    }
}
