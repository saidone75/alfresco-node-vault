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

package org.saidone.test;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.saidone.utils.SecureTemporaryFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SecureTemporaryFileTests {

    @Test
    void restrictsPermissionsAndDeletesFileAndDirectory() throws IOException {
        Path path;
        try (val temporaryFile = new SecureTemporaryFile("secure-test-", ".bin")) {
            path = temporaryFile.getPath();
            assertPrivatePermissions(path, false);
            assertPrivatePermissions(path.getParent(), true);
            Files.writeString(path, "content");
            assertEquals("content", Files.readString(path));
        }
        assertFalse(Files.exists(path));
        assertFalse(Files.exists(path.getParent()));
    }

    @Test
    void cleansUpWhenProcessingFails() throws IOException {
        val temporaryFile = new SecureTemporaryFile("secure-test-", ".bin");
        val path = temporaryFile.getPath();
        assertThrows(IOException.class, () -> {
            try (temporaryFile) {
                throw new IOException("Processing failed");
            }
        });
        assertFalse(Files.exists(path));
        assertFalse(Files.exists(path.getParent()));
    }

    private static void assertPrivatePermissions(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            assertEquals(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"),
                    Files.getPosixFilePermissions(path));
        } else {
            val view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            assertNotNull(view);
            val acl = view.getAcl();
            assertEquals(1, acl.size());
            val entry = acl.getFirst();
            assertEquals(AclEntryType.ALLOW, entry.type());
            assertEquals(Files.getOwner(path), entry.principal());
            assertEquals(EnumSet.allOf(AclEntryPermission.class), entry.permissions());
        }
    }
}
