package com.appbana.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Local-disk implementation of {@link FileStorageAdapter}.
 *
 * Layout: {root}/{tenantId}/{appId}/{yyyyMMdd}/{fileId}
 *
 * Tenant + app segments are pre-validated by {@link com.appbana.server.routes.FileRoutes}
 * (alphanumeric + dash/underscore only) so no directory traversal is possible.
 * The date bucket keeps directory sizes manageable.
 */
public final class LocalFilesystemAdapter implements FileStorageAdapter {

    private static final DateTimeFormatter DATE_BUCKET = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path root;

    public LocalFilesystemAdapter(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public LocalFilesystemAdapter() throws IOException {
        this(Paths.get("uploads"));
    }

    @Override
    public String save(String tenantId, String appId, String fileId, byte[] contents) throws IOException {
        String bucket = LocalDate.now(ZoneId.systemDefault()).format(DATE_BUCKET);
        Path dir = root.resolve(tenantId).resolve(appId).resolve(bucket);
        Files.createDirectories(dir);
        Path file = dir.resolve(fileId).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("Refusing to write outside storage root: " + file);
        }
        Files.write(file, contents);
        // Return a relative path so the DB row stays portable if the root moves.
        return root.relativize(file).toString().replace('\\', '/');
    }

    @Override
    public InputStream open(String storagePath) throws IOException {
        Path resolved = root.resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Refusing to read outside storage root: " + resolved);
        }
        return Files.newInputStream(resolved);
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Path resolved = root.resolve(storagePath).normalize();
        if (!resolved.startsWith(root)) {
            return;
        }
        Files.deleteIfExists(resolved);
    }

    /** Exposed for tests / diagnostics. */
    public Path root() {
        return root;
    }
}
