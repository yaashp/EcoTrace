package com.ecotrace.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end checks through the real controllers, services and an in-memory database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ecotrace-test;DB_CLOSE_DELAY=-1",
        "ecotrace.demo-mode=true",
        "ecotrace.demo-password=DemoPass123"})
@AutoConfigureMockMvc
class EcoTraceApiTest {

    private static final String PASSWORD = "DemoPass123";

    @Autowired
    private MockMvc mvc;

    private Cookie login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("ECOTRACE_SESSION");
        assertNotNull(cookie);
        return cookie;
    }

    private String bookingJson(String slot) {
        return "{\"items\":[{\"category\":\"Laptop\",\"quantity\":1,\"condition\":\"Non-functional\",\"details\":\"Test\"}],"
                + "\"address\":\"12 Test Road\",\"city\":\"Mumbai\",\"locality\":\"Andheri\",\"contactPhone\":\"9876543210\","
                + "\"pickupDate\":\"" + LocalDate.now().plusDays(10) + "\",\"timeSlot\":\"" + slot + "\"}";
    }

    private String transition(Cookie who, String reference, String body, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/pickups/" + reference + "/transition")
                        .cookie(who)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void protectedEndpointsRequireSignIn() throws Exception {
        mvc.perform(get("/api/pickups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsNeedTheCsrfHeader() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@demo.example\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer2@demo.example\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotUseAdminEndpoints() throws Exception {
        Cookie customer = login("customer@demo.example");
        mvc.perform(get("/api/admin/metrics").cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(customer)).andExpect(status().isForbidden());
    }

    @Test
    void publicTrackingShowsNoPersonalData() throws Exception {
        String body = mvc.perform(get("/api/track/EW-DEMO0011"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("Sample address"));
        assertFalse(body.contains("9000000001"));
        assertFalse(body.contains("Asha"));
        assertEquals("COMPLETED", JsonPath.read(body, "$.status"));
    }

    @Test
    void completeCustodyJourneyWithPermissionChecks() throws Exception {
        Cookie customer = login("customer@demo.example");
        Cookie otherCustomer = login("customer2@demo.example");
        Cookie collector = login("collector@demo.example");
        Cookie otherCollector = login("collector2@demo.example");
        Cookie recycler = login("recycler@demo.example");
        Cookie admin = login("admin@demo.example");

        // booking
        String created = mvc.perform(post("/api/pickups")
                        .cookie(customer)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson("09:00-12:00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(created, "$.reference");
        assertEquals("REQUESTED", JsonPath.read(created, "$.status"));

        // privacy between customers
        mvc.perform(get("/api/pickups/" + ref).cookie(otherCustomer)).andExpect(status().isNotFound());

        // permissions and ordering
        transition(customer, ref, "{\"status\":\"COLLECTED\"}", 403);
        transition(admin, ref, "{\"status\":\"COLLECTED\"}", 403);
        transition(admin, ref, "{\"status\":\"CONFIRMED\"}", 400);

        String collectors = mvc.perform(get("/api/admin/users?role=COLLECTOR").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.List<Number> collectorIds = JsonPath.read(collectors, "$.content[?(@.email=='collector@demo.example')].id");
        Number collectorId = collectorIds.get(0);
        transition(admin, ref, "{\"status\":\"CONFIRMED\",\"collectorId\":" + collectorId + "}", 200);

        mvc.perform(get("/api/pickups/" + ref).cookie(otherCollector)).andExpect(status().isNotFound());
        transition(otherCollector, ref, "{\"status\":\"COLLECTED\"}", 404);
        transition(collector, ref, "{\"status\":\"TRANSFERRED\",\"partnerId\":1}", 409);
        // collection is refused until the customer's one-time code has been verified
        transition(collector, ref, "{\"status\":\"COLLECTED\"}", 409);
        String code = issueCollectionCode(customer, ref);
        verifyCollectionCode(collector, ref, code, 200);
        transition(collector, ref, "{\"status\":\"COLLECTED\",\"note\":\"Collected at the door\"}", 200);

        String facilities = mvc.perform(get("/api/partners?type=COLLECTION_FACILITY"))
                .andReturn().getResponse().getContentAsString();
        Number facilityId = JsonPath.read(facilities, "$[0].id");
        String recyclers = mvc.perform(get("/api/partners?type=RECYCLER"))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> partnerIds = JsonPath.read(recyclers, "$[?(@.name=='Sample Recycling Partner, Thane')].id");
        Number partnerId = partnerIds.get(0);

        transition(collector, ref, "{\"status\":\"AT_FACILITY\",\"facilityId\":" + facilityId + "}", 200);
        transition(collector, ref, "{\"status\":\"TRANSFERRED\",\"partnerId\":" + partnerId + "}", 200);

        // the recycler now sees it, without the customer's contact details
        String recyclerView = mvc.perform(get("/api/pickups/" + ref).cookie(recycler))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(recyclerView.contains("12 Test Road"));
        assertFalse(recyclerView.contains("9876543210"));

        transition(recycler, ref, "{\"status\":\"RECEIVED_BY_PARTNER\"}", 200);
        transition(recycler, ref, "{\"status\":\"PROCESSING\"}", 200);
        transition(recycler, ref, "{\"status\":\"RECYCLING_CONFIRMED\"}", 400);
        transition(admin, ref, "{\"status\":\"RECYCLING_CONFIRMED\",\"outcome\":\"RECYCLED\",\"weightKg\":2,\"processingReference\":\"X\"}", 403);
        transition(recycler, ref,
                "{\"status\":\"RECYCLING_CONFIRMED\",\"outcome\":\"RECYCLED\",\"weightKg\":2.4,\"processingReference\":\"PR-TEST-1\"}", 200);
        transition(admin, ref, "{\"status\":\"COMPLETED\"}", 200);

        // the customer sees the full history and was notified along the way
        String detail = mvc.perform(get("/api/pickups/" + ref).cookie(customer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals("COMPLETED", JsonPath.read(detail, "$.status"));
        assertEquals(9, ((java.util.List<?>) JsonPath.read(detail, "$.timeline")).size());

        String notifications = mvc.perform(get("/api/notifications?size=50").cookie(customer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(((java.util.List<?>) JsonPath.read(notifications, "$.content[?(@.pickupReference=='" + ref + "')]")).isEmpty());

        // blocked attempts were audited
        String audit = mvc.perform(get("/api/admin/audit?failedOnly=true&size=100").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(((java.util.List<?>) JsonPath.read(audit, "$.content[?(@.pickupReference=='" + ref + "')]")).isEmpty());
    }

    private String issueCollectionCode(Cookie customer, String ref) throws Exception {
        String body = mvc.perform(post("/api/pickups/" + ref + "/handover/code")
                        .cookie(customer)
                        .header("X-Requested-With", "test"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private void verifyCollectionCode(Cookie collector, String ref, String code, int expectedStatus) throws Exception {
        mvc.perform(post("/api/pickups/" + ref + "/handover/verify")
                        .cookie(collector)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void adminMetricsComeFromStoredRecords() throws Exception {
        Cookie admin = login("admin@demo.example");
        String body = mvc.perform(get("/api/admin/metrics").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number total = JsonPath.read(body, "$.totalBookings");
        assertFalse(total.intValue() < 16);
        String real = mvc.perform(get("/api/admin/metrics?includeDemo=false").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(Boolean.FALSE, JsonPath.read(real, "$.includesDemoData"));
    }
}
