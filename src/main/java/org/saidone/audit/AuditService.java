package org.saidone.audit;

import lombok.RequiredArgsConstructor;
import org.saidone.component.BaseComponent;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService extends BaseComponent {

    private final MongoTemplate mongoTemplate;

    public void saveEntry(Map<String, Object> metadata, String type) {
        AuditEntry entry = new AuditEntry();
        entry.setTimestamp(Instant.now());
        entry.setMetadata(metadata);
        entry.setType(type);
        mongoTemplate.insert(entry);
    }
}
