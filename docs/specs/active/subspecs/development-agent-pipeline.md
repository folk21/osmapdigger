---
type: Specification
title: Development agent pipeline
description: Active contributor-tooling change contract for staged MAIN_MODEL and Aider WORKER_MODEL development in OsmapDigger.
document_role: subspec
spec_status: active
parent: ../spec-initial-functional-product.md
---
# Development agent pipeline

## Status

Active **contributor-tooling design**. A Git-tracked processing checkpoint, strict schema and read-only semantic validator are available. No Aider executor, orchestration controller, independent reviewer adapter or Git promotion command is implemented. No stage is accepted. The first implementation focus is **A1**; A2–A5 are ordered future stages and require separate authorization. This cross-cutting tooling sub-spec is linked to the existing umbrella solely to preserve repository navigation; it is **not** part of the product P0 acceptance map and does not replace `current_focus`.

Normative requirements live here. The paired [implementation plan](development-agent-pipeline-implementation-plan.md) is advisory and cannot authorize a run or expand editable paths. Neither a normative design nor a checkpoint grants execution or Git permissions.

## Feature scope

Contributor/development automation only. No user-facing Feature ID applies; do not invent one. Existing `TESTING.DETERMINISTIC_LOCAL` remains the cataloged product testing capability, not a pipeline-stage ID.

## Goal

Introduce a fail-closed, developer-controlled workflow in which ChatGPT acts as `MAIN_MODEL`, Aider is an explicitly authorized `WORKER_MODEL` executor for bounded stages, an independently configured `REVIEWER_MODEL` evaluates source-bound evidence, and the developer owns final acceptance and merge.

## Implementation scope

- Planned owner: `tools/agent-pipeline/` (new contributor tooling), with future check integration under `tests/`, `Makefile`, and `docs/TESTS.md` only when implemented.
- Normative inputs: root `README.md`, `AGENTS.md`, `docs/specs/README.md`, this spec, applicable nearest `mobile/AGENTS.md`, `geo-builder/AGENTS.md`, or `geo-format/AGENTS.md`, and owning contracts/tests.
- Target branch: one developer-approved `feature/*` branch per staged spec, not a branch per stage. Project root is `osmapdigger/`.
- Existing verification: `make check` is the network-free canonical gate. `make check-all` is the configured Kotlin/Android gate; focused geospatial, persisted-format, platform/native, and package checks follow `docs/TESTS.md`.
- Explicit exclusions: generated geographic data, local `.env`/credentials, PBF/PMTiles/SQLite/OMD packages, unrelated module rewrites, unapproved network/provider calls, automatic PR merge, product spec acceptance and archival.
- Undefined until A1/A2 implementation and developer decision: exact runner location, model/endpoint allowlists, approved scanner/tool versions, evidence custody, invocation API and runtime cost budgets. These are **not currently available commands or guarantees**.

## Requirement map

| Requirement | Stage | Normative outcome |
|---|---|---|
| `AP-R1`–`AP-R4` | A1 | Source identity, task authorization, path/risk guardrails, deterministic fake-backed validation |
| `AP-R5`–`AP-R7` | A2 | Isolated Aider execution, independently approved model configurations, source-bound worker outputs |
| `AP-R8`–`AP-R11` | A3 | Independent verification/review, bounded repair, security and privacy, failure classification |
| `AP-R12`–`AP-R15` | A4 | Private evidence, guarded stage promotion, ordered next-stage checkpoint, explicit acceptance boundary |
| `AP-R16`–`AP-R18` | A5 | First bounded pilot, recovery/adversarial proof, integration and rollout controls |

## Requirements

**A1 — trustworthy source and authorization foundation**

