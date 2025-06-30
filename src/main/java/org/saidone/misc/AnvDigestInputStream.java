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

package org.saidone.misc;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@code AnvDigestInputStream} wraps another {@link InputStream} and computes a
 * message digest while the data is read.
 * <p>
 * The digest algorithm is provided at construction time and each read operation
 * updates the underlying {@link MessageDigest}. When the stream has been
 * consumed the final hash value can be obtained via {@link #getHash()}.
 * This utility is typically used when a checksum or fingerprint of the streamed
 * content is required without buffering the entire input in memory.
 */
public class AnvDigestInputStream extends FilterInputStream {

    private final MessageDigest digest;

    /**
     * Creates a new digesting stream using the supplied algorithm.
     *
     * @param inputStream the underlying stream to read
     * @param algorithm   name of the {@link MessageDigest} algorithm
     * @throws NoSuchAlgorithmException if the algorithm is not available
     */
    public AnvDigestInputStream(InputStream inputStream, String algorithm) throws NoSuchAlgorithmException {
        super(inputStream);
        this.digest = MessageDigest.getInstance(algorithm);
    }

    /**
     * Creates a digesting stream that performs no hashing. This constructor is
     * mainly useful when the same type is required but hashing is disabled.
     *
     * @param inputStream the underlying stream
     * @throws NoSuchAlgorithmException if the algorithm is not available
     */
    public AnvDigestInputStream(InputStream inputStream) throws NoSuchAlgorithmException {
        super(inputStream);
        digest = null;
    }

    /** {@inheritDoc} */
    @Override
    public int read() throws IOException {
        int byteRead = in.read();
        if (byteRead != -1) {
            if (digest != null) digest.update((byte) byteRead);
        }
        return byteRead;
    }

    /** {@inheritDoc} */
    @Override
    public int read(byte[] b) throws IOException {
        int bytesRead = in.read(b);
        if (bytesRead != -1) {
            if (digest != null) digest.update(b, 0, bytesRead);
        }
        return bytesRead;
    }

    /** {@inheritDoc} */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = in.read(b, off, len);
        if (bytesRead != -1) {
            if (digest != null) digest.update(b, off, bytesRead);
        }
        return bytesRead;
    }

    /**
     * Returns the computed hash as a lowercase hexadecimal string. This method
     * should be called once the stream has been fully consumed. If hashing was
     * disabled via {@link #AnvDigestInputStream(InputStream)}, this method
     * returns {@code null}.
     *
     * @return the digest of the read bytes or {@code null} if hashing is disabled
     */
    public String getHash() {
        return digest != null ? HexFormat.of().formatHex(digest.digest()) : null;
    }

}