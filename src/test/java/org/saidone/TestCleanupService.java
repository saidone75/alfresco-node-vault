package org.saidone;

import jakarta.annotation.PreDestroy;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.mongodb.client.MongoClient;
import org.alfresco.core.handler.NodesApi;
import lombok.RequiredArgsConstructor;

//@Component
@RequiredArgsConstructor
public class TestCleanupService {

    private final NodesApi nodesApi;
    private final MongoClient mongoClient;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Setter
    private static String parentId;

    @PreDestroy
    public void cleanUp() {
        if (parentId != null) {
            nodesApi.deleteNode(parentId, true);
            mongoClient.getDatabase(database).drop();
        }
    }

}