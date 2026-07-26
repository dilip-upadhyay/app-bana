package com.appbana.server.routes;

import com.appbana.JdbcManager;
import com.appbana.api.Router;
import com.appbana.storage.FileStorageAdapter;
import com.appbana.storage.LocalFilesystemAdapter;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase B3 — File upload / download endpoints.
 *
 * Design decision: uploads travel as base64-encoded JSON to reuse the router's
 * existing JSON parsing (multipart/form-data would require adding a dependency).
 * Payload cap is enforced server-side against the decoded byte length.
 *
 * POST /api/files/upload
 *   Body: { tenantId, appId, filename, mimeType, contentBase64, entityKey?, fieldName? }
 *   200:  { fileId, url, filename, mimeType, size }
 *   400:  malformed payload / oversized file / disallowed mime
 *
 * GET  /api/files/{fileId}
 *   Streams the raw bytes with the original Content-Type. 404 if unknown.
 *
 * NOTE: file endpoints match ENTITY_API_PATTERN so SessionMiddleware skips
 * them — consistent with the rest of the codebase's current dev-mode auth
 * posture (see /api/apps/, /api/ai/, /appbana-studio/). Tighten in production.
 */
public class FileRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(FileRoutes.class);

    /** 10 MiB default cap; can be raised per-field via metadata (Phase B3 doesn't wire that yet). */
    private static final int MAX_BYTES = 10 * 1024 * 1024;

    /** Alphanumeric + dash + underscore. Applied to tenantId, appId, fileId. */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    private static final String INSERT_SQL =
            "INSERT INTO appbana_files " +
            "(file_id, tenant_id, app_id, entity_key, field_name, original_name, mime_type, size_bytes, storage_path, uploaded_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_SQL =
            "SELECT original_name, mime_type, size_bytes, storage_path FROM appbana_files WHERE file_id = ?";

    private FileRoutes() {}

    public static void register(Router router) {
        FileStorageAdapter storage;
        try {
            storage = new LocalFilesystemAdapter();
        } catch (IOException e) {
            LOG.error("Failed to initialise LocalFilesystemAdapter — file uploads disabled", e);
            return;
        }

        router.post("/api/files/upload", (req, res) -> handleUpload(req, res, storage));
        router.get("/api/files/{fileId}", (req, res) -> handleDownload(req, res, storage));

        LOG.info("Registered file routes: POST /api/files/upload, GET /api/files/{{fileId}}");
    }

    private static void handleUpload(Router.HttpRequest req, Router.HttpResponse res, FileStorageAdapter storage) {
        Map<String, Object> body;
        try {
            body = req.readJson(new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            res.json(400, Map.of("error", "Invalid JSON body"));
            return;
        }
        if (body == null) {
            res.json(400, Map.of("error", "Empty body"));
            return;
        }

        String tenantId = asString(body.get("tenantId"));
        String appId = asString(body.get("appId"));
        String filename = asString(body.get("filename"));
        String mimeType = asString(body.get("mimeType"));
        String contentBase64 = asString(body.get("contentBase64"));

        if (tenantId == null || !SAFE_ID.matcher(tenantId).matches()) {
            res.json(400, Map.of("error", "Invalid or missing tenantId"));
            return;
        }
        if (appId == null || !SAFE_ID.matcher(appId).matches()) {
            res.json(400, Map.of("error", "Invalid or missing appId"));
            return;
        }
        if (filename == null || filename.isBlank()) {
            res.json(400, Map.of("error", "filename is required"));
            return;
        }
        if (mimeType == null || mimeType.isBlank()) {
            res.json(400, Map.of("error", "mimeType is required"));
            return;
        }
        if (contentBase64 == null || contentBase64.isBlank()) {
            res.json(400, Map.of("error", "contentBase64 is required"));
            return;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(contentBase64);
        } catch (IllegalArgumentException e) {
            res.json(400, Map.of("error", "contentBase64 is not valid base64"));
            return;
        }
        if (bytes.length == 0) {
            res.json(400, Map.of("error", "File is empty"));
            return;
        }
        if (bytes.length > MAX_BYTES) {
            res.json(400, Map.of("error", "File exceeds max size", "maxBytes", MAX_BYTES, "actualBytes", bytes.length));
            return;
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String storagePath;
        try {
            storagePath = storage.save(tenantId, appId, fileId, bytes);
        } catch (IOException e) {
            LOG.error("Failed to persist file bytes for tenant {} app {}", tenantId, appId, e);
            res.json(500, Map.of("error", "Failed to store file"));
            return;
        }

        String entityKey = asString(body.get("entityKey"));
        String fieldName = asString(body.get("fieldName"));
        String uploadedBy = asString(req.getAttribute("userId"));

        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setString(1, fileId);
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            ps.setString(4, entityKey);
            ps.setString(5, fieldName);
            ps.setString(6, filename);
            ps.setString(7, mimeType);
            ps.setLong(8, bytes.length);
            ps.setString(9, storagePath);
            ps.setString(10, uploadedBy);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.error("Failed to insert file registry row for {}", fileId, e);
            // Best-effort cleanup of orphan bytes
            try { storage.delete(storagePath); } catch (IOException ignore) { /* best-effort */ }
            res.json(500, Map.of("error", "Failed to register file"));
            return;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", fileId);
        out.put("url", "/api/files/" + fileId);
        out.put("filename", filename);
        out.put("mimeType", mimeType);
        out.put("size", bytes.length);
        res.json(201, out);
    }

    private static void handleDownload(Router.HttpRequest req, Router.HttpResponse res, FileStorageAdapter storage) {
        String fileId = req.pathParam("fileId");
        if (fileId == null || !SAFE_ID.matcher(fileId).matches()) {
            res.json(400, Map.of("error", "Invalid fileId"));
            return;
        }

        String originalName;
        String mimeType;
        String storagePath;
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    res.json(404, Map.of("error", "Unknown fileId"));
                    return;
                }
                originalName = rs.getString("original_name");
                mimeType = rs.getString("mime_type");
                storagePath = rs.getString("storage_path");
            }
        } catch (Exception e) {
            LOG.error("Failed to look up file {}", fileId, e);
            res.json(500, Map.of("error", "Failed to look up file"));
            return;
        }

        byte[] bytes;
        try (InputStream in = storage.open(storagePath)) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            LOG.error("Failed to read stored bytes for {} at {}", fileId, storagePath, e);
            res.json(404, Map.of("error", "File bytes not available"));
            return;
        }

        res.setHeader("Content-Disposition", "inline; filename=\"" + sanitizeHeader(originalName) + "\"");
        res.bytes(200, bytes, mimeType);
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String sanitizeHeader(String s) {
        if (s == null) return "download";
        return s.replaceAll("[\\r\\n\"]", "_");
    }
}
