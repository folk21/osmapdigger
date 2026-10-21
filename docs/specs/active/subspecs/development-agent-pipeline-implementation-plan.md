---
type: Implementation Guide
title: Development agent pipeline implementation plan
description: Advisory file-by-file and test-oriented implementation guidance for OsmapDigger staged Aider development.
---
# Development agent pipeline — implementation plan

This is advisory implementation guidance for the normative [development agent pipeline](development-agent-pipeline.md). The plan is neither an executable task packet nor permission to run Aider, configure external models or change a Git ref. `MAIN_MODEL` owns specification/review; a later `WORKER_MODEL` implements only developer-authorized paths and records deviations.

## Source and ownership mapping

Read in order: root `README.md`, root `AGENTS.md`, `docs/specs/README.md`, normative spec, this plan, then owning docs/contracts/tests. Follow nearest `AGENTS.md` for any touched `mobile/`, `geo-builder/` or `geo-format/` areas. OsmapDigger has a strict `docs/specs/STATE.json` processing checkpoint and read-only validator, but **no** `tools/agent-pipeline/` controller, reviewed provider registry, Aider wrapper, run-evidence contract or independent reviewer adapter. Candidate paths listed below do not exist yet unless verified against a newer checkout.

| Concern | Existing boundary / intended integration |
|---|---|
| Modules | Kotlin `mobile/core`, `application`, `presentation`, `shared`, platform hosts; Python `geo-builder`; persisted `geo-format` |
| Active specs | Product umbrella remains focused on shortlist acceptance; contributor automation is separate |
| Checkpoint | Strict `docs/specs/STATE.json`, schema, semantic validator and deterministic tests; no runtime authority |
| Verification | `make check` network-free; `make check-all` configured KMP/Android; see `docs/TESTS.md` |
| Source identity | Verify real local Git HEAD/status and spec/plan hashes before any later worker run |
| Worker | Aider-only isolated adapter is planned, not installed or authorized by this document |
| Reviewer | Separate approved provider/model, egress/privacy and structured result validation; not implemented |
| Security | Approved scanner/version and scope will be required; no pipeline scanner exists yet |
| Evidence | Future private custody path distinct from generated data or cleanup targets |
| Git | Existing `feature/*` workflow; guarded CAS promotion only after implementation/approval |

## A1 — source-bound foundation (first implementation)

Normative IDs: `AP-R1`–`AP-R4`. **Developer-led**, no Aider calls and no network in the test harness. Expected owner: new `tools/agent-pipeline/`, with tests in `tools/agent-pipeline/tests/`, and focused canonical integration in `Makefile` and `docs/TESTS.md` when implementation is ready. Use Python stdlib where feasible; new dependencies require approval.

Candidate files (**not yet implemented**):

- `tools/agent-pipeline/README.md` — actual command/contract owner after implementation, distinguishing available commands from roadmap.
- `tools/agent-pipeline/agent_pipeline.py` — CLI/service for `preflight`/packet validation and source identity; must be safe in read-only mode.
- `tools/agent-pipeline/stage-task.schema.json` — typed immutable packet contract. Avoid executable strings supplied by the model.
- `tools/agent-pipeline/pipeline-policy.json` — reviewed allowed-path/risk/check inventory, not a provider secret store.
- `tools/agent-pipeline/tests/test_agent_pipeline.py` — fake Git roots, hostile path/symlink variants, schema/security tests.
- `tests/test_agent_pipeline_integration.py` (optional) — network-free check wiring and policy/document consistency.

Reference packet fields (illustrative, **not** an accepted schema): `schemaVersion`, `specPath`, `specSha256`, `planSha256`, `stageId`, `requirementIds`, `orderedStageIds`, `baseCommit`, `featureBranch`, `readablePaths`, `editablePaths`, `riskClass`, `requiredChecks`, `workerModelRef`, `reviewerModelRef`, `approvalRef`. Validate actual branch/HEAD and normalized paths in the controller, not by reading a model-provided claim. A human approval reference cannot prove approval on its own.

