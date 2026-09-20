package com.flamelab.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** HTTP-level regression tests for the inverse solve: validation, demo job, persistence and retrieval. */
@SpringBootTest
@AutoConfigureMockMvc
class InverseApiTest {

    private static final double TARGET = 2200.0;

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
    void builtInInverseDemoJobIsSeededWithBothSidesConverged() throws Exception {
        MvcResult result = mvc.perform(get("/api/inverse-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label", is("demo-inverse-methane")))
                .andExpect(jsonPath("$.fuel", is("methane")))
                .andExpect(jsonPath("$.intakeTemperature", is(298.15)))
                .andExpect(jsonPath("$.targetTemperature", is(TARGET)))
                .andExpect(jsonPath("$.requestedSide", is("BOTH")))
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.peakTemperature", greaterThan(TARGET)))
                .andExpect(jsonPath("$.peakEquivalenceRatio", greaterThan(0.9)))
                .andExpect(jsonPath("$.peakEquivalenceRatio", lessThan(1.2)))
                .andExpect(jsonPath("$.lean.converged", is(true)))
                .andExpect(jsonPath("$.lean.equivalenceRatio", lessThan(1.0)))
                .andExpect(jsonPath("$.lean.flameTemperature", closeTo(TARGET, 0.5)))
                .andExpect(jsonPath("$.lean.productMoleFractions", hasKey("CO2")))
                .andExpect(jsonPath("$.lean.productMoleFractions", hasKey("H2O")))
                .andExpect(jsonPath("$.lean.productMoleFractions", hasKey("N2")))
                .andExpect(jsonPath("$.lean.maxAtomResidual", closeTo(0.0, 1e-9)))
                .andExpect(jsonPath("$.rich.converged", is(true)))
                .andExpect(jsonPath("$.rich.equivalenceRatio", greaterThan(1.0)))
                .andExpect(jsonPath("$.rich.flameTemperature", closeTo(TARGET, 0.5)))
                .andExpect(jsonPath("$.rich.maxAtomResidual", closeTo(0.0, 1e-9)))
                .andExpect(jsonPath("$.error", nullValue()))
                .andReturn();

        JsonNode job = json.readTree(result.getResponse().getContentAsString());
        double leanPhi = job.get("lean").get("equivalenceRatio").asDouble();
        double richPhi = job.get("rich").get("equivalenceRatio").asDouble();
        assertTrue(Math.abs(leanPhi - richPhi) > 0.1, "demo lean and rich roots must be clearly different");

        double tolerance = job.get("temperatureTolerance").asDouble();
        for (String side : new String[]{"lean", "rich"}) {
            JsonNode trace = job.get(side).get("convergence");
            assertTrue(trace.size() > 1, side + " side must record its outer convergence sequence");
            for (int i = 0; i < trace.size(); i++) {
                assertEquals(i, trace.get(i).get("step").asInt(), side + " trace steps must be sequential");
            }
            double lastDeviation = Math.abs(
                    trace.get(trace.size() - 1).get("temperatureDeviation").asDouble());
            assertTrue(lastDeviation <= tolerance,
                    side + " trace must close below the pinned temperature tolerance");
        }
    }

