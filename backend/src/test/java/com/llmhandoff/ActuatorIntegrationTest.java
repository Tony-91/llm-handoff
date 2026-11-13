package com.llmhandoff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@AutoConfigureObservability
@Import(TestcontainersConfiguration.class)
class ActuatorIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void managementServer_runsOnSeparatePort() {
        assertThat(managementPort).isNotEqualTo(serverPort);
    }

    @Test
    void healthProbes_onManagementPort_arePublic() {
        ResponseEntity<String> health = managementGet("/actuator/health");
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
        assertThat(health.getBody()).doesNotContain("\"db\":");
        assertThat(managementGet("/actuator/health/liveness").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(managementGet("/actuator/health/readiness").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void prometheus_onManagementPort_isScrapableAnonymously() {
        ResponseEntity<String> prometheus = managementGet("/actuator/prometheus");
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prometheus.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    void otherEndpoints_onManagementPort_requireAuthentication() {
        assertThat(managementGet("/actuator/metrics").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(managementGet("/actuator/env").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuator_onApplicationPort_isNotServed() {
        assertThat(applicationGet("/actuator/health").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(applicationGet("/actuator/prometheus").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(applicationGet("/actuator/metrics").getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> managementGet(String path) {
        return restTemplate.getForEntity("http://localhost:" + managementPort + path, String.class);
    }

    private ResponseEntity<String> applicationGet(String path) {
        return restTemplate.getForEntity("http://localhost:" + serverPort + path, String.class);
    }
}
