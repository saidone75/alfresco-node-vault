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

package org.saidone.component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;


@Profile("atlas")
@Component
@Slf4j
public class AlfNodeCollectionInitializer {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void createAlfNodeCollectionIfNotExists() {
        var db = mongoTemplate.getDb();

        boolean exists = db.listCollectionNames()
                .into(new java.util.ArrayList<>())
                .contains("alf_node");

        if (exists) {
            log.debug("Collection 'alf_node' already exists.");
            return;
        }

        val validator = new Document("$jsonSchema", new Document()
                .append("bsonType", "object")
                .append("required", List.of("_id", "arcDt", "res", "enc", "node"))
                .append("properties", new Document()
                        .append("_id", new Document("bsonType", "string").append("immutable", true))
                        .append("arcDt", new Document("bsonType", "date").append("immutable", true))
                        .append("res", new Document("bsonType", "bool"))
                        .append("enc", new Document("bsonType", "bool").append("immutable", true))
                        .append("node", new Document("bsonType", "string").append("immutable", true))
                )
        );

        db.runCommand(new Document("create", "alf_node")
                .append("validator", validator)
                .append("validationLevel", "strict")
                .append("validationAction", "error")
        );

        log.debug("Collection 'alf_node' created with immutability constraints.");
    }

}