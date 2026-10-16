/*
 * Alfresco Node Vault - ad aeternam documentorum conservationem
 * Copyright (C) 2025-2026 Saidone
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

package org.saidone.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for opening filesystem files and classpath resources as streams.
 * <p>
 * Resource paths are resolved against the filesystem first, accepting both absolute
 * and relative paths. If no regular file is found, the resource is looked up using
 * this class's class loader. Classpath resources, including those packaged inside
 * JAR files, are read directly without extraction to temporary files.
 * </p>
 * <p>
 * Callers are responsible for closing each returned {@link InputStream}, preferably
 * with a try-with-resources statement. Missing resources and failures to open a
 * stream are reported through {@link IOException}.
 * </p>
 */
@UtilityClass
@Slf4j
public class ResourceFileUtils {

    /**
     * Opens a resource, searching the filesystem first and then the classpath.
     * The caller must close the returned stream.
     *
     * @param resourcePath absolute or relative filesystem path, or classpath resource name
     * @return stream containing the resource's bytes
     * @throws IOException if the resource cannot be found or opened
     */
    public InputStream getInputStreamFromResource(String resourcePath) throws IOException {
        val filePath = Path.of(resourcePath);
        if (Files.isRegularFile(filePath)) {
            log.info("Found file in filesystem => {}", resourcePath);
            return Files.newInputStream(filePath);
        }
        log.info("Looking for resource in classpath => {}", resourcePath);
        val inputStream = ResourceFileUtils.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new FileNotFoundException("Resource not found in file system or classpath: " + resourcePath);
        }
        return inputStream;
    }
}
