package org.saidone.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("node_route", r -> r
                .path("/alfresco/api/-default-/public/alfresco/versions/1/nodes/{nodeId}/content")
                .filters(f -> f
                    .setPath("/api/vault/nodes/{nodeId}/content")
                    .addRequestParameter("attachment", "true")
                )
                .uri("http://localhost:8086")
            )
            .route("default_route", r -> r
                .path("/**")
                .uri("http://localhost:8080")
            )
            .build();
    }
}