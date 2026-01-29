package llm.serving.gateway.proxy.api;

import llm.serving.gateway.TestGatewayRouteConfig;
import llm.serving.gateway.proxy.service.JtokService;
import llm.serving.gateway.proxy.service.RedisTpmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@Import(TestGatewayRouteConfig.class)
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TestEchoControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    RedisTpmService redisTpmService;
    @MockBean
    JtokService jtokService;

    private static final String REQUEST_BODY = """
        {
          "model": "llama3.2",
          "messages": [
            { "role": "user", "content": "hello" }
          ]
        }
        """;

    @BeforeEach
    void setUp() {
        when(redisTpmService.tryConsume(any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Mono.just(1L));

        when(jtokService.estimateTokensFromBody(any()))
                .thenReturn(10L);
    }

    @Test
    void fixedFilter_shouldPreserveRequestBody() {

        webTestClient.post()
                .uri("/gateway/test/echo")
                .header("X-Client-Id", "clientA")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(REQUEST_BODY)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body ->
                        assertThat(body).contains("hello")
                );
    }

}