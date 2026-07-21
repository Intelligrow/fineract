# Fineract Lead Module — Revised Roadmap and Project Plan

Version: 1.0
Target: the exact Apache Fineract release pinned by the repository
Recommended module name: `fineract-lead`
Goal: lead creation followed by configurable, pluggable processing stages.

## 1. Scope

Build:

- Lead CRUD, search, assignment and audit.
- A fixed code-owned catalogue of stage types.
- Per-tenant/process configuration to enable, disable, order, require or skip stages.
- A Java extension contract for adding new stages.
- Stage-level maker-checker.
- Stage validation policies.
- Fineract datatables attached to stage instances.
- Documents attached to and verified within stage instances.
- Manual stages and API-prefilled stages.
- Stage history, permissions, events and concurrency control.

Postpone until the final phase:

- Dynamic form builder.
- Generic JSON form runtime.
- BPMN designer.
- Arbitrary tenant-authored code or rules.
- AI underwriting.
- Full approval matrix.
- Client/loan conversion until the stage engine is stable.

Fineract datatables are the first configurable data-capture mechanism. Dynamic forms may later plug into the same stage-data boundary.

## 2. Architecture

Implement a bounded module inside Fineract’s modular monolith.

Reuse Fineract for:

- tenant context;
- users, roles and permissions;
- offices and staff;
- codes and code values;
- datatables;
- documents;
- command audit;
- business events;
- scheduled jobs;
- clients and loans through explicit adapters later.

The lead module owns:

- leads;
- process definitions and versions;
- stage configuration;
- stage runtime and state;
- stage validation;
- maker-checker;
- stage document requirements;
- integration execution state.

Do not modify core client or loan tables for lead state.

## 3. Core Design Rule

A stage is a backend component, not merely a status.

Every stage has:

- stable `stageCode`;
- capabilities;
- entry/submission/completion validation;
- supported actions;
- maker-checker policy;
- datatable requirements;
- document requirements;
- optional prefill logic;
- optional async callback/polling;
- lifecycle hooks;
- permissions and audit events.

Tenant configuration references only `stageCode`. It must never contain an arbitrary Java class name.

Spring discovers all `LeadStageHandler` beans. A registry maps one code to one handler and fails on duplicates.

## 4. Initial Stage Catalogue

Start with only the stages required for the first reference process:

```text
BASIC_DETAILS
KYC
DOCUMENT_COLLECTION
PERSONAL_DISCUSSION
FIELD_VERIFICATION
CREDIT_BUREAU
INCOME_ASSESSMENT
CREDIT_APPRAISAL
REVIEW
FINAL_DECISION
```

A process version configures for each stage:

- enabled;
- sequence;
- mandatory;
- skippable;
- maker-checker required;
- assigned role;
- SLA;
- required datatables;
- required documents;
- auto-prefill;
- manual fallback;
- rework target;
- stage-specific typed options or limited JSON options.

Published process versions are immutable. Running leads retain the version with which they started.

## 5. Recommended Packages

```text
org.apache.fineract.lead
  api
  command
  domain.lead
  domain.process
  domain.stage
  domain.policy
  domain.document
  domain.event
  service
  infrastructure.persistence
  infrastructure.datatable
  infrastructure.document
  infrastructure.identity
  infrastructure.integration
  stages.basicdetails
  stages.kyc
  stages.persondiscussion
  stages.fieldverification
  stages.creditbureau
  stages.review
```

Dependency direction:

```text
API -> command/application services -> domain
Infrastructure implements domain ports
Stage implementations use domain contracts
Domain never depends on REST or provider SDKs
```

## 6. Stage Contract

Codex should create a compilable equivalent of:

```java
public interface LeadStageHandler {

    String stageCode();

    Set<StageCapability> capabilities();

    LeadStageValidationResult validateEntry(LeadStageContext context);

    LeadStageValidationResult validateSubmission(LeadStageContext context);

    LeadStageValidationResult validateCompletion(LeadStageContext context);

    default LeadStagePrefillResult prefill(LeadStageContext context) {
        return LeadStagePrefillResult.notSupported();
    }

    LeadStageExecutionResult executeAction(
            LeadStageAction action,
            LeadStageContext context);

    default void onEnter(LeadStageContext context) {}

    default void onExit(LeadStageContext context) {}

    default void onRework(LeadStageContext context) {}

    default Set<String> supportedDatatableNames(
            StageConfiguration configuration) {
        return Set.of();
    }

    default List<LeadStageDocumentRequirement> documentRequirements(
            LeadStageContext context) {
        return List.of();
    }
}
```

