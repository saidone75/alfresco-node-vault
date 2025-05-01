package org.saidone;

import com.mongodb.client.MongoClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.datafaker.Faker;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.NodeBodyCreate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.saidone.behaviour.EventHandler;
import org.saidone.job.NodeArchivingJob;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.service.AlfrescoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import utils.ResourceFileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Objects;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public abstract class BaseTest {

    @MockitoBean
    EventHandler eventHandler;
    @MockitoBean
    NodeArchivingJob nodeArchivingJob;

    @Autowired
    protected NodesApi nodesApi;

    @Autowired
    protected MongoClient mongoClient;

    @Value("${spring.data.mongodb.database}")
    private String database;

    protected static String parentId;
    protected static final Faker faker = new Faker();
    private static final HashMap<String, Integer> names = new HashMap<>();

    @BeforeEach
    public void before(TestInfo testInfo) {
        if (parentId == null) {
            val nodeBodyCreate = new NodeBodyCreate();
            nodeBodyCreate.setName(String.format("%s_test", faker.animal().name()));
            nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_FOLDER);
            parentId = Objects.requireNonNull(nodesApi.createNode(AlfrescoService.guestHome.getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry().getId();
        }
        log.info("Running --> {}", testInfo.getDisplayName());
    }

    @AfterAll
    public void cleanUp() {
        nodesApi.deleteNode(parentId, true);
        mongoClient.getDatabase(database).drop();
    }

    private synchronized String generateName() {
        val name = faker.animal().name();
        if (names.containsKey(name)) {
            names.put(name, names.get(name) + 1);
        } else {
            names.put(name, 1);
        }
        return String.format("%s_%d", name, names.get(name));
    }

    @SneakyThrows
    public Node createNode(File file) {
        val nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(String.format("%s.pdf", generateName()));
        nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
        val node = Objects.requireNonNull(nodesApi.createNode(parentId, nodeBodyCreate, true, null, null, null, null).getBody()).getEntry();
        nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
        return node;
    }

    @SneakyThrows
    public Node createNode() {
        val file = ResourceFileUtils.getFileFromResource("sample.pdf");
        return createNode(file);
    }

}
