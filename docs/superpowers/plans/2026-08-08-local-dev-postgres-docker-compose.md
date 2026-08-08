# 로컬 dev 실행 DB를 H2에서 Docker Compose Postgres로 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로컬 dev 실행(`bootRun`, `persistence-local` 프로파일)이 H2 대신 Docker Compose로 자동 기동/종료되는 Postgres를 사용하도록 전환한다.

**Architecture:** 프로젝트 루트에 `compose.yaml`을 추가하고, `spring-boot-docker-compose` 의존성을 `developmentOnly`로 추가한다. Spring Boot Docker Compose 모듈이 `bootRun` 시작 시 `docker compose up`을, JVM 종료 시 `docker compose stop`을 자동으로 수행하며, 컨테이너 접속 정보(`ConnectionDetails`)를 자동으로 데이터소스에 주입한다. `persistence-local` 프로파일에서 H2 관련 설정(datasource url/username/password, h2.console)을 모두 제거하고 `docker.compose.lifecycle-management` 설정으로 교체한다.

**Tech Stack:** Spring Boot 4.1.0, Gradle Kotlin DSL, Docker Compose (로컬에 Docker 29.3.0 / Compose v5.3.1 설치 확인됨), PostgreSQL 17, Flyway.

## Global Constraints

- `spring-boot` 버전은 카탈로그의 `spring-boot = "4.1.0"`에 맞춰야 한다 (다른 spring-boot-starter류와 동일 `version.ref`).
- 운영(`persistence-prod`) 프로파일은 변경하지 않는다.
- jOOQ codegen용 컨테이너(`jooq-docker` 플러그인, `generateJooqClasses` 태스크)는 변경하지 않는다 — 빌드 프로세스 종속이며 이번 변경과 무관하다.
- 테스트(`persistence-test-testcontainer` 번들, `src/test`)는 변경하지 않는다.
- 데이터 볼륨은 사용하지 않는다 — 매 실행마다 깨끗한 상태로 시작한다(Flyway 마이그레이션 재적용).
- 컨테이너 포트는 고정하지 않고 랜덤 매핑한다 (`ports: - "5432"` 형태, 호스트 포트 생략).

---

### Task 1: `compose.yaml` 추가 및 의존성 카탈로그 갱신

**Files:**
- Create: `compose.yaml` (프로젝트 루트, `settings.gradle.kts`와 같은 위치)
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Consumes: 없음 (최초 태스크)
- Produces: Gradle 버전 카탈로그에 `libs.spring.docker.compose` 라이브러리 별칭과 `libs.bundles.persistence.database.embedded` 번들(교체됨) — Task 2가 `build.gradle.kts`에서 이 별칭들을 참조한다.

- [ ] **Step 1: `compose.yaml` 파일 작성**

`/Users/jin/Documents/repository/lol-view-back/compose.yaml` 생성:

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: lolviewback
      POSTGRES_USER: lolviewback
      POSTGRES_PASSWORD: lolviewback
    ports:
      - "5432"
```

- [ ] **Step 2: `libs.versions.toml`에 `spring-docker-compose` 라이브러리 추가**

`gradle/libs.versions.toml`의 `[libraries]` 섹션, `spring-boot-h2` 정의(24번째 라인 부근, `spring-boot-h2 = { module = "org.springframework.boot:spring-boot-h2console", version.ref = "spring-boot"}`) 바로 아래에 추가:

```toml
spring-docker-compose = { module = "org.springframework.boot:spring-boot-docker-compose", version.ref = "spring-boot" }
```

- [ ] **Step 3: `spring-boot-h2` 라이브러리 정의 제거**

같은 파일에서 다음 줄을 삭제:

```toml
spring-boot-h2 = { module = "org.springframework.boot:spring-boot-h2console", version.ref = "spring-boot"}
```

- [ ] **Step 4: `persistence-database-embedded` 번들을 새 라이브러리로 교체**

`[bundles]` 섹션에서:

```toml
persistence-database-embedded = ["spring-boot-h2"]
```

를 다음으로 교체:

```toml
persistence-database-embedded = ["spring-docker-compose"]
```

- [ ] **Step 5: 카탈로그 문법 검증**

Run: `cd /Users/jin/Documents/repository/lol-view-back && ./gradlew help --quiet`
Expected: 에러 없이 종료 (TOML 문법 오류나 존재하지 않는 `version.ref` 참조 시 Gradle이 카탈로그 파싱 단계에서 실패한다).

- [ ] **Step 6: Commit**

```bash
git add compose.yaml gradle/libs.versions.toml
git commit -m ":sparkles: add docker compose postgres for local dev"
```

---

### Task 2: `application.yaml`의 `persistence-local` 프로파일을 Postgres 기반으로 전환

**Files:**
- Modify: `src/main/resources/application.yaml:49-69` (`persistence-local` 프로파일 블록)

**Interfaces:**
- Consumes: Task 1에서 추가한 `compose.yaml`(서비스명 `postgres`, DB `lolviewback`)과 `spring-boot-docker-compose` 의존성(Task 1의 번들 교체로 `build.gradle.kts`의 `developmentOnly(libs.bundles.persistence.database.embedded)`가 자동으로 끌어온다 — `build.gradle.kts` 자체는 수정하지 않음).
- Produces: `bootRun` 실행 시 Postgres 컨테이너 자동 기동/종료 및 Flyway 마이그레이션 자동 적용. 이후 태스크 없음(최종 태스크).

- [ ] **Step 1: `persistence-local` 블록 교체**

`src/main/resources/application.yaml`에서 다음 블록(49~69번째 줄):

```yaml
--- # persistence-local
spring:
  config:
    activate:
      on-profile: persistence-local
  jpa:
    generate-ddl: false
  flyway:
    enabled: true
    locations: classpath:db/migration, classpath:db/seed
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
#redis:
#  host: localhost
#  port: 6379
```

를 다음으로 교체:

```yaml
--- # persistence-local
spring:
  config:
    activate:
      on-profile: persistence-local
  jpa:
    generate-ddl: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  docker:
    compose:
      lifecycle-management: start-and-stop