Create `AbstractLeadStageHandler` to centralize:

- validation policy invocation;
- required datatable validation;
- required document validation;
- common maker-checker checks;
- shared helper methods.

Do not put all business logic in the abstract class. Individual stages own stage-specific rules.

## 7. Registry

```java
@Component
public final class LeadStageRegistry {

    private final Map<String, LeadStageHandler> handlers;

    public LeadStageRegistry(List<LeadStageHandler> discovered) {
        Map<String, LeadStageHandler> mapped = new HashMap<>();
        for (LeadStageHandler handler : discovered) {
            if (mapped.put(handler.stageCode(), handler) != null) {
                throw new IllegalStateException(
                        "Duplicate stage code: " + handler.stageCode());
            }
        }
        handlers = Map.copyOf(mapped);
    }

    public LeadStageHandler getRequired(String stageCode) {
        LeadStageHandler handler = handlers.get(stageCode);
        if (handler == null) {
            throw new LeadStageHandlerNotFoundException(stageCode);
        }
        return handler;
    }
}
```

Process-definition publication must fail if an enabled stage code has no registered handler.

## 8. Stage Context

Use immutable identifiers and snapshots:

```java
public record LeadStageContext(
        Long leadId,
        Long processInstanceId,
        Long stageInstanceId,
        String stageCode,
        Long officeId,
        Long assignedStaffId,
        Long actingUserId,
        StageConfiguration configuration,
        Map<String, Object> attributes) {
}
```

Do not expose mutable repositories in this context. Prefer typed value objects over a large untyped attributes map.

## 9. Manual Stage Example

A `PERSONAL_DISCUSSION` handler may declare:

```java
Set.of(
    StageCapability.MANUAL_DATA,
    StageCapability.DATATABLES,
    StageCapability.DOCUMENTS,
    StageCapability.MAKER_CHECKER
);
```

It returns its registered datatable names and document requirements. Submission validation is handled through the base class plus stage-specific validation.

## 10. API-Prefilled Stage Example

A `CREDIT_BUREAU` handler may declare:

```java
Set.of(
    StageCapability.EXTERNAL_PREFILL,
    StageCapability.ASYNC_CALLBACK,
    StageCapability.MANUAL_FALLBACK,
    StageCapability.DATATABLES,
    StageCapability.DOCUMENTS,
    StageCapability.MAKER_CHECKER
);
```

Its `prefill` implementation:

1. Creates a normalized request.
2. Calls a provider port, not a provider SDK from the domain.
3. Stores provider reference and execution state.
4. Maps the normalized response into a registered stage datatable.
5. Returns a prefill result.
6. Does not automatically checker-approve the stage.

External API rules:

- no remote call from REST resource;
- no provider-specific object in domain entities;
- idempotency key required;
- timeouts and normalized failures;
- async callback or polling support;
- manual fallback configurable;
- no sensitive payloads in logs;
- do not hold a database transaction during the remote call.

## 11. Lifecycles

Lead status:

```text
DRAFT
ACTIVE
ON_HOLD
COMPLETED
REJECTED
CANCELLED
CONVERTED
```

Stage status:

```text
NOT_STARTED
READY
IN_PROGRESS
PENDING_EXTERNAL
PREFILLED
PENDING_CHECK
RETURNED
COMPLETED
SKIPPED
FAILED
CANCELLED
```

Normal maker-checker:

```text
READY -> IN_PROGRESS -> PENDING_CHECK -> COMPLETED
```

Rework:

```text
PENDING_CHECK -> RETURNED -> IN_PROGRESS
```

External prefill:

```text
READY -> PENDING_EXTERNAL -> PREFILLED
        -> IN_PROGRESS or PENDING_CHECK -> COMPLETED
```

Rules:

- maker and checker must differ;
- checker approves a specific submission revision;
- post-submission changes make the check stale;
- completed stage reopens only by explicit command;
- skip requires configuration, permission and reason;
- disabled stages are not instantiated for new leads;
- mandatory stages cannot be bypassed;
- process versions are immutable after publication.

## 12. Database Model

Suggested tables:

```text
m_lead
m_lead_process_definition
m_lead_process_definition_version
m_lead_process_stage_config
m_lead_process_transition_config
m_lead_process_instance
m_lead_stage_instance
m_lead_stage_action_history
m_lead_stage_assignment_history
m_lead_stage_submission
m_lead_stage_check
m_lead_stage_document_link
m_lead_stage_external_execution
```

Select either `m_lead_*` or `m_los_*` and use it consistently.

Do not store process state, maker-checker state or integration status in datatables.

