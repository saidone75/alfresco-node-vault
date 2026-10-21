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

package org.saidone.service.crypto;

import lombok.val;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.saidone.config.EncryptionConfig;
import org.saidone.service.SecretService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

/** Writes ANV v1 payloads and selects decryption parameters exclusively from their headers. */
@Service
@ConditionalOnExpression("('${application.service.vault.encryption.content.enabled:false}' == 'true' or '${application.service.vault.encryption.metadata.enabled:false}' == 'true')")
public class AnvCryptoService extends AbstractCryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();
    private final SecretService secrets;
    private final EncryptionConfig config;

    public AnvCryptoService(SecretService secrets, EncryptionConfig config) {
        this.secrets = secrets;
        this.config = config;
    }

    @Override
    public InputStream encrypt(InputStream input, Secret secret) {
        try {
            val cipherId = switch (config.getImpl()) {
                case "jca" -> 1;
                case "bc" -> 2;
                default -> throw new IllegalArgumentException("Unknown encryption implementation");
            };
            val saltLength = cipherId == 1 ? config.getJca().getSaltLength() : config.getBc().getSaltLength();
            val nonceLength = cipherId == 1 ? config.getJca().getIvLength() : config.getBc().getNonceLength();
            if (saltLength < 16 || saltLength > 64 || nonceLength != 12) throw new IOException("ANV v1 requires a 16..64 byte salt and a 12 byte nonce");
            val salt = new byte[saltLength];
            val nonce = new byte[nonceLength];
            RANDOM.nextBytes(salt); RANDOM.nextBytes(nonce);
            val header = new EncryptedPayloadHeader(cipherId, secret.getVersion(), config.getKdf(), salt, nonce);
            val encoded = header.encode();
            val cipher = initialize(Cipher.ENCRYPT_MODE, header, secret, encoded);
            return new SequenceInputStream(new ByteArrayInputStream(encoded), new TransformStream(input, cipher));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt ANV payload", e);
        }
    }

    @Override
    public InputStream decrypt(InputStream input) {
        try {
            val decoded = EncryptedPayloadHeader.read(input);
            val secret = secrets.getSecret(decoded.header().keyVersion());
            val cipher = initialize(Cipher.DECRYPT_MODE, decoded.header(), secret, decoded.bytes());
            return new TransformStream(input, cipher);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            try { input.close(); } catch (IOException closing) { e.addSuppressed(closing); }
            throw new IllegalStateException("Unable to decrypt ANV payload", e);
        }
    }

    private Cipher initialize(int mode, EncryptedPayloadHeader h, Secret secret, byte[] aad) throws GeneralSecurityException {
        if (secret == null || secret.getVersion() != h.keyVersion()) throw new IllegalArgumentException("Required Vault secret version is unavailable");
        val algorithm = h.cipher() == 1 ? "AES" : "ChaCha20";
        val key = deriveSecretKey(secret, algorithm, h.salt(), h.kdf()).getLeft();
        // BC supports incremental processing for both algorithms without buffering a whole GCM document
        val cipher = Cipher.getInstance(h.cipher() == 1 ? "AES/GCM/NoPadding" : "ChaCha20-Poly1305", PROVIDER);
        if (h.cipher() == 1) cipher.init(mode, key, new GCMParameterSpec(128, h.nonce()));
        else cipher.init(mode, key, new IvParameterSpec(h.nonce()));
        cipher.updateAAD(aad);
        return cipher;
    }

    /** Propagates authentication failures from doFinal; successful authentication requires reading to EOF. */
    private static final class TransformStream extends InputStream {
        private final InputStream source;
        private final Cipher cipher;
        private final byte[] input = new byte[8192];
        private final byte[] output;
        private final byte[] singleByte = new byte[1];
        private int outputLength;
        private int position;
        private boolean finished;
        private boolean closed;
        private IOException failure;

        TransformStream(InputStream source, Cipher cipher) {
            this.source = source;
            this.cipher = cipher;
            // allow for a pending block/tag as well as the next input chunk.
            output = new byte[Math.max(input.length, cipher.getOutputSize(input.length)) + Math.max(16, cipher.getBlockSize())];
        }

        @Override public int read() throws IOException {
            return read(singleByte, 0, 1) < 0 ? -1 : singleByte[0] & 0xff;
        }

        @Override public int read(byte[] target, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, target.length);
            if (closed) throw new IOException("Stream closed");
            if (failure != null) throw failure;
            if (length == 0) return 0;
            while (position == outputLength) {
                if (finished) return -1;
                val count = source.read(input);
                if (count == 0) continue;
                try {
                    if (count < 0) { finished = true; outputLength = cipher.doFinal(output, 0); }
                    else outputLength = cipher.update(input, 0, count, output, 0);
                    position = 0;
                } catch (GeneralSecurityException e) {
                    failure = new IOException("ANV payload authentication failed or payload is truncated", e);
                    throw failure;
                }
            }
            val count = Math.min(length, outputLength - position);
            System.arraycopy(output, position, target, offset, count);
            position += count;
            return count;
        }

        @Override public void close() throws IOException {
            if (!closed) { closed = true; source.close(); }
        }
    }
}
