package com.kuronami.jmwaypointstoxaero.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** File queue operations for the import folder. No game classes. */
public final class ImportFolder {

    public static final String IMPORTED_DIR = "imported";

    private ImportFolder() {
    }

    /** Newest {@code *.zip} directly under {@code dir}, or {@code null}. */
    public static Path latestZip(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(ImportFolder::isZip)
                    .max(Comparator
                            .comparingLong(ImportFolder::modified)
                            .thenComparing(path -> path.getFileName().toString()))
                    .orElse(null);
        }
    }

    /**
     * Moves a processed ZIP into {@code dir/imported}. An existing file with the
     * same name gets an epoch suffix instead of being replaced.
     *
     * @return the new location
     */
    public static Path moveToImported(Path dir, Path zip) throws IOException {
        Path importedDir = dir.resolve(IMPORTED_DIR);
        Files.createDirectories(importedDir);

        Path target = importedDir.resolve(zip.getFileName().toString());
        if (Files.exists(target)) {
            String fileName = zip.getFileName().toString();
            String base = fileName.substring(0, fileName.length() - ".zip".length());
            long epoch = System.currentTimeMillis();
            do {
                target = importedDir.resolve(base + "-" + epoch + ".zip");
                epoch++;
            } while (Files.exists(target));
        }
        Files.move(zip, target);
        return target;
    }

    private static boolean isZip(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }
}
