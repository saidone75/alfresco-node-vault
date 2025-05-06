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

package org.saidone.repository;

import lombok.val;
import org.saidone.model.NodeWrapper;
import org.saidone.service.CryptoService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "application.service.vault.encryption.metadata", havingValue = "true")
public class EncryptedMongoNodeRepositoryImpl extends MongoNodeRepositoryImpl {

    private final CryptoService cryptoService;

    public EncryptedMongoNodeRepositoryImpl(
            @Qualifier("mongoNodeRepository") MongoRepository<NodeWrapper, String> delegate,
            CryptoService cryptoService
    ) {
        super(delegate);
        this.cryptoService = cryptoService;
    }

    @Override
    public <S extends NodeWrapper> S save(S entity) {
        encryptNode(entity);
        return super.save(entity);
    }

    @Override
    public Optional<NodeWrapper> findById(String s) {
        val result = super.findById(s);
        result.ifPresent(this::decryptNode);
        return result;
    }

    private <S extends NodeWrapper> void encryptNode(S node) {
        if (node != null && node.getNodeJson() != null) {
            node.setNodeJson(cryptoService.encryptText(node.getNodeJson()));
            node.setEncrypted(true);
        }
    }

    private <S extends NodeWrapper> void decryptNode(S node) {
        if (node != null && node.getNodeJson() != null && node.isEncrypted()) {
            node.setNodeJson(cryptoService.decryptText(node.getNodeJson()));
        }
    }

}