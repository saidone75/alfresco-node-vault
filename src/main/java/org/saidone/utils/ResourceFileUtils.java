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

package org.saidone.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class providing methods to access files from filesystem or classpath resources.
 * <p>
 * This class offers methods to obtain a {@link File} instance from a specified resource path.
 * It first attempts to locate the file in the local filesystem using an absolute or relative path.
 * If the file is not found in the filesystem, it will attempt to locate and extract the resource from the classpath.
 * When accessing resources from the classpath, the method creates a temporary file which will be deleted on JVM exit,
 * or will use a specified destination path if provided.
 * Logging is used to notify where the file was found or if a resource is being loaded from the classpath.
 */
@UtilityClass
@Slf4j
public class ResourceFileUtils {

    /**
     * Obtains a file from either the filesystem or classpath resources.
     *
     * @param resourcePath the path to the resource, either absolute path in the filesystem or a classpath resource
     * @return the {@link File} object representing the resource
     */
    public File getFileFromResource(String resourcePath) throws IOException {
        return getFileFromResource(resourcePath, null);
    }

    /**
     * Retrieves a file from the given path, searching first in the filesystem and then in the classpath resources.
     * <br>
     * If the specified resource path exists as a file in the filesystem, a {@link File} object pointing to this location is returned.
     * If the file does not exist, the method attempts to locate the resource in the application's classpath. If found,
     * it will extract the resource as a temporary file, or use a specific destination path if provided.
     * If a temporary file is created, it will be marked for deletion on JVM exit.
     *
     * @param resourcePath    the path to the resource in the filesystem or the name of the classpath resource
     * @param destinationPath optional path for the extracted resource file (used if resource is found in classpath);
     *                        if null, a temporary file is created
     * @return a {@link File} object representing the file or a copy of the extracted classpath resource
     * @throws FileNotFoundException if the resource cannot be found in both the filesystem and classpath
     * @throws IOException           if an I/O error occurs during file access or resource extraction
     */
    public File getFileFromResource(String resourcePath, String destinationPath) throws FileNotFoundException, IOException {
        val filePath = Path.of(resourcePath);
        val file = filePath.toFile();
        if (file.exists() && file.isFile()) {
            log.info("Found file as absolute path => {}", resourcePath);
            return file;
        } else {
            log.info("Looking for resource in classpath => {}", resourcePath);
            try (var is = ResourceFileUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new FileNotFoundException("Resource not found in file system or classpath: " + resourcePath);
                }
                val tempFile = destinationPath != null
                        ? new File(destinationPath)
                        : Files.createTempFile("resource-", file.getName().replaceAll("^.*\\.", ".")).toFile();
                tempFile.deleteOnExit();
                try (val fos = new FileOutputStream(tempFile)) {
                    is.transferTo(fos);
                }
                return tempFile;
            }
        }
    }

}