package org.saidone.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Registers and exposes application specific metrics using Micrometer.
 * <p>
 * Currently exposes a gauge metric named {@code nodes_on_vault} reporting the
 * number of documents stored in the {@code alf_node} MongoDB collection.
 */
@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;
    private final MongoTemplate mongoTemplate;

    /**
     * Creates a new instance and registers the custom gauge metric.
     *
     * @param meterRegistry the registry to register metrics with
     * @param mongoTemplate template used to query MongoDB collections
     */
    public CustomMetrics(MeterRegistry meterRegistry, MongoTemplate mongoTemplate) {
        this.meterRegistry = meterRegistry;
        this.mongoTemplate = mongoTemplate;
        Gauge.builder("nodes_on_vault", this, CustomMetrics::getAlfNodeCount)
                .description("Nodes on vault")
                .tags(Tags.of("vault", "nodes"))
                .register(meterRegistry);
    }

    /**
     * Retrieves the count of nodes currently stored on the vault.
     *
     * @return number of documents in the {@code alf_node} collection
     */
    private long getAlfNodeCount() {
        return mongoTemplate.getCollection("alf_node").countDocuments();
    }

}