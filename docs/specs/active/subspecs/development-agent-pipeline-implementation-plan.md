---
type: Implementation Guide
title: Optional contributor-stage pipeline implementation plan
description: Advisory design, interfaces, file-level slices, threat scenarios, and test oracles for a tool-independent optional contributor pipeline.
---
# Optional contributor-stage pipeline — implementation plan

## Purpose and authority

This document is **advisory**, paired with the normative [contributor-stage pipeline specification](development-agent-pipeline.md). It is intentionally independent of authoring tools, model families, vendors, chat products, patch transports, and review services. Neither document selects how OsmapDigger is developed. The optional pipeline is **parked** and must not be implemented, activated, or invoked during ordinary product work without a separate developer decision.

Normative `AP-R1`–`AP-R18` and stages `A1`–`A5` belong to the specification. The file layout, type names, command surfaces, payload sketches and numeric limits below are **candidate designs**, not implemented public APIs, accepted schemas, installed commands, approved dependencies, or permission to edit or access a repository. Do not turn illustrative examples into runtime behavior without a reviewed contract. `docs/specs/STATE.json` and its read-only validator exist; the controller, isolated executor, reviewer integration, evidence custody and promotion do **not**.

Read first: `README.md`, root `AGENTS.md`, `docs/specs/README.md`, the paired normative spec, `docs/ARCHITECTURE.md`, `docs/TESTS.md`, and owning code/tests. Read the nearest module `AGENTS.md` if a future authorized stage touches `mobile/`, `geo-builder/` or `geo-format/`. The project remains offline-first; optional contributor tooling must never become a runtime dependency of Desktop/Android or the dataset builder.

## Ownership, scope, and invariants

| Existing owner | Concrete role | Boundary |
|---|---|---|
| `docs/specs/STATE.json` | Current selected-stage checkpoint | Developer-maintained; not a lock, run record, permission or acceptance |
| `docs/specs/state.schema.json` | Checkpoint shape | Do not widen phases/decisions to encode execution status |
| `tools/specs/validate_state.py` | Read-only semantic checkpoint validation | Must not execute, mutate Git, inspect secrets or contact services |
| `tests/test_spec_state.py` | Deterministic checkpoint regressions | Retain the negative cases when adding optional tooling |
| `AGENTS.md` and nearest local guides | Source ownership/security/testing invariants | Not a set of runnable stage packets |
| `docs/TESTS.md` and `Makefile` | Canonical verification commands | `make check` remains deterministic and network-free |
| `tools/agent-pipeline/` | **Candidate new** tooling owner, absent in supplied FULL | Never call an unimplemented tool |
| `tools/agent-pipeline/tests/` | **Candidate new** fake-backed test owner | No real external credentials, services or user data |

The developer authorizes each stage and final acceptance. A validated packet does not grant its own authorization. A passing test, review or signed summary does not alter Git refs. Any unexpected path, dirty worktree, stale source identity, unapproved configuration, missing mandatory check, or ambiguous state must fail closed **before** external execution. Source/cross-layer contracts and valid tests always outrank a generated suggestion.

The parked optional tooling is not part of `ANALYSIS.INTERPRETATION` or product P0/P1 acceptance. Its own implementation does not change `geo-format`, user settings, GIS packages or Android/Desktop runtime behavior. Any later authorized source changes remain subject to the owning module policies, including high-risk escalation, and ordinary development must not require network access.

## Proposed component boundaries (not yet implemented)

Use the smallest set of Python standard-library modules that enforces the security boundaries. The names below are candidates, not a requirement to create every file at once.

| Candidate file | Responsibility | Stage first needed |
|---|---|---|
| `tools/agent-pipeline/README.md` | Actual supported commands and threat/permission contract **only after implementation** | A1 |
| `tools/agent-pipeline/agent_pipeline.py` | Thin orchestration entry point; never mixes command parsing, shell execution and authorization | A1 |
| `tools/agent-pipeline/stage-task.schema.json` | Closed typed packet fields and versioned semantics | A1 |
| `tools/agent-pipeline/pipeline-policy.json` | Reviewable path classes, required check IDs, risk escalation, resource limits | A1 |
| `tools/agent-pipeline/packet.py` | Strict parsing, duplicate-field rejection, canonical digest and immutable DTOs | A1 |
| `tools/agent-pipeline/git_snapshot.py` | `HEAD`, branch, tree/status/ref identity via checked subprocess argv | A1 |
| `tools/agent-pipeline/path_policy.py` | Root-relative normalization, symlink safety, changed-path inventory | A1 |
| `tools/agent-pipeline/execution.py` | Isolated worktree lifecycle, externally supplied adapter interface, budgets | A2 |
| `tools/agent-pipeline/evidence.py` | Append-only attempt/manifest records and source-bound digests | A2/A4 |
| `tools/agent-pipeline/verification.py` | Approved check IDs to fixed commands and exact-byte attestations | A3 |
| `tools/agent-pipeline/review.py` | Independent typed review result parser and evidence binding | A3 |
| `tools/agent-pipeline/promotion.py` | Developer-authorized compare-and-swap Git promotion only | A4 |
| `tools/agent-pipeline/tests/test_*.py` | Temporary Git fixtures, fake adapters and adversarial oracles | A1–A5 |
| `tests/test_agent_pipeline_integration.py` | Canonical-check wiring and repository contract tests if needed | A1/A3 |

