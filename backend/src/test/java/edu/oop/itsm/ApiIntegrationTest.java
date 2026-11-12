package edu.oop.itsm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end through HTTP, security, services, JPA and the seeded H2 database.
 * Covers authentication, role-based authorisation, the create pipeline, validation and the full lifecycle.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:itsm-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "itsm.sla.monitor-initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    ResultActions postJson(String url, String token, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    JsonNode createIncident(String token) throws Exception {
        String res = postJson("/api/incidents", token, """
                {"title":"VPN connection keeps dropping","description":"The remote VPN disconnects every few minutes for the whole team",
                 "serviceId":8,"impact":"MEDIUM","urgency":"HIGH","affectedUsers":40}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    @Test
    @DisplayName("TC21 Invalid login returns 401 with a stable error code")
    void invalidLogin() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"rohan@itsm.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    @DisplayName("TC22 Authorization: no token -> 401, employee on admin API -> 403")
    void authorization() throws Exception {
        mvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized());
        String emp = login("rohan@itsm.local", "User@123");
        mvc.perform(get("/api/admin/users").header("Authorization", emp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/dashboard").header("Authorization", emp)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC23 Create pipeline: incident is classified, prioritised, put on SLA and assigned automatically")
    void createPipeline() throws Exception {
        JsonNode d = createIncident(login("meera@itsm.local", "User@123"));
        JsonNode s = d.get("summary");
        org.assertj.core.api.Assertions.assertThat(s.get("category").get("name").asText()).isEqualTo("Network");
        org.assertj.core.api.Assertions.assertThat(s.get("priority").asText()).isIn("P1", "P2");
        org.assertj.core.api.Assertions.assertThat(s.get("status").asText()).isEqualTo("ASSIGNED");
        org.assertj.core.api.Assertions.assertThat(s.get("resolutionDueAt").isNull()).isFalse();
        org.assertj.core.api.Assertions.assertThat(d.get("autoCategorized").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("TC24 Invalid input: missing fields are reported per field with 400")
    void validation() throws Exception {
        String emp = login("rohan@itsm.local", "User@123");
        postJson("/api/incidents", emp, "{\"title\":\"x\",\"serviceId\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.impact").exists());
        postJson("/api/incidents", emp, """
                {"title":"Something broke","description":"Long enough description","serviceId":9999,"impact":"LOW","urgency":"LOW","affectedUsers":1}""")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC25 Authorization: an employee cannot open or close someone else's incident")
    void ownership() throws Exception {
        long id = createIncident(login("meera@itsm.local", "User@123")).get("summary").get("id").asLong();
        String other = login("karan@itsm.local", "User@123");
        mvc.perform(get("/api/incidents/" + id).header("Authorization", other))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("UNAUTHORIZED_OPERATION"));
    }

    @Test
    @DisplayName("TC26 Business rule: resolving before investigation starts is an invalid transition (409)")
    void invalidTransitionOverApi() throws Exception {
        JsonNode d = createIncident(login("meera@itsm.local", "User@123"));
        long id = d.get("summary").get("id").asLong();
        String assignee = d.get("assignee").get("email").asText();
        String tech = login(assignee, "Tech@123");
        postJson("/api/incidents/" + id + "/acknowledge", tech, "{}").andExpect(status().isOk());
        postJson("/api/incidents/" + id + "/resolve", tech, "{\"resolution\":\"Restarted gateway\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("TC27 Full lifecycle over the API ends CLOSED with feedback and a complete audit trail")
    void fullLifecycle() throws Exception {
        String emp = login("meera@itsm.local", "User@123");
        JsonNode d = createIncident(emp);
        long id = d.get("summary").get("id").asLong();
        String tech = login(d.get("assignee").get("email").asText(), "Tech@123");
        postJson("/api/incidents/" + id + "/acknowledge", tech, "{}").andExpect(status().isOk());
        postJson("/api/incidents/" + id + "/start", tech, "{}").andExpect(jsonPath("$.summary.status").value("IN_PROGRESS"));
        postJson("/api/incidents/" + id + "/request-info", tech, "{\"text\":\"Which office are you in?\"}")
                .andExpect(jsonPath("$.summary.status").value("AWAITING_INFO"));
        postJson("/api/incidents/" + id + "/provide-info", emp, "{\"text\":\"Pune\"}").andExpect(jsonPath("$.summary.status").value("IN_PROGRESS"));
        postJson("/api/incidents/" + id + "/resolve", tech, "{\"resolution\":\"Updated VPN client\",\"rootCause\":\"Keep-alive mismatch\"}")
                .andExpect(jsonPath("$.summary.status").value("RESOLVED"));
        postJson("/api/incidents/" + id + "/feedback", emp, "{\"rating\":5,\"comment\":\"Great\"}")
                .andExpect(jsonPath("$.summary.status").value("CLOSED"))
                .andExpect(jsonPath("$.feedbackRating").value(5));
        mvc.perform(get("/api/incidents/" + id + "/timeline").header("Authorization", emp))
                .andExpect(jsonPath("$[*].action", hasItems("CREATED", "CLASSIFIED", "PRIORITIZED", "ASSIGNED", "ACKNOWLEDGED",
                        "INFO_REQUESTED", "RESOLVED", "VERIFIED", "CLOSED")));
    }

    @Test
    @DisplayName("TC28 Registration enforces password policy and unique email")
    void registration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Person\",\"email\":\"new@itsm.local\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dup\",\"email\":\"rohan@itsm.local\",\"password\":\"Password1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC29 Admin can switch the assignment strategy at runtime; unknown strategies are rejected")
    void switchStrategy() throws Exception {
        String admin = login("admin@itsm.local", "Admin@123");
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentStrategy\":\"round-robin\"}")).andExpect(jsonPath("$.assignmentStrategy").value("round-robin"));
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentStrategy\":\"magic\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignmentStrategy\":\"smart\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC30 Search filters: employees only ever see their own incidents")
    void searchScoping() throws Exception {
        String emp = login("rohan@itsm.local", "User@123");
        mvc.perform(get("/api/incidents?size=100").header("Authorization", emp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].reporter.name", everyItem(is("Rohan Gupta"))));
        String admin = login("admin@itsm.local", "Admin@123");
        mvc.perform(get("/api/incidents?priority=P1&open=true").header("Authorization", admin))
                .andExpect(jsonPath("$.content[*].priority", everyItem(is("P1"))));
    }
}
