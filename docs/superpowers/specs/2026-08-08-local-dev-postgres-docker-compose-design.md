# 로컬 dev 실행 DB를 H2에서 Docker Compose Postgres로 전환

## 배경

현재 로컬 개발 서버(`bootRun`, `persistence-local` 프로파일)는 H2 인메모리 DB(`MODE=PostgreSQL`)를 사용한다. 반면 jOOQ codegen(`dev.monosoul.jooq-docker` 플러그인)은 빌드 시점에 임시 Postgres 컨테이너를 띄워 Flyway 마이그레이션을 적용하고 그 스키마를 기준으로 코드를 생성한다.

즉 jOOQ가 생성한 코드는 Postgres 스키마를 기준으로 하지만, 실제 앱 실행은 H2에서 이루어져 방언 차이(JSONB, 배열, upsert 문법 등)로 인한 버그가 로컬에서는 드러나지 않고 운영에서만 드러날 수 있는 구조다.

## 목표

로컬 dev 실행 시 H2를 제거하고, Spring Boot Docker Compose 모듈로 Postgres 컨테이너를 자동 기동/종료하여 jOOQ codegen과 런타임 DB 엔진을 일치시킨다.

## 결정 사항

- **구현 방식**: Spring Boot Docker Compose 모듈 (`spring-boot-docker-compose`). Testcontainers 기반 커스텀 `@TestConfiguration` 방식 대신 채택 — `compose.yaml` 선언만으로 구성이 끝나 설정이 단순하고 명시적이다.
- **H2 처리**: 완전 제거. `persistence-local` 프로파일을 Postgres 전용으로 일원화한다. H2를 선택지로 남기지 않는다.
- **데이터 영속성**: 볼륨 미사용. 서버를 재시작할 때마다 컨테이너와 데이터를 깨끗한 상태로 새로 시작한다 (Flyway 마이그레이션부터 매번 재적용).
- **컨테이너 생명주기**: `spring.docker.compose.lifecycle-management=start-and-stop`. 서버 시작 시 `docker compose up`, JVM 종료 시 `docker compose stop`. 컨테이너를 완전히 삭제(`down`)하지 않고 멈추기만 하여 다음 실행 속도를 확보한다. 볼륨이 없으므로 stop 상태에서 재사용되든 안 되든 데이터 관점에서는 차이가 없다.
- **포트**: 컨테이너 포트를 고정하지 않고 랜덤 매핑한다. Spring Boot가 실제 매핑된 포트를 자동으로 찾아 접속하므로, 로컬에 이미 Postgres가 떠 있어도 충돌하지 않는다.
- **테스트 영향 없음**: `persistence-test-testcontainer` 의존성 및 테스트 코드는 이번 변경과 무관하며 그대로 유지한다.

## 변경 사항

### 1. `compose.yaml` (프로젝트 루트, 신규)

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

### 2. `gradle/libs.versions.toml`

- `spring-boot-h2` 라이브러리 제거 (다른 용도로 쓰이지 않는지 확인 후 제거)
- `spring-docker-compose = { module = "org.springframework.boot:spring-boot-docker-compose", version.ref = "spring-boot" }` 추가
- `persistence-database-embedded` 번들을 H2 대신 `spring-docker-compose`로 교체

### 3. `build.gradle.kts`

- `developmentOnly(libs.bundles.persistence.database.embedded)`는 그대로 유지 (번들 내용만 교체되므로 코드 변경 불필요)

### 4. `src/main/resources/application.yaml`

`persistence-local` 프로파일 블록을 교체:

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
  docker:
    compose:
      lifecycle-management: start-and-stop
```

- 기존 `datasource.url/username/password` 명시 제거 — Docker Compose 모듈이 컨테이너 기동 후 `ConnectionDetails`를 자동 주입하므로 명시하면 오히려 자동 연결과 충돌한다.
- `h2.console` 설정 블록 제거.

## 검증 방법

1. `./gradlew bootRun` 실행 → 로그에서 Docker Compose가 postgres 컨테이너를 올리는 것을 확인
2. 앱이 Flyway 마이그레이션을 정상 적용하는지 확인 (테이블 생성 로그)
3. 애플리케이션이 정상 기동하고 API 요청이 DB에 반영되는지 간단히 확인
4. `Ctrl+C`로 서버 종료 → `docker ps` / `docker ps -a`로 컨테이너가 stop 상태가 되는지 확인
5. 재실행 시 데이터가 초기화된 상태(마이그레이션부터 재적용)로 시작되는지 확인

## 범위 밖

- 운영(`persistence-prod`) 프로파일은 변경하지 않는다 (이미 실제 Postgres 사용 중).
- jOOQ codegen용 컨테이너(`jooq-docker` 플러그인)는 변경하지 않는다 — 빌드 프로세스 종속이라 이번 변경과 별개다.
- 데이터 영속성이 필요해지면(볼륨 추가) 별도 후속 작업으로 다룬다.
