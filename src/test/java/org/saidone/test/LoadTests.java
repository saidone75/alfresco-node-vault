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
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class LoadTests extends BaseTest {

    @Autowired
    VaultService vaultService;
    @Autowired
    private AlfrescoService alfrescoService;

    private int getChildrenCount() {
        return Objects.requireNonNull(nodesApi.listNodeChildren(parentId, 0, Integer.MAX_VALUE, null, null, null, null, null, null).getBody()).getList().getEntries().size();
    }

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
        val url = (URI.create("https://freetestdata.com/wp-content/uploads/2021/09/Free_Test_Data_100KB_PDF.pdf").toURL());
        val batchSize = new AtomicInteger(0);

        @Cleanup var executor = Executors.newFixedThreadPool(4);

        val paused = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                while (true) {
                    if (!paused.get()) {
                        val nodeId = createNode(url).getId();
                        log.debug("Node created: {}", nodeId);
                        alfrescoService.addAspects(nodeId, List.of(AnvContentModel.ASP_ARCHIVE));
                        batchSize.incrementAndGet();
                    }
                    if (batchSize.get() >= 1000) {
                        val childrenCount = getChildrenCount();
                        batchSize.set(childrenCount);
                        if (childrenCount > 1000) {
                            log.debug("Children count: {} - Pausing for 10 seconds...", childrenCount);
                            paused.set(true);
                            TimeUnit.SECONDS.sleep(10);
                        }
                    } else {
                        paused.set(false);
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        for (int i = 0; i < 8; i++) {
            executor.submit(task);
        }
    }

}