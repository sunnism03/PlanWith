# PlanWith Repository Instructions

## Performance Experiment Rules

- 실제 동작 판단에는 현재 로컬 애플리케이션 코드와 설정을 우선한다.
- 분석 결과를 `FACT`, `HYPOTHESIS`, `UNKNOWN`으로 구분한다.
- secret 값은 출력하거나 결과 파일에 저장하지 않는다.
- 운영 환경을 대상으로 부하 테스트하지 않는다.
- 실제 OpenAI API에는 고부하를 전송하지 않는다.
- baseline과 after는 의도된 애플리케이션 변경 외에 동일한 조건으로 측정한다.
- 각 experiment는 `BASELINE_COMMIT`에서 분기한 별도 branch와 Git worktree에서 격리한다.
- 측정값을 조작하거나 측정하지 않은 값을 추정하지 않는다.
- 테스트 실패를 숨기거나 성공으로 기록하지 않는다.
- 기존 사용자 코드와 변경 사항을 보존한다.
- `git reset --hard`, 강제 checkout 등 위험한 Git 명령을 사용하지 않는다.
