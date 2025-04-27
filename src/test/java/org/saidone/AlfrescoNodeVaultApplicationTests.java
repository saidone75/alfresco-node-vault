/*
 *  Alfresco Node Vault - archive today, accelerate tomorrow
 *  Copyright (C) 2025 Saidone
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
        var file = ResourceFileUtils.getFileFromResource("sample.pdf");
        var nodeBodyCreate = new NodeBodyCreate();
        nodeBodyCreate.setName(String.format("%s.pdf", faker.animal().name()));
        nodeBodyCreate.setNodeType(AlfrescoContentModel.TYPE_CONTENT);
        var node = Objects.requireNonNull(nodesApi.createNode(AlfrescoService.guestHome.getId(), nodeBodyCreate, null, null, null, null, null).getBody()).getEntry();
        nodesApi.updateNodeContent(node.getId(), Files.readAllBytes(file.toPath()), null, null, null, null, null);
        alfrescoService.addAspects(node.getId(), List.of(AnvContentModel.ASP_ARCHIVE));
    }

    @Test
    @SneakyThrows
    void createNodesTest() {
        IntStream.range(0, 1000).parallel().forEach(i -> {
            var file = (File) null;
            try {
                file = ResourceFileUtils.getFileFromResource("sample.pdf");
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