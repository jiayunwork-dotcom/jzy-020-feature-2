package com.flamelab.inverse.api;

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
 * Many inverse jobs searching in parallel must each accumulate their own lean
 * and rich convergence sequences; no job may observe or overwrite another
 * job's traces, and each stored trace must match a fresh serial run.
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

    private record Case(String fuel, double intake, double target, String side) {
        String body() {
            return "{\"fuel\":\"" + fuel + "\",\"intakeTemperature\":" + intake
                    + ",\"targetTemperature\":" + target + ",\"side\":\"" + side + "\"}";
        }
    }

    @Test
    void parallelInverseJobsKeepSeparateTraces() throws Exception {
        List<Case> cases = List.of(
                new Case("methane", 298.15, 2150.0, "BOTH"),
                new Case("ethane", 298.15, 2150.0, "BOTH"),
                new Case("methane", 298.15, 2000.0, "LEAN"),
                new Case("methane", 298.15, 2000.0, "RICH"),
                new Case("ethane", 310.0, 2050.0, "BOTH"),
                new Case("methane", 400.0, 2150.0, "BOTH"),
                new Case("ethane", 298.15, 1900.0, "LEAN"),
                new Case("methane", 350.0, 2200.0, "BOTH"));

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
                    assertEquals("CONVERGED", job.get("status").asText(),
                            "parallel inverse job failed: " + job.get("error"));
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

        for (int i = 0; i < cases.size(); i++) {
            JsonNode stored = json.readTree(
                    http.getForEntity("/api/inverse-jobs/" + jobIds.get(i), String.class).getBody());
            JsonNode reference = json.readTree(post(cases.get(i).body()).getBody());

            assertEquals(cases.get(i).fuel(), stored.get("fuel").asText());
            assertEquals(cases.get(i).target(), stored.get("targetTemperature").asDouble(), 0.0);
            assertEquals(cases.get(i).side(), stored.get("side").asText());
            assertEquals(reference.get("leanSequence"), stored.get("leanSequence"),
                    "inverse job " + jobIds.get(i) + " lean trace was contaminated");
            assertEquals(reference.get("richSequence"), stored.get("richSequence"),
                    "inverse job " + jobIds.get(i) + " rich trace was contaminated");
            assertEquals(reference.get("leanSolution"), stored.get("leanSolution"));
            assertEquals(reference.get("richSolution"), stored.get("richSolution"));

            assertTraceCloses(stored.get("leanSequence"), stored.get("leanSolution"),
                    cases.get(i).target(), stored.get("temperatureTolerance").asDouble());
            assertTraceCloses(stored.get("richSequence"), stored.get("richSolution"),
                    cases.get(i).target(), stored.get("temperatureTolerance").asDouble());
        }
    }

    private void assertTraceCloses(JsonNode sequence, JsonNode solution, double target, double tolerance) {
        if (solution == null || solution.isNull()) {
            return; // side not requested
        }
        double finalPhi = solution.get("equivalenceRatio").asDouble();
        JsonNode last = sequence.get(sequence.size() - 1);
        assertEquals(finalPhi, last.get("equivalenceRatio").asDouble(), 1e-12,
                "trace must end at the reported phi");
        double finalDeviation = Math.abs(last.get("temperatureDeviation").asDouble());
        assertTrue(finalDeviation <= tolerance, "last probe must close within tolerance");
        assertTrue(Math.abs(solution.get("flameTemperature").asDouble() - target) <= tolerance);
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/inverse-jobs", new HttpEntity<>(body, headers), String.class);
    }
}
