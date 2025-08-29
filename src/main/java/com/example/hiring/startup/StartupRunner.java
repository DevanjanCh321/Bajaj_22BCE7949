package com.example.hiring.startup;

import com.example.hiring.dto.GenerateWebhookRequest;
import com.example.hiring.dto.GenerateWebhookResponse;
import com.example.hiring.util.FinalSql;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class StartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final RestTemplate restTemplate;

    public StartupRunner(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${app.candidate.name}")
    private String name;

    @Value("${app.candidate.regNo}")
    private String regNo;

    @Value("${app.candidate.email}")
    private String email;

    @Value("${endpoints.generate}")
    private String generateUrl;

    @Value("${app.submission.useBearerPrefix:false}")
    private boolean useBearerPrefix;

    @Override
    public void run(String... args) {
        log.info("=== Starting HealthRx Hiring workflow ===");

        // 1) Generate webhook + token
        GenerateWebhookResponse gen = generateWebhook();

        if (gen == null || gen.getWebhook() == null || gen.getAccessToken() == null) {
            log.error("Failed to obtain webhook or accessToken; aborting.");
            return;
        }

        // 2) Build final SQL
        String finalQuery = FinalSql.build();
        log.info("Final SQL query constructed.");

        // 3) Persist the result locally (as requested: 'stores the result')
        Path out = Path.of("final-sql-query.sql");
        try {
            Files.writeString(out, finalQuery);
            log.info("Final SQL query saved to {}", out.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not persist SQL to file: {}", e.getMessage());
        }

        // 4) Submit to webhook with JWT in Authorization header
        submitFinalQuery(gen.getWebhook(), gen.getAccessToken(), finalQuery);

        log.info("=== Workflow complete ===");
    }

    private GenerateWebhookResponse generateWebhook() {
        log.info("Requesting webhook/token from {}", generateUrl);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GenerateWebhookRequest payload = new GenerateWebhookRequest(name, regNo, email);
        HttpEntity<GenerateWebhookRequest> request = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<GenerateWebhookResponse> resp = restTemplate
                    .postForEntity(generateUrl, request, GenerateWebhookResponse.class);

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                log.info("Received generateWebhook response: {}", resp.getBody());
                return resp.getBody();
            } else {
                log.error("Non-2xx from generateWebhook: {}", resp.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error generating webhook: {}", e.getMessage());
        }
        return null;
    }

    private void submitFinalQuery(String webhookUrl, String accessToken, String finalQuery) {
        log.info("Submitting final query to {}", webhookUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // The problem statement shows Authorization: <accessToken> (no 'Bearer ')
        String tokenValue = useBearerPrefix ? "Bearer " + accessToken : accessToken;
        headers.set("Authorization", tokenValue);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("finalQuery", finalQuery), headers);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("Submission response ({}): {}", resp.getStatusCodeValue(), resp.getBody());
        } catch (HttpClientErrorException.Unauthorized ex) {
            // Optional fallback: some gateways expect 'Bearer ' prefix.
            if (!useBearerPrefix) {
                log.warn("401 Unauthorized with raw token. Retrying with 'Bearer ' prefix …");
                headers.set("Authorization", "Bearer " + accessToken);
                HttpEntity<Map<String, String>> retry = new HttpEntity<>(Map.of("finalQuery", finalQuery), headers);
                ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, retry, String.class);
                log.info("Submission response after Bearer retry ({}): {}", resp.getStatusCodeValue(), resp.getBody());
            } else {
                throw ex;
            }
        } catch (Exception e) {
            log.error("Error submitting final query: {}", e.getMessage());
        }
    }
}