## 13. Datatable Port

```java
public interface FineractDatatablePort {

    void validateRegistration(
            String stageCode,
            Set<String> datatableNames);

    LeadStageValidationResult validateRequiredStageData(
            Long stageInstanceId,
            Set<String> datatableNames);

    Map<String, Object> readStageData(
            Long stageInstanceId,
            String datatableName);

    void upsertStageData(
            Long stageInstanceId,
            String datatableName,
            Map<String, Object> values);
}
```

Rules:

- associate datatable rows with a stable stage-instance application-table identifier;
- validate all referenced tables before publishing a process;
- record whether values came from `MANUAL` or `EXTERNAL` sources;
- define whether external values may be overridden;
- keep workflow state in typed tables.

Codex must inspect the pinned Fineract implementation before deciding the concrete registration mechanism.

## 14. Document Port

```java
public interface FineractDocumentPort {

    LeadStageDocument upload(
            Long leadId,
            Long stageInstanceId,
            StageDocumentUpload upload);

    List<LeadStageDocument> findByStage(Long stageInstanceId);

    void verify(Long documentId, Long checkerUserId, String remarks);

    void reject(Long documentId, Long checkerUserId, String reason);

    LeadStageValidationResult validateRequirements(
            Long stageInstanceId,
            List<LeadStageDocumentRequirement> requirements);
}
```

Requirements may include:

- category;
- applicant role;
- minimum count;
- mandatory flag;
- verification required;
- MIME types;
- maximum size;
- expiry validation;
- multiple versions.

Never expose storage paths.

## 15. Validation Policies

Validation should be composable:

```java
public interface LeadStageValidationPolicy {
    LeadStageValidationResult validate(LeadStageContext context);
}
```

Examples:

- required datatable exists;
- required document uploaded;
- required document verified;
- PAN format valid;
- external report not expired;
- current user owns the stage;
- maker differs from checker;
- previous mandatory stage completed;
- action allowed by configuration.

Return structured validation errors and collect correctable errors together.

## 16. Maker-Checker

Suggested actions:

```text
START
SAVE
PREFILL
RETRY_PREFILL
SUBMIT_FOR_CHECK
CHECK_APPROVE
CHECK_RETURN
COMPLETE
SKIP
HOLD
RESUME
REOPEN
CANCEL
```

Suggested permissions:

```text
CREATE_LEAD
READ_LEAD
UPDATE_LEAD
START_LEAD_PROCESS
ASSIGN_LEAD_STAGE
PROCESS_LEAD_STAGE
SUBMIT_LEAD_STAGE
CHECK_LEAD_STAGE
RETURN_LEAD_STAGE
SKIP_LEAD_STAGE
REOPEN_LEAD_STAGE
UPLOAD_LEAD_STAGE_DOCUMENT
VERIFY_LEAD_STAGE_DOCUMENT
CONFIGURE_LEAD_PROCESS
PUBLISH_LEAD_PROCESS
```

A submission/check record should include maker, checker, revision, timestamps, decision, remarks and a hash/revision of controlled stage data and documents.

## 17. API Direction

Example namespace:

```text
POST /v1/leads
GET  /v1/leads
GET  /v1/leads/{leadId}
PUT  /v1/leads/{leadId}

POST /v1/leads/{leadId}/process
GET  /v1/leads/{leadId}/stages
GET  /v1/leads/{leadId}/stages/{stageInstanceId}

POST /v1/leads/{leadId}/stages/{stageInstanceId}/actions
POST /v1/leads/{leadId}/stages/{stageInstanceId}/prefill
POST /v1/leads/{leadId}/stages/{stageInstanceId}/documents

GET  /v1/lead-process-definitions
POST /v1/lead-process-definitions
POST /v1/lead-process-definitions/{id}/publish
```

The backend calculates `availableActions`. The frontend must not invent transitions.

## 18. Transactions and Concurrency

- optimistic locking on process and stage instances;
- transition and history entry commit atomically;
- unique submission revision;
- only one checker result per revision;
- external execution stored before remote call;
- remote call outside the database transaction;
- idempotent callback and retry;
- job locking for scheduled retries;
- structured correlation IDs.

## 19. Business Events

Create events for:

```text
LeadCreated
LeadProcessStarted
LeadStageEntered
LeadStagePrefillRequested
LeadStagePrefilled
LeadStagePrefillFailed
LeadStageSubmitted
LeadStageCheckApproved
LeadStageCheckReturned
LeadStageCompleted
LeadStageSkipped
LeadCompleted
```

