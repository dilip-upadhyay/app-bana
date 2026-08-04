package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.security.AppMembershipService;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UsersMeAppsRouteTest — S2.10 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Covers {@code GET /api/users/me/apps} — the one deliberately non-tenant-scoped app-listing
 * route in the plan (see {@code AppMembershipRoutes.handleListMyApps}'s Javadoc for the full
 * security rationale). Verifies:
 * <ul>
 *   <li>fails closed (401) with no session at all;</li>
 *   <li>the caller's own-tenant apps are returned, tagged with their own tenantId;</li>
 *   <li>a cross-tenant membership grant surfaces the foreign app tagged with ITS tenantId and
 *       the grant's role;</li>
 *   <li>a same-tenant grant (the automatic owner grant every app creator gets, S2.3) is not
 *       double-counted against the own-tenant listing;</li>
 *   <li>an orphaned cross-tenant grant (app deleted after the grant was made) is skipped rather
 *       than 500ing or fabricating a null entry;</li>
 *   <li>a caller with no cross-tenant memberships at all still gets 200 with just their own
 *       apps.</li>
 * </ul>
 */
public class UsersMeAppsRouteTest {

    private static final int PORT = 18103;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HOME_TENANT = "t_s210_home";
    private static final String OTHER_TENANT = "t_s210_other";
    private static final String HOME_APP_ID = "s210-home-app";
    private static final String FOREIGN_APP_ID = "s210-foreign-app";

