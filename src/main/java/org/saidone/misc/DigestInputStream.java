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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
public class DigestInputStream extends InputStream {

    private final InputStream inputStream;
    private final MessageDigest digest;

    public DigestInputStream(InputStream inputStream, String algorithm) throws NoSuchAlgorithmException {
        this.inputStream = inputStream;
        this.digest = MessageDigest.getInstance(algorithm);
    }

    @Override
    public int read() {
        try {
            int byteRead = inputStream.read();
            if (byteRead != -1) {
                digest.update((byte) byteRead);
            }
            return byteRead;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) {
        try {
            int bytesRead = inputStream.read(b, off, len);
            if (bytesRead != -1) {
                digest.update(b, off, bytesRead);
            }
            return bytesRead;
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public void close() {
        try {
            inputStream.close();
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    public String getHash() {
        return HexFormat.of().formatHex(digest.digest());
    }

}