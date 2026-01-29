package llm.serving.gateway.proxy.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RequestMapping("/test")
@RestController
public class TestEchoController {
    private final Logger log = LoggerFactory.getLogger(TestEchoController.class);
    @PostMapping("/echo")
    public Mono<String> echo(@RequestBody Mono<String> body) {
        log.info("/test/echo START");
        return body.defaultIfEmpty("EMPTY");
    }
}