No package layout or new dependency is mandatory until source review confirms the current repository baseline and an approved implementation slice. In particular, do not add a shell wrapper, external provider SDK, reviewer service, credential manager or scanner by assumption.

## Data contracts and state ownership

### Candidate immutable task packet (A1)

The proposed fields form a **design checklist**, not an accepted JSON Schema:

| Field | Candidate type / invariant |
|---|---|
| `schemaVersion` | Fixed supported positive integer; reject unknown versions |
| `repoId` | Exact expected repository identifier; reject unknown roots |
| `specPath`, `planPath` | Existing bounded repository-relative files; must belong to the same staged feature |
| `specSha256`, `planSha256` | 64 lowercase hex characters, compared with current **file bytes** |
| `stageId`, `requirementIds` | Referenced normative stage and requirements; nonempty and deduplicated |
| `orderedStageIds` | Exact ordered roster from normative spec; reject skipped/reordered stages |
| `baseCommit`, `featureBranch` | Verified `HEAD`/branch identity; branch must match an approved `feature/*` ref |
| `readablePaths`, `editablePaths` | Explicit root-relative allowlists; no glob expansion without reviewed semantics |
| `riskClass` | `LOW`, `HIGH`, or `UNKNOWN`; unknown and protected changes escalate |
| `requiredChecks` | Ordered identifiers from an independently approved fixed check registry |
| `executorRef`, `reviewerRef` | Optional non-secret identifiers for separately configured adapters, never credentials |
| `approvalRef` | Non-secret developer-side pointer; **not** proof of authorization by itself |

Parser obligations: strict UTF-8, bounded file size and field lengths, reject duplicate JSON keys, unknown required/privileged fields, unexpected numeric/coercion types, `NaN`/infinities, absolute paths, `..`, empty path components, ambiguous Unicode separators and string-encoded executable commands. Canonical packet serialization and SHA-256 are needed only for immutable identity; do not hash a display-only reformat and call it the original task. Approval is verified outside self-asserted packet fields.

### Candidate result records (A2–A4)

Separate the following records; never mix them into `STATE.json`:

- `preflight`: packet/policy/spec/plan digests, real Git identity, path/risk/check decisions and failure code; no external call when invalid.
- `attempt`: fresh single-use run identifier, exact task digest, bounded adapter status, elapsed-resource outcome, source tree and changed-path manifest. Never store credentials.
- `verification`: exact source-manifest digest, check IDs, command/environment identity, individual PASS/FAIL/BLOCKED, full private log locations and timestamps.
- `review`: independently parsed severity findings, `PASS` / `CHANGES_REQUIRED` / `ESCALATE`, reviewer configuration identity and **identical** source digest.
- `promotion-intent` / `promotion-receipt`: developer approval binding, expected ref/base, validated source manifest, new commit identity and CAS outcome.

Use append-only per-attempt custody outside cleanable build directories; export minimized redacted summaries only. A log path or digest stored inside an editable stage packet must not be trusted as authority. Store rejection evidence even if no source change occurred. Retain raw source/log artifacts privately according to an explicit approved retention policy; **no retention period has yet been approved**.

### Proposed typed API seams (illustrative)

```python
@dataclass(frozen=True)
class SourceIdentity:
    repository: str
    branch: str
    commit: str
    status_paths: tuple[str, ...]

@dataclass(frozen=True)
class PreflightDecision:
    allowed: bool
    failure_code: str | None
    packet_sha256: str
    source: SourceIdentity

def preflight(packet_bytes: bytes, *, repository_root: Path, policy_bytes: bytes) -> PreflightDecision: ...

def inventory_changes(worktree: Path, base_commit: str) -> tuple[ChangedPath, ...]: ...

def verify_exact_source(check_ids: tuple[str, ...], source_digest: str) -> VerificationRecord: ...
```

