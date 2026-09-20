package com.flamelab.inverse.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HTTP-level regression tests for inverse jobs: demo seed, persistence, traces, errors. */
@SpringBootTest
@AutoConfigureMockMvc
class InverseJobApiTest {

    private static final double TARGET = 2150.0;
    private static final double TOLERANCE = 1.0;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String db = Files.createTempFile("flamelab-inverse-api-test", ".db").toAbsolutePath().toString();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Test
    void builtInInverseDemoIsSeededWithBothSidesConverged() throws Exception {
        MvcResult result = mvc.perform(get("/api/inverse-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label", is("demo-inverse-methane-2150")))
                .andExpect(jsonPath("$.fuel", is("methane")))
                .andExpect(jsonPath("$.intakeTemperature", is(298.15)))
                .andExpect(jsonPath("$.targetTemperature", is(TARGET)))
                .andExpect(jsonPath("$.side", is("BOTH")))
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.peakTemperature", notNullValue()))
                .andExpect(jsonPath("$.peakEquivalenceRatio", notNullValue()))
                .andExpect(jsonPath("$.leanSolution", notNullValue()))
                .andExpect(jsonPath("$.richSolution", notNullValue()))
                .andExpect(jsonPath("$.error", nullValue()))
                .andReturn();
        JsonNode job = json.readTree(result.getResponse().getContentAsString());

        double tolerance = job.get("temperatureTolerance").asDouble();
        JsonNode lean = job.get("leanSolution");
        JsonNode rich = job.get("richSolution");
        assertTrue(lean.get("equivalenceRatio").asDouble() < 1.0, "demo lean phi must be below 1");
        assertTrue(rich.get("equivalenceRatio").asDouble() > 1.0, "demo rich phi must be above 1");
        assertTrue(Math.abs(lean.get("temperatureDeviation").asDouble()) <= tolerance);
        assertTrue(Math.abs(rich.get("temperatureDeviation").asDouble()) <= tolerance);
        assertTrue(Math.abs(lean.get("flameTemperature").asDouble() - TARGET) <= tolerance);
        assertTrue(Math.abs(rich.get("flameTemperature").asDouble() - TARGET) <= tolerance);
        assertTrue(lean.get("productMoleFractions").has("N2"));
        assertTrue(lean.get("maxAtomResidual").asDouble() < 1e-9);
        assertTrue(job.get("leanSequence").size() >= 2);
        assertTrue(job.get("richSequence").size() >= 2);
        assertTrue(job.get("peakTemperature").asDouble() > TARGET,
                "target must be a notch below the reported peak");
    }

    @Test
    void inverseJobIsCreatedAndRetrievedByNumber() throws Exception {
        MvcResult created = mvc.perform(post("/api/inverse-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ethane", 298.15, TARGET, "BOTH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.temperatureTolerance", closeTo(TOLERANCE, 1e-9)))
                .andExpect(jsonPath("$.maxOuterSteps", notNullValue()))
                .andReturn();
        JsonNode job = json.readTree(created.getResponse().getContentAsString());
        long id = job.get("id").asLong();

        mvc.perform(get("/api/inverse-jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuel", is("ethane")))
                .andExpect(jsonPath("$.leanSequence", notNullValue()))
                .andExpect(jsonPath("$.richSequence", notNullValue()));
    }

