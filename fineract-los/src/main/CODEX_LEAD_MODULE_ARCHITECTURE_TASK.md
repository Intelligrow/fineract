# Codex Task — Scaffold the Fineract Lead Stage Architecture

Treat this as an architecture-scaffolding task only. Do not implement the complete Lead/LOS product.

## First actions

1. Inspect the currently opened Fineract repository.
2. Record the exact tag/commit, Java version and relevant modules.
3. Study current conventions for Gradle modules, APIs, commands, permissions, Liquibase, audit, business events, datatables, documents and tests.
4. Write a short implementation plan before changing code.

## Product boundary

Create a bounded lead-processing module supporting:

- lead foundation;
- versioned process definitions;
- ordered fixed stage handlers;
- enabled/disabled/mandatory/skippable configuration;
- validation policies;
- maker-checker contracts;
- datatable and document ports;
- external API-prefill contracts;
- stage registry and lifecycle abstractions.

Explicitly exclude:

- dynamic forms;
- visual workflow designer;
- BPMN;
- real credit-bureau integration;
- full business-stage implementation;
- client/loan conversion;
- unrelated refactoring.

## Required contracts

Create repository-conventional equivalents of:

- `LeadStageHandler`
- `AbstractLeadStageHandler`
- `LeadStageRegistry`
- `LeadStageContext`
- `LeadStageValidationResult`
- `LeadStageExecutionResult`
- `LeadStagePrefillResult`
- `StageCapability`
- `StageConfiguration`
- `LeadStageValidationPolicy`
- `LeadStageMakerCheckerPolicy`
- `FineractDatatablePort`
- `FineractDocumentPort`
- external integration/prefill port

A handler is a Spring-discovered bean with a stable `stageCode`.

The registry must:

- discover handlers with dependency injection;
- reject duplicate codes at startup;
- resolve required handlers;
- never instantiate classes using tenant-supplied class names;
- allow publication validation to reject unregistered stages.

## Example handlers

Create two examples:

1. `BASIC_DETAILS`: manual, datatable-capable and optionally maker-checker.
2. `CREDIT_BUREAU`: fake API-prefilled handler using an in-memory/fake client.

The fake bureau example must demonstrate:

- external-prefill capability;
- normalized result;
- datatable-port write;
- failure result;
- idempotency boundary;
- no real network call;
- no sensitive log output.

## Package/dependency rules

Use actual repository conventions while preserving:

```text
api -> command/application services -> domain
infrastructure implements domain ports
stage implementations use domain contracts
domain does not depend on REST or provider SDKs
```

Do not expose mutable repositories in `LeadStageContext`.

## Minimal domain/persistence skeleton

Create intentionally minimal structures for:

- Lead
- Process definition
- Process definition version
- Stage configuration
- Process instance
- Stage instance
- Stage action history
- Stage submission/check
- External execution

Published definitions must be designed as immutable. Running instances retain their definition version.

## States

Create explicit enums equivalent to:

Lead:

```text
DRAFT ACTIVE ON_HOLD COMPLETED REJECTED CANCELLED CONVERTED
```

Stage:

```text
NOT_STARTED READY IN_PROGRESS PENDING_EXTERNAL PREFILLED
PENDING_CHECK RETURNED COMPLETED SKIPPED FAILED CANCELLED
```

Actions:

```text
START SAVE PREFILL RETRY_PREFILL SUBMIT_FOR_CHECK
CHECK_APPROVE CHECK_RETURN COMPLETE SKIP HOLD RESUME REOPEN CANCEL
```

Change names only when required by repository conventions and document why.

## Maker-checker contract

The architecture must support:

- maker and checker are different users;
- checker approves a specific revision;
- changes after submission stale the check;
- return/rework preserves history;
- skip requires configuration, permission and reason;
- completed stage reopens only through an explicit command.

Implement focused domain tests, not the full production approval workflow.

## Datatable boundary

Inspect how the pinned Fineract version registers application tables.

Create a port capable of:

- validating stage datatable registration;
- reading stage data;
- writing API-prefilled stage data;
- validating required stage data.

Workflow state must not be stored in datatables.

If a safe concrete adapter requires an unresolved product decision, implement the port and a documented stub. Do not invent an unsafe workaround.

## Document boundary

Create a port for:

- stage document upload;
- list;
- verify/reject;
- requirements validation.

Isolate any registration limitations behind the port.

## Minimal API skeleton

Following current Fineract conventions, add only honestly supported endpoints such as:

```text
POST /leads
GET /leads/{id}
GET /leads/{id}/stages
POST /leads/{id}/stages/{stageInstanceId}/actions
POST /leads/{id}/stages/{stageInstanceId}/prefill
```

Do not return fake success for incomplete features.

## Migrations and permissions

Create initial Liquibase changesets and permissions for:

```text
CREATE_LEAD
READ_LEAD
UPDATE_LEAD
CONFIGURE_LEAD_PROCESS
PUBLISH_LEAD_PROCESS
PROCESS_LEAD_STAGE
SUBMIT_LEAD_STAGE
CHECK_LEAD_STAGE
SKIP_LEAD_STAGE
UPLOAD_LEAD_STAGE_DOCUMENT
VERIFY_LEAD_STAGE_DOCUMENT
```

Do not modify unrelated migrations.

## Business events

Create event classes or documented contracts for:

- lead created;
- stage entered;
- prefill requested/completed/failed;
- stage submitted;
- checker approved/returned;
- stage completed/skipped.

Follow current Fineract event conventions.

## Tests

At minimum:

1. Registry resolves a handler.
2. Duplicate stage code fails.
3. Unknown stage code fails safely.
4. Manual example exposes correct capabilities.
5. Fake bureau prefills through the port.
6. Validation results merge correctly.
7. Maker cannot check own submission.
8. Disabled stage is omitted.
9. Mandatory stage cannot be skipped.
10. Published definition cannot mutate.
11. Stale stage version is rejected.
12. Tests perform no real external request.

## Documentation

Add a developer guide explaining:

- purpose and non-goals;
- package map and dependency rules;
- how to add a stage;
- manual-stage example;
- external-prefill example;
- configuration-to-handler mapping;
- datatable boundary;
- document boundary;
- maker-checker boundary;
- open decisions;
- manual and external-prefill sequence diagrams.

## Guardrails

- No dynamic forms.
- No BPMN.
- No real bureau.
- No client/loan conversion.
- No core client/loan table changes.
- No arbitrary class names from configuration.
- No DB transaction held across external calls.
- No unrelated refactors.
- No suppressed tests.
- Do not claim datatable/document integration works unless verified.

## Execution order

1. Inspect repository and plan.
2. Identify closest existing modules.
3. Add ADR/open-decision notes.
4. Add module/package skeleton.
5. Add domain contracts.
6. Add registry and examples.
7. Add persistence and migrations.
8. Add permissions and events.
9. Add minimal APIs.
10. Add tests.
11. Run formatting and focused tests.
12. Run broad practical build checks.
13. Summarize changes, assumptions, gaps and the next recommended task.

## Completion standard

The repository must contain a compiling, tested reference architecture that another developer can copy to add a new stage without redesigning the module.

Stop after scaffolding. Do not implement all stages.
