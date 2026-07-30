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
# ADR 0001: Lead Stage Datatable And Document Boundaries

## Status

Accepted for the architecture skeleton.

## Context

The lead roadmap requires stage handlers to declare datatable and document capabilities while also forbidding workflow state in datatables. The current Fineract datatable integration registers application tables through the core `EntityTables` enum. That enum does not include `m_lead_stage_instance`, so a concrete datatable adapter cannot be verified as safe without a broader platform decision.

Fineract documents use generic parent entity references. They can store files for a parent entity, but lead-stage document requirement and verification state must remain lead-owned workflow state.

## Decision

This module defines `FineractDatatablePort` and `FineractDocumentPort`.

The production beans are documented stubs:

* `DocumentedFineractDatatablePortStub`
* `DocumentedFineractDocumentPortStub`

The datatable stub fails registration and write/read operations when lead-stage datatables are requested, and returns an invalid validation result for required datatables. The document stub similarly refuses upload and verification writes.

Tests use in-memory ports to verify handler behavior without claiming platform integration is complete.

## Consequences

The architecture skeleton compiles and keeps integration boundaries honest. A later implementation must either extend Fineract's custom application table registry for `m_lead_stage_instance` or choose a separate lead-owned table strategy. Document upload can be implemented once the parent entity type and verification semantics are agreed and covered by migrations and integration tests.
