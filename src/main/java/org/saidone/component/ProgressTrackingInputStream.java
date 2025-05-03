package org.saidone.component;

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
        int b = super.read();
        if (b != -1) {
            bytesRead++;
            logProgress();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int bytes = super.read(b, off, len);
        if (bytes != -1) {
            bytesRead += bytes;
            logProgress();
        }
        return bytes;
    }

    private void logProgress() {
        if (contentLength > 0) {
            int percentage = (int) ((double) bytesRead / contentLength * 100);
            if (bytesRead == contentLength || percentage >= lastLoggedPercentage + 10) {
                lastLoggedPercentage = percentage;
                log.info("Download progress for node {}: {} bytes read ({}% out of {} bytes)",
                        nodeId, bytesRead, percentage, contentLength);
            }
        }
    }

}