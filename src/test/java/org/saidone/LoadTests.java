package org.saidone;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.saidone.service.VaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class LoadTests extends BaseTest {

    @Autowired
    VaultService vaultService;

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

}