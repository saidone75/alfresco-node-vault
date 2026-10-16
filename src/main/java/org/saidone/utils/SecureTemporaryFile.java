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

import lombok.Getter;
import lombok.val;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;

/**
 * Temporary file in a private directory, with permissions applied atomically at creation.
 * Uses POSIX permissions or an owner-only ACL (including on Windows/NTFS).
 * Creation fails safely if neither protection can be applied atomically.
 */
public final class SecureTemporaryFile implements AutoCloseable {

    private final Path directory;
    @Getter
    private final Path path;

    public SecureTemporaryFile(String prefix, String suffix) throws IOException {
        val temporaryRoot = Path.of(System.getProperty("java.io.tmpdir"));
        val store = Files.getFileStore(temporaryRoot);
        FileAttribute<?> directoryPermissions;
        FileAttribute<?> filePermissions;
        if (store.supportsFileAttributeView("posix")) {
            directoryPermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            filePermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
        } else if (store.supportsFileAttributeView("acl")) {
            directoryPermissions = ownerOnlyAcl(temporaryRoot, true);
            filePermissions = ownerOnlyAcl(temporaryRoot, false);
        } else {
            throw new IOException("Temporary filesystem supports neither POSIX permissions nor ACLs");
        }
        directory = Files.createTempDirectory(temporaryRoot, prefix, directoryPermissions);
        try {
            path = Files.createTempFile(directory, prefix, suffix, filePermissions);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(directory);
            } catch (IOException cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }

    private static FileAttribute<List<AclEntry>> ownerOnlyAcl(Path root, boolean directory) throws IOException {
        val owner = root.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        val entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (directory) {
            entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        }
        val acl = List.of(entry.build());
        return new FileAttribute<>() {
            @Override
            public String name() {
                return "acl:acl";
            }

            @Override
            public List<AclEntry> value() {
                return acl;
            }
        };
    }

    @Override
    public void close() throws IOException {
        try {
            Files.deleteIfExists(path);
        } finally {
            Files.deleteIfExists(directory);
        }
    }
}
