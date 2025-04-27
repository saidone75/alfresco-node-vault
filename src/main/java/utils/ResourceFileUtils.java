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

package utils;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

@UtilityClass
@Slf4j
public class ResourceFileUtils {

    public File getFileFromResource(String resourcePath) throws Exception {
        return getFileFromResource(resourcePath, null);
    }

    @SneakyThrows
    public File getFileFromResource(String resourcePath, String destinationPath) {
        // first look into FS
        var filePath = Path.of(resourcePath);
        var file = filePath.toFile();
        if (file.exists() && file.isFile()) {
            log.info("Found file as absolute path => {}", resourcePath);
            return file;
        } else {
            // look for it in the classpath (resources)
            log.info("Looking for resource in classpath => {}", resourcePath);
            var is = Objects.requireNonNull(
                    ResourceFileUtils.class.getClassLoader().getResourceAsStream(resourcePath),
                    "Resource not found in classpath => " + resourcePath
            );
            // create temporary file
            var tempFile = (File) null;
            if (destinationPath != null) {
                tempFile = new File(destinationPath);
            } else {
                tempFile = Files.createTempFile("resource-", ".tmp").toFile();
            }
            tempFile.deleteOnExit();
            try (var fos = new FileOutputStream(tempFile)) {
                is.transferTo(fos);
            }
            return tempFile;
        }
    }

}