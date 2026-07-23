#!/bin/bash

set -e

PROJECT_ROOT="/home/ubuntu/jai"
BUILD_DIR="$PROJECT_ROOT/build/libs"

JAR_FILE="$PROJECT_ROOT/ToDoWithMe-0.0.1-SNAPSHOT.jar"
ENV_FILE="/opt/planwith/runtime.env"

APP_LOG="$PROJECT_ROOT/application.log"
ERROR_LOG="$PROJECT_ROOT/error.log"
DEPLOY_LOG="$PROJECT_ROOT/deploy.log"
PID_FILE="$PROJECT_ROOT/application.pid"

TIME_NOW=$(date '+%Y-%m-%d %H:%M:%S')

# 1. 환경변수 파일 확인
if [ ! -f "$ENV_FILE" ]; then
  echo "$TIME_NOW > 환경변수 파일을 찾을 수 없습니다: $ENV_FILE" >> "$DEPLOY_LOG"
  exit 1
fi

# 2. 환경변수 로드
set -a
source "$ENV_FILE"
set +a

echo "$TIME_NOW > 환경변수 파일 로드 완료: $ENV_FILE" >> "$DEPLOY_LOG"

# 3. 프로필 강제 지정
# application.yaml의 dev-perf 그룹을 사용
SPRING_PROFILES_ACTIVE="dev-perf"
export SPRING_PROFILES_ACTIVE

echo "$TIME_NOW > 활성 프로필: $SPRING_PROFILES_ACTIVE" >> "$DEPLOY_LOG"

# 4. 실행할 Spring Boot JAR 찾기
SOURCE_JAR=$(find "$BUILD_DIR" \
  -maxdepth 1 \
  -type f \
  -name "*.jar" \
  ! -name "*-plain.jar" \
  | head -n 1)

if [ -z "$SOURCE_JAR" ]; then
  echo "$TIME_NOW > 실행 가능한 JAR 파일을 찾을 수 없습니다: $BUILD_DIR" >> "$DEPLOY_LOG"
  exit 1
fi

# 5. 빌드 파일 복사
echo "$TIME_NOW > $SOURCE_JAR 파일을 $JAR_FILE 로 복사" >> "$DEPLOY_LOG"
cp "$SOURCE_JAR" "$JAR_FILE"

# 6. 기존 프로세스 확인
EXISTING_PID=$(pgrep -f "$JAR_FILE" || true)

if [ -n "$EXISTING_PID" ]; then
  echo "$TIME_NOW > 이미 실행 중인 프로세스가 있습니다: $EXISTING_PID" >> "$DEPLOY_LOG"
  exit 1
fi

# 7. JAR 실행
echo "$TIME_NOW > $JAR_FILE 실행, profile=$SPRING_PROFILES_ACTIVE" >> "$DEPLOY_LOG"

nohup java \
  -jar "$JAR_FILE" \
  --spring.profiles.active="$SPRING_PROFILES_ACTIVE" \
  > "$APP_LOG" \
  2> "$ERROR_LOG" &

CURRENT_PID=$!

echo "$CURRENT_PID" > "$PID_FILE"

echo "$TIME_NOW > 실행된 프로세스 ID: $CURRENT_PID" >> "$DEPLOY_LOG"

# 8. 실행 여부 확인
sleep 8

if kill -0 "$CURRENT_PID" 2>/dev/null; then
  echo "$TIME_NOW > 애플리케이션 프로세스 실행 확인 완료" >> "$DEPLOY_LOG"
else
  echo "$TIME_NOW > 애플리케이션 실행 실패. error.log를 확인하세요." >> "$DEPLOY_LOG"
  exit 1
fi