package org.saidone;

import com.github.javafaker.Faker;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.NodeBodyCreate;
import org.junit.jupiter.api.Test;
import org.saidone.behaviour.EventHandler;
import org.saidone.job.NodeArchivingJob;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.model.alfresco.AnvContentModel;
import org.saidone.service.AlfrescoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import utils.ResourceFileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

@SpringBootTest
@Slf4j
class AlfrescoNodeVaultApplicationTests {


    @MockitoBean
    EventHandler eventHandler;
    @MockitoBean
    NodeArchivingJob nodeArchivingJob;

    @Autowired
    AlfrescoService alfrescoService;

    @Autowired
    NodesApi nodesApi;

    private final static Faker faker = new Faker(Locale.ENGLISH);

    @Test
    @SneakyThrows
    void archiveNodeTest() {
        var file = ResourceFileUtils.getFileFromResource("C:/Users/mmarini/Downloads/Software/apache-maven-3.9.9-bin.zip");
        var nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(String.format("%s.pdf", faker.animal().name()));
        nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
        var node = Objects.requireNonNull(nodesApi.createNode(AlfrescoService.guestHome.getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry();
        nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
        alfrescoService.addAspects(node.getId(), List.of(AnvContentModel.ASP_ARCHIVE));
    }

    @Test
    @SneakyThrows
    void



    createNodesTest() {
        IntStream.range(0, 100).parallel().forEach(i -> {
            var file = (File) null;
            try {
                file = ResourceFileUtils.getFileFromResource("C:/Users/mmarini/Downloads/Software/apache-maven-3.9.9-bin.zip");
                var nodeBodyCreate = new NodeBodyCreate();
                nodeBodyCreate.setName(String.format("%s-%s-%s.pdf", UUID.randomUUID().toString().substring(0, 4), faker.animal().name(), UUID.randomUUID().toString().substring(0, 8)));
                nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
                nodeBodyCreate.setAspectNames(List.of(AnvContentModel.ASP_ARCHIVE));
                var node = Objects.requireNonNull(nodesApi.createNode(AlfrescoService.guestHome.getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry();
                nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}