These signatures omit important injected dependencies (Git runner, clock, checksum writer, policy parser, adapters). When implementing, inject them explicitly so authorization tests can run without any external system. Do **not** import host filesystem/network functionality at module import time. Define typed operational failures rather than return ambiguously successful `None` values.

## A1 — source-bound foundation (`AP-R1`–`AP-R4`)

**Deliverable:** a read-only, network-free preflight usable against disposable Git repositories, strict packet/policy validators, and negative tests. No external executor at A1.

**Suggested implementation order:**

1. Inspect existing `tools/specs/validate_state.py`, `docs/specs/state.schema.json`, `tests/test_spec_state.py` and root `AGENTS.md`. Record checkpoint semantics without changing them.
2. Define independent valid/invalid packet fixtures (unknown version/key, duplicate key, stale SHA, invalid stage/requirement, mismatched branch/base, missing checks). Include positive minimal packets with an actual temporary active spec and its companion plan.
3. Implement strict packet decoding and schema checks in `packet.py`/`stage-task.schema.json`; no string field may be evaluated, interpolated into a shell command, or treated as a credential source.
4. Build Git identity in `git_snapshot.py` with argument-vector subprocess calls, explicit cwd, bounded output and exit checks. Verify the actual checkout and protected ref state; never trust packet values as Git facts.
5. Validate allowlists and changed-path inventory in `path_policy.py`: canonical repository-relative paths, physical symlink containment, file modes, untracked/renamed/deleted paths, case collisions and path aliases. Reject unknown ownership and special files.
6. Add `pipeline-policy.json` with a default-deny classification. `geo-format` contracts, installation/storage/security/platform adapters, dependency/plugin changes, network access and broad refactors require escalation, irrespective of filename extensions.
7. Keep CLI read-only: a proposed `preflight` operation may print a typed decision but must not create worktrees, call external tools, write checkpoint state or touch refs.
8. Wire a focused deterministic A1 test target into the documented canonical gate **only once the new tests exist**. Keep existing `make check` independent of external services.

**Independent oracles:**

| Scenario | Fixture/action | Required observation |
|---|---|---|
| `AP-A1-P01` | Clean temporary feature branch, correct packet/digests and bounded test path | Explicit allow; no file/ref/network side effect |
| `AP-A1-N01` | Dirty tracked/untracked path, stale `HEAD`, wrong branch | Reject before execution, preserve tree and refs |
| `AP-A1-N02` | Wrong spec/plan digest, unknown stage, duplicate JSON fields, unsupported schema version | Reject with typed reason, no implicit permission |
| `AP-A1-N03` | Absolute/parent path, symlink escape, case alias, nested worktree, special file | Reject before unsafe path access |
| `AP-A1-B01` | Empty edit allowlist, missing mandatory check, UNKNOWN risk, protected `geo-format` contract | Escalate or reject, never LOW authorization |
| `AP-A1-R01` | Re-run without network, adapters, secrets or PBF data | Same decision/digest; no mutations |

**Completion evidence:** tests covering every oracle, `python3 tools/specs/validate_state.py`, focused test command, network-free `make check`, independent path/security review. Explicitly list blocked gates. Do not claim A1 accepted merely because its tests pass.

## A2 — isolated execution (`AP-R5`–`AP-R7`)

**Prerequisite:** A1 developer acceptance and separately approved tool/credential/privacy policy. There is no default executor and no fallback that silently runs a local shell or remote service.

**Suggested implementation order:**

1. Define an `ExecutionAdapter` seam with bounded input bytes, approved readable/editable paths, cancellation, exit status and per-attempt output capture. Fake adapter first; external implementations are separate, opt-in extensions.
2. Verify fresh A1 preflight, developer approval and packet digest before creating an isolated disposable worktree rooted at the exact immutable base. Preserve developer checkout and refs.
3. Deny committing, checkout/ref updates, branch changes and unapproved spawning in the executor **independently** of tool options. If the sandbox cannot enforce this, fail closed; do not claim it is isolated.
4. Enforce approved time/process/output budgets and cancellation semantics. A timeout or interruption is a terminal recorded attempt; never replay its run ID.
5. Inventory changed/added/deleted/renamed files, symlink targets and modes from Git and filesystem; compare with the exact editable allowlist. Reject no-diff or out-of-scope changes.
6. Compute stable source and task digests over final bytes, not over summaries. Preserve immutable attempt evidence privately, redacting display output without destroying necessary private diagnostics.
7. Do not stage, commit, push, update checkpoint state or promote a ref at this stage. Distinguish expected tool failure, unsafe output and infrastructure failure.