    @Test
    void inverseSolveCreatesRetrievableJob() throws Exception {
        MvcResult created = mvc.perform(post("/api/inverse-jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"ethane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.temperatureTolerance", notNullValue()))
                .andExpect(jsonPath("$.maxOuterSteps", notNullValue()))
                .andExpect(jsonPath("$.lean.equivalenceRatio", lessThan(1.0)))
                .andExpect(jsonPath("$.rich.equivalenceRatio", greaterThan(1.0)))
                .andReturn();
        JsonNode job = json.readTree(created.getResponse().getContentAsString());
        long id = job.get("id").asLong();

        mvc.perform(get("/api/inverse-jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuel", is("ethane")))
                .andExpect(jsonPath("$.targetTemperature", is(TARGET)))
                .andExpect(jsonPath("$.lean.equivalenceRatio",
                        is(job.get("lean").get("equivalenceRatio").asDouble())))
                .andExpect(jsonPath("$.rich.equivalenceRatio",
                        is(job.get("rich").get("equivalenceRatio").asDouble())))
                .andExpect(jsonPath("$.lean.convergence", notNullValue()))
                .andExpect(jsonPath("$.rich.convergence", notNullValue()));
    }

    @Test
    void leanOnlyRequestSolvesOnlyTheLeanSide() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":2200.0,\"side\":\"lean\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.requestedSide", is("LEAN")))
                .andExpect(jsonPath("$.lean.converged", is(true)))
                .andExpect(jsonPath("$.lean.equivalenceRatio", lessThan(1.0)))
                .andExpect(jsonPath("$.rich", nullValue()));
    }

    @Test
    void invalidSideIsRejectedBeforeTheOuterSolveStarts() throws Exception {
        int before = json.readTree(mvc.perform(get("/api/inverse-jobs"))
                .andReturn().getResponse().getContentAsString()).size();

        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":2200.0,\"side\":\"MIDDLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("INVALID_SIDE")));

        int after = json.readTree(mvc.perform(get("/api/inverse-jobs"))
                .andReturn().getResponse().getContentAsString()).size();
        assertEquals(before, after, "a rejected request must not create an inverse job");
    }

    @Test
    void nonPositiveTargetTemperatureIsRejected() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":0.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_TARGET_TEMPERATURE")));
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":-2200.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_TARGET_TEMPERATURE")));
    }

    @Test
    void nonFiniteTargetTemperatureIsRejected() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":1e999,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_FINITE_VALUE")));
    }

    @Test
    void missingFieldsAreRejectedWithTypedError() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("MISSING_FIELD")));
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":2200.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("MISSING_FIELD")));
    }

    @Test
    void unknownFuelIsRejected() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"propane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("UNKNOWN_FUEL")));
    }

    @Test
    void nonPositiveIntakeTemperatureIsRejected() throws Exception {
        mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":0.0,"
                                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_INTAKE_TEMPERATURE")));
    }

    @Test
    void targetAbovePeakFailsUnreachableAndReportsThePeak() throws Exception {
        MvcResult result = mvc.perform(post("/api/inverse-jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                                + "\"targetTemperature\":3000.0,\"side\":\"BOTH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.error.type", is("TARGET_UNREACHABLE")))
                .andExpect(jsonPath("$.peakTemperature", notNullValue()))
                .andExpect(jsonPath("$.peakTemperature", lessThan(3000.0)))
                .andExpect(jsonPath("$.peakEquivalenceRatio", notNullValue()))
                .andExpect(jsonPath("$.lean.converged", is(false)))
                .andExpect(jsonPath("$.lean.error.type", is("TARGET_UNREACHABLE")))
                .andExpect(jsonPath("$.rich.converged", is(false)))
                .andExpect(jsonPath("$.rich.error.type", is("TARGET_UNREACHABLE")))
                .andReturn();
        JsonNode job = json.readTree(result.getResponse().getContentAsString());

        // The failed inverse job is still retrievable by id, peak estimate included.
        mvc.perform(get("/api/inverse-jobs/" + job.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.error.type", is("TARGET_UNREACHABLE")))
                .andExpect(jsonPath("$.peakTemperature", is(job.get("peakTemperature").asDouble())))
                .andExpect(jsonPath("$.peakEquivalenceRatio",
                        is(job.get("peakEquivalenceRatio").asDouble())));
    }

    @Test
    void ethaneAndMethaneInverseResultsDoNotCollapse() throws Exception {
        JsonNode methane = postAndRead("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}");
        JsonNode ethane = postAndRead("{\"fuel\":\"ethane\",\"intakeTemperature\":298.15,"
                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}");

        double methaneLean = methane.get("lean").get("equivalenceRatio").asDouble();
        double ethaneLean = ethane.get("lean").get("equivalenceRatio").asDouble();
        double methaneRich = methane.get("rich").get("equivalenceRatio").asDouble();
        double ethaneRich = ethane.get("rich").get("equivalenceRatio").asDouble();
        assertTrue(Math.abs(methaneLean - ethaneLean) > 0.01,
                "lean roots of methane and ethane must differ, got " + methaneLean + " vs " + ethaneLean);
        assertTrue(Math.abs(methaneRich - ethaneRich) > 0.01,
                "rich roots of methane and ethane must differ, got " + methaneRich + " vs " + ethaneRich);
        assertTrue(Math.abs(methane.get("peakTemperature").asDouble()
                        - ethane.get("peakTemperature").asDouble()) > 1.0,
                "the two fuels must peak at different temperatures");
    }

    @Test
    void hotterIntakeShiftsTheRootsForTheSameTarget() throws Exception {
        JsonNode cold = postAndRead("{\"fuel\":\"methane\",\"intakeTemperature\":298.15,"
                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}");
        JsonNode hot = postAndRead("{\"fuel\":\"methane\",\"intakeTemperature\":398.15,"
                + "\"targetTemperature\":2200.0,\"side\":\"BOTH\"}");

        double leanCold = cold.get("lean").get("equivalenceRatio").asDouble();
        double leanHot = hot.get("lean").get("equivalenceRatio").asDouble();
        double richCold = cold.get("rich").get("equivalenceRatio").asDouble();
        double richHot = hot.get("rich").get("equivalenceRatio").asDouble();
        assertTrue(leanHot < leanCold,
                "hotter intake must reach the same target with a leaner mixture: "
                        + leanHot + " vs " + leanCold);
        assertTrue(richHot > richCold,
                "hotter intake must push the rich root further rich: " + richHot + " vs " + richCold);
        // Every reported ratio still forward-verifies against the same target.
        for (JsonNode job : new JsonNode[]{cold, hot}) {
            for (String side : new String[]{"lean", "rich"}) {
                double flame = job.get(side).get("flameTemperature").asDouble();
                assertTrue(Math.abs(flame - TARGET) <= job.get("temperatureTolerance").asDouble(),
                        side + " root must forward-verify against the target");
            }
        }
    }

    @Test
    void unknownInverseJobIdGivesTypedNotFound() throws Exception {
        mvc.perform(get("/api/inverse-jobs/424242"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type", is("JOB_NOT_FOUND")));
    }

    @Test
    void inverseJobsListContainsEverySubmittedJob() throws Exception {
        mvc.perform(get("/api/inverse-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(notNullValue())));
    }

    private JsonNode postAndRead(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/inverse-jobs")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
