package org.saidone.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.model.Node;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.IOException;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alf_node")
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class NodeWrapper {

    @Transient
    @JsonIgnore
    private static final ObjectMapper objectMapper = createObjectMapper();

    @Id
    private String id;
    private Instant archiveDate;
    private String nodeJson;

    public NodeWrapper(Node node) throws JsonProcessingException {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        this.id = node.getId();
        this.archiveDate = java.time.Instant.now();
        this.nodeJson = objectMapper.writeValueAsString(node);
    }
    
    public Node getNode() {
        if (nodeJson == null || nodeJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(nodeJson, Node.class);
        } catch (IOException e) {
            log.error("Error while deserializing node => {}", e.getMessage(), e);
            return null;
        }
    }
    
    private static ObjectMapper createObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
    
}