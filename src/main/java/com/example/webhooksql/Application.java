package com.example.webhooksql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class Application implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
  private final RestTemplate http = new RestTemplate();
    static class GenerateWebhookRequest {
        public String name;
        public String regNo;
        public String email;
        public GenerateWebhookRequest(String name, String regNo, String email) {
            this.name = name; this.regNo = regNo; this.email = email;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GenerateWebhookResponse {
        public String webhook;
        public String accessToken;
    }
    static class FinalQueryRequest {
        public String finalQuery;
        public FinalQueryRequest(String finalQuery) { this.finalQuery = finalQuery; }
    }
    @Override
    public void run(String... args) throws Exception {
        String name  = getenvOrDefault("NAME",  "John Doe");
        String regNo = getenvOrDefault("REGNO", "REG12347");
        String email = getenvOrDefault("EMAIL", "john@example.com");

        String genUrl = getenvOrDefault("GEN_URL",
                "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA");
        String fallbackSubmitUrl = getenvOrDefault("SUBMIT_URL",
                "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA");

        GenerateWebhookRequest body = new GenerateWebhookRequest(name, regNo, email);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GenerateWebhookRequest> req = new HttpEntity<>(body, headers);

        System.out.println("Calling generateWebhook...");
        ResponseEntity<GenerateWebhookResponse> resp =
                http.postForEntity(genUrl, req, GenerateWebhookResponse.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Failed to generate webhook: " + resp.getStatusCode());
        }

        String webhookUrl = resp.getBody().webhook;
        String accessToken = resp.getBody().accessToken;
        if (webhookUrl == null || webhookUrl.isBlank()) {
            webhookUrl = fallbackSubmitUrl;
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("accessToken missing in response");
        }

        System.out.println("Webhook URL: " + webhookUrl);
        System.out.println("Token (first 16): " + (accessToken.length() > 16 ? accessToken.substring(0,16)+"..." : accessToken));
        String finalSql =
          "SELECT e.emp_id, e.first_name, e.last_name, d.department_name, " +
          "(SELECT COUNT(*) FROM employee e2 WHERE e2.department = e.department AND e2.dob > e.dob) AS younger_employees_count " +
          "FROM employee e JOIN department d ON d.department_id = e.department " +
          "ORDER BY e.emp_id DESC;";
        Path out = Path.of("target", "final-query.sql");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, finalSql);
        } catch (IOException e) {
            System.err.println("Failed to write final-query.sql: " + e.getMessage());
        }

        // 3) Submit with JWT
        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        submitHeaders.set("Authorization", accessToken);


        FinalQueryRequest finalBody = new FinalQueryRequest(finalSql);
        HttpEntity<FinalQueryRequest> submitReq = new HttpEntity<>(finalBody, submitHeaders);

        System.out.println("Submitting final query...");
        ResponseEntity<String> submitResp = http.postForEntity(webhookUrl, submitReq, String.class);

        System.out.println("Submit status: " + submitResp.getStatusCode());
        System.out.println("Submit body: " + submitResp.getBody());
    }

    private static String getenvOrDefault(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }
}
