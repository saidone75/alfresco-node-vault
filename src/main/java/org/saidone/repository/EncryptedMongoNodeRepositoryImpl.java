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

import lombok.NonNull;
import lombok.val;
import org.saidone.model.NodeWrapper;
import org.saidone.service.crypto.CryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository implementation that encrypts node data before saving to MongoDB
 * and decrypts it when retrieving. This implementation is active only when
 * vault encryption and metadata encryption are enabled in the application properties.
 */
@Repository
@ConditionalOnProperty(name = {"application.service.vault.encryption.enabled", "application.service.vault.encryption.metadata"},
        havingValue = "true")
public class EncryptedMongoNodeRepositoryImpl extends MongoNodeRepositoryImpl {

    private final CryptoService cryptoService;

    /**
     * Constructs an EncryptedMongoNodeRepositoryImpl with the given MongoOperations and CryptoService.
     *
     * @param mongoOperations the MongoDB operations instance
     * @param cryptoService   the service used for encryption and decryption
     */
    public EncryptedMongoNodeRepositoryImpl(
            MongoOperations mongoOperations,
            CryptoService cryptoService
    ) {
        super(mongoOperations);
        this.cryptoService = cryptoService;
    }

    /**
     * Saves the given node entity after encrypting its JSON content.
     *
     * @param entity the node entity to save
     * @param <S>    the type of the node entity
     * @return the saved node entity
     */
    @Override
    public <S extends NodeWrapper> @NonNull S save(@NonNull S entity) {
        encryptNode(entity);
        return super.save(entity);
    }

    /**
     * Finds a node by its ID and decrypts its JSON content if it is encrypted.
     *
     * @param s the ID of the node to find
     * @return an Optional containing the found node, or empty if not found
     */
    @Override
    public @NonNull Optional<NodeWrapper> findById(@NonNull String s) {
        val result = super.findById(s);
        result.ifPresent(this::decryptNode);
        return result;
    }

    /**
     * Encrypts the JSON content of the given node and marks it as encrypted.
     *
     * @param node the node to encrypt
     * @param <S>  the type of the node
     */
    private <S extends NodeWrapper> void encryptNode(S node) {
        if (node != null && node.getNodeJson() != null) {
            node.setNodeJson(cryptoService.encryptText(node.getNodeJson()));
            node.setEncrypted(true);
        }
    }

    /**
     * Decrypts the JSON content of the given node if it is marked as encrypted.
     *
     * @param node the node to decrypt
     * @param <S>  the type of the node
     */
    private <S extends NodeWrapper> void decryptNode(S node) {
        if (node != null && node.getNodeJson() != null && node.isEncrypted()) {
            node.setNodeJson(cryptoService.decryptText(node.getNodeJson()));
        }
    }

}