- **AP-R1:** A stage MUST use a stable `stageId` and requirement IDs from an active normative spec; the approved task packet MUST bind the exact repo/branch/base commit, spec and plan content digests, ordered stage roster, risk class, readable context, permitted editable paths, required checks and developer authorization. Plan prose or ChatGPT advice MUST NOT count as authorization.
- **AP-R2:** Preflight MUST be read-only by default and reject stale/dirty source, changed spec/plan, unexpected worktree content, invalid packet, untrusted path traversal/symlink aliases, and protected or unknown editable paths before contacting a provider or modifying any source. Unknown module ownership MUST be escalated.
- **AP-R3:** Policy MUST distinguish LOW-risk bounded edits from high/unknown-risk changes. Persisted `geo-format` contracts, schema migrations, Android/Desktop installation/security, credentials, source downloads, network access, dependency changes, cross-module API changes and broad architecture work MUST NOT receive automatic low-risk authorization.
- **AP-R4:** All controller decisions MUST be independently testable using fake workers/providers and temporary Git repositories, without production credentials, network access, datasets, Aider, Docker, Gradle, or an actual cloud service.

**A2 — bounded Aider execution**

- **AP-R5:** An explicitly approved Aider stage MUST run in a separately isolated Git worktree rooted at the approved base, with commits disabled (e.g. Aider `--no-auto-commits`) and no automatic checkout, ref mutation, merge or rebase. Worker source edits and added/untracked files MUST be inventoried and compared against the allowed paths, including symlinks and file modes.
- **AP-R6:** Coding and reviewer model/endpoint/provider choices MUST be separately configured, allowlisted and validated. Credentials MUST come only from approved external secret sources, not stage packets, files, or chat artifacts. An unimplemented executor/provider adapter MUST fail closed.
- **AP-R7:** Each worker attempt MUST retain exact task bytes, worker source digest, changed-file manifest, deviations and failure details. A worker MAY recommend deviations but MUST NOT relax scope, requirements or verification; material deviations require a fresh approved packet.

**A3 — verification, review and bounded repair**

- **AP-R8:** Run prescribed focused/contract checks and `make check` on the exact worker bytes, with `make check-all` and configured/native acceptance whenever the owning change requires them. Missing prerequisites MUST be reported as not verified, never PASS. A passing test does not establish completeness of requirements.
- **AP-R9:** Independent review MUST use the same source-bound bytes and normative requirements, return structured severity/findings and `PASS`, `CHANGES_REQUIRED` or `ESCALATE`, and reject malformed/partial output. Reviewer PASS is evidence, not authorization to promote or accept.
- **AP-R10:** Auto-repair MUST be opt-in, bounded, and allowed only for diagnosed LOW-risk implementation failures. Any edited bytes invalidate previous checks/review. Policy violation, security finding, credentials risk, unsupported provider, uncertain architecture, infrastructure outage and exhausted budget MUST stop and escalate.
- **AP-R11:** Tests MUST encode positive, negative, boundary, regression and relevant adversarial observations independently of worker-authored implementation. No valid test may be deleted, disabled, weakened or tailored solely to obtain PASS. Secret scans and sanitized provider payloads MUST be required by the approved policy before external review.

**A4 — evidence and Git checkpoints**

- **AP-R12:** Preserve run-specific private evidence outside build cleanup, including per-attempt task, verified manifest/digests, complete check logs, reviewer replies and recovery intent; export only minimized sanitized summaries. Evidence MUST be bound to the exact source/version and MUST NOT silently disappear on failure.
- **AP-R13:** Stage promotion MUST require an explicit developer approval and revalidation of exact source bytes, clean expected parent and review/check evidence. If automated Git promotion is implemented, it MUST create a commit from verified worker bytes and compare-and-swap only an expected, non-checked-out `feature/*` ref, with durable intent/receipt. Failed/uncertain CAS MUST escalate, not silently retry.
- **AP-R14:** The next stage MUST start only after the developer checks out the promoted feature HEAD, validates its predecessor receipt and authorizes a new immutable packet. Stage order and source identity MUST be checked; an interrupted/single-use `runId` MUST NOT be replayed.
- **AP-R15:** The existing `docs/specs/STATE.json`, `docs/specs/state.schema.json` and `tools/specs/validate_state.py` MUST remain a strictly validated, developer-maintained between-run checkpoint. It MUST NOT represent live runtime state, a lock, worker permission, stage promotion, or spec acceptance. Future controller integration MUST keep run evidence separate and fail closed on invalid/stale checkpoint identity. Final acceptance, archive move, push, PR and merge require independent developer decisions.

