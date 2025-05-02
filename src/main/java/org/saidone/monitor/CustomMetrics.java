package org.saidone.monitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;
    private final MongoTemplate mongoTemplate;

    public CustomMetrics(MeterRegistry meterRegistry, MongoTemplate mongoTemplate) {
        this.meterRegistry = meterRegistry;
        this.mongoTemplate = mongoTemplate;
        Gauge.builder("nodes_on_vault", this, CustomMetrics::getAlfNodeCount)
                .description("Nodes on vault")
                .tags(Tags.of("vault", "nodes"))
                .register(meterRegistry);
    }

    private long getAlfNodeCount() {
        return mongoTemplate.getCollection("alf_node").countDocuments();
    }

}