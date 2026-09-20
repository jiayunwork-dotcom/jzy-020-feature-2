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
 * Many jobs iterating in parallel must each accumulate their own residual
 * sequence; no job may observe or overwrite another job's iterations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParallelJobsTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String db = Files.createTempFile("flamelab-parallel-test", ".db").toAbsolutePath().toString();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
    }

    private record Case(String fuel, double phi, double intakeTemperature) {
        String body() {
            return "{\"fuel\":\"" + fuel + "\",\"equivalenceRatio\":" + phi
                    + ",\"intakeTemperature\":" + intakeTemperature + "}";
        }
    }

    @Test
    void parallelJobsKeepSeparateResidualCurves() throws Exception {
        List<Case> cases = List.of(
                new Case("methane", 1.0, 298.15),
                new Case("ethane", 1.0, 298.15),
                new Case("methane", 0.8, 298.15),
                new Case("methane", 1.2, 298.15),
                new Case("ethane", 0.9, 310.0),
                new Case("methane", 1.0, 350.0),
                new Case("ethane", 1.1, 298.15),
                new Case("methane", 0.9, 400.0));

        // Fire all solves at once.
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
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(cases.size(), jobIds.size());
        assertEquals(cases.size(), jobIds.values().stream().distinct().count(),
                "parallel jobs must get distinct ids");

        // Each stored job must equal a fresh serial solve of the same inputs,
        // iteration point for iteration point.
        for (int i = 0; i < cases.size(); i++) {
            JsonNode stored = json.readTree(
                    http.getForEntity("/api/jobs/" + jobIds.get(i), String.class).getBody());
            JsonNode reference = json.readTree(post(cases.get(i).body()).getBody());

            assertEquals(cases.get(i).fuel(), stored.get("fuel").asText());
            assertEquals(cases.get(i).phi(), stored.get("equivalenceRatio").asDouble(), 0.0);
            assertEquals(cases.get(i).intakeTemperature(), stored.get("intakeTemperature").asDouble(), 0.0);
            assertEquals(reference.get("iterations"), stored.get("iterations"),
                    "job " + jobIds.get(i) + " residual curve was contaminated by another job");
            assertEquals(reference.get("finalTemperature"), stored.get("finalTemperature"));

            // The stored curve itself must be a strictly decreasing residual sequence
            // whose last point closes below the pinned tolerance.
            JsonNode iterations = stored.get("iterations");
            double tolerance = stored.get("enthalpyTolerance").asDouble();
            double previous = Double.MAX_VALUE;
            for (JsonNode point : iterations) {
                double residual = Math.abs(point.get("enthalpyResidual").asDouble());
                assertTrue(residual < previous, "residual curve must strictly decrease");
                previous = residual;
            }
            assertTrue(previous <= tolerance, "last residual must close below tolerance");
        }
    }

    private ResponseEntity<String> post(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/jobs", new HttpEntity<>(body, headers), String.class);
    }
}