#redis:
#  host: localhost
#  port: 6379
```

주의: `src/main/resources/db/seed` 디렉토리는 현재 존재하지 않는다 (`src/main/resources/db/migration`만 존재). `flyway.locations`에서 `classpath:db/seed`를 제거한다 — 존재하지 않는 위치가 남아 있으면 Flyway가 마이그레이션 적용 시 에러를 낸다. 추후 seed 파일이 추가되면 그때 다시 넣는다.

- [ ] **Step 2: dev 프로파일로 부팅하여 Postgres 컨테이너 자동 기동 확인**

Run: `cd /Users/jin/Documents/repository/lol-view-back && SPRING_PROFILES_ACTIVE=dev timeout 60 ./gradlew bootRun --args='--server.port=9031' &`

그 다음 별도 셸에서:

Run: `sleep 15 && docker ps --filter "ancestor=postgres:17"`
Expected: `postgres:17` 이미지로 뜬 컨테이너가 한 개 나열되고, 포트가 `0.0.0.0:<random>->5432/tcp` 형태로 랜덤 매핑되어 있음.

- [ ] **Step 3: 애플리케이션 로그에서 Flyway 마이그레이션 적용 확인**

Run: `curl -s http://localhost:9031/actuator/health || echo "actuator 미노출 시 생략 가능"`

Gradle/앱 로그에서 다음을 확인:
- `Flyway Community Edition ... by Redgate` 로그와 `Successfully validated 1 migration` 혹은 `Migrating schema "public" to version "1"` 로그가 출력됨
- `Started LolViewBackApplication` 로그가 출력되며 예외 없이 기동 완료

- [ ] **Step 4: 서버 종료 시 컨테이너가 stop 되는지 확인**

Run: `pkill -f 'GradleDaemon.*bootRun' 2>/dev/null; jobs -l` 대신, 포그라운드로 띄웠다면 `Ctrl+C`. 백그라운드로 띄운 경우:

Run: `kill %1 2>/dev/null || true; sleep 5`
Run: `docker ps --filter "ancestor=postgres:17"` → 실행 중인 컨테이너 없음 확인
Run: `docker ps -a --filter "ancestor=postgres:17"` → `Exited` 상태의 컨테이너가 남아 있음을 확인 (삭제되지 않고 stop만 됨 — `start-and-stop` 정책의 기대 동작)

- [ ] **Step 5: 재실행 시 데이터가 초기화된 상태로 시작되는지 확인**

Run: `cd /Users/jin/Documents/repository/lol-view-back && SPRING_PROFILES_ACTIVE=dev timeout 30 ./gradlew bootRun --args='--server.port=9031'`

로그에서 Flyway가 `V1__create_table.sql` 마이그레이션을 처음부터 다시 적용하는 것을 확인 (볼륨이 없으므로 이전 컨테이너의 데이터가 남아있지 않아야 함 — stop된 기존 컨테이너를 재사용하지 않고 매번 새 컨테이너로 뜨는지, 혹은 재사용되더라도 `docker compose up`이 상태를 초기화하는지 확인. 만약 stop된 컨테이너가 재사용되어 데이터가 남아있다면 `docker compose rm -f postgres` 후 재시도하여 스펙대로 "매번 깨끗하게 시작" 요건이 실질적으로 충족되는지 판단하고, 충족되지 않으면 `compose.yaml`에 `POSTGRES_HOST_AUTH_METHOD` 등이 아니라 컨테이너 재생성이 필요함을 사용자에게 보고한다).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.yaml
git commit -m ":truck: switch persistence-local profile from h2 to docker compose postgres"
```

---

## Self-Review Notes

- **Spec coverage**: compose.yaml(✅ Task1), 의존성 교체(✅ Task1), H2 완전 제거(✅ Task1 라이브러리 제거 + Task2 datasource/h2 설정 제거), persistence-local 프로파일 전환(✅ Task2), lifecycle-management: start-and-stop(✅ Task2), 검증 4단계(✅ Task2 Step2-5) 모두 커버됨.
- **범위 밖 항목**(persistence-prod, jooq-docker 플러그인, 테스트 testcontainer) 변경 없음 — 계획에서 손대지 않음을 확인.
- **주의사항**: spec 원문의 `flyway.locations: classpath:db/migration, classpath:db/seed`를 그대로 옮기면 존재하지 않는 `db/seed` 디렉토리 때문에 부팅이 실패할 수 있어, 계획에서는 `db/seed`를 제거하고 그 사유를 명시했다. seed 디렉토리가 추후 생기면 다시 추가하면 된다.
