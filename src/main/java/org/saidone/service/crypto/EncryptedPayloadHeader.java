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

import lombok.Value;
import lombok.experimental.Accessors;
import lombok.val;

import org.saidone.service.crypto.AbstractCryptoService.Kdf;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** ANV v1 binary header. All integers are big-endian; the complete encoding is AEAD AAD. */
@Value
@Accessors(fluent = true)
class EncryptedPayloadHeader {
    int cipher;
    int keyVersion;
    Kdf kdf;
    byte[] salt;
    byte[] nonce;

    private static final byte[] MAGIC = {(byte) 0x89, 'A', 'N', 'V'};
    static final int MAX_HEADER_BYTES = 4096;

    byte[] encode() throws IOException {
        validate();
        val bodyBytes = new ByteArrayOutputStream();
        try (val body = new DataOutputStream(bodyBytes)) {
            body.writeByte(cipher);
            // Logical key reference 1 = the configured Vault secret; its location stays external.
            body.writeByte(1);
            body.writeInt(keyVersion);
            switch (kdf.getImpl()) {
                case "pbkdf2" -> { body.writeByte(1); body.writeInt(kdf.getPbkdf2().getIterations()); }
                case "hkdf" -> { body.writeByte(2); writeBytes(body, kdf.getHkdf().getInfo().getBytes(StandardCharsets.UTF_8)); }
                case "argon2" -> {
                    body.writeByte(3);
                    body.writeByte(0x13);
                    body.writeInt(kdf.getArgon2().getMemory());
                    body.writeInt(kdf.getArgon2().getIterations());
                    body.writeInt(kdf.getArgon2().getParallelism());
                }
                default -> throw new IOException("Unsupported KDF");
            }
            writeBytes(body, salt);
            writeBytes(body, nonce);
        }
        val bytes = new ByteArrayOutputStream();
        try (val out = new DataOutputStream(bytes)) {
            out.write(MAGIC);
            out.writeByte(1);
            val length = 7 + bodyBytes.size();
            if (length > MAX_HEADER_BYTES) throw new IOException("Header too large");
            out.writeShort(length);
            bodyBytes.writeTo(out);
        }
        return bytes.toByteArray();
    }

    static Decoded read(InputStream source) throws IOException {
        val input = new DataInputStream(source);
        if (!Arrays.equals(input.readNBytes(4), MAGIC)) throw new IOException("Unsupported encrypted payload: ANV magic missing (legacy format is not supported)");
        if (input.readUnsignedByte() != 1) throw new IOException("Unsupported ANV format version");
        val length = input.readUnsignedShort();
        if (length < 7 || length > MAX_HEADER_BYTES) throw new IOException("Invalid ANV header length");
        val bodyBytes = input.readNBytes(length - 7);
        if (bodyBytes.length != length - 7) throw new EOFException("Truncated ANV header");
        val body = new DataInputStream(new ByteArrayInputStream(bodyBytes));
        val cipher = body.readUnsignedByte();
        if (body.readUnsignedByte() != 1) throw new IOException("Unknown logical key reference");
        val keyVersion = body.readInt();
        val kdf = new Kdf();
        switch (body.readUnsignedByte()) {
            case 1 -> {
                kdf.setImpl("pbkdf2");
                val parameters = new Kdf.Pbkdf2();
                parameters.setIterations(body.readInt());
                kdf.setPbkdf2(parameters);
            }
            case 2 -> {
                kdf.setImpl("hkdf");
                val parameters = new Kdf.Hkdf();
                parameters.setInfo(new String(readBytes(body), StandardCharsets.UTF_8));
                kdf.setHkdf(parameters);
            }
            case 3 -> {
                kdf.setImpl("argon2");
                if (body.readUnsignedByte() != 0x13) throw new IOException("Unsupported Argon2 version");
                val parameters = new Kdf.Argon2();
                parameters.setMemory(body.readInt());
                parameters.setIterations(body.readInt());
                parameters.setParallelism(body.readInt());
                kdf.setArgon2(parameters);
            }
            default -> throw new IOException("Unknown KDF ID");
        }
        val header = new EncryptedPayloadHeader(cipher, keyVersion, kdf, readBytes(body), readBytes(body));
        if (body.available() != 0) throw new IOException("Unexpected ANV header fields");
        header.validate();
        // Authenticate the original bytes, never a reconstructed representation.
        val raw = new ByteArrayOutputStream();
        val out = new DataOutputStream(raw);
        out.write(MAGIC); out.writeByte(1); out.writeShort(length); out.write(bodyBytes);
        return new Decoded(header, raw.toByteArray());
    }

    private void validate() throws IOException {
        if (cipher != 1 && cipher != 2) throw new IOException("Unknown cipher ID");
        if (keyVersion <= 0) throw new IOException("Invalid key version");
        if (salt.length < 16 || salt.length > 64 || nonce.length != 12) throw new IOException("Invalid salt or nonce length");
        // Unauthenticated KDF costs must be bounded before accessing Vault or deriving a key.
        switch (kdf.getImpl()) {
            case "pbkdf2" -> {
                val iterations = kdf.getPbkdf2().getIterations();
                if (iterations < 100_000 || iterations > 10_000_000) throw new IOException("PBKDF2 iterations outside ANV v1 limits");
            }
            case "hkdf" -> {
                if (kdf.getHkdf().getInfo().getBytes(StandardCharsets.UTF_8).length > 1024) throw new IOException("HKDF info too long");
            }
            case "argon2" -> {
                val a = kdf.getArgon2();
                if (a.getMemory() < 65536 || a.getMemory() > 262144 || a.getIterations() < 3 || a.getIterations() > 10 || a.getParallelism() < 1 || a.getParallelism() > 16)
                    throw new IOException("Argon2 costs outside ANV v1 limits");
            }
            default -> throw new IOException("Unknown KDF");
        }
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeShort(bytes.length); out.write(bytes);
    }

    private static byte[] readBytes(DataInputStream input) throws IOException {
        val length = input.readUnsignedShort();
        if (length > MAX_HEADER_BYTES || length > input.available()) throw new EOFException("Invalid ANV field length");
        return input.readNBytes(length);
    }

    @Value
    @Accessors(fluent = true)
    static class Decoded {
        EncryptedPayloadHeader header;
        byte[] bytes;
    }
}