**Independent oracles:** `AP-A2-P01` approved fake edit has a stable source manifest; `AP-A2-N01` outside-path edit fails; `AP-A2-N02` no meaningful diff fails; `AP-A2-N03` attempted commit/ref change fails; `AP-A2-N04` unapproved adapter/credential source fails; `AP-A2-B01` symlink/mode/binary/untracked variation fails closed; `AP-A2-R01` interrupt/timeout preserves evidence and original refs.

**Completion evidence:** fake-backed subprocess/worktree tests, unchanged developer checkout, no unapproved ref movement, positive and adversarial manifests, independent security review. A real external-tool smoke is separately authorized and not an A2 default test.

## A3 — verification, independent review and bounded repair (`AP-R8`–`AP-R11`)

**Prerequisite:** A2 independently accepted. This stage cannot infer reviewer approval from execution success.

**Suggested implementation order:**

1. Introduce a fixed, versioned check registry mapping approved check IDs to argument-vector commands; do not accept arbitrary packet-provided command strings. Use the existing `Makefile` and `docs/TESTS.md` command matrix as owner truth.
2. Bind check execution to the exact resulting source manifest and approved environment. Capture exit code, truncated-display/full-private logs, prerequisite failures, resource budget and a new source digest after checks; fail on changed bytes during checks.
3. Require focused tests plus `make check`; for changed Kotlin contracts require configured Gradle/Desktop/Android gates. For package/PMTiles/native behavior require explicitly marked real-data/manual evidence; absence means BLOCKED/NOT_VERIFIED, never PASS.
4. Define a closed typed review result containing source digest, evaluated requirements, severity-ranked findings and verdict. Independently configured reviewer input must be minimized, redacted and authorized; malformed, stale or incomplete output is not approval.
5. Use a separate risk/security decision: unsafe permissions, secret exposure, unapproved external egress or unknown scanner status stop before review/promotion. Adding a scanner or remote endpoint requires its own explicit approval and regression suite.
6. Permit bounded repair only for a diagnosed, approved LOW-risk implementation fault. Every repair creates a new immutable attempt with new bytes/digest; repeat all required checks and review. Never change valid assertions to obtain PASS.
7. Stop without automatic retry for policy violations, missing checks, security findings, reviewer drift, infrastructure outage, protected scope or exhausted budget.

**Independent oracles:** `AP-A3-P01` matching source/check/review digests permit consideration; `AP-A3-N01` failing valid regression blocks; `AP-A3-N02` stale review digest blocks; `AP-A3-N03` malformed review/missing log/security finding blocks; `AP-A3-N04` reviewer timeout/network outage is not PASS; `AP-A3-B01` repair budget exhaustion blocks; `AP-A3-R01` any post-review edit invalidates evidence; `AP-A3-A01` assertion weakening is detected and rejected.

**Completion evidence:** reproducible per-check records, review schema/parser tests, source-digest replay tests, privacy/secret-handling checks and a documented matrix of requirements versus executable and non-executable acceptance gates.

## A4 — custody, guarded promotion and checkpoint separation (`AP-R12`–`AP-R15`)

**Prerequisite:** A3 developer acceptance. Do not equate repository checkpoint validation with authorization.

**Suggested implementation order:**

1. Specify private evidence directory ownership and atomic, bounded, append-only writes outside routine build/cache cleanup. Validate manifest bytes and digests when loading; protect secrets and log permissions.
2. Record a developer approval bound to the exact task, policy, source manifest, check list, reviewer identity and expected base/ref. Do not trust a self-authored `approvalRef` in a packet.
3. Prepare promotion intent *before* any ref operation, with durable expected-ref and source identity. Re-read current Git ref and verify evidence on the exact bytes immediately before action.
4. If separately authorized to automate promotion, construct one commit from already-verified bytes and compare-and-swap only a non-checked-out `feature/*` ref. Never reset, checkout, force-push, rebase, merge or mutate `main` as a recovery shortcut.
5. Write the promotion receipt durably. Unknown/ref-changed/CAS-failed outcome must become an explicit blocked state and be reconciled by the developer without retrying a consumed identifier.
6. Keep `docs/specs/STATE.json` updates manual and **between** independent runs. Validate it with the existing read-only validator; do not extend schema to represent RUNNING, acceptance or a lock.
7. Next stage packet must bind the developer-confirmed promoted HEAD and predecessor receipt; mismatches stop without execution.

