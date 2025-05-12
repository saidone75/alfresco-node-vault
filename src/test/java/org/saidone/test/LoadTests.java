package org.saidone.test;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.saidone.model.alfresco.AnvContentModel;
import org.saidone.service.AlfrescoService;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class LoadTests extends BaseTest {

    @Autowired
    VaultService vaultService;
    @Autowired
    AlfrescoService alfrescoService;
    @Autowired
    MongoTemplate mongoTemplate;

    @Test
    @Tag("load")
    @SneakyThrows
    void archiveNodesTest() {
        val startTime = System.currentTimeMillis();
        val url = (URI.create("https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_100KB_PDF.pdf").toURL());
        IntStream.range(0, 1000).parallel().forEach(i -> {
            try {
                val nodeId = createNode(url).getId();
                // save node on the vault
                assertDoesNotThrow(() -> vaultService.archiveNode(nodeId));
                // check if node is on the vault
                assertDoesNotThrow(() -> vaultService.getNode(nodeId));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        val totalTime = System.currentTimeMillis() - startTime;
        log.info("Total execution time: {} ms", totalTime);
    }

    @Test
    @Tag("massive")
    @SneakyThrows
    void massiveLoadTest() {
        val url = URI.create("https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_100KB_PDF.pdf").toURL();
        @Cleanup val executor = Executors.newFixedThreadPool(8);
        val running = new AtomicBoolean(true);
        Runnable task = () -> {
            while (running.get()) {
                val nodeId = createNode(url).getId();
                alfrescoService.addAspects(nodeId, List.of(AnvContentModel.ASP_ARCHIVE));
            }
        };

        for (int i = 0; i < 8; i++) {
            executor.submit(task);
        }

        TimeUnit.HOURS.sleep(1);
        running.set(false);
        TimeUnit.MINUTES.sleep(2);
        val archivedNodesCount = mongoTemplate.getCollection("alf_node").countDocuments();
        log.info("Nodes archived: {}", archivedNodesCount);
    }

}