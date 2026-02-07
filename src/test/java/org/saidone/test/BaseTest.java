/*
 * Alfresco Node Vault - archive today, accelerate tomorrow
 * Copyright (C) 2025-2026 Saidone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.saidone.test;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.datafaker.Faker;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.NodeBodyCreate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.saidone.behaviour.EventHandler;
import org.saidone.job.NodeArchivingJob;
import org.saidone.model.alfresco.AlfrescoContentModel;
import org.saidone.service.AlfrescoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.saidone.utils.ResourceFileUtils;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Objects;

import org.apache.commons.io.FileUtils;

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
    protected AlfrescoService alfrescoService;

    @Autowired
    protected NodesApi nodesApi;

    protected static String parentId;
    protected static final Faker faker = new Faker();
    private static final HashMap<String, Integer> names = new HashMap<>();
    private static final HashMap<String, File> downloadedFiles = new HashMap<>();

    @BeforeEach
    public void before(TestInfo testInfo) {
        if (parentId == null) {
            val nodeBodyCreate = new NodeBodyCreate();
            nodeBodyCreate.setName(String.format("%s_test", faker.animal().name()));
            nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_FOLDER);
            parentId = Objects.requireNonNull(nodesApi.createNode(alfrescoService.getGuestHome().getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry().getId();
            TestCleanupService.setParentId(parentId);
        }
        log.info("Running --> {}", testInfo.getDisplayName());
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
        nodeBodyCreate.setName(String.format("%s.%s", generateName(), file.getName().replaceAll("^.*\\.(.*)$", "$1")));
        nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
        val node = Objects.requireNonNull(nodesApi.createNode(parentId, nodeBodyCreate, true, null, null, null, null).getBody()).getEntry();
        nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
        log.debug("Node {} created with name: {}", node.getId(), node.getName());
        return node;
    }

    @SneakyThrows
    public Node createNode() {
        val file = ResourceFileUtils.getFileFromResource("sample.pdf");
        return createNode(file);
    }

    @SneakyThrows
    public Node createNode(URL url) {
        if (!downloadedFiles.containsKey(url.toString())) {
            val extension = url.getPath().replaceAll("^.*\\.(.*)$", "$1");
            val tmpFile = File.createTempFile("anv-", String.format(".%s", extension));
            tmpFile.deleteOnExit();
            FileUtils.copyURLToFile(url, tmpFile);
            downloadedFiles.put(url.toString(), tmpFile);
        }
        return createNode(downloadedFiles.get(url.toString()));
    }

}
