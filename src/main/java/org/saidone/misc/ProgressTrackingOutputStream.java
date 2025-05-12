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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@Slf4j
public class ProgressTrackingOutputStream extends FilterOutputStream {

    private final String nodeId;
    private final long contentLength;
    private long bytesWritten = 0;
    private int lastLoggedPercentage = 0;

    public ProgressTrackingOutputStream(OutputStream out, String nodeId, long contentLength) {
        super(out);
        this.nodeId = nodeId;
        this.contentLength = contentLength;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        bytesWritten++;
        if (log.isTraceEnabled()) logProgress();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        bytesWritten += len;
        if (log.isTraceEnabled()) logProgress();
    }

    private void logProgress() {
        if (contentLength > 0) {
            int percentage = (int) ((double) bytesWritten / contentLength * 100);
            if (bytesWritten == contentLength || percentage >= lastLoggedPercentage + 10) {
                lastLoggedPercentage = percentage;
                log.trace("Upload progress for node {}: {} bytes written ({}% out of {} bytes)",
                        nodeId, bytesWritten, percentage, contentLength);
            }
        }
    }

}