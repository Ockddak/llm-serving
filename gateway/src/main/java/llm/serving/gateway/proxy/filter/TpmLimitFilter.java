package llm.serving.gateway.proxy.filter;

import llm.serving.gateway.proxy.service.JtokService;
import llm.serving.gateway.proxy.service.RedisTpmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TpmLimitFilter implements GlobalFilter, Ordered {

    private final Logger log = LoggerFactory.getLogger(TpmLimitFilter.class);


    private final RedisTpmService redisTpmService;
    private final JtokService jtokService;

    // ===== 정책 값 (실무에서는 yml or DB) =====
    private static final long GLOBAL_TPM_LIMIT = 200_000;
    private static final long DEFAULT_CLIENT_TPM_LIMIT = 50_000;
    private static final long TTL_SECONDS = 61;

    private static final DateTimeFormatter MINUTE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public TpmLimitFilter(
            final RedisTpmService redisTpmService,
            final JtokService jtokService
    ) {
        this.redisTpmService = redisTpmService;
        this.jtokService = jtokService;

    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String clientId = resolveClientId(exchange);
        if (clientId == null) {
            return reject(exchange, "CLIENT_ID_MISSING");
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);

                    long tokens = jtokService.estimateTokensFromBody(body);
                    String minute = currentMinute();

                    String globalKey = "tpm:global:" + minute;
                    String clientKey = "tpm:client:" + clientId + ":" + minute;

                    // Global TPM
                    return redisTpmService.tryConsume(
                                    globalKey,
                                    tokens,
                                    GLOBAL_TPM_LIMIT,
                                    TTL_SECONDS
                            )
                            .flatMap(globalResult -> {
                                if (globalResult < 0) {
                                    return reject(exchange, "GLOBAL_TPM_EXCEEDED")
                                            .then(Mono.empty());
                                }

                                // Client TPM
                                return redisTpmService.tryConsume(
                                        clientKey,
                                        tokens,
                                        clientLimit(clientId),
                                        TTL_SECONDS
                                );
                            })
                            .flatMap(clientResult -> {
                                if (clientResult < 0) {
                                    return reject(exchange, "CLIENT_TPM_EXCEEDED");
                                }
                                ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        DataBuffer buffer = exchange.getResponse()
                                                .bufferFactory()
                                                .wrap(bytes);
                                        return Flux.just(buffer);
                                    }
                                };

                                ServerWebExchange mutatedExchange =
                                        exchange.mutate()
                                                .request(decoratedRequest)
                                                .build();
                                // 통과
                                return chain.filter(mutatedExchange);
                            });
                });
    }

    // ===== Helper Methods =====

    private String resolveClientId(ServerWebExchange exchange) {
        return exchange.getRequest()
                .getHeaders()
                .getFirst("X-Client-Id");
    }

    private String resolveModel(String body) {
        if (body.contains("\"model\"")) {
            int idx = body.indexOf("\"model\"");
            int start = body.indexOf("\"", idx + 7) + 1;
            int end = body.indexOf("\"", start);
            return body.substring(start, end);
        }
        return "unknown";
    }

    private long clientLimit(String clientId) {
        return DEFAULT_CLIENT_TPM_LIMIT;
    }

    private String currentMinute() {
        return LocalDateTime.now().format(MINUTE_FMT);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String reason) {
        log.warn("TPM reject: {}", reason);

        byte[] body = """
                {
                  "error": "TPM_LIMIT_EXCEEDED",
                  "reason": "%s"
                }
                """.formatted(reason).getBytes(StandardCharsets.UTF_8);

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return exchange.getResponse()
                .writeWith(Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(body)
                ));
    }

    @Override
    public int getOrder() {
        // 인증 이후, 라우팅 이전
        return -50;
    }
}
