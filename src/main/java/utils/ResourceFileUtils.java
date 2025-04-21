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