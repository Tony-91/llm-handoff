# Observability Roadmap

Goal: know when ai-handoff.online is down, slow, or failing, and have the data to find out why.

Decisions:
- Metrics and dashboards run in **self-hosted Prometheus + Grafana** containers (bound to 127.0.0.1; prod access via SSH tunnel). Logs and alerting targets are decided in M4/M5.
- Alerts are delivered by **email**.
- Scope ends at **M5**. Distributed tracing is out of scope.

Each milestone is a small, independently deployable change with an exit check.

## M0 – Test suite green (prerequisite)
- Make Flyway migrations run on the H2 test database: vendor-specific V3 (`db/vendor/{mysql,h2}`), prod migration unchanged.
- Fix stale tests (`@WebMvcTest` security beans, manifest/gitsync service tests).
- **Exit:** `mvn test` passes, including `ActuatorIntegrationTest`.

## M1 – Secure endpoints and health
- Only `/actuator/health` (liveness/readiness) is public; metrics/prometheus move to an internal management port that is not published to the host.
- `health.show-details=when-authorized`; remove deprecated `management.endpoint.prometheus.enabled`.
- Compose `healthcheck` on the backend using `/actuator/health/readiness`; frontend waits for a healthy backend.
- **Exit:** `/actuator/prometheus` unreachable from outside; `docker compose ps` shows backend `healthy`; tests cover both.

## M2 – Metrics in self-hosted Prometheus + Grafana
- Add Prometheus and Grafana services to compose, UIs bound to 127.0.0.1, with a scrape config against the backend management port, a retention limit, and persistent volumes.
- Management server listens on the container interface (`management.server.address` unset); `/actuator/prometheus` is permitAll since only the internal compose network can reach port 8081.
- Provision Grafana datasource + dashboards automatically: JVM, HTTP RED (rate, errors, duration), HikariCP, MySQL.
- On prod, access Grafana via SSH tunnel, not a public port.
- **Exit:** dashboards show live data locally and on the server.

## M3 – App-level metrics
- Counters/timers for: logins (provider, outcome), OAuth failures, git sync (duration, outcome), webhooks received, manifest saves and validation failures, handoff generations.
- Build `GitHubClient` from the injected `RestClient.Builder` so outbound GitHub calls are recorded as `http.client.requests`.
- No high-cardinality tags (no project or user IDs).
- **Exit:** new metrics visible in Grafana Cloud; unit tests assert them via `MeterRegistry`.

## M4 – Structured logs
- JSON console logs via Boot's `logging.structured.format.console`, with a request/correlation ID in MDC.
- Docker log rotation on all services.
- Alloy ships container logs to Grafana Cloud Logs (Loki).
- **Exit:** one request can be followed across log lines by its ID in Grafana Cloud.

## M5 – Alerting and operations
- Grafana Cloud alert rules: backend down, 5xx rate, p95 latency, git sync failure rate, JVM heap pressure.
- Email contact point and notification policy.
- Synthetic uptime check on https://ai-handoff.online (Grafana Cloud Synthetic Monitoring).
- Runbook (`docs/runbook.md`) and `ADR-002` for the observability stack choice.
- **Exit:** a test alert arrives by email; stopping the backend triggers a real alert.