Critical state changes remain in command/application services. Events support integrations, notifications and projections.

# 20. Development Roadmap

## Phase 0 — Repository Study

- Pin exact Fineract tag/commit and Java version.
- Study a comparable modular feature.
- Study commands, permissions, audit, events, datatables, documents and jobs.
- Decide module/package/table/API names.
- Define the first process and stage catalogue.
- Prove datatable/document association with a stage instance.

**Exit:** architecture decisions accepted and technical spike succeeds.

## Phase 1 — Architecture Skeleton

Codex creates:

- module/package wiring;
- core enums and records;
- handler interface and abstract class;
- registry;
- policy interfaces;
- datatable/document/integration ports;
- one manual example handler;
- one fake external-prefill handler;
- domain entity shells;
- repository interfaces;
- API and command shells;
- initial permissions and Liquibase;
- focused architecture tests;
- developer guide.

No full business stages.

**Exit:** build passes, app starts, registry discovers handlers, no real provider is called.

## Phase 2 — Lead CRUD

- create/update/read/search;
- lead number;
- office/staff;
- duplicate warning;
- permissions;
- audit.

## Phase 3 — Process Configuration

- definitions and versions;
- stage ordering;
- enable/disable;
- mandatory/skippable;
- maker-checker flag;
- datatable/document mappings;
- publication validation.

No visual designer.

## Phase 4 — Runtime Engine

- process/stage instances;
- state machine;
- history;
- actions;
- available-action calculation;
- optimistic locking;
- sequential execution first.

## Phase 5 — Stage Datatables

- concrete adapter;
- registration validation;
- read/write facade;
- manual/external source metadata;
- example datatables.

## Phase 6 — Stage Documents

- upload/list/download;
- stage links;
- requirements;
- verify/reject;
- replacement/version;
- completion validation.

## Phase 7 — Maker-Checker

- submission revision;
- checker decision;
- self-approval prevention;
- stale submission;
- rework;
- concurrency and permission tests.

## Phase 8 — External Prefill

- execution record;
- sync and async ports;
- retry/idempotency;
- callback verification;
- manual fallback;
- fake credit-bureau integration.

## Phase 9 — Production Stages

Implement one complete vertical slice at a time:

1. Basic Details
2. KYC
3. Document Collection
4. Personal Discussion
5. Field Verification
6. Credit Bureau
7. Review
8. Final Decision

Each stage includes handler, policies, datatables, documents, maker-checker, tests, API contract and UI acceptance criteria.

## Phase 10 — Operations

- assignment;
- work queues;
- SLA;
- reminders;
- search;
- dashboard;
- aging;
- integration failures;
- reports.

## Phase 11 — Fineract Conversion

Only after the engine is stable:

- client matching/creation;
- optional loan application creation;
- idempotency;
- reconciliation;
- retry;
- events.

## Final Phase — Dynamic Forms

Dynamic forms must implement a future `StageDataPort`. They must not replace stage lifecycle, validation, documents or maker-checker.

# 21. Sprint Plan

Two-week sprint example:

1. Repository study, ADRs, stage catalogue, datatable/document spike.
2. Architecture skeleton, registry, examples and tests.
3. Lead CRUD, security, audit and search.
4. Process definitions and publication.
5. Runtime stages, transitions and locking.
6. Datatable adapter.
7. Document adapter and requirements.
8. Maker-checker.
9. External-prefill framework and fake bureau.
10+. One production stage per bounded vertical slice.

# 22. Definition of Done for a Stage

A stage is complete only when:

- stage code and capabilities are defined;
- handler is registered;
- configuration is documented;
- entry/submission/completion validation exists;
- datatables and documents are tested;
- maker-checker policy exists;
- permissions and available actions are correct;
- history and events are verified;
- concurrency is tested;
- external calls are idempotent and redacted;
- unit/integration/API tests pass;
- UI acceptance criteria are written;
- upgrade implications are documented.

# 23. Guardrails

- Do not implement the roadmap in one change.
- Do not modify client/loan tables for lead state.
- Do not store workflow state in datatables.
- Do not put provider SDK models in domain code.
- Do not expose repositories through stage context.
- Do not allow class names from tenant configuration.
- Do not edit published definitions.
- Do not hold transactions across remote calls.
- Do not allow maker self-approval.
- Do not log KYC/bureau/document secrets.
- Do not build dynamic forms now.
- Every PR must be one bounded architectural or vertical slice.

## Final Milestone

The first milestone is complete when one manual stage and one fake API-prefilled stage prove the full extension architecture. After team review, developers can implement business stages independently without redesigning the module.
