# Phase 6.5 — Test Harness Report

## Input files

- `performance/plans/jmeter-test-design.md`
- `performance/harness/**`
- Current application jar built from local application source

## Harness content

- JMeter C1 JMX with HTTP, body-code and JSON Extractor assertions
- runtime-only user/JWT CSV bootstrap
- OpenAI-compatible local mock with configurable delay
- Actuator collector
- run metadata and artifact manifest generation
- DB-backed API and direct Redis invariant checks
- Redis cleanup
- plan, harness, artifact, comparison and secret validators

## Dry-run environment

- runId: `dry-20260728T181551Z-40677`
- users: 1
- OpenAI mode: `MOCK`
- application: local Spring Boot jar
- DB: isolated H2 file DB
- Redis: isolated local Redis port
- JMeter: CLI
- raw result path: ignored `performance/harness/runtime/<runId>/`

No JWT, generated password, JWT signing secret or OpenAI key is recorded here.

## Verification results

| Check | Result | Evidence |
|---|---|---|
| Application start | PASS | local app accepted test signup/login |
| Real OpenAI blocked | PASS | mock URL injected; mock server handled both calls |
| 1-user JMeter run | PASS | one sample, error rate 0 |
| HTTP assertion | PASS | actual HTTP 200 |
| Body assertion | PASS | `code=201` |
| JSON Extractor | PASS | token extractor assertion passed |
| DB invariant | PASS | no personal event persisted by C1 |
| Redis invariant | PASS | staged payload and token mapping observed |
| cleanup | PASS | remaining Chatbot keys 0 |
| Actuator file | CREATED / NOT_AVAILABLE | current app has no Actuator endpoint |
| artifact manifest | PASS | validator exit code 0 |
| secret scan | PASS | validator exit code 0 |

## Retry history

1. Sandbox port binding denied: rerun with approved localhost test-process permission.
2. First application start with H2 lacked explicit driver/dialect: harness fixed.
3. Test nickname exceeded DB column: fixture shortened.
4. Initial assertion expected HTTP 201: runtime proved HTTP 200 with body code 201; design and JMX corrected.
5. JTL validation quoting bug: harness fixed.
6. A completion audit found that H2 shutdown changed the DB file after the manifest was generated.
7. Process shutdown was moved before manifest generation; the final fresh run passed validation both immediately and after shutdown.

Failed raw runs remain under ignored runtime directories and were not rewritten as successes.

## Build/test observation

- `./gradlew bootJar`: PASS.
- `./gradlew test`: FAIL because the existing test package cannot discover `@SpringBootConfiguration`.
- This existing test failure is recorded and not hidden. It did not prevent the separate application dry run from proving the harness path.

## Commit boundary

- `HARNESS_COMMIT`: `0b3f4d8b05f344e286d50d41e9a4c322bcf26f42`
- It contains harness content only.
- `harness-manifest.json`, this report and state are stored in the following metadata-only local commit.
- No remote push or PR was performed.

## Remaining UNKNOWN

- Pinpoint traceability.
- Live MySQL constraints/indexes.
- Actuator metrics until a separately authorized observability change exists.
- Group Event executable fixtures; only Chatbot C1 was dry-run in Phase 6.5.

## Completion check

- 1-user mock dry run: **PASS**
- JMeter assertion and JSON Extractor: **PASS**
- Actuator result file creation: **PASS** (`NOT_AVAILABLE` content)
- DB/Redis invariant scripts executed: **PASS**
- cleanup: **PASS**
- secret scan: **PASS**
- manifest and HARNESS_COMMIT: **PASS**
