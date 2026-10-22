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

package org.saidone.component;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.saidone.utils.SecureTemporaryFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prepares the directory holding the temporary files of the application.
 * <p>
 * At startup the directory is created and emptied: no upload is in progress
 * yet, so anything found there was left behind by a process that died before
 * removing its temporary files. The directory must therefore belong to a
 * single running instance.
 * </p>
 */
@Component
@Slf4j
public class TempDirInitializer extends BaseComponent {

    @Value("${application.temp-dir}")
    private Path directory;

    @Override
    public void init() {
        super.init();
        try {
            Files.createDirectories(directory);
            try (val entries = Files.list(directory)) {
                val leftovers = entries.toList();
                for (val leftover : leftovers) {
                    FileUtils.forceDelete(leftover.toFile());
                }
                if (!leftovers.isEmpty()) {
                    log.warn("Removed {} leftover temporary entries from {}", leftovers.size(), directory);
                }
            }
        } catch (IOException e) {
            log.error("Unable to prepare temporary directory {}: {}", directory, e.getMessage());
            super.shutDown(1);
        }
        SecureTemporaryFile.setRoot(directory);
    }

}
