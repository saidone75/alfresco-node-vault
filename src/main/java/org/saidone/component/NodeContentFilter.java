package org.saidone.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class NodeContentFilter extends AbstractGatewayFilterFactory<NodeContentFilter.Config> {

    private final MongoNodeRepository mongoNodeRepository;

    public NodeContentFilter(MongoNodeRepository mongoNodeRepository) {
        super(Config.class);
        this.mongoNodeRepository = mongoNodeRepository;
    }

    @Override
    public Class<Config> getConfigClass() {
        return Config.class;
    }


    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return chain.filter(exchange)
                    .then(filter(exchange, chain).then(Mono.fromRunnable(() -> {})));
        };
    }

    private Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        Pattern nodeContentPattern = Pattern.compile(".*/nodes/(.{36})/content");
        Matcher matcher = nodeContentPattern.matcher(path);

        if (matcher.matches()) {
            String nodeId = matcher.group(1);
            log.debug("Detected node ID => {}", nodeId);

            // Check if node exists in MongoDB repository
            return Mono.fromSupplier(() -> mongoNodeRepository.findById(nodeId).orElse(null))
                    .flatMap(node -> {
                        if (node == null) {
                            log.debug("Node {} not found on the vault, forwarding to default system", nodeId);
                            return chain.filter(exchange);
                        }

                        log.debug("Node {} found on the vault, redirecting to internal API", nodeId);

                        // Keep any existing query parameters
                        String query = exchange.getRequest().getURI().getQuery();
                        String newPath = "/api/vault/nodes/" + nodeId + "/content";
                        if (query != null && !query.isEmpty()) {
                            //newPath += "?" + query;
                        }

                        // Modify request to redirect to internal API
                        ServerHttpRequest request = exchange.getRequest().mutate()
                                .path(newPath)
                                .build();

                        // Create new exchange with modified request
                        ServerWebExchange newExchange = exchange.mutate()
                                .request(request)
                                .build();

                        return chain.filter(newExchange);
                    });
        }

        // If pattern doesn't match, continue normally
        return chain.filter(exchange);
    }

    public static class Config {
    }

}