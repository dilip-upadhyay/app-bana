package com.appbana.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Phase B3 — pluggable file bytes storage.
 *
 * The registry row (tenant/app/mime/size/original name) always lives in the
 * {@code appbana_files} Postgres table; only the raw bytes are delegated to an
 * implementation of this interface. The default implementation
 * ({@link LocalFilesystemAdapter}) writes to disk under the service working
 * directory; a future S3 / Azure Blob implementation can drop in without
 * touching {@code FileRoutes}.
 */
public interface FileStorageAdapter {

    /** Save raw file bytes under the tenant/app namespace and return the
     *  opaque storage path (persisted in the DB, opaque to callers). */
    String save(String tenantId, String appId, String fileId, byte[] contents) throws IOException;

    /** Open a stream over the previously stored bytes. Callers must close. */
    InputStream open(String storagePath) throws IOException;

    /** Best-effort deletion; ignore missing paths. */
    void delete(String storagePath) throws IOException;
}
