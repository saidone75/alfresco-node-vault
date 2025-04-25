package org.saidone.service;

import com.mongodb.client.MongoClient;
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
    private final MongoClient mongoClient;

    @Value("${application.service.vault.hash-algorithm}")
    private String checksumAlgorithm;

    @Value("${application.service.vault.double-check-algorithm}")
    private String doubleCheckAlgorithm;

    public void archiveNode(String nodeId) {
        log.info("Archiving node => {}", nodeId);
        try {
            var node = alfrescoService.getNode(nodeId);
            var file = alfrescoService.getNodeContent(nodeId);
            mongoNodeRepository.save(new NodeWrapper(node));
            try (var is = new FileInputStream(file)) {
                var metadata = new HashMap<String, String>() {{
                    put(MetadataKeys.UUID, nodeId);
                }};
                if (Strings.isNotBlank(checksumAlgorithm)) {
                    metadata.put(MetadataKeys.CHECKSUM_ALGORITHM, checksumAlgorithm);
                    metadata.put(MetadataKeys.CHECKSUM_VALUE, getDigest(file, checksumAlgorithm));
                }
                gridFsRepository.saveFile(is, node.getName(), node.getContent().getMimeType(), metadata);
            }
            Files.deleteIfExists(file.toPath());
            if (Strings.isNotBlank(doubleCheckAlgorithm)) doubleCheck(nodeId);
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

    public void doubleCheck(String nodeId) {
        log.debug("Comparing {} digest for node => {}", doubleCheckAlgorithm, nodeId);
        File file = null;
        String alfrescoDigest;
        String mongoDigest;
        try {
            file = alfrescoService.getNodeContent(nodeId);
            alfrescoDigest = getDigest(file, doubleCheckAlgorithm);
            mongoDigest = gridFsRepository.calculateMd5(nodeId);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ArchiveNodeException("Cannot compute hashes");
        } finally {
            try {
                if (file != null) Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
        if (alfrescoDigest.equals(mongoDigest)) {
            log.debug("Digest check passed for node => {}", nodeId);
        } else {
            throw new ArchiveNodeException("Hashes mismatch");
        }
    }

}