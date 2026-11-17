package com.ecotrace.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Verified handover through the real controllers, services and an in-memory database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ecotrace-test;DB_CLOSE_DELAY=-1",
        "ecotrace.demo-mode=true",
        "ecotrace.demo-password=DemoPass123"})
@AutoConfigureMockMvc
class HandoverApiTest {

    private static final String PASSWORD = "DemoPass123";
    /** A different booking date for every pickup, so slot capacity is never a factor. */
    private static final AtomicInteger DAY = new AtomicInteger();

    /** A minimal PNG: the real signature followed by padding. Enough for the signature check. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired
    private MockMvc mvc;

    private record Pickup(String ref, Cookie customer, Cookie otherCustomer, Cookie collector,
                          Cookie otherCollector, Cookie recycler, Cookie admin) {
    }

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

    private String bookingJson(LocalDate date) {
        return "{\"items\":[{\"category\":\"Laptop\",\"quantity\":1,\"condition\":\"Non-functional\",\"details\":\"Test\"}],"
                + "\"address\":\"12 Test Road\",\"city\":\"Mumbai\",\"locality\":\"Powai\",\"contactPhone\":\"9876543210\","
                + "\"pickupDate\":\"" + date + "\",\"timeSlot\":\"15:00-18:00\"}";
    }

    /** Books a pickup and assigns the demonstration collector, so the pickup is CONFIRMED. */
    private Pickup confirmedPickup() throws Exception {
        Cookie customer = login("customer@demo.example");
        Cookie admin = login("admin@demo.example");
        LocalDate date = LocalDate.now().plusDays(25 + (DAY.getAndIncrement() % 30));
        String created = mvc.perform(post("/api/pickups")
                        .cookie(customer)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(created, "$.reference");

        String collectors = mvc.perform(get("/api/admin/users?role=COLLECTOR").cookie(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(collectors, "$.content[?(@.email=='collector@demo.example')].id");
        transition(admin, ref, "{\"status\":\"CONFIRMED\",\"collectorId\":" + ids.get(0) + "}", 200);

        return new Pickup(ref, customer, login("customer2@demo.example"), login("collector@demo.example"),
                login("collector2@demo.example"), login("recycler@demo.example"), admin);
    }

    private String transition(Cookie who, String ref, String body, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/pickups/" + ref + "/transition")
                        .cookie(who)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private String issueCode(Cookie customer, String ref) throws Exception {
        String body = mvc.perform(post("/api/pickups/" + ref + "/handover/code")
                        .cookie(customer)
                        .header("X-Requested-With", "test"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
        return code;
    }

    private String verify(Cookie who, String ref, String code, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/pickups/" + ref + "/handover/verify")
                        .cookie(who)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static String wrongCodeFor(String code) {
        return String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
    }

    // ------------------------------------------------------------------ code issue, privacy and lockout

    @Test
    void codeIsNeverPublishedAndFiveWrongGuessesLockIt() throws Exception {
        Pickup p = confirmedPickup();
        String code = issueCode(p.customer(), p.ref());

        // the public tracking page never carries the code
        String publicPage = mvc.perform(get("/api/track/" + p.ref()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(publicPage.contains(code));

        // the customer's own view shows only the status, never the code
        String detail = mvc.perform(get("/api/pickups/" + p.ref()).cookie(p.customer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains(code));
        assertEquals(true, JsonPath.read(detail, "$.handoverCode.active"));
        assertEquals(true, JsonPath.read(detail, "$.canGenerateHandoverCode"));

        String wrong = wrongCodeFor(code);
        for (int attempt = 1; attempt <= 4; attempt++) {
            String body = verify(p.collector(), p.ref(), wrong, 400);
            String message = JsonPath.read(body, "$.fieldErrors.code");
            assertTrue(message.contains((5 - attempt) + (attempt == 4 ? " attempt" : " attempts") + " left"));
        }
        verify(p.collector(), p.ref(), wrong, 429);
        // after the limit even the right code is refused
        verify(p.collector(), p.ref(), code, 429);
    }

    @Test
    void regeneratingACodeStopsTheEarlierOne() throws Exception {
        Pickup p = confirmedPickup();
        String first = issueCode(p.customer(), p.ref());
        String second = issueCode(p.customer(), p.ref());
        for (int i = 0; i < 5 && first.equals(second); i++) {
            second = issueCode(p.customer(), p.ref());
        }
        assertNotEquals(first, second);
        // the earlier code is no longer the one being checked, so it counts as a wrong guess
        verify(p.collector(), p.ref(), first, 400);
        verify(p.collector(), p.ref(), second, 200);
    }

    // ------------------------------------------------------------------ verification and collection

    @Test
    void verifiedHandoverIsRequiredOnceAndIsRecorded() throws Exception {
        Pickup p = confirmedPickup();

        transition(p.collector(), p.ref(), "{\"status\":\"COLLECTED\"}", 409);

        String code = issueCode(p.customer(), p.ref());
        String verified = verify(p.collector(), p.ref(), code, 200);
        String reference = JsonPath.read(verified, "$.handoverReference");
        assertTrue(reference.startsWith("HO-"));

        // the same code cannot be used twice
        verify(p.collector(), p.ref(), code, 409);

        transition(p.collector(), p.ref(), "{\"status\":\"COLLECTED\",\"note\":\"At the door\"}", 200);

        String customerView = mvc.perform(get("/api/pickups/" + p.ref()).cookie(p.customer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(reference, JsonPath.read(customerView, "$.handover.handoverReference"));
        assertNotNull(JsonPath.read(customerView, "$.handover.collectedAt"));
        // collected: the customer cannot see the collector's or customer's internal IDs
        assertNull(JsonPath.read(customerView, "$.handover.collectorId"));

        String adminView = mvc.perform(get("/api/pickups/" + p.ref()).cookie(p.admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(reference, JsonPath.read(adminView, "$.handover.handoverReference"));
        assertNotNull(JsonPath.read(adminView, "$.handover.collectorId"));
        assertNotNull(JsonPath.read(adminView, "$.handover.customerId"));

        String list = mvc.perform(get("/api/admin/handovers?size=100").cookie(p.admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(((List<?>) JsonPath.read(list, "$.content[?(@.handoverReference=='" + reference + "')]")).isEmpty());

        String publicPage = mvc.perform(get("/api/track/" + p.ref()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(reference, JsonPath.read(publicPage, "$.handoverReference"));
        assertFalse(publicPage.contains(code));
        assertFalse(publicPage.contains("Imran"));

        String events = mvc.perform(get("/api/pickups/" + p.ref()).cookie(p.customer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> collectedDetails = JsonPath.read(events, "$.timeline[?(@.status=='COLLECTED')].detail");
        assertTrue(collectedDetails.get(0).contains(reference));
    }

    @Test
    void onlyTheAssignedCollectorCanVerifyAndOnlyTheCustomerCanIssue() throws Exception {
        Pickup p = confirmedPickup();
        // another customer cannot issue a code for this pickup
        mvc.perform(post("/api/pickups/" + p.ref() + "/handover/code")
                        .cookie(p.otherCustomer()).header("X-Requested-With", "test"))
                .andExpect(status().isNotFound());
        // a collector who is not assigned cannot verify
        verify(p.otherCollector(), p.ref(), "123456", 404);
        // the customer cannot verify; collectors and customers have separate roles
        verify(p.customer(), p.ref(), "123456", 403);
    }

    @Test
    void codeCannotBeIssuedBeforeACollectorIsAssigned() throws Exception {
        Cookie customer = login("customer2@demo.example");
        LocalDate date = LocalDate.now().plusDays(25 + (DAY.getAndIncrement() % 30));
        String created = mvc.perform(post("/api/pickups")
                        .cookie(customer)
                        .header("X-Requested-With", "test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(created, "$.reference");
        mvc.perform(post("/api/pickups/" + ref + "/handover/code")
                        .cookie(customer).header("X-Requested-With", "test"))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedCodesAreRejectedWithoutCountingAsAttempts() throws Exception {
        Pickup p = confirmedPickup();
        String code = issueCode(p.customer(), p.ref());
        verify(p.collector(), p.ref(), "12ab56", 400);
        verify(p.collector(), p.ref(), "12345", 400);
        // none of the above used up an attempt, so the next wrong guess reports four attempts left
        String body = verify(p.collector(), p.ref(), wrongCodeFor(code), 400);
        String message = JsonPath.read(body, "$.fieldErrors.code");
        assertTrue(message.contains("4 attempts left"));
    }

    // ------------------------------------------------------------------ photo evidence

    @Test
    void photoIsOptionalAndValidatedByContentNotByName() throws Exception {
        Pickup p = confirmedPickup();

        // no photo before a verified handover
        mvc.perform(multipart("/api/pickups/" + p.ref() + "/handover/photo")
                        .file(new MockMultipartFile("photo", "evidence.png", "image/png", PNG))
                        .cookie(p.collector()).header("X-Requested-With", "test"))
                .andExpect(status().isConflict());

        String code = issueCode(p.customer(), p.ref());
        verify(p.collector(), p.ref(), code, 200);

        // a script disguised as a PNG is refused
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/pickups/" + p.ref() + "/handover/photo")
                        .file(new MockMultipartFile("photo", "evidence.png", "image/png", script))
                        .cookie(p.collector()).header("X-Requested-With", "test"))
                .andExpect(status().isBadRequest());

        mvc.perform(multipart("/api/pickups/" + p.ref() + "/handover/photo")
                        .file(new MockMultipartFile("photo", "evidence.png", "image/png", PNG))
                        .cookie(p.collector()).header("X-Requested-With", "test"))
                .andExpect(status().isOk());

        // the photo is served to the customer and administrator, never to other people
        mvc.perform(get("/api/pickups/" + p.ref() + "/handover/photo").cookie(p.customer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(get("/api/pickups/" + p.ref() + "/handover/photo").cookie(p.admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pickups/" + p.ref() + "/handover/photo").cookie(p.otherCustomer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pickups/" + p.ref() + "/handover/photo").cookie(p.otherCollector()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pickups/" + p.ref() + "/handover/photo").cookie(p.recycler()))
                .andExpect(status().isNotFound());

        // the photo is reflected in the handover details
        String detail = mvc.perform(get("/api/pickups/" + p.ref()).cookie(p.customer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(true, JsonPath.read(detail, "$.handover.hasPhoto"));
    }

    @Test
    void adminHandoverListRequiresAdministrator() throws Exception {
        mvc.perform(get("/api/admin/handovers").cookie(login("customer@demo.example")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/handovers").cookie(login("admin@demo.example")))
                .andExpect(status().isOk());
    }
}