    private String homeUserSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN ('" + HOME_TENANT + "', '" + OTHER_TENANT + "')");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + HOME_TENANT + "', '" + OTHER_TENANT + "')");
        }

        homeUserSession = SessionService.createSession("s210_user", HOME_TENANT).sessionId();

        HttpResponse<String> homeAppRes = send("POST", "/appbana-studio/" + HOME_TENANT + "/apps", homeUserSession,
                MAPPER.writeValueAsString(Map.of("id", HOME_APP_ID, "name", "S2.10 home app", "version", "1.0.0")));
        assertEquals(201, homeAppRes.statusCode(), "Test fixture setup: home app creation must succeed: " + homeAppRes.body());

        String foreignOwnerSession = SessionService.createSession("s210_foreign_owner", OTHER_TENANT).sessionId();
        HttpResponse<String> foreignAppRes = send("POST", "/appbana-studio/" + OTHER_TENANT + "/apps", foreignOwnerSession,
                MAPPER.writeValueAsString(Map.of("id", FOREIGN_APP_ID, "name", "S2.10 foreign app", "version", "1.0.0")));
        assertEquals(201, foreignAppRes.statusCode(), "Test fixture setup: foreign app creation must succeed: " + foreignAppRes.body());
    }

    private HttpResponse<String> send(String method, String path, String sessionOrNull, String bodyOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path));
        if (sessionOrNull != null) {
            b.header("X-Session-Token", sessionOrNull);
        }
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.DELETE();
            default -> b.method(method, HttpRequest.BodyPublishers.ofString(bodyOrNull != null ? bodyOrNull : "{}"))
                    .header("Content-Type", "application/json");
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode findApp(JsonNode apps, String appId) {
        for (JsonNode app : apps) {
            if (appId.equals(app.path("id").asText(null))) {
                return app;
            }
        }
        return null;
    }

    @Test
    public void unauthenticatedRequestIsRejected() throws Exception {
        HttpResponse<String> res = send("GET", "/api/users/me/apps", null, null);
        assertEquals(401, res.statusCode(), res.body());
    }

    @Test
    public void ownTenantAppAppearsTaggedWithOwnTenantId() throws Exception {
        HttpResponse<String> res = send("GET", "/api/users/me/apps", homeUserSession, null);
        assertEquals(200, res.statusCode(), res.body());

        JsonNode apps = MAPPER.readTree(res.body()).get("apps");
        JsonNode homeApp = findApp(apps, HOME_APP_ID);
        assertNotNull(homeApp, "own-tenant app must appear in the union: " + res.body());
        assertEquals(HOME_TENANT, homeApp.get("tenantId").asText(), "own-tenant app must be tagged with the caller's own tenantId");
    }

    @Test
    public void crossTenantGrantAppearsTaggedWithForeignTenantIdAndRole() throws Exception {
        AppMembershipService.grant(OTHER_TENANT, FOREIGN_APP_ID, "s210_user", AppMembershipService.Role.MEMBER, "test-setup");

        HttpResponse<String> res = send("GET", "/api/users/me/apps", homeUserSession, null);
        assertEquals(200, res.statusCode(), res.body());

        JsonNode apps = MAPPER.readTree(res.body()).get("apps");
        JsonNode foreignApp = findApp(apps, FOREIGN_APP_ID);
        assertNotNull(foreignApp, "cross-tenant membership grant must surface the foreign app: " + res.body());
        assertEquals(OTHER_TENANT, foreignApp.get("tenantId").asText(), "foreign app must be tagged with ITS OWN tenantId, not the caller's");
        assertEquals("member", foreignApp.get("role").asText(), "foreign app must be tagged with the grant's role");
    }

    @Test
    public void sameTenantOwnerGrantDoesNotDuplicateOwnApp() throws Exception {
        // S2.3 auto-grants "s210_user" OWNER on HOME_APP_ID in HOME_TENANT at creation time
        // (see setUp). listAppsForUser() returns that grant too, same-tenant — the route must
        // not add a second entry for it on top of the unfiltered own-tenant listing.
        HttpResponse<String> res = send("GET", "/api/users/me/apps", homeUserSession, null);
        assertEquals(200, res.statusCode(), res.body());

        JsonNode apps = MAPPER.readTree(res.body()).get("apps");
        int count = 0;
        for (JsonNode app : apps) {
            if (HOME_APP_ID.equals(app.path("id").asText(null)) && HOME_TENANT.equals(app.path("tenantId").asText(null))) {
                count++;
            }
        }
        assertEquals(1, count, "the caller's own app must appear exactly once, not duplicated by the same-tenant membership grant: " + res.body());
    }

    @Test
    public void orphanedCrossTenantGrantIsSkippedGracefully() throws Exception {
        // Grant membership on an app id that was never actually created via the App API — models
        // an app deleted after the membership row was written. Must be skipped, not 500 or
        // fabricate a null-backed entry.
        AppMembershipService.grant(OTHER_TENANT, "s210-deleted-app", "s210_user", AppMembershipService.Role.MEMBER, "test-setup");

        HttpResponse<String> res = send("GET", "/api/users/me/apps", homeUserSession, null);
        assertEquals(200, res.statusCode(), "an orphaned grant must not turn into a 500: " + res.body());

        JsonNode apps = MAPPER.readTree(res.body()).get("apps");
        assertNull(findApp(apps, "s210-deleted-app"), "an orphaned grant with no backing app must be skipped, not fabricated: " + res.body());
    }

    @Test
    public void noCrossTenantMembershipStillReturnsOwnTenantAppsOnly() throws Exception {
        String soloUserSession = SessionService.createSession("s210_solo_user", HOME_TENANT).sessionId();
        String soloAppId = "s210-solo-app";
        HttpResponse<String> createRes = send("POST", "/appbana-studio/" + HOME_TENANT + "/apps", soloUserSession,
                MAPPER.writeValueAsString(Map.of("id", soloAppId, "name", "S2.10 solo app", "version", "1.0.0")));
        assertEquals(201, createRes.statusCode(), createRes.body());

        HttpResponse<String> res = send("GET", "/api/users/me/apps", soloUserSession, null);
        assertEquals(200, res.statusCode(), res.body());

        JsonNode apps = MAPPER.readTree(res.body()).get("apps");
        assertNotNull(findApp(apps, soloAppId), "the solo user's own app must still appear: " + res.body());
        // The own-tenant half is a deliberately UNFILTERED dump of every app in that tenant (same
        // semantics as the pre-existing GET /appbana-studio/{tenantId}/apps) -- s210_solo_user
        // shares HOME_TENANT with s210_user, so HOME_APP_ID legitimately appears here too even
        // though s210_solo_user holds no membership grant on it. This is the route's documented
        // design, not a leak: membership only gates the CROSS-tenant half.
        assertNotNull(findApp(apps, HOME_APP_ID),
                "own-tenant listing is unfiltered by membership by design -- another app in the same tenant must still appear: " + res.body());
        assertNull(findApp(apps, FOREIGN_APP_ID),
                "a user with zero cross-tenant memberships must not see any app from a genuinely different tenant: " + res.body());
    }
}