**A5 — bounded rollout and acceptance**

- **AP-R16:** The first live pilot MUST be a developer-approved LOW-risk, test-only single stage with a verified clean baseline, confirmed provider/privacy permissions, focused checks and independent review. No unattended sub-spec batch is permitted by A1–A5.
- **AP-R17:** Deterministic adversarial tests MUST prove rejection/recovery for stale base, wrong stage/spec, out-of-scope edits, symlink/path escape, no diff, changed logs/digests, missing review, provider failure, timeout, interrupted run, changed branch ref, reviewer drift and post-PASS code changes. Preserve failure evidence and do not claim a blocked run as success.
- **AP-R18:** Developer acceptance MUST be based on a final whole-feature review against every requirement and regression oracle, final configured validation/CI evidence and explicit approval. Only then MAY current-state docs be synchronized and normative spec plus companion plan moved together to the archive. Do not auto-merge or auto-archive.

## Stage sequence and delegation boundary

| Stage | Scope | Delegation risk | Gate to begin |
|---|---|---|---|
| A1 — local foundation | Packet schema, Git/context/path preflight, fake-backed regression harness and policy mapping | Developer-led implementation; no real worker | Approval of normative contract and plan |
| A2 — isolated executor | Aider adapter, model configuration, worktree edit inventory, task/deviation evidence | High until A1 is proven; opt-in only | A1 acceptance + provider/privacy approvals |
| A3 — independent checks/review | Verified checks, scanner policy, structured reviewer, bounded repair | High until deterministic failure gates pass | A2 source-bound evidence and approved reviewers |
| A4 — checkpoint/promotion | Private evidence, CAS promotion, next-stage verification and validated checkpoint integration | High; developer approval each promotion | A3 review/evidence conformance |
| A5 — pilot/rollout | Single test-only live pilot, recovery matrix, full verification and manual handoff | LOW only per independently approved packet | A1–A4 acceptance, authorized toolchain and clean feature HEAD |

The stage labels above are machine-stable within this spec, not proof of stage completion. Each stage requires its own approved task packet when the machinery exists. Autonomous batching/notifications are outside this spec.

## Failure semantics

- Fail closed with no provider invocation on invalid authorization, missing source identity, disallowed paths, dirty checkout or unresolved risk.
- Preserve evidence and stop on invalid test/security/reviewer output. A provider outage is infrastructure failure, not a reason to weaken production requirements.
- Refuse promotion when bytes, reviewer identity, checks, predecessor receipt or expected Git ref differ from authorized evidence; keep the developer's worktree untouched.
- After manual changes to a reviewed diff, require fresh checks and review. Interrupted runs are not resumed by replaying a consumed ID.
- Keep `main` and product requirements independent of contributor automation experiments.

## Non-goals

- Reusing unverified controller scripts, provider policies, credentials or executable commands outside their validated repository contract.
- Shipping an embedded LLM in Desktop/Android, cloud requirement for offline analysis, or changes to PBF/SQLite/PMTiles data contracts.
- Automatic multi-stage scheduling, PR creation/merge, Git checkout/reset, bypassing developer approvals, or accepting a product specification from a model verdict.

## Validation

A1 acceptance requires deterministic packet/path/dirty/stale/source-contract fixtures including negative cases, plus the canonical `make check` on the actual implementation baseline. A2–A4 each require focused test suites, independent adversarial oracles and source-bound full check/review evidence; use `make check-all` when Kotlin/native boundaries are impacted. A5 requires an authorized real test-only pilot, final full-feature review and configured gates. **The checkpoint validator is implemented and covered by deterministic tests; A1 packet/preflight, Aider execution, review, evidence collection and Git promotion remain unimplemented and unverified.**

## Remaining work / acceptance

1. Developer review/approve this design and the [implementation plan](development-agent-pipeline-implementation-plan.md); commit a clean feature baseline.
2. Implement A1 with fake-backed tests; verify source identity and fail-closed behavior before provider use.
3. Progress A2, A3, A4, A5 only through separately authorized, evidenced stages.
4. Complete final MAIN_MODEL review and developer acceptance before paired archival and current-state documentation updates.
