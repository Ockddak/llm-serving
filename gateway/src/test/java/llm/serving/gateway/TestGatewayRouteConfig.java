package llm.serving.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestGatewayRouteConfig {
    @Bean
    public RouteLocator testRoutes(
            final RouteLocatorBuilder builder
    ) {
        return builder.routes()
                .route("test-routes", r -> r
                        .path("/gateway/test/**")
                        .uri("forward:/test/echo")
                )
                .build();
    }
}
