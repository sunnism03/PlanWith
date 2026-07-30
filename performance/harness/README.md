# PlanWith Performance Harness

This harness is test-only. It refuses real OpenAI in dry-run/measured mode and never tracks JWT or raw result files.

## Dry run

Prerequisites: Java 17, JMeter CLI, Redis server, `curl`, `jq`, and a built Spring Boot jar.

```bash
performance/harness/bin/dry-run.sh
```

The script uses:

- local H2 file DB under the runtime result directory;
- local Redis on a dedicated port;
- local OpenAI-compatible mock;
- one generated test user;
- JMeter CLI with one thread and one iteration.

Runtime secrets and raw output are stored under `performance/harness/runtime/`, which is ignored.

The JMeter plan writes each successful response token to an ignored runtime token file.
`record-redis-keys.sh` resolves those tokens to the exact
`chatbot_token:<token>` and `chatbot:<payload-id>` keys, records their TTLs in
an ignored JSON ledger, and rejects missing or malformed keys. `cleanup.sh`
accepts only that ledger, verifies every exact key and positive TTL, deletes
only those exact keys, and verifies that they are absent afterward. It never
uses Redis `SCAN`, wildcard patterns, or broad prefix deletion.

`verify-user-data-postcondition.sh` checks each test user in `users.csv` before
and after C1. For the configured scenario year/month, both Event and Todo
counts must be zero.

## Result layout

Tracked result prose stays in the experiment directory:

```text
performance/results/<experimentId>/baseline/summary.md
```

Raw run data is placed below an ignored directory:

```text
performance/results/<experimentId>/baseline/raw/<runId>/
```

JTL, JSONL, logs, HTML reports, `users.csv`, JWT material, token files and
generated Redis key ledgers must remain under the ignored raw directory.
Small secret-free workflow metadata may be tracked only when the workflow
contract explicitly requires it; raw payloads are never tracked.

## Validators

```bash
performance/harness/bin/validate-plans.sh
performance/harness/bin/validate-harness.sh
performance/harness/bin/validate-run-artifacts.sh <run-directory>
performance/harness/bin/validate-comparison.sh <comparison.md>
performance/harness/bin/scan-secrets.sh <path>
```

The following regression test starts only an isolated loopback Redis instance.
It proves that exact ledger keys are removed while an unrelated
`chatbot:<uuid>` key remains:

```bash
performance/harness/bin/test-exact-key-cleanup.sh
```

It is never an EC2/shared-Redis test and is not run automatically.

## Deployment preflight

Run this on the PlanWith EC2 host. It prints only non-secret deployment facts.

```bash
performance/harness/bin/deployment-preflight.sh \
  planwith-api \
  <pinpoint-collector-private-host>
```

Do not paste full `docker inspect`, container environment, or application
configuration output into performance artifacts.
