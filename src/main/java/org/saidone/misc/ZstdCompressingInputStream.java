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

import com.github.luben.zstd.ZstdOutputStreamNoFinalizer;
import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Pull-based Zstandard compression with bounded buffers and no worker thread or staging file. */
public final class ZstdCompressingInputStream extends InputStream {
    private final InputStream source;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private final ZstdOutputStreamNoFinalizer compressor;
    private final byte[] input = new byte[8192];
    private final byte[] single = new byte[1];
    private byte[] output = new byte[0];
    private int position;
    private boolean finished;
    private boolean closed;
    private IOException failure;

    public ZstdCompressingInputStream(InputStream source, int level) throws IOException {
        this.source = source;
        compressor = new ZstdOutputStreamNoFinalizer(pending, level);
        try {
            compressor.setWindowLog(23).setChecksum(true);
        } catch (IOException | RuntimeException e) {
            try { compressor.close(); } catch (IOException closing) { e.addSuppressed(closing); }
            throw e;
        }
    }

    @Override
    public int read() throws IOException {
        return read(single, 0, 1) < 0 ? -1 : single[0] & 0xff;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, target.length);
        if (closed) throw new IOException("Stream closed");
        if (failure != null) throw failure;
        if (length == 0) return 0;
        try {
            while (position == output.length) {
                if (finished) return -1;
                val count = source.read(input);
                if (count < 0) {
                    compressor.close();
                    finished = true;
                } else if (count > 0) {
                    compressor.write(input, 0, count);
                }
                if (pending.size() > 0) {
                    output = pending.toByteArray();
                    pending.reset();
                    position = 0;
                }
            }
            val count = Math.min(length, output.length - position);
            System.arraycopy(output, position, target, offset, count);
            position += count;
            return count;
        } catch (IOException e) {
            failure = e;
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try (source) {
                compressor.close();
            }
        }
    }
}
