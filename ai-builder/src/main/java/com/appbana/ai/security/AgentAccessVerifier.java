package com.appbana.ai.security;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * AgentAccessVerifier — Task S5.1 (Tenant Isolation Security Plan).
 *
 * <p>ai-builder is a second, independent process from app-bana-service (8081 vs 8080) with no
 * shared JVM, so it cannot call {@code EntityAccessGuard}/{@code AppAuthorization.isAppOwnerOrSystem}
 * directly the way {@code GenericEntityRoutes} does. Instead, this class reuses those guards
 * indirectly: it forwards the caller's own session token to an app-bana-service route already
 * gated by {@code TenantAccessGuard.requireOwnTenant} and trusts that route's verdict. This is the
 * same HTTP call {@link com.appbana.ai.agent.tool.ListAppsTool} and (app-context branch of)
 * {@link com.appbana.ai.agent.tool.ListEntitiesTool} already make in production — no new
 * app-bana-service route or backend change was needed for this task.
 *
 * <p><b>Why this check exists even though every individual tool call already carries the caller's
 * token and app-bana-service independently re-validates it on every route (S1-S4):</b> today, a
 * forged {@code tenantId}/{@code appId} in the chat request body would already cause every
 * subsequent tool call to 401/403 at the backend — this is not, today, a reachable data leak. This
 * check is deliberately defense-in-depth against exactly the failure mode the plan doc names for
 * S5 ("stop the AI Builder service from being a second, independent place where 'trust the
 * client-supplied tenant/app id' could resurface"): (a) a future tool that reads/writes
 * ai-builder's *own* Postgres/Qdrant store directly, scoped by {@code tenantId}/{@code appId},
 * would have no independent protection unless this upfront gate exists; (b) it fails fast with one
 * clear 401/403 instead of the agent silently burning up to 3 iterations against a stream of
 * per-tool 401s before giving the user a vague "I couldn't complete that" (see
 * {@link com.appbana.ai.agent.tool.BackendAuthException}'s javadoc for that failure mode); (c) it
 * centralizes the check in one file, matching this plan's stated goal elsewhere that "a bypass
 * means breaking one file, not auditing every route by hand again."
 *
 * <p><b>The "no app yet" case:</b> a brand-new app-creation conversation sends {@code appId:
 * "default"} (see {@code ChatPane.tsx}: {@code appId: currentApp?.id ?? 'default'}) because no app
 * has been scaffolded yet — there is nothing to own. {@link #verify} treats a null/blank/"default"
 * {@code appId} as "no app context" and checks only that the caller's session belongs to
 * {@code tenantId}, via the same bare tenant-list route {@code ListAppsTool} already calls
 * ({@code GET /appbana-studio/{tenantId}/apps}, gated by {@code TenantAccessGuard.requireOwnTenant}
 * with no membership exception since it carries no path {@code appId} — own-tenant only). A real
 * {@code appId} instead calls {@code GET /appbana-studio/{tenantId}/apps/{appId}}, gated by the
 * same guard's membership-exception branch.
 *
 * <p><b>Fail-closed on the unreachable-backend case</b> (review round 96 next-item watch (a)): any
 * non-2xx/401/403/404 status, or an {@link IOException} (covers {@link java.net.ConnectException})
 * / {@link InterruptedException}, denies with 503 rather than admitting the request. An
 * unreachable app-bana-service means ownership cannot be confirmed, which must not be treated the
 * same as confirming it.
 *
 * <p><b>Never a service/admin token</b> (review round 96 next-item watch (b)): {@link #verify}
 * only ever forwards the {@code token} argument, which both callers ({@code AiChatController},
 * {@code AgentStreamController}) source from {@code ChatRequest.getToken()} — the caller's own
 * session credential, already required non-blank by the C4.4e guard before either controller
 * reaches this call. There is no code path here that reads any local/admin/service token config.
 */
@Slf4j
public final class AgentAccessVerifier {

    private final HttpClient httpClient;
    private final String baseUrl;

    public AgentAccessVerifier(String baseUrl) {
        this.httpClient = HttpClient.newHttpClient();
        this.baseUrl = baseUrl;
    }

    /**
     * Result of a {@link #verify} call.
     *
     * @param allowed    true if the chat/stream request may proceed
     * @param statusCode the HTTP status the caller should send when {@code allowed} is false;
     *                   meaningless when {@code allowed} is true
     * @param message    the "error" body message to send when {@code allowed} is false; null when
     *                   {@code allowed} is true
     */
    public record VerifyResult(boolean allowed, int statusCode, String message) {
        public static VerifyResult allow() {
            return new VerifyResult(true, 200, null);
        }

        public static VerifyResult deny(int statusCode, String message) {
            return new VerifyResult(false, statusCode, message);
        }
    }

    /**
     * Verifies the caller's session (identified by {@code token}) is actually authorized for
     * {@code tenantId}/{@code appId}, by delegating to app-bana-service's own
     * {@code TenantAccessGuard}-gated routes rather than trusting the request body's claim.
     *
     * @param tenantId the tenant id the client claims (defaults to "default" when blank, matching
     *                 both controllers' own null-coalescing of the request field)
     * @param appId    the app id the client claims; null/blank/"default" means "no app selected
     *                 yet" (new-app-creation flow) and is checked at the tenant level only
     * @param token    the caller's own session token — never a service/admin token
     * @return a {@link VerifyResult} describing whether the request may proceed
     */
    public VerifyResult verify(String tenantId, String appId, String token) {
        String tenant = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
        boolean hasApp = appId != null && !appId.isBlank() && !"default".equals(appId);

        String url = hasApp
                ? baseUrl + "/appbana-studio/" + tenant + "/apps/" + appId
                : baseUrl + "/appbana-studio/" + tenant + "/apps";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return VerifyResult.allow();
            } else if (response.statusCode() == 401) {
                log.warn("[AgentAccessVerifier] 401 verifying tenant={} app={}", tenant, appId);
                return VerifyResult.deny(401, "Unauthorized: valid session required");
            } else if (response.statusCode() == 403) {
                log.warn("[AgentAccessVerifier] 403 verifying tenant={} app={}", tenant, appId);
                return VerifyResult.deny(403, "Forbidden: caller is not authorized for this tenant/app");
            } else if (response.statusCode() == 404) {
                log.warn("[AgentAccessVerifier] 404 verifying tenant={} app={}", tenant, appId);
                return VerifyResult.deny(404, "App not found: " + appId);
            } else {
                log.error("[AgentAccessVerifier] Unexpected status {} verifying tenant={} app={}",
                        response.statusCode(), tenant, appId);
                return VerifyResult.deny(503, "Unable to verify tenant/app access");
            }
        } catch (IOException | InterruptedException e) {
            // Fail-closed: an unreachable app-bana-service must not be treated as an implicit
            // allow. Covers ConnectException (subclass of IOException) per house convention.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("[AgentAccessVerifier] Failed to verify tenant={} app={}: {}", tenant, appId, e.getMessage());
            return VerifyResult.deny(503, "Unable to verify tenant/app access: app-bana-service unreachable");
        }
    }
}
