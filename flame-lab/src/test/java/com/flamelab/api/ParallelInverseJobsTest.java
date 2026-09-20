package com.flamelab.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Many inverse jobs searching in parallel must each accumulate their own
 * outer convergence sequences; no job may observe or overwrite another job's
 * trials, and the lean and rich sequences of one job must never mix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParallelInverseJobsTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String db = Files.createTempFile("flamelab-inverse-parallel-test", ".db").toAbsolutePath().toString();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    private record Case(String fuel, double intakeTemperature, double targetTemperature, String side) {
        String body() {
            return "{\"fuel\":\"" + fuel + "\",\"intakeTemperature\":" + intakeTemperature
                    + ",\"targetTemperature\":" + targetTemperature + ",\"side\":\"" + side + "\"}";
        }
    }

    @Test
    void parallelInverseJobsKeepSeparateConvergenceSequences() throws Exception {
        List<Case> cases = List.of(
                new Case("methane", 298.15, 2200.0, "BOTH"),
                new Case("ethane", 298.15, 2200.0, "BOTH"),
                new Case("methane", 298.15, 2100.0, "BOTH"),
                new Case("methane", 350.0, 2200.0, "BOTH"),
                new Case("ethane", 310.0, 2250.0, "BOTH"),
                new Case("methane", 298.15, 2250.0, "LEAN"),
                new Case("ethane", 298.15, 2300.0, "RICH"),
                new Case("methane", 400.0, 2300.0, "BOTH"));

        // Fire all inverse solves at once.
        ExecutorService pool = Executors.newFixedThreadPool(cases.size());
        CountDownLatch start = new CountDownLatch(1);
        Map<Integer, Long> jobIds = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    ResponseEntity<String> response = post(cases.get(index).body());
                    assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    JsonNode job = json.readTree(response.getBody());
                    assertEquals("CONVERGED", job.get("status").asText());
                    jobIds.put(index, job.get("id").asLong());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(cases.size(), jobIds.size());
        assertEquals(cases.size(), jobIds.values().stream().distinct().count(),
                "parallel inverse jobs must get distinct ids");

        // Each stored job must equal a fresh serial solve of the same inputs,
        // convergence sequence for convergence sequence.
        for (int i = 0; i < cases.size(); i++) {
            JsonNode stored = json.readTree(
                    http.getForEntity("/api/inverse-jobs/" + jobIds.get(i), String.class).getBody());
            JsonNode reference = json.readTree(post(cases.get(i).body()).getBody());

            assertEquals(cases.get(i).fuel(), stored.get("fuel").asText());
            assertEquals(cases.get(i).targetTemperature(), stored.get("targetTemperature").asDouble(), 0.0);
            assertEquals(reference.get("peakEquivalenceRatio"), stored.get("peakEquivalenceRatio"));
            assertEquals(reference.get("peakTemperature"), stored.get("peakTemperature"));

            double peakPhi = stored.get("peakEquivalenceRatio").asDouble();
            double tolerance = stored.get("temperatureTolerance").asDouble();
            for (String side : new String[]{"lean", "rich"}) {
                JsonNode storedSide = stored.get(side);
                JsonNode referenceSide = reference.get(side);
                if (referenceSide == null || referenceSide.isNull()) {
                    assertTrue(storedSide == null || storedSide.isNull(),
                            "side '" + side + "' must not appear unrequested");
                    continue;
                }
                assertEquals(referenceSide.get("convergence"), storedSide.get("convergence"),
                        "job " + jobIds.get(i) + " " + side + " convergence sequence was"
                                + " contaminated by another job");
                assertEquals(referenceSide.get("equivalenceRatio"), storedSide.get("equivalenceRatio"));

                // The stored sequence stays on its own side of the peak and
                // closes below the pinned temperature tolerance.
                JsonNode trace = storedSide.get("convergence");
                for (JsonNode point : trace) {
                    double phi = point.get("equivalenceRatio").asDouble();
                    if (side.equals("lean")) {
                        assertTrue(phi <= peakPhi + 1e-9,
                                "lean trace must stay on the lean side of the peak");
                    } else {
                        assertTrue(phi >= peakPhi - 1e-9,
                                "rich trace must stay on the rich side of the peak");
                    }
                }
                double lastDeviation = Math.abs(
                        trace.get(trace.size() - 1).get("temperatureDeviation").asDouble());
                assertTrue(lastDeviation <= tolerance,
                        side + " trace must close below the pinned temperature tolerance");
            }
        }
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/inverse-jobs", new HttpEntity<>(body, headers), String.class);
    }
}
