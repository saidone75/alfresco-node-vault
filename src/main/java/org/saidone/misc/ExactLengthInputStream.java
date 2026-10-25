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

package org.saidone.misc;

import lombok.val;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Limits decoded output to its recorded original size and rejects truncated content at EOF. */
public final class ExactLengthInputStream extends InputStream {
    private final InputStream source;
    private final long expected;
    private long count;
    private final byte[] single = new byte[1];
    private IOException failure;

    public ExactLengthInputStream(InputStream source, long expected) {
        if (expected < 0) throw new IllegalArgumentException("Negative original content length");
        this.source = source;
        this.expected = expected;
    }

    @Override
    public int read() throws IOException {
        return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);
        if (failure != null) throw failure;
        if (length == 0) return 0;
        // Read one extra byte only to detect an overrun, never exposing it to the caller.
        val remaining = expected - count;
        val allowed = remaining >= length ? length : (int) remaining + 1;
        val read = source.read(target, offset, allowed);
        if ((read < 0 && count != expected) || (read > 0 && read > expected - count)) {
            failure = new IOException("Decoded content length differs from recorded original length");
            throw failure;
        }
        if (read > 0) count += read;
        return read;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
