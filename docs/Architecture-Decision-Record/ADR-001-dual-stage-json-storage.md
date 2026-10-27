# ADR-001: Dual-Stage JSON Document Storage vs Relational Normalization for Manifests

## Status
Accepted

## Context
Project manifests track a rich, evolving set of metadata required to construct LLM context handoff prompts. This includes project overviews, environment configurations, command dictionaries, architectural decisions, developer rules, and open questions.

When designing the persistence + ingestion architecture for project manifests, I evaluated two architectural strategies:
1. **Full Relational Normalization**: Decomposing each manifest into separate database tables (e.g., `manifests`, `environments`, `commands`, `decisions`, `rules`, `open_questions`) linked via foreign keys.
2. **Dual-Stage JSON Document Storage**: Validating and mapping incoming JSON payloads into strongly-typed Java records in memory upon write, while persisting the payload as an immutable raw JSON document (`raw_content`) in MySQL linked to the project entity.

## Decision
I chose **Dual-Stage JSON Document Storage** using a two-stage lifecycle:

1. **Stage 1 (In-Memory Validation & Type Safety)**:
   * Incoming manifest payloads are deserialized via Jackson `ObjectMapper` into a strongly-typed Java `ManifestData` record hierarchy.
   * Syntax, structure, and required data contracts are strictly enforced at the service boundary (`ManifestService`). Invalid JSON payloads are rejected before database execution.
   * Extraneous / unexpected keys are safely handled via `@JsonIgnoreProperties(ignoreUnknown = true)`.

2. **Stage 2 (Document Persistence & Snapshotting)**:
   * Validated payloads are persisted as a single `raw_content` document in the MySQL `manifests` table (`LONGTEXT`).
   * Each save creates an immutable, timestamped record preserving snapshot version history for each project.
   * On prompt generation (`/api/projects/{id}/handoff`), the latest document is retrieved in a single-row query and deserialized back into `ManifestData` for Markdown rendering.

## Consequences

### Pros
* **Schema Evolution & Flexibility**: New prompt context fields (e.g., coding styles, token limits, AI model targets) can be introduced without requiring repetitive SQL schema alterations (`ALTER TABLE`) or Flyway migrations across multiple tables.
* **Optimized Read Performance**: Generating a handoff prompt requires fetching the entire project context at once. Reading a single row from `manifests` eliminates multi-table relational `JOIN` operations.
* **Deterministic Versioning**: Saving entire document snapshots provides out-of-the-box manifest version history without complex audit or history table synchronization.

### Cons / Trade-offs
* **Coarse-Grained Updates**: Updating a single rule or command requires the client to send the entire JSON document; partial in-place field updates at the database level are NOT supported.
* **No Database-Level Constraint Enforcement**: MySQL treats the manifest payload as a text block; data integrity guarantees rely entirely on application-layer Jackson deserialization and Java service validations.
* **Relational Query Limitations**: Querying / filtering projects based on individual nested rules or decisions cannot be performed directly via standard relational SQL without leveraging MySQL JSON functions.

## (*) Document-oriented pattern (read by ID) versus ( ) relational pattern (filter/aggregate across manifest entities)
## Stage 1 is deserialization for in-memory validation. Stage 2 is document persistence/storage, `raw_content` with deserialization.