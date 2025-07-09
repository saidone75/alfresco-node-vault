package org.saidone.test;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.saidone.job.DocumentNotarizationJob;
import org.saidone.model.NodeWrapper;
import org.saidone.service.EthereumService;
import org.saidone.service.NodeService;
import org.saidone.service.content.ContentService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

class DocumentNotarizationJobTests {

    @Test
    void notarizeCallsStoreHash() {
        NodeService nodeService = mock(NodeService.class);
        ContentService contentService = mock(ContentService.class);
        EthereumService ethereumService = mock(EthereumService.class);

        NodeWrapper node = new NodeWrapper();
        node.setId("1");
        when(nodeService.findByTxId(null)).thenReturn(List.of(node));
        when(contentService.computeHash("1", "SHA-256")).thenReturn("abc");
        when(ethereumService.storeHash("1", "abc")).thenReturn("txid");

        DocumentNotarizationJob job = new DocumentNotarizationJob(nodeService, contentService, ethereumService);
        ReflectionTestUtils.setField(job, "algorithm", "SHA-256");

        job.notarize();

        verify(ethereumService).storeHash("1", "abc");
        verify(nodeService).save(argThat(n -> "txid".equals(n.getNotarizationTxId())));
    }
}