Suggested path policy: default deny; explicitly permit scoped source/test/doc paths for the stage, read only authoritative instructions and contracts. For `geo-format/VERSION`, `geo-format/schema.sql`, `geo-format/metadata.schema.json`, mobile platform adapters, native map/rendering, security/storage changes, dependency/configuration updates or generated data, escalate risk and require manual design/approval; never infer LOW risk from file suffix alone.

Independent acceptance scenarios:

| ID | Setup / action | Expected result |
|---|---|---|
| `AP-A1-P01` | Clean temporary Git repo at the exact packet base, valid active spec and bounded test file | Read-only preflight succeeds without modifying checkout or contacting provider |
| `AP-A1-N01` | Dirty/untracked file, stale `HEAD` or wrong feature branch | Preflight rejects; no worker/evidence mutation |
| `AP-A1-N02` | Wrong spec or plan digest, unknown stage, duplicate/invalid JSON fields | Rejection with explicit cause, no implicit permission |
| `AP-A1-N03` | `../` escape, absolute path, symlink alias to protected data, mode/symlink changes | Rejection before any file access outside approved root |
| `AP-A1-B01` | Empty edited scope, missing canonical check, unknown risk, protected format contract | Fail closed or escalate; no LOW-risk authorization |
| `AP-A1-R01` | A1 test suite re-run with no Aider/provider/network installed | Deterministic PASS on valid fixtures only |

Gate: focused fake-backed tests and `make check`; independent code review of source identity, scope and default-deny invariants. No live provider execution at A1.

## A2 — isolated Aider worker

Normative IDs: `AP-R5`–`AP-R7`; depends on accepted A1. Candidate updates: `agent_pipeline.py`, new `run_aider_stage.sh` (only after reviewed CLI contract), `pipeline-policy.json`, `tests/test_agent_pipeline.py`, `tools/agent-pipeline/README.md`. The actual command syntax, pinned Aider version, compatible model provider, credential source and cost/time limits need approval and conformance tests. Do not use unverified endpoint configuration.

1. Check clean source, packet digest and explicit developer authorization; create a separate temporary Git worktree from immutable base.
2. Invoke Aider as the only supported worker adapter with auto-commits disabled, no unapproved Git or provider options, approved files only and bounded resource/time budgets.
3. Record exact attempted task bytes, stdout/stderr with redaction, exit status, changed paths including untracked files, file modes and symlinks, source digest and declared worker deviations.
4. Validate every changed path against approved scope; fail on no meaningful diff, unsupported tool, mutated inputs, unexpected file or missing evidence.
5. Keep developer checkout and refs unchanged; do not stage/commit/promote from worker execution.

Scenario matrix: `AP-A2-P01` fake worker produces bounded test-only diff and stable digest; `AP-A2-N01` outside-path edit; `AP-A2-N02` no-diff; `AP-A2-N03` Aider auto-commit attempt; `AP-A2-N04` credential/unsupported model; `AP-A2-B01` symlink, binary and file-mode changes; `AP-A2-R01` interruption retains attempt evidence without unauthorized ref movement. Use fake subprocesses for deterministic tests; a real Aider smoke requires separate opt-in approval.

## A3 — deterministic verification, independent review, bounded repair

Normative IDs: `AP-R8`–`AP-R11`; depends on accepted A2. Candidate updates: `agent_pipeline.py`, `pipeline-policy.json`, a reviewed reviewer endpoint registry/schema under `tools/agent-pipeline/`, verification tests/oracles, `docs/TESTS.md`. A new pinned secret scanner (for example Gitleaks) is a proposed dependency and must be explicitly approved; until configured, no automated security PASS can be asserted.

- For Python builder changes, run focused tests plus `make check` and appropriate synthetic/package acceptance. For KMP module changes, require focused Gradle tests and `make check-all` on configured hosts. For geospatial/native workflows, declare real PBF/PMTiles and device checks explicitly; absent data means **not verified**.
- A separate reviewer must receive sanitized, source-bound diff/spec/plan/task/check evidence, never uncontrolled raw secrets/provider logs. Validate reviewer response schema and source digest. Reviewer model is distinct from coding model in policy.
- Repair only a classified, approved low-risk implementation defect; use one initial attempt and no more than three **policy-approved** repairs as a candidate maximum, not a promise to retry. Every attempt has a new task digest, fresh checks and fresh review.