**Independent oracles:** `AP-A4-P01` consistent approved commit and compare-and-swap succeeds in a throwaway repo; `AP-A4-N01` changed ref fails without override; `AP-A4-N02` stale/missing approval or review evidence fails; `AP-A4-N03` modified log/manifest fails hash validation; `AP-A4-B01` interrupted promotion retains durable intent; `AP-A4-R01` consumed run ID cannot be replayed; `AP-A4-A01` checkout/HEAD/main remain unchanged on every rejection.

**Completion evidence:** adversarial Git fixture suite, custody/permission checks, interruption recovery evidence and a clearly documented manual reconciliation flow. No Git acceptance claim follows automatically from a successful CAS.

## A5 — authorized pilot and rollout decision (`AP-R16`–`AP-R18`)

**Prerequisite:** accepted A1–A4 behavior, verified clean feature baseline, approved adapter/privacy/security configuration and individual authorization. No unattended multi-stage execution.

1. Choose one low-risk, test-only stage with an independent observable acceptance oracle and explicit non-goals. Bind stage/spec/plan/policy digests, exact source identity and allowable files.
2. Dry-run with a fake adapter and rejected-case suite. Require no file/ref/network effects on preflight denial.
3. Run one explicitly authorized external-tool attempt only if required toolchain/permissions exist; otherwise record BLOCKED and do not fabricate pilot evidence.
4. Collect exact input/output identity, source diff, tests, review, secret/security verdict (when configured), custody receipt and human decision. Re-run or re-review when any bytes change.
5. Exercise adversarial recovery matrix: stale base, wrong stage, path/symlink escape, mode change, missing review/check, timeout, interruption, changed ref, tampered evidence, changed source after PASS, duplicate run ID.
6. Independently review all `AP-R1`–`AP-R18` evidence and remaining non-executable acceptance gates. The developer alone decides whether the optional workflow is ready for broader work.
7. On acceptance, move durable operational facts into owning documentation and archive normative spec plus companion together; do not auto-archive or claim product P0/P1 acceptance.

**Independent oracles:** `AP-A5-P01` one fully approved test-only pilot retains complete evidence; `AP-A5-N01` every injected failure blocks promotion and preserves evidence; `AP-A5-N02` missing native/configured gate stays NOT_VERIFIED; `AP-A5-R01` changed bytes after PASS force rechecks and fresh review; `AP-A5-A01` no unattended queue, PR merge or cross-repository action occurs.

## Validation matrix and repeatable handoff

| Stage | Must run on a complete repository once implemented | Must explicitly report as unavailable if missing |
|---|---|---|
| A1 | Packet/path/Git fake-backed tests, checkpoint validator, `make check` | Git fixture prerequisites, Python environment |
| A2 | Isolated fake-adapter/worktree tests + A1 regressions + `make check` | Approved real executor and sandbox policy |
| A3 | Fixed check-registry tests, independent review parsing/digest oracles, `make check`, relevant `make check-all` | Host SDK, reviewer/scanner configuration, real datasets |
| A4 | Evidence/recovery/CAS adversarial Git tests, validator, `make check` | Explicit promotion authorization; checked-out-ref constraints |
| A5 | Complete fault-injection matrix, authorized one-stage pilot, source-bound review, configured checks | Network/privacy approval, native platform manual checks |

At each implementation-stage handoff record: normative stage ID and AP requirement IDs; expected observable behavior; exact edited/removed paths; validation commands actually executed and results; blocked/missing evidence; new source digest; next two ordered stages; unresolved risk and decisions. The current `STATE.json` checkpoint is **not** an acceptance record and must remain untouched during ordinary app work. A manual edit to source after review invalidates prior checks and review.

## Decisions requiring explicit approval before implementation

- Whether this parked optional pipeline should be implemented at all; which task types, if any, warrant automation.
- Which executor/sandbox technology can *enforce* no-commit, no-network/egress and allowed-path restrictions, independent of self-reported flags.
- Whether third-party review/security services are used, and their privacy, credential, retention, service access and cost rules.
- Exact packet/schema and policy versioning, resource budgets, private evidence custody/retention, and the approval proof format.
- Whether guarded Git promotion is desired instead of a purely manual developer-owned commit workflow.

Until these decisions and the stage-specific acceptance evidence exist, keep the design inactive; ordinary OsmapDigger development does not depend on or invoke it.
