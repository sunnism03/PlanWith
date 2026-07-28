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

## Validators

```bash
performance/harness/bin/validate-plans.sh
performance/harness/bin/validate-harness.sh
performance/harness/bin/validate-run-artifacts.sh <run-directory>
performance/harness/bin/validate-comparison.sh <comparison.md>
performance/harness/bin/scan-secrets.sh <path>
```

