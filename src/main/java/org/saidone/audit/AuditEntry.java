package org.saidone.audit;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "vault_audit")
public class AuditEntry {
    @Id
    private String id;
    private Instant timestamp;
    private Map<String, Object> metadata;
    private String type;
}
