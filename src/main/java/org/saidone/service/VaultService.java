package org.saidone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.saidone.component.BaseComponent;
import org.saidone.exception.ArchiveNodeException;
import org.saidone.model.MetadataKeys;
import org.saidone.model.NodeWrapper;
import org.saidone.repository.GridFsRepositoryImpl;
import org.saidone.repository.MongoNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService extends BaseComponent {

    private final AlfrescoService alfrescoService;
    private final MongoNodeRepository mongoNodeRepository;
    private final GridFsRepositoryImpl gridFsRepository;

    @Value("${application.service.vault.hash}")
    private String hash;

    public void archiveNode(String nodeId) {
        log.debug("Archiving node => {}", nodeId);
        try {
            var node = alfrescoService.getNode(nodeId);
            mongoNodeRepository.save(new NodeWrapper(node));
            var file = alfrescoService.getNodeContent(nodeId);
            try (var is = new FileInputStream(file)) {
                var metadata = new HashMap<String, String>() {{
                    put(MetadataKeys.UUID, nodeId);
                }};
                if (Strings.isNotBlank(hash)) {
                    metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, hash);
                    metadata.put(MetadataKeys.CHECKSUM_VALUE, getDigest(file, hash));
                }
                gridFsRepository.saveFile(is, node.getName(), node.getContent().getMimeType(), metadata);
            }
            Files.deleteIfExists(file.toPath());
            alfrescoService.deleteNode(nodeId);
        } catch (Exception e) {
            log.trace(e.getMessage(), e);
            // rollback
            log.debug("Rollback required for node => {}", nodeId);
            mongoNodeRepository.deleteById(nodeId);
            gridFsRepository.deleteFileById(nodeId);
            throw new ArchiveNodeException(String.format("Error archiving node %s => %s", nodeId, e.getMessage()));
        }
    }

    public static String getDigest(File file, String hash) throws IOException, NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance(hash);
        try (var fis = new FileInputStream(file)) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        var sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}