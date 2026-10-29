package com.kuronami.jmwaypointstoxaero.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportFolderTest {

    @TempDir
    Path tempDir;

    @Test
    void latestZipUsesModificationTime() throws IOException {
        Path old = touch("old.zip", 1_000L);
        Path newest = touch("newest.zip", 3_000L);
        touch("middle.zip", 2_000L);
        Files.writeString(tempDir.resolve("notes.txt"), "ignored");

        assertEquals(newest, ImportFolder.latestZip(tempDir));
        assertTrue(Files.exists(old));
    }

    @Test
    void latestZipIsNullWhenEmptyOrMissing() throws IOException {
        assertNull(ImportFolder.latestZip(tempDir));
        assertNull(ImportFolder.latestZip(tempDir.resolve("missing")));
    }

    @Test
    void moveToImportedKeepsTheNameAndMovesTheFile() throws IOException {
        Path zip = touch("selftest.zip", 1_000L);

        Path moved = ImportFolder.moveToImported(tempDir, zip);

        assertFalse(Files.exists(zip));
        assertTrue(Files.exists(moved));
        assertEquals("selftest.zip", moved.getFileName().toString());
        assertEquals("imported", moved.getParent().getFileName().toString());
    }

    @Test
    void moveToImportedSuffixesWhenTheNameAlreadyExists() throws IOException {
        Path importedDir = Files.createDirectories(tempDir.resolve("imported"));
        Files.writeString(importedDir.resolve("selftest.zip"), "previous");
        Path zip = touch("selftest.zip", 1_000L);

        Path moved = ImportFolder.moveToImported(tempDir, zip);

        assertTrue(Files.exists(moved));
        assertNotEquals("selftest.zip", moved.getFileName().toString());
        assertTrue(moved.getFileName().toString().startsWith("selftest-"));
        assertTrue(moved.getFileName().toString().endsWith(".zip"));
        assertFalse(Files.exists(zip));
    }

    private Path touch(String name, long modified) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, name);
        Files.setLastModifiedTime(path, FileTime.fromMillis(modified));
        return path;
    }
}
