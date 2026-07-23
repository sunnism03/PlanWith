# PlanWith Project Overview

## Product

PlanWith는 AI 비서가 일정과 투두 등록을 보조하는 일정 관리 서비스다.

주요 기능:

- 회원가입·로그인
- 개인 투두와 개인 일정
- 그룹과 그룹 멤버
- 그룹 투두와 그룹 일정
- 카테고리
- 실시간 알림
- Chatbot 기반 일정·투두 자동 생성

## Backend baseline

현재 공개 저장소와 제공된 트리를 기준으로 확인된 구성:

- Java 17
- Spring Boot 3.3.5
- Gradle
- Spring MVC
- Spring Data JPA
- Spring Security와 JWT
- Redis
- MySQL
- OpenAI API
- Springdoc OpenAPI

현재 `build.gradle`에는 Actuator가 포함되지 않은 상태일 수 있으므로,
로컬 소스를 다시 확인한 뒤 추가 계획을 제시한다.

## Relevant packages

### P1 Chatbot

```text
com.JAI.chatbot
├── controller
├── controller.dto
├── domain
├── exception
├── mapper
├── service
└── RedisChatbotUtil
```

연관 패키지:

- `com.JAI.config`
- `com.JAI.global.util`
- `com.JAI.event`
- `com.JAI.todo`
- `com.JAI.category`
- `com.JAI.alarm`
- `com.JAI.user.jwt`

### P2 Group

```text
com.JAI.group
├── controller
├── converter
├── domain
├── exception
├── repository
└── service
```

연관 패키지:

- `com.JAI.alarm`
- `com.JAI.user`
- `com.JAI.category`
- `com.JAI.event`
- `com.JAI.todo`

## Source-of-truth order

1. 현재 로컬 branch의 Controller와 Service
2. 현재 로컬 Entity, Repository, DB migration/schema
3. 현재 로컬 application profile
4. 첨부 API 명세
5. 공개 GitHub `develop` branch
6. 이 문서

문서나 명세가 코드와 다르면 실제 로컬 코드를 우선한다.
