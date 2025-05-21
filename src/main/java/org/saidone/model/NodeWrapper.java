/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.model.Node;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

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
    @BsonProperty("arcDt")
    private Instant archiveDate;
    @BsonProperty("res")
    private boolean restored;
    @BsonProperty("enc")
    private boolean encrypted;
    @BsonProperty("node")
    private String nodeJson;

    public NodeWrapper(Node node) throws IllegalArgumentException, JsonProcessingException {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        this.id = node.getId();
        this.archiveDate = java.time.Instant.now();
        this.nodeJson = objectMapper.writeValueAsString(node);
    }

    @SneakyThrows
    public Node getNode() {
        try {
            return objectMapper.readValue(nodeJson, Node.class);
        } catch (Exception e) {
            log.error("Error while deserializing node: {}", e.getMessage());
            log.trace(e.getMessage(), e);
            throw e;
        }
    }

    private static ObjectMapper createObjectMapper() {
        val mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

}