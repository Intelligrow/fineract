<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements. See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership. The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.

-->
# Lead Stage Architecture Skeleton

This module intentionally stops at the architecture skeleton requested by the lead roadmap. It is not a full LOS, does not convert leads to clients or loans, and does not implement dynamic forms, BPMN, a visual workflow designer, or real credit-bureau integration.

## Package Map

`org.apache.fineract.lead.domain.stage` contains the stable stage contract:

* `LeadStageHandler`
* `AbstractLeadStageHandler`
* `LeadStageRegistry`
* `LeadStageContext`
* validation, execution, prefill, and capability result types

`org.apache.fineract.lead.domain.process` contains immutable-published process definition skeletons and running process instances. A process instance stores the process definition version id and version number it started with.

`org.apache.fineract.lead.domain.stage.port` contains integration ports for Fineract datatables, documents, and external stage prefill. Stage context carries ids and configuration only; it does not expose repositories.

`org.apache.fineract.lead.stages.basicdetails` and `org.apache.fineract.lead.stages.creditbureau` demonstrate manual and fake external-prefill stages.

## Stage Discovery

Handlers are Spring beans discovered by dependency injection. `LeadStageRegistry` normalizes stable stage codes and fails application startup if two handlers claim the same code. Tenant or database configuration may reference only those stable codes. No Java class names are loaded from the database.

Unknown stage codes fail through `LeadStageHandlerNotFoundException`.

The settings UI can list available stage types from:

```text
GET /v1/leadprocesses/available-stages
```

The response is based on `LeadStageRegistry` and includes the stable `stageCode` and declared capabilities.

## Process Configuration

Lead process definitions are configured before lead workflow execution. The current architecture slice supports:

```text
POST /v1/leadprocesses
GET /v1/leadprocesses
GET /v1/leadprocesses/{processDefinitionId}
GET /v1/leadprocesses/{processDefinitionId}/versions
POST /v1/leadprocesses/{processDefinitionId}/versions
GET /v1/leadprocesses/{processDefinitionId}/versions/{versionId}
PUT /v1/leadprocesses/{processDefinitionId}/versions/{versionId}/stages
POST /v1/leadprocesses/{processDefinitionId}/versions/{versionId}/publish
```

The stage configuration endpoint replaces the complete ordered stage list for the draft version in one command and one transaction. The complete list is validated before persistence. Each stage entry must reference a stable `stageCode`; unknown stage codes, duplicate stage codes, duplicate sequence values, non-positive sequence values, incompatible capability flags, and mandatory-plus-skippable stages are rejected. Published versions reject further mutation.

Before publication, at least one enabled stage is required. Changes after publication are made by creating a new draft process definition version, not by reopening the published version.

## Demo Stages

`BASIC_DETAILS` is manual, datatable-capable, document-capable, and maker-checker-capable.

`CREDIT_BUREAU` calls `FakeCreditBureauPrefillProvider` only. The fake provider:

* requires an idempotency key
* stores completed fake responses in memory by idempotency key
* returns a deterministic normalized response
* supports a failure flag through context attribute `simulateCreditBureauFailure`
* exposes `realExternalRequestCount()` for tests and always returns `0`

The handler writes normalized values to `FineractDatatablePort` on first success. Idempotent replays verify that the lead datatable state already exists before reporting replay success.

## Process Invariants

Published process definition versions reject mutation. Running process instances retain `definitionVersionId` and `definitionVersionNumber`. Disabled stage configurations are omitted when a process instance is created.

Mandatory stages cannot be skipped. Stage and process instances use optimistic locking through `@Version`.

Maker-checker approval requires:

* maker and checker user ids
* different maker and checker users
* approval of the exact submitted revision
* rejection of stale checks after stage data changes

## Datatable And Document Boundaries

The current Fineract datatable registry is backed by `EntityTables`, which does not include `m_lead_stage_instance`. Because this was not verified as safe to extend inside this architecture task, the module ships clean ports and documented stubs rather than a pretend adapter.

Documents are also behind a port. Fineract stores generic document rows by parent entity type and id, but lead-stage-specific verification state belongs in the lead module table `m_lead_stage_document_link`.

See `adr-0001-lead-stage-datatable-document-boundaries.md`.

## Next Implementation Step

The next task should implement process configuration and publication APIs that validate configured stage codes against `LeadStageRegistry`, persist only stable codes, and create immutable published process definition versions.
