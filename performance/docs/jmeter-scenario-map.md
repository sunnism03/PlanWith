# Chatbot JMeter Scenario Map

All files are safe-by-default test definitions. Codex does not execute them.
The user runs each scenario independently from a Mac with explicit environment
properties and an ignored users CSV.

| Scenario | File | Hypotheses | Default workload | Purpose |
|---|---|---|---|---|
| S1 | `chatbot-s1-smoke.jmx` | prerequisite for all | 1 user × 1 iteration | request, auth, assertions, token extraction |
| S2 | `chatbot-s2-openai-delay.jmx` | CHAT-PERF-01, CHAT-PERF-02 | 1 × 1 | mock delay sensitivity |
| S3 | `chatbot-s3-concurrency.jmx` | CHAT-PERF-03, CHAT-PERF-05 | 1 × 1 | independent concurrency steps |
| S4 | `chatbot-s4-burst.jmx` | CHAT-PERF-03, CHAT-OBS-01 | 1 × 1 | short synchronized arrival |
| S5 | `chatbot-s5-sustained.jmx` | CHAT-PERF-03, CHAT-PERF-06 | 1 user, 10 seconds | sustained low-load observation |
| S6 | `chatbot-s6-intention-comparison.jmx` | CHAT-PERF-04 | 1 × 1 | run separately with empty/EVENT intention |
| S7 | `chatbot-s7-failure-resilience.jmx` | CHAT-REL-01, CHAT-OBS-01 | 1 × 1 | mock-only failure profiles |
| S8 | no JMX (`NOT_APPLICABLE`) | CHAT-PERF-02 | none | no cache exists in current code |
| S9 | `chatbot-s9-db-pool.jmx` | CHAT-PERF-05 | 1 × 1 | observe auth SQL and Hikari under API load |
| S10 | `chatbot-s10-baseline-candidate.jmx` | pending evidence | 1 × 1 | draft only; finalize after S1–S9 evidence |

S2 delay values and S7 failure profiles configure the external mock process;
JMeter properties label the intended condition but cannot reconfigure the
server-side mock by themselves. The repository mock supports `MOCK_DELAY_MS`,
`MOCK_PROFILE`, and `MOCK_TIMEOUT_DELAY_MS`.
