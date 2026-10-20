/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
 * SPDX-License-Identifier: GPL-3.0-or-later
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

import lombok.val;

import org.alfresco.core.model.Node;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.client.OzoneKeyDetails;
import org.apache.hadoop.ozone.client.io.OzoneInputStream;
import org.apache.hadoop.ozone.client.io.OzoneOutputStream;
import org.apache.hadoop.ozone.om.exceptions.OMException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.saidone.exception.NodeNotFoundOnVaultException;
import org.saidone.model.MetadataKeys;
import org.saidone.repository.EncryptedOzoneRepositoryImpl;
import org.saidone.repository.OzoneRepository;
import org.saidone.repository.OzoneRepositoryImpl;
import org.saidone.service.SecretService;
import org.saidone.service.content.OzoneContentService;
import org.saidone.service.crypto.CryptoService;
import org.saidone.service.crypto.Secret;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class OzoneRepositoryTests {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectsExactlyOneRepositoryForOzone(boolean encrypted) {
        val runner = new ApplicationContextRunner()
                .withUserConfiguration(OzoneRepositoryImpl.class, EncryptedOzoneRepositoryImpl.class)
                .withBean(OzoneBucket.class, () -> mock(OzoneBucket.class))
                .withPropertyValues("application.service.vault.storage.impl=ozone",
                        "application.service.vault.encryption.content.enabled=%s".formatted(encrypted));
        val configuredRunner = encrypted
                ? runner.withBean(SecretService.class, () -> mock(SecretService.class))
                        .withBean(CryptoService.class, () -> mock(CryptoService.class))
                : runner;
        configuredRunner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(OzoneRepository.class).size());
            assertEquals(encrypted ? EncryptedOzoneRepositoryImpl.class : OzoneRepositoryImpl.class,
                    context.getBean(OzoneRepository.class).getClass());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"s3", "gridfs"})
    void doesNotCreateOzoneRepositoriesForOtherBackends(String backend) {
        new ApplicationContextRunner()
                .withUserConfiguration(OzoneRepositoryImpl.class, EncryptedOzoneRepositoryImpl.class)
                .withPropertyValues("application.service.vault.storage.impl=%s".formatted(backend))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(OzoneRepository.class).isEmpty());
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void archivesAndReadsContentWithPlaintextHashAndLength(boolean encrypted) throws Exception {
        val bucket = mock(OzoneBucket.class);
        val details = mock(OzoneKeyDetails.class);
        val bytes = new ByteArrayOutputStream();
        val savedMetadata = new HashMap<String, String>();
        when(bucket.createKey(eq("node"), anyLong(), any(), anyMap())).thenAnswer(call -> {
            val length = call.<Long>getArgument(1);
            val metadata = call.<Map<String, String>>getArgument(3);
            savedMetadata.putAll(metadata);
            // Ciphertext in this test has one extra header byte, including for empty input.
            assertEquals(Long.parseLong(metadata.get(OzoneRepository.PLAINTEXT_LENGTH))
                    + (encrypted ? 1 : 0), length);
            return new OzoneOutputStream(bytes, null);
        });
        when(bucket.getKey("node")).thenReturn(details);
        when(details.getMetadata()).thenReturn(savedMetadata);
        when(bucket.readKey("node")).thenAnswer(call ->
                new OzoneInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        val crypto = mock(CryptoService.class);
        val secrets = mock(SecretService.class);
        val secret = Secret.builder().version(7).data(new byte[32]).build();
        when(secrets.getSecret()).thenReturn(secret);
        when(crypto.encrypt(any(), same(secret))).thenAnswer(call -> {
            val source = call.<InputStream>getArgument(0);
            val ciphertext = new ByteArrayOutputStream();
            ciphertext.write(42);
            source.transferTo(ciphertext);
            return new ByteArrayInputStream(ciphertext.toByteArray());
        });
        when(crypto.decrypt(any())).thenAnswer(call -> {
            val source = call.<InputStream>getArgument(0);
            assertEquals(42, source.read());
            return source;
        });
        val repository = encrypted ? new EncryptedOzoneRepositoryImpl(bucket, secrets, crypto)
                : new OzoneRepositoryImpl(bucket);
        val service = new OzoneContentService(repository);
        ReflectionTestUtils.setField(service, "checksumAlgorithm", "SHA-256");
        val node = mock(Node.class, RETURNS_DEEP_STUBS);
        when(node.getId()).thenReturn("node");
        when(node.getName()).thenReturn("documento-à.pdf");
        when(node.getContent().getMimeType()).thenReturn("application/pdf");
        for (val plaintext : new byte[][] {new byte[0], new byte[] {0, 1, -1, 42}}) {
            bytes.reset();
            savedMetadata.clear();
            service.archiveNodeContent(node, new ByteArrayInputStream(plaintext));
            assertEquals(Boolean.toString(encrypted), savedMetadata.get(MetadataKeys.ENCRYPTED));
            assertEquals(encrypted ? "7" : null, savedMetadata.get(MetadataKeys.KEY_VERSION));
            val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plaintext));
            assertEquals(hash, savedMetadata.get(MetadataKeys.CHECKSUM_VALUE));
            val result = service.getNodeContent("node");
            assertEquals(plaintext.length, result.getLength());
            assertEquals("documento-à.pdf", result.getFileName());
            try (val stream = result.getContentStream()) {
                assertArrayEquals(plaintext, stream.readAllBytes());
            }
            assertEquals(hash, service.computeHash("node", "SHA-256"));
            assertEquals(hash, service.getNodeContentInfo("node").getContentHash());
        }
        if (encrypted) verify(secrets, times(2)).getSecret();
        else verifyNoInteractions(crypto, secrets);
    }

    @Test
    void encryptedRepositoryCanReadExistingPlaintextContent() throws Exception {
        val bucket = mock(OzoneBucket.class);
        val details = mock(OzoneKeyDetails.class);
        when(details.getMetadata()).thenReturn(Map.of(MetadataKeys.ENCRYPTED, "false"));
        val raw = new OzoneInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(bucket.readKey("node")).thenReturn(raw);
        val crypto = mock(CryptoService.class);
        val repository = new EncryptedOzoneRepositoryImpl(bucket, mock(SecretService.class), crypto);
        try (val content = repository.getObject("node", details)) {
            assertArrayEquals(new byte[] {1, 2, 3}, content.readAllBytes());
        }
        verifyNoInteractions(crypto);
    }

    @Test
    void closesRawStreamIfDecryptionFails() throws Exception {
        val bucket = mock(OzoneBucket.class);
        val details = mock(OzoneKeyDetails.class);
        when(details.getMetadata()).thenReturn(Map.of(MetadataKeys.ENCRYPTED, "true"));
        val raw = mock(OzoneInputStream.class);
        when(bucket.readKey("node")).thenReturn(raw);
        val crypto = mock(CryptoService.class);
        val failure = new IllegalStateException("invalid ciphertext");
        when(crypto.decrypt(raw)).thenThrow(failure);
        val repository = new EncryptedOzoneRepositoryImpl(bucket, mock(SecretService.class), crypto);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> repository.getObject("node", details)));
        verify(raw).close();
    }

    @Test
    void translatesMissingKeysAndPreservesOtherStorageFailures() throws Exception {
        val bucket = mock(OzoneBucket.class);
        val repository = new OzoneRepositoryImpl(bucket);
        val missing = new OMException("missing", OMException.ResultCodes.KEY_NOT_FOUND);
        when(bucket.getKey("node")).thenThrow(missing);
        when(bucket.readKey("node")).thenThrow(missing);
        doThrow(missing).when(bucket).deleteKey("node");
        assertThrows(NodeNotFoundOnVaultException.class, () -> repository.getObjectDetails("node"));
        assertThrows(NodeNotFoundOnVaultException.class, () -> repository.getObject("node", null));
        assertDoesNotThrow(() -> repository.deleteObject("node"));
        val failure = new IOException("storage unavailable");
        doThrow(failure).when(bucket).getKey("node");
        assertSame(failure, assertThrows(IOException.class, () -> repository.getObjectDetails("node")));
    }
}
