# Prometheus Telemetry in handoff

How I implemented metrics collection for the backend. 
Prometheus container and visualized in Grafana. This covers the custom application metrics
(`manifest_saves_total`, `manifest_save_duration`) added to `ManifestService`.

## Architecture

```
React SPA ──> Backend (Spring Boot, :8080) ──> MySQL
                   │
                   │  Micrometer registry writes time-series
                   ▼
            Actuator /actuator/prometheus  (management server :8081, never published)
                   │
                   │  HTTP scrape every 15s over the docker network
                   ▼
            Prometheus (:9090, 127.0.0.1)  ──>  Grafana dashboards (:3001, 127.0.0.1)
```

## Instrumentation: extending `ManifestService` without violating Open/Closed

The Open/Closed Principle: extend behavior without modifying the intent of existing code and more practically, add instrumentation **additively** without rewriting or hard-wiring metrics plumbing into every method.

Accomplished this by injecting a `MeterRegistry` bean:

```java
@Service
@Transactional
public class ManifestService {

    private final Counter manifestSaves;
    private final Timer manifestSaveDuration;

    public ManifestService(
            ManifestRepository manifestRepository,
            ProjectRepository projectRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry          // injected by Spring
    ) {
        this.manifestRepository = manifestRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
        this.manifestSaves = meterRegistry.counter("manifest_saves_total");
        this.manifestSaveDuration = Timer.builder("manifest_save_duration")
                .description("Time taken to validate and persist a manifest")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }
```

Why this respects Open/Closed:

- `ManifestService` does not know Prometheus exists. It depends on the Micrometer
  abstraction (`MeterRegistry`); Spring Boot wires a `PrometheusMeterRegistry` bean at startup. Swapping backends (Prometheus → another registry) requires zero changes here.
- Instrumentation is additive: the counters are created in the constructor and used inside
  `saveManifest`, but no existing method signature or business logic changed. New metrics
  in other services follow the same pattern — inject `MeterRegistry`, register a meter,
  update it — without touching core logic.
- The registry acts as a factory: `Counter` and `Timer` are not themselves Spring beans,
  they're meters *derived from* the injected bean. That keeps the dependency graph clean —
  one bean feeds every custom metric in the service.

### Where the metrics fire

`saveManifest` wraps its entire body in `Timer.record(...)`, so `manifest_save_duration`
captures the full validate→persist path:

```java
public ManifestResponse saveManifest(UUID projectId, SaveManifestRequest request, UUID userId) {
    return manifestSaveDuration.record(() -> {
        Project project = getProjectWithOwnershipCheck(projectId, userId);
        ManifestData manifestData = parseManifestData(request.content());
        ...
        Manifest saved = manifestRepository.save(manifest);
        manifestSaves.increment();                 // count every successful save
        return ManifestResponse.from(saved, manifestData);
    });
}
```

## Exposed metrics

| Metric | Type | Meaning |
|---|---|---|
| `manifest_saves_total` | Counter | Total manifests saved since process start |
| `manifest_save_duration` | Timer | Distribution of save latency; `publishPercentileHistogram()` makes `_bucket` series available for `histogram_quantile` in Grafana |
| `http_server_requests_seconds_*` | Timer family (auto) | Request rate, error rate, latency per URI — auto-instrumented by Micrometer's web binder |
| `jvm_*`, `hikaricp_*` | Gauges/timers (auto) | JVM memory, GC pause, DB connection pool — free with Actuator |

All series carry `application="llm-handoff-backend"` (`management.metrics.tags.application`),
which is how the dashboards filter.
