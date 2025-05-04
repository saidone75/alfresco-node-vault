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

import lombok.val;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;

public class InputStreamDuplicator {

    public static InputStream[] duplicate(InputStream inputStream) throws IOException {
        return duplicate(inputStream, 8192);
    }

    public static InputStream[] duplicate(InputStream inputStream, int bufferSize) throws IOException {
        val pipedIn1 = new PipedInputStream(bufferSize);
        val pipedIn2 = new PipedInputStream(bufferSize);
        val pipedOut1 = new PipedOutputStream(pipedIn1);
        val pipedOut2 = new PipedOutputStream(pipedIn2);

        CompletableFuture.runAsync(() -> {
            try (inputStream; pipedOut1; pipedOut2) {
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    pipedOut1.write(buffer, 0, bytesRead);
                    pipedOut2.write(buffer, 0, bytesRead);
                }
                pipedOut1.flush();
                pipedOut2.flush();
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });

        return new InputStream[]{pipedIn1, pipedIn2};
    }

}