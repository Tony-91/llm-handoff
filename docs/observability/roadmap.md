# Observability Roadmap

Goal: know when ai-handoff.online is down, slow, or failing, and have the data to find out why.

Decisions:
- Metrics and dashboards run in **self-hosted Prometheus + Grafana** containers (bound to 127.0.0.1; prod access via SSH tunnel). Logs and alerting targets are decided in M4/M5.
- Alerts are delivered by **email**.
- Scope ends at **M3**. Distributed tracing is out of scope.

Each milestone is a small, independently deployable change with an exit check.

## M0 – Test suite green (prerequisite)
- Make Flyway migrations run on the H2 test database: vendor-specific V3 (`db/vendor/{mysql,h2}`), prod migration unchanged.
- Fix stale tests (`@WebMvcTest` security beans, manifest/gitsync service tests).
- **Exit:** `mvn test` passes, including `ActuatorIntegrationTest`.

## M1 – Lock down the endpoints and add health checks (small)
- Audit endpoints: authenticate or internally-bind the sensitive endpoints.
- Health checks give us green light.
- Make only `/actuator/health` (liveness and readiness) public.
- Move metrics and prometheus to an internal management port (for example 8081) that isn't published to the host. Set `show-details=when-authorized`. Remove the deprecated `prometheus.enabled` setting.
- Add a compose healthcheck for the backend that hits `/actuator/health/readiness`. Make the frontend wait until the backend reports healthy.
- **Done when:** from outside, `/actuator/prometheus` doesn't respond, `docker compose ps` shows the backend as healthy, and the tests cover both.

## M2 – Collect metrics and build dashboards (medium)
- Add Prometheus and Grafana containers to compose, bound to 127.0.0.1, with a scrape config, a retention limit and a persistent volume.
- Set up Grafana dashboards automatically for JVM, HTTP traffic (request rate, errors, response time), the database connection pool and MySQL.
- On prod, open Grafana through an SSH tunnel rather than exposing it publicly.
- **Done when:** the dashboards show live data locally and on the server.
**Lesson learned:**
- You can query Prometheus directly without Grafana — e.g. `http://localhost:9090` has a built-in query UI, which is handy for debugging whether a metric exists before building a panel around it.
- Prometheus = collector + storage + query engine.

## M3 – App-level metrics
- Counters/timers for: logins (provider, outcome), OAuth failures, git sync (duration, outcome), webhooks received, manifest saves and validation failures, handoff generations.
- Build `GitHubClient` from the injected `RestClient.Builder` so outbound GitHub calls are recorded as `http.client.requests`.
- No high-cardinality tags (no project or user IDs).
- **Exit:** new metrics visible in Grafana Cloud; unit tests assert them via `MeterRegistry`.
