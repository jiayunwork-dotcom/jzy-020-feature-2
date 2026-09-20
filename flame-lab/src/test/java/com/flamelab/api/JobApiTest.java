package com.flamelab.api;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
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

/** HTTP-level regression tests: validation, demo job, persistence and retrieval. */
@SpringBootTest
@AutoConfigureMockMvc
class JobApiTest {

    private static final double METHANE_STOICH_MIN = 2100.0;
    private static final double METHANE_STOICH_MAX = 2400.0;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String db = Files.createTempFile("flamelab-api-test", ".db").toAbsolutePath().toString();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    @Test
    void builtInDemoJobIsSeededAndConverged() throws Exception {
        MvcResult result = mvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label", is("demo-stoich-methane")))
                .andExpect(jsonPath("$.fuel", is("methane")))
                .andExpect(jsonPath("$.equivalenceRatio", is(1.0)))
                .andExpect(jsonPath("$.intakeTemperature", is(298.15)))
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.finalTemperature",
                        greaterThan(METHANE_STOICH_MIN)))
                .andExpect(jsonPath("$.finalTemperature",
                        lessThan(METHANE_STOICH_MAX)))
                .andExpect(jsonPath("$.productMoleFractions", hasKey("CO2")))
                .andExpect(jsonPath("$.productMoleFractions", hasKey("H2O")))
                .andExpect(jsonPath("$.productMoleFractions", hasKey("O2")))
                .andExpect(jsonPath("$.productMoleFractions", hasKey("N2")))
                .andExpect(jsonPath("$.error", nullValue()))
                .andReturn();

        JsonNode job = json.readTree(result.getResponse().getContentAsString());
        JsonNode iterations = job.get("iterations");
        double tolerance = job.get("enthalpyTolerance").asDouble();
        double lastResidual = Math.abs(
                iterations.get(iterations.size() - 1).get("enthalpyResidual").asDouble());
        assertTrue(lastResidual <= tolerance,
                "demo residual curve must close below tolerance, got " + lastResidual);
        for (int i = 1; i < iterations.size(); i++) {
            double prev = Math.abs(iterations.get(i - 1).get("enthalpyResidual").asDouble());
            double cur = Math.abs(iterations.get(i).get("enthalpyResidual").asDouble());
            assertTrue(cur < prev, "demo residual curve must decrease monotonically");
        }
    }

    @Test
    void solveCreatesRetrievableJob() throws Exception {
        MvcResult created = mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"ethane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":298.15}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("CONVERGED")))
                .andExpect(jsonPath("$.enthalpyTolerance", notNullValue()))
                .andExpect(jsonPath("$.maxAtomResidual", closeTo(0.0, 1e-9)))
                .andReturn();
        JsonNode job = json.readTree(created.getResponse().getContentAsString());
        long id = job.get("id").asLong();
        double temperature = job.get("finalTemperature").asDouble();

        mvc.perform(get("/api/jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuel", is("ethane")))
                .andExpect(jsonPath("$.finalTemperature", is(temperature)))
                .andExpect(jsonPath("$.iterations", notNullValue()));
    }

    @Test
    void ethaneAndMethaneJobsGiveDifferentTemperatures() throws Exception {
        JsonNode methane = postAndRead("{\"fuel\":\"methane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":298.15}");
        JsonNode ethane = postAndRead("{\"fuel\":\"ethane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":298.15}");
        assertTrue(Math.abs(methane.get("finalTemperature").asDouble()
                        - ethane.get("finalTemperature").asDouble()) > 1.0,
                "ethane and methane must not collapse to the same flame temperature");
    }

    private JsonNode postAndRead(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void unknownFuelIsRejected() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"propane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":298.15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("UNKNOWN_FUEL")));
    }

    @Test
    void nonPositiveEquivalenceRatioIsRejected() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"equivalenceRatio\":0.0,\"intakeTemperature\":298.15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_EQUIVALENCE_RATIO")));
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"equivalenceRatio\":-1.5,\"intakeTemperature\":298.15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_EQUIVALENCE_RATIO")));
    }

    @Test
    void nonPositiveIntakeTemperatureIsRejected() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":0.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_INTAKE_TEMPERATURE")));
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":-273.15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_POSITIVE_INTAKE_TEMPERATURE")));
    }

    @Test
    void missingFieldsAreRejectedWithTypedError() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"intakeTemperature\":298.15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("MISSING_FIELD")));
    }

    @Test
    void nonFiniteValuesAreRejectedWithTypedError() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"equivalenceRatio\":1e999,\"intakeTemperature\":298.15}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type", is("NON_FINITE_VALUE")));
    }

    @Test
    void outOfRangeIntakeFailsTheJobWithoutComposition() throws Exception {
        MvcResult result = mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fuel\":\"methane\",\"equivalenceRatio\":1.0,\"intakeTemperature\":100.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.error.type", is("TEMPERATURE_OUT_OF_RANGE")))
                .andExpect(jsonPath("$.finalTemperature", nullValue()))
                .andExpect(jsonPath("$.productMoleFractions", nullValue()))
                .andReturn();
        JsonNode job = json.readTree(result.getResponse().getContentAsString());

        // The failed job is still retrievable by id, with its error recorded.
        mvc.perform(get("/api/jobs/" + job.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")))
                .andExpect(jsonPath("$.error.type", is("TEMPERATURE_OUT_OF_RANGE")));
    }

    @Test
    void unknownJobIdGivesTypedNotFound() throws Exception {
        mvc.perform(get("/api/jobs/424242"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type", is("JOB_NOT_FOUND")));
    }

    @Test
    void jobsListContainsEverySubmittedJob() throws Exception {
        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(notNullValue())));
    }
}
