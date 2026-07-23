# Performance Plan Outputs

이 디렉터리는 Codex가 사용자 승인 후 생성하는 분석 산출물을 저장한다.

예상 파일:

```text
performance/plans/
├── chatbot-code-map.md
├── chatbot-bottleneck-hypotheses.md
├── group-code-map.md
├── group-concurrency-hypotheses.md
├── observability-plan.md
└── jmeter-test-plan.md
```

각 문서는 다음 원칙을 따른다.

- FACT/HYPOTHESIS/UNKNOWN 구분
- 파일과 메서드 경로 포함
- 재현 시나리오 포함
- 측정 지표 포함
- 성공·실패 판정 기준 포함
- 사용자 승인 전 프로덕션 코드 변경 금지
