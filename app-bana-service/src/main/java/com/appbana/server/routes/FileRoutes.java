package com.appbana.server.routes;

import com.appbana.JdbcManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.security.TenantAccessGuard;
import com.appbana.service.AuthService;
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
 *   401/403: no resolved identity, or the identity's own tenant doesn't match tenantId (S1.7)
 *
 * GET  /api/files/{tenantId}/{appId}/{fileId}
 *   Streams the raw bytes with the original Content-Type. 404 on an unknown or cross-tenant triple.
 *   Remains anonymous end-to-end (S1.18: SessionMiddleware explicitly excludes this exact 3-segment
 *   shape) — protection rests entirely on the (tenantId, appId, fileId) triple. fileId is a
 *   server-issued random UUID (122 random bits once a v4 UUID's fixed version/variant bits are
 *   excluded; still unguessable by any margin that matters); SELECT_SQL below returns an identical 404 for
 *   an unknown fileId and for a wrong tenant/app, so a probe attack learns nothing either way. This
 *   is deliberate, not an oversight: both places that render this URL (FileUploadField.tsx's
 *   "Preview" link, StudioTableLive.tsx's "Download" column) use a plain <a href target="_blank">,
 *   which can never carry the Authorization header this app's header-based auth model needs — so a
 *   session requirement here could only ever 401 real users, never an attacker who lacks the
 *   unguessable fileId anyway. (Previously broken 2026-08-01 → 2026-08-0X: SessionMiddleware had no
 *   exclusion matching this route's 4-segment path, so it 401'd every real download click despite
 *   this Javadoc always documenting anonymous intent — tracked and fixed as S1.18 in
 *   TENANT_ISOLATION_IMPLEMENTATION_TASKS.md.)
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

    /** Tenant-scoped lookup — the (tenantId, appId, fileId) triple prevents
     *  cross-tenant reads. A file uploaded to tenant A cannot be downloaded
     *  by supplying tenant B's id in the URL, even with a valid fileId. */
    private static final String SELECT_SQL =
            "SELECT original_name, mime_type, size_bytes, storage_path FROM appbana_files " +
            "WHERE file_id = ? AND tenant_id = ? AND app_id = ?";

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
        // Tenant-scoped download URL. The tenantId + appId in the path are
        // enforced against the stored row — a mismatched triple returns 404.
        router.get("/api/files/{tenantId}/{appId}/{fileId}", (req, res) -> handleDownload(req, res, storage));

        LOG.info("Registered file routes: POST /api/files/upload, GET /api/files/{{tenantId}}/{{appId}}/{{fileId}}");
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

        // S1.7 — require a resolved identity whose own tenant matches the upload's target
        // tenant/app, instead of trusting tenantId/appId as handed to us in the body. A valid
        // service/admin token still bypasses (break-glass), same as every other
        // TenantAccessGuard call site; appId ownership/existence itself is not verified here,
        // consistent with every other S1 route — that is deferred to S2's membership model.
        AppConfig cfg = ConfigManager.getConfig();
        TenantAccessGuard.Result access = TenantAccessGuard.requireOwnTenant(req, cfg, tenantId, appId);
        if (!access.allowed()) {
            res.json(access.statusCode(), Map.of("error", access.message()));
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
        // S1.7 — resolved identity (not a request attribute SessionMiddleware never sets for this
        // route, since it matches ENTITY_API_PATTERN and is skipped) so the audit column reflects
        // who actually uploaded the file rather than always being null.
        String uploadedBy = AuthService.resolveIdentity(req, cfg);

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
        // Tenant-scoped download URL. Consumers should use this verbatim
        // rather than constructing their own path — the tenantId + appId
        // are enforced server-side and a mismatched triple returns 404.
        out.put("url", "/api/files/" + tenantId + "/" + appId + "/" + fileId);
        out.put("tenantId", tenantId);
        out.put("appId", appId);
        out.put("filename", filename);
        out.put("mimeType", mimeType);
        out.put("size", bytes.length);
        res.json(201, out);
    }

    private static void handleDownload(Router.HttpRequest req, Router.HttpResponse res, FileStorageAdapter storage) {
        String tenantId = req.pathParam("tenantId");
        String appId = req.pathParam("appId");
        String fileId = req.pathParam("fileId");
        if (tenantId == null || !SAFE_ID.matcher(tenantId).matches()) {
            res.json(400, Map.of("error", "Invalid tenantId"));
            return;
        }
        if (appId == null || !SAFE_ID.matcher(appId).matches()) {
            res.json(400, Map.of("error", "Invalid appId"));
            return;
        }
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
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Do NOT distinguish "unknown fileId" from "wrong tenant" —
                    // returning 404 in both cases prevents a probe-attack that
                    // enumerates file ids across tenants.
                    res.json(404, Map.of("error", "Unknown fileId"));
                    return;
                }
                originalName = rs.getString("original_name");
                mimeType = rs.getString("mime_type");
                storagePath = rs.getString("storage_path");
            }
        } catch (Exception e) {
            LOG.error("Failed to look up file {} for tenant {} app {}", fileId, tenantId, appId, e);
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
