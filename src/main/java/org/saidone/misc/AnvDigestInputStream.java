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

public class AnvDigestInputStream extends FilterInputStream {

    private final MessageDigest digest;

    public AnvDigestInputStream(InputStream inputStream, String algorithm) throws NoSuchAlgorithmException {
        super(inputStream);
        this.digest = MessageDigest.getInstance(algorithm);
    }

    @Override
    public int read() throws IOException {
        int byteRead = in.read();
        if (byteRead != -1) {
            digest.update((byte) byteRead);
        }
        return byteRead;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int bytesRead = in.read(b);
        if (bytesRead != -1) {
            digest.update(b, 0, bytesRead);
        }
        return bytesRead;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytesRead = in.read(b, off, len);
        if (bytesRead != -1) {
            digest.update(b, off, bytesRead);
        }
        return bytesRead;
    }

    public String getHash() {
        return HexFormat.of().formatHex(digest.digest());
    }

}