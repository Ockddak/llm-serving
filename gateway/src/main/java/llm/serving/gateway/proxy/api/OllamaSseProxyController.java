package llm.serving.gateway.proxy.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@RestController
public class OllamaSseProxyController {

    private final WebClient ollamaWebClient;

    public OllamaSseProxyController(
            final WebClient webClient
    ) {
        this.ollamaWebClient = webClient;

    }

    @GetMapping(
            value = "/llm/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE // ollama 에서 응답이 오면 전달받은 응답을 바로 클라이언트에 전달해
    )
    public Flux<String> stream() {

        return ollamaWebClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                      "model": "llama3.2",
                      "prompt": "Spring Cloud Gateway와 SSE를 설명해줘",
                      "stream": true
                    }
                    """)
                .retrieve()
                .bodyToFlux(String.class);
    }
}