    @Test
    void leanSideOnlyHasNoRichSolution() throws Exception {
        JsonNode job = postAndRead(body("methane", 298.15, TARGET, "LEAN"));
        org.junit.jupiter.api.Assertions.assertEquals("CONVERGED", job.get("status").asText());
        org.junit.jupiter.api.Assertions.assertFalse(job.get("leanSolution").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(job.get("richSolution").isNull());
    }

    @Test
    void targetAbovePeakFailsJobAndReportsPeak() throws Exception {
        MvcResult result = mvc.perform(post("/api/inverse-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("methane", 298.15, 4000.0, "BOTH")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.error.type", is("TARGET_UNREACHABLE")))
                .andExpect(jsonPath("$.leanSolution", nullValue()))
                .andExpect(jsonPath("$.richSolution", nullValue()))
                .andExpect(jsonPath("$.peakTemperature", notNullValue()))
                .andExpect(jsonPath("$.peakEquivalenceRatio", notNullValue()))
                .andReturn();
        JsonNode job = json.readTree(result.getResponse().getContentAsString());
        assertTrue(job.get("peakTemperature").asDouble() < 4000.0, "reported peak must be below target");
        assertTrue(job.get("peakTemperature").asDouble() > 2000.0);

        // The failed job is retrievable by id with peak and trace intact.
        long id = job.get("id").asLong();
        mvc.perform(get("/api/inverse-jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.error.type", is("TARGET_UNREACHABLE")))
                .andExpect(jsonPath("$.peakTemperature", notNullValue()));
    }

    @Test
    void illegalSideIsRejectedBeforeSolving() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body("methane", 298.15, TARGET, "MIDDLE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("ILLEGAL_SIDE")));
    }

    @Test
    void nonPositiveTargetIsRejectedBeforeSolving() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body("methane", 298.15, 0.0, "BOTH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_TARGET_TEMPERATURE")));
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content(body("methane", 298.15, -5.0, "BOTH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_TARGET_TEMPERATURE")));
    }

    @Test
    void nonFiniteAndMissingFieldsAreRejectedWithTypedErrors() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,\"targetTemperature\":1e999,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_FINITE_VALUE")));
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"targetTemperature\":2150.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("MISSING_FIELD")));
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,\"targetTemperature\":2150.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("MISSING_FIELD")));
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("MALFORMED_REQUEST")));
    }

    @Test
    void unknownInverseJobGivesTypedNotFound() throws Exception {
        mvc.perform(get("/api/inverse-jobs/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type", is("JOB_NOT_FOUND")));
    }

    @Test
    void hotterIntakeShiftsLeanRootTowardStoichiometric() throws Exception {
        JsonNode cold = postAndRead(body("methane", 298.15, TARGET, "BOTH"));
        JsonNode hot = postAndRead(body("methane", 450.0, TARGET, "BOTH"));
        double coldLean = cold.get("leanSolution").get("equivalenceRatio").asDouble();
        double hotLean = hot.get("leanSolution").get("equivalenceRatio").asDouble();
        assertTrue(hotLean < coldLean, "hotter intake must need a leaner mix for the same target");
        assertTrue(Math.abs(hot.get("leanSolution").get("temperatureDeviation").asDouble()) <= TOLERANCE);
    }

    @Test
    void leanAndRichTracesDoNotMix() throws Exception {
        JsonNode job = postAndRead(body("methane", 298.15, TARGET, "BOTH"));
        JsonNode leanSeq = job.get("leanSequence");
        JsonNode richSeq = job.get("richSequence");
        for (JsonNode step : leanSeq) {
            assertTrue(step.get("equivalenceRatio").asDouble() <= job.get("peakEquivalenceRatio").asDouble() * 1.01,
                    "lean trace must stay on the lean side");
        }
        for (JsonNode step : richSeq) {
            assertTrue(step.get("equivalenceRatio").asDouble() >= job.get("peakEquivalenceRatio").asDouble() * 0.99,
                    "rich trace must stay on the rich side");
        }
    }

    @Test
    void listContainsEveryInverseJob() throws Exception {
        postAndRead(body("methane", 298.15, TARGET, "LEAN"));
        postAndRead(body("ethane", 320.0, 2000.0, "RICH"));
        mvc.perform(get("/api/inverse-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(notNullValue())));
    }

    private String body(String fuel, double intake, double target, String side) {
        return "{\"fuel\":\"" + fuel + "\",\"intakeTemperature\":" + intake
                + ",\"targetTemperature\":" + target + ",\"side\":\"" + side + "\"}";
    }

    private JsonNode postAndRead(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/inverse-jobs")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
