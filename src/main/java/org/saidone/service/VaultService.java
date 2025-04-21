package org.saidone.service;

import lombok.Cleanup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.saidone.component.BaseComponent;
import org.saidone.exception.ArchiveNodeException;
import org.saidone.model.NodeWrapper;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService extends BaseComponent {

    private final AlfrescoService alfrescoService;
    private final MongoNodeRepository mongoNodeRepository;
    private final GridFsRepositoryImpl gridFsRepository;

    public void archiveNode(String nodeId) {
        log.debug("Archiving node: {}", nodeId);
        try {
            var node = alfrescoService.getNode(nodeId);
            mongoNodeRepository.save(new NodeWrapper(node));
            @Cleanup var is = alfrescoService.getNodeContent(nodeId);
            gridFsRepository.saveFile(nodeId, is, node.getName(), node.getContent().getMimeType());
            alfrescoService.deleteNode(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            // rollback
            log.debug("Rollback required for node: {}", nodeId);
            mongoNodeRepository.deleteById(nodeId);
            gridFsRepository.deleteFileById(nodeId);
            throw new ArchiveNodeException(String.format("Error archiving node %s: %s", nodeId, e.getMessage()));
        }
    }

}