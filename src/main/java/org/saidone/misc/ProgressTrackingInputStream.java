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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class ProgressTrackingInputStream extends FilterInputStream {

    private final String nodeId;
    private final long contentLength;
    private long bytesRead = 0;
    private int lastLoggedPercentage = 0;

    public ProgressTrackingInputStream(InputStream in, String nodeId, long contentLength) {
        super(in);
        this.nodeId = nodeId;
        this.contentLength = contentLength;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b != -1) {
            bytesRead++;
            if (log.isTraceEnabled()) logProgress();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytes = in.read(b, off, len);
        if (bytes != -1) {
            bytesRead += bytes;
            if (log.isTraceEnabled()) logProgress();
        }
        return bytes;
    }

    private void logProgress() {
        if (contentLength > 0) {
            int percentage = (int) ((double) bytesRead / contentLength * 100);
            if (bytesRead == contentLength || percentage >= lastLoggedPercentage + 10) {
                lastLoggedPercentage = percentage;
                log.trace("Download progress for node {}: {} bytes read ({}% out of {} bytes)",
                        nodeId, bytesRead, percentage, contentLength);
            }
        }
    }

}