package edu.oop.itsm;

import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs every HTTP integration test again on a real PostgreSQL, the production database on Render. */
@ActiveProfiles("postgres")
class PostgresSmokeTest extends ApiIntegrationTest {
    static final EmbeddedPostgres PG;
    static { try { PG = EmbeddedPostgres.start(); } catch (Exception e) { throw new IllegalStateException(e); } }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres", "postgres"));
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "");
    }

    @Test
    void readEndpointsWorkOnPostgres() throws Exception {
        String admin = login("admin@itsm.local", "Admin@123"), tech = login("neha@itsm.local", "Tech@123");
        for (String u : new String[]{"/api/reports/dashboard", "/api/admin/users", "/api/admin/services", "/api/admin/sla-policies",
                "/api/admin/categories", "/api/admin/settings", "/api/admin/audit", "/api/admin/outbox", "/api/catalog", "/api/notifications",
                "/api/incidents?q=vpn&breached=true&open=true&unassigned=false&slaStatus=BREACHED&priority=P1&status=IN_PROGRESS&from=2026-01-01T00:00:00Z&to=2030-01-01T00:00:00Z&sort=priority",
                "/api/incidents/1", "/api/incidents/1/timeline", "/api/incidents/1/recommendations", "/api/incidents/1/suggested-resolutions", "/api/incidents/1/similar"})
            mvc.perform(get(u).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/me").header("Authorization", tech)).andExpect(status().isOk());
    }
}