Scenarios: `AP-A3-P01` valid checks and independent structured review for same diff; `AP-A3-N01` correct regression test fails; `AP-A3-N02` stale or mismatched review digest; `AP-A3-N03` malformed schema, missing logs or security finding; `AP-A3-N04` reviewer timeout/network/429; `AP-A3-B01` exhausted repair budget; `AP-A3-R01` post-repair bytes force complete revalidation; `AP-A3-A01` worker weakens a valid assertion (must block). Separate product defects from invalid test and infrastructure failures.

## A4 — evidence, guarded commit, next checkpoint

Normative IDs: `AP-R12`–`AP-R15`; depends on accepted A3. Candidate files: evidence export/verify utilities and tests under `tools/agent-pipeline/`. Existing `docs/specs/STATE.json`, `docs/specs/state.schema.json`, `tools/specs/validate_state.py` and their deterministic tests provide a structural processing checkpoint; A4 must integrate them without treating checkpoint contents as execution authorization or live evidence.

- Preserve exact worker/check/review artifacts privately outside Gradle/pytest cleanup; a sanitized summary alone cannot attest a verified diff.
- Developer explicitly authorizes guarded promotion. Re-verify worker digest, spec/plan/policy, required checks, reviewer identity and PASS, intended full parent, and that target `feature/*` ref is not checked out in any worktree.
- Build commit from verified worker tree without touching developer worktree, write durable promotion intent, update exact expected ref with CAS, then write promotion receipt. On uncertainty stop for developer recovery; no speculative replay.
- Developer checks out new HEAD, cleans up authorized worktrees, verifies predecessor receipt and then creates a new immutable packet for next stage. No automatic stage chaining.
- Update the existing validated STATE checkpoint only through developer-controlled Git commits between source-bound runs. It is not runtime/process authority, a lock, approval, promotion or acceptance. `RUNNING`/`ACCEPTED` are invalid checkpoint values.

Scenarios: `AP-A4-P01` exact source-bound guarded commit with approval; `AP-A4-N01` changed expected parent or checked-out branch; `AP-A4-N02` missing reviewer/scan/check evidence; `AP-A4-N03` changed run bytes after PASS; `AP-A4-N04` CAS failure/intention without receipt; `AP-A4-B01` missing predecessor on next stage; `AP-A4-R01` interrupted single-use `runId` cannot be replayed. All Git operations must be exercised in disposable repositories, not the developer checkout.

## A5 — first bounded pilot and rollout

Normative IDs: `AP-R16`–`AP-R18`; depends on accepted A1–A4. Use a dedicated test-only fixture in `tools/agent-pipeline/tests/` as a candidate edit target and never choose a `geo-format` schema, native map renderer, platform persistence or user-data migration for first live execution.

1. Developer records approved provider privacy/security, model and separate reviewer choices, limits, exact stage packet and clean `feature/*` HEAD.
2. Run one low-risk source-bound Aider test-only stage, inspect every recorded attempt and reject any unapproved changes.
3. Execute required checks on exact bytes, independent reviewer, MAIN_MODEL handoff and human decision. Promote only with explicit authorization and preserved receipt; do not merge automatically.
4. Validate negative fault-injection and recovery matrix before considering wider scopes. Inspect final feature diff, rerun global checks, review all requirements and manually handle PR/CI/merge.

Pilot evidence must include an exact input fixture, approved packet digest, before/after Git identity, worker diff digest, deterministic checks, independent review, private custody receipt, security verdict (only if configured), and developer acceptance decision. A worker PASS or a successful Git promotion alone is insufficient.

## Developer handoff and future changes

A standalone `DEFAULT` bug fix still uses a ChatGPT PATCH and developer-run `make check`. `GENSPEC` authors a normative staged spec plus same-directory advisory plan; it does **not** invoke Aider. `REVIEW` checks a real source-bound worker diff and verified evidence; findings-only returns no PATCH. Unknown or contradictory scope triggers `ESCALATE` and explicit replan between stages. Do not treat third-party scripts, provider settings, model names or scanner installations as approved repository capabilities.

Later automated batching, external notifications, cross-repository scheduling and unattended Git ref movement need a new explicit specification with locks, evidence/permission boundaries, cost enforcement and recovery oracles. They are not part of A1–A5.
