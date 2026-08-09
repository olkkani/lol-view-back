# esports-api 연동 — Tournament/Match 수집 파이프라인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `esports-api.lolesports.com`에서 Tournament/Match 데이터를 자동으로 수집해 DB에 upsert하는 파이프라인을 구축한다. League는 수동 관리로 남기고, Tournament는 리그별 대회 주기(`league_recurrence_windows`)를 근거로, Match는 일별(예정 경기 수집)/5분(라이브 갱신) 이중 스케줄러로 수집한다.

**Architecture:** `sync` 패키지 아래에 계층을 신설한다 — `dto`(API 응답 역직렬화 전용, 도메인 엔티티와 분리), `client`(WebClient 기반 coroutine API 클라이언트), `mapper`(DTO→Entity 변환), `TournamentDueChecker`(recurrence window 매칭 순수 로직, 단위 테스트 대상), `scheduler`(3개: Tournament 1개, Match 2개). Repository는 이 프로젝트 최초의 Spring Data JPA 계층이므로 엔티티 옆(`infastructure/outbound/repository/`)에 신설한다. `league_recurrence_windows.last_known_start_date` 컬럼은 실제로 "마지막 확인된 Tournament의 종료일"을 담으므로 `start_date`로 리네임한다(V1 마이그레이션 직접 수정 — 아직 실서비스 전).

**Tech Stack:** Kotlin, Spring Boot(WebClient/`spring-webflux`, `spring-boot-starter-data-jpa`, `@Scheduled`), Kotlin Coroutines, PostgreSQL(Flyway), JUnit 5. 신규 의존성 추가 없음 — 필요한 것(webflux, coroutines, jpa)이 이미 카탈로그에 있다.

## Global Constraints

- League는 자동 수집 대상이 아니다. Tournament/Match 스케줄러는 DB에 이미 존재하는 League를 전제로 동작하며, League가 없으면 조회 후보에 오르지 않는다.
- 동일 League 내에서 두 Tournament가 동시에(날짜 겹침) 진행되지 않는다고 가정한다. 이 가정이 깨지는 경우(같은 League에서 트리거 조건을 만족하는 Tournament가 2개 이상)는 로그만 남기고 스킵한다 — 별도 처리 없음.
- 신규 HTTP 클라이언트/스케줄링 라이브러리를 추가하지 않는다. `spring-webflux`(WebClient)를 사용하고, `spring-quartz`는 의존성에 있지만 사용하지 않는다(plain `@Scheduled`로 충분).
- 에러 처리 v1 기본값: 예외를 던지고 스케줄러가 catch-and-log 후 해당 회차만 실패 처리(다음 회차 자동 재시도). 백오프/서킷브레이커/rate-limit 대응/스케줄러 중복 실행 방지(`ShedLock`)는 이번 스코프에 포함하지 않는다.
- 자동화된 통합 테스트(스케줄러 전체 흐름, 실제 API 호출)는 이번 스코프에 포함하지 않는다 — 구현 후 수동 DB 확인으로 검증한다. 단, `TournamentDueChecker`의 순수 판단 로직은 I/O가 없는 단위 테스트로 검증한다(TDD 대상).
- 컬럼/필드 리네임(`last_known_start_date` → `start_date`, `lastKnownStartDate` → `startDate`)은 V1 마이그레이션 파일을 직접 수정한다. 새 버전 마이그레이션을 추가하지 않는다.
- `esports-api.lolesports.com`은 비공식 API — 응답 스키마와 필요 헤더는 실제 호출로 확인해야 한다(Task 2에서 스파이크).

---

### Task 1: `league_recurrence_windows` 컬럼/필드 리네임

**Files:**
- Modify: `src/main/resources/db/migration/V1__create_table.sql`
- Modify: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/entity/LeagueRecurrenceWindow.kt`

**Interfaces:**
- Consumes: 없음 (최초 태스크)
- Produces: `LeagueRecurrenceWindow.startDate: LocalDate` 필드명 — Task 5(`TournamentDueChecker`)와 Task 8(스케줄러)이 이 이름을 사용한다.

- [ ] **Step 1: 마이그레이션에서 컬럼명 변경**

`src/main/resources/db/migration/V1__create_table.sql`에서 `league_recurrence_windows` 테이블 정의를 찾는다:

```sql
CREATE TABLE "league_recurrence_windows"
(
    "id"                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "label"                 varchar,
    "sequence_order"        integer,
    "league_id"             BIGINT,
    "interval_years"        integer,
    "last_known_start_date" date
);
```

`"last_known_start_date" date`를 `"start_date" date`로 교체:

```sql
CREATE TABLE "league_recurrence_windows"
(
    "id"                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "label"                 varchar,
    "sequence_order"        integer,
    "league_id"             BIGINT,
    "interval_years"        integer,
    "start_date"            date
);
```

- [ ] **Step 2: 엔티티 필드명 변경**

`src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/entity/LeagueRecurrenceWindow.kt` 전체를 다음으로 교체:

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository.entity

import io.hypersistence.utils.hibernate.id.Tsid
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDate

@Entity
class LeagueRecurrenceWindow(
    @Id @Tsid
    var id: Long? = null,
    var label: String,
    var sequenceOrder: Int,
    var intervalYear: Int,
    var startDate: LocalDate,

    @ManyToOne @JoinColumn(name = "league_id")
    var league: League,
)
```

(사용하지 않는 `GeneratedValue`, `GenerationType` import와 빈 클래스 바디 `{ }`를 함께 정리한다.)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL (참조하는 곳이 없으므로 컴파일 에러 없음)

- [ ] **Step 4: 로컬 DB로 마이그레이션 재적용 확인**

Run: `./gradlew bootRun --args='--spring.profiles.active=dev'` 를 짧게 띄웠다가 `Ctrl+C`로 종료 (Flyway가 V1을 새로 적용하는 로컬 컨테이너이므로 컬럼명 변경이 반영되는지 로그로 확인). 로그에 Flyway 마이그레이션 에러가 없어야 한다.
Expected: 애플리케이션이 정상 기동 로그를 출력하고 에러 없이 시작됨.

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/db/migration/V1__create_table.sql src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/entity/LeagueRecurrenceWindow.kt
git commit -m "refactor: rename league_recurrence_windows.last_known_start_date to start_date"
```

---

### Task 2: esports-api 응답 구조 확인 및 DTO 정의

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/dto/TournamentApiResponse.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/dto/MatchApiResponse.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `TournamentApiResponse`, `MatchApiResponse` 데이터 클래스 — Task 3(클라이언트)이 반환 타입으로, Task 4(매퍼)가 입력 타입으로 사용한다.

- [ ] **Step 1: 로컬 환경변수 로드**

프로젝트 루트의 `.env.local` 파일(git-ignore됨)에 `LOL_API_KEY`, `LOL_API_URL_TOURNAMENT`, `LOL_API_URL_MATCH`가 이미 채워져 있다. 셸에서 다음으로 로드:

```bash
source .env.local
```

인증 헤더는 `x-api-key: $LOL_API_KEY` 형태로 보낸다. League 엔드포인트는 이번 스파이크에 필요하지 않다(League는 자동 수집 대상이 아니므로).

- [ ] **Step 2: curl로 Tournament/Match 엔드포인트 실제 응답 캡처**

URL은 이미 `?hl=ko-KR&leagueId=` 형태로 쿼리스트링 프리픽스까지 포함되어 있다 — 뒤에 leagueApiId만 이어 붙인다. 샘플 leagueApiId로 LCK를 시도한다(esports-api의 잘 알려진 leagueId 중 하나, `98767991302996019`):

```bash
mkdir -p docs/samples
curl -s -H "x-api-key: $LOL_API_KEY" "${LOL_API_URL_TOURNAMENT}98767991302996019" \
  | tee docs/samples/tournament-response-sample.json | head -c 2000
curl -s -H "x-api-key: $LOL_API_KEY" "${LOL_API_URL_MATCH}98767991302996019" \
  | tee docs/samples/match-response-sample.json | head -c 2000
```

만약 이 leagueId로 빈 배열이나 404가 오면, 같은 헤더로 League 목록 엔드포인트(`https://esports-api.lolesports.com/persisted/gw/getLeagues?hl=ko-KR`)를 호출해 유효한 leagueId를 하나 확인한 뒤 재시도한다.

응답 JSON 구조에서 다음을 반드시 확인한다:
- Tournament 응답: 대회명, 시작일, 종료일, 대회 API ID 필드명 (실제 이 API는 `getTournamentsForLeague` 응답이 `data.leagues[].tournaments[]` 같은 중첩 구조일 가능성이 높다 — 최상위가 배열이 아닐 수 있으니 실제 구조를 반드시 확인)
- Match 응답: 경기 ID, 시작 시각, 매치 타입(BO3/BO5 매핑 가능한 필드), 상태(SCHEDULED/ONGOING/FINISHED에 매핑 가능한 필드), 라벨(이름). `getSchedule` 응답도 `data.schedule.events[]` 같은 중첩 구조일 가능성이 높다.

캡처한 JSON 파일 2개는 `docs/samples/`에 커밋 대상으로 남긴다.

- [ ] **Step 3: 캡처한 실제 구조를 기준으로 `TournamentApiResponse` 작성**

`docs/samples/tournament-response-sample.json`에서 확인한 실제 필드명/중첩 구조로 DTO를 작성한다. 응답이 중첩되어 있다면(예: `{"data":{"leagues":[{"tournaments":[...]}]}}`), 최상위 응답 DTO와 내부 Tournament DTO를 분리하고, Task 3의 클라이언트에서 필요한 리스트만 추출해서 반환하는 방식으로 설계한다. 최종적으로 매퍼(Task 4)가 받는 타입은 다음 형태를 유지한다(필드명은 실제 응답에 맞게 조정):

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class TournamentApiResponse(
    @JsonProperty("id")
    val apiId: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("startDate")
    val startDate: LocalDate,
    @JsonProperty("endDate")
    val endDate: LocalDate,
)
```

- [ ] **Step 4: `MatchApiResponse` 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

data class MatchApiResponse(
    @JsonProperty("id")
    val apiId: String,
    @JsonProperty("startTime")
    val startTime: ZonedDateTime,
    @JsonProperty("label")
    val label: String,
    @JsonProperty("strategy")
    val strategyType: String,
    @JsonProperty("state")
    val state: String,
)
```

실제 캡처한 응답 구조에 맞춰 필드명/중첩을 조정한다(Step 3과 동일한 원칙).

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/dto/ docs/samples/
git commit -m "feat: add esports-api response DTOs based on captured samples"
```

---

### Task 3: `LolEsportsApiClient` 작성

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/LolEsportsApiClient.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/config/LolApiProperties.kt`

**Interfaces:**
- Consumes: `TournamentApiResponse`, `MatchApiResponse` (Task 2), `application.yaml`의 `lol-api.key`/`lol-api.url.tournament`/`lol-api.url.match`
- Produces: `LolEsportsApiClient.fetchTournaments(leagueApiId: String): List<TournamentApiResponse>`, `LolEsportsApiClient.fetchMatchesForLeague(leagueApiId: String): List<MatchApiResponse>`, `LolEsportsApiClient.fetchMatchDetail(matchApiId: String): MatchApiResponse` — Task 8(스케줄러)이 이 함수들을 호출한다.

- [ ] **Step 1: `application.yaml` 바인딩용 `LolApiProperties` 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "lol-api")
data class LolApiProperties(
    val key: String,
    val url: Url,
) {
    data class Url(
        val league: String,
        val tournament: String,
        val match: String,
    )
}
```

- [ ] **Step 2: `LolApiProperties`를 활성화하는 설정 클래스 작성**

`src/main/kotlin/io/olkkani/lolviewback/infastructure/config/LolApiConfig.kt` 신규 생성:

```kotlin
package io.olkkani.lolviewback.infastructure.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(LolApiProperties::class)
class LolApiConfig
```

- [ ] **Step 3: `LolEsportsApiClient` 작성**

Task 2에서 캡처한 실제 응답 구조(배열인지, 중첩된 wrapper인지)에 맞춰 아래 뼈대의 파싱 로직을 조정한다. 아래는 응답이 최상위 배열인 경우 기준 — Task 2 결과가 중첩 구조라면 `awaitBody`로 wrapper DTO를 먼저 받고 필요한 리스트를 추출하는 형태로 바꾼다:

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync

import io.olkkani.lolviewback.infastructure.config.LolApiProperties
import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.TournamentApiResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class LolEsportsApiClient(
    private val properties: LolApiProperties,
    webClientBuilder: WebClient.Builder,
) {
    private val webClient = webClientBuilder.build()

    suspend fun fetchTournaments(leagueApiId: String): List<TournamentApiResponse> {
        return webClient.get()
            .uri("${properties.url.tournament}?leagueId=$leagueApiId")
            .header("x-api-key", properties.key)
            .retrieve()
            .awaitBody<List<TournamentApiResponse>>()
    }

    suspend fun fetchMatchesForLeague(leagueApiId: String): List<MatchApiResponse> {
        return webClient.get()
            .uri("${properties.url.match}?leagueId=$leagueApiId")
            .header("x-api-key", properties.key)
            .retrieve()
            .awaitBody<List<MatchApiResponse>>()
    }

    suspend fun fetchMatchDetail(matchApiId: String): MatchApiResponse {
        return webClient.get()
            .uri("${properties.url.match}/$matchApiId")
            .header("x-api-key", properties.key)
            .retrieve()
            .awaitBody<MatchApiResponse>()
    }
}
```

**주의**: `properties.url.tournament`/`properties.url.match`는 이미 `?hl=ko-KR&leagueId=` 형태의 쿼리 프리픽스를 포함하고 있을 수 있다(Task 2에서 확인한 실제 URL 형태에 맞춰 `application.yaml`의 `lol-api.url.*` 값과 URI 조합 방식을 조정한다 — 단순 `?leagueId=$leagueApiId` 이어붙이기가 아니라 URL 자체에 파라미터가 이미 있다면 `&`로 이어붙이거나, URL 끝에 leagueApiId를 바로 붙이는 형태일 수 있음). `fetchMatchDetail`의 정확한 URL 패턴은 Task 2에서 확인한 실제 엔드포인트 규칙에 맞춰 조정한다 — esports-api가 매치 단위 상세 조회를 별도 엔드포인트가 아니라 League 조회 응답 안에서만 제공한다면, Task 8에서 이 함수 대신 `fetchMatchesForLeague` 결과를 `matchApiId`로 필터링하는 방식으로 대체한다.

(`WebClient.Builder` 빈은 `spring-boot-starter-webflux`가 자동 구성으로 제공한다 — 별도 `@Bean` 불필요.)

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/LolEsportsApiClient.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/config/LolApiProperties.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/config/LolApiConfig.kt
git commit -m "feat: add WebClient-based esports-api client for tournament/match fetch"
```

---

### Task 4: DTO → Entity 매퍼

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/mapper/TournamentMapper.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/mapper/MatchMapper.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/mapper/TournamentMapperTest.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/mapper/MatchMapperTest.kt`

**Interfaces:**
- Consumes: `TournamentApiResponse`, `MatchApiResponse`(Task 2), `Tournament`, `Match`, `League`(기존 엔티티)
- Produces: `TournamentApiResponse.toEntity(league: League): Tournament`, `MatchApiResponse.toEntity(tournament: Tournament): Match` 확장 함수 — Task 8(스케줄러)이 사용한다.

- [ ] **Step 1: Tournament 매퍼 실패 테스트 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueCycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TournamentMapperTest {

    @Test
    fun `toEntity maps api response fields onto Tournament entity`() {
        val league = League(
            leagueName = "LCK",
            isActive = true,
            leagueApiId = "league-api-1",
            leagueCycle = LeagueCycle.MULTI_SPLIT,
        )
        val response = TournamentApiResponse(
            apiId = "tournament-api-1",
            name = "LCK 2025 Summer",
            startDate = LocalDate.of(2025, 6, 1),
            endDate = LocalDate.of(2025, 8, 30),
        )

        val entity = response.toEntity(league)

        assertEquals("tournament-api-1", entity.tournamentApiId)
        assertEquals("LCK 2025 Summer", entity.tournamentName)
        assertEquals(LocalDate.of(2025, 6, 1), entity.startDate)
        assertEquals(LocalDate.of(2025, 8, 30), entity.endDate)
        assertEquals(league, entity.league)
    }
}
```

(위 테스트는 `TournamentApiResponse`가 Task 2 계획대로 `apiId`/`name`/`startDate`/`endDate` 필드를 갖는다고 가정한다. Task 2에서 실제 필드명이 달라졌다면 이 테스트도 그에 맞춰 조정한다.)

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*.TournamentMapperTest" 2>&1 | tail -40`
Expected: FAIL — `toEntity` 함수가 아직 없어 컴파일 에러 (unresolved reference)

- [ ] **Step 3: `TournamentMapper` 구현**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.TournamentApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament

fun TournamentApiResponse.toEntity(league: League): Tournament {
    return Tournament(
        tournamentName = this.name,
        startDate = this.startDate,
        endDate = this.endDate,
        tournamentApiId = this.apiId,
        league = league,
    )
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*.TournamentMapperTest" 2>&1 | tail -40`
Expected: PASS

- [ ] **Step 5: Match 매퍼 실패 테스트 작성**

Task 2에서 확인한 실제 `MatchApiResponse.strategyType`/`state` 값이 `BO3`/`BO5`, `SCHEDULED`/`ONGOING`/`FINISHED`와 정확히 일치하지 않을 수 있다 — 아래 테스트는 API 값이 대문자로 그대로 일치한다고 가정한 기본 케이스이며, Step 2에서 실제 캡처한 값 표기(대소문자, 다른 명칭)에 맞게 조정한다.

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueCycle
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class MatchMapperTest {

    @Test
    fun `toEntity maps api response fields onto Match entity using given tournament`() {
        val league = League(
            leagueName = "LCK",
            isActive = true,
            leagueApiId = "league-api-1",
            leagueCycle = LeagueCycle.MULTI_SPLIT,
        )
        val tournament = Tournament(
            tournamentName = "LCK 2025 Summer",
            startDate = LocalDate.of(2025, 6, 1),
            endDate = LocalDate.of(2025, 8, 30),
            tournamentApiId = "tournament-api-1",
            league = league,
        )
        val response = MatchApiResponse(
            apiId = "match-api-1",
            startTime = ZonedDateTime.parse("2025-06-01T10:00:00Z"),
            label = "Week 1 Day 1",
            strategyType = "BO3",
            state = "SCHEDULED",
        )

        val entity = response.toEntity(tournament)

        assertEquals("match-api-1", entity.matchApiId)
        assertEquals(ZonedDateTime.parse("2025-06-01T10:00:00Z"), entity.startTime)
        assertEquals("Week 1 Day 1", entity.matchLabel)
        assertEquals(MatchType.BO3, entity.matchType)
        assertEquals(MatchState.SCHEDULED, entity.matchState)
        assertEquals(tournament, entity.tournament)
    }
}
```

- [ ] **Step 6: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*.MatchMapperTest" 2>&1 | tail -40`
Expected: FAIL — `toEntity` 미정의

- [ ] **Step 7: `MatchMapper` 구현**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper

import io.olkkani.lolviewback.infastructure.outbound.client.sync.dto.MatchApiResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament

fun MatchApiResponse.toEntity(tournament: Tournament): Match {
    return Match(
        startTime = this.startTime,
        matchType = MatchType.valueOf(this.strategyType),
        matchState = MatchState.valueOf(this.state),
        matchLabel = this.label,
        matchApiId = this.apiId,
        tournament = tournament,
    )
}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests "*.MatchMapperTest" 2>&1 | tail -40`
Expected: PASS

- [ ] **Step 9: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/mapper/ src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/client/sync/mapper/
git commit -m "feat: add DTO to entity mappers for tournament and match, with tests"
```

---

### Task 5: Repository 계층 신설

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/LeagueRepository.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/TournamentRepository.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepository.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/LeagueRecurrenceWindowRepository.kt`

**Interfaces:**
- Consumes: `League`, `Tournament`, `Match`, `LeagueRecurrenceWindow` 엔티티(기존)
- Produces:
  - `LeagueRepository.findByLeagueApiId(leagueApiId: String): League?`
  - `LeagueRepository.findByIsActiveTrue(): List<League>`
  - `TournamentRepository.findByTournamentApiId(tournamentApiId: String): Tournament?`
  - `MatchRepository.findByMatchApiId(matchApiId: String): Match?`
  - `MatchRepository.findByMatchState(matchState: MatchState): List<Match>`
  - `LeagueRecurrenceWindowRepository.findByLeague(league: League): List<LeagueRecurrenceWindow>`
  - (모든 repository는 `JpaRepository`를 상속하므로 `findAll()`, `save()`도 기본 제공 — Task 8이 `TournamentRepository.findAll()`을 League/날짜 필터링에, `LeagueRepository.save()`/`TournamentRepository.save()`/`MatchRepository.save()`를 upsert에 사용한다.)

  위 커스텀 조회 메서드들은 Task 6(`TournamentDueChecker`)과 Task 8(스케줄러)이 사용한다.

- [ ] **Step 1: `LeagueRepository` 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRepository : JpaRepository<League, Long> {
    fun findByLeagueApiId(leagueApiId: String): League?
    fun findByIsActiveTrue(): List<League>
}
```

- [ ] **Step 2: `TournamentRepository` 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentRepository : JpaRepository<Tournament, Long> {
    fun findByTournamentApiId(tournamentApiId: String): Tournament?
}
```

("오늘이 어떤 Tournament의 `start_date - 7일` ~ `end_date` 범위에 드는가"를 찾는 로직은 Task 8에서 `findAll()` 결과를 메모리에서 필터링하는 방식으로 구현한다 — 이번 스코프의 데이터량에서는 별도 파생 쿼리 메서드가 굳이 필요하지 않다.)

- [ ] **Step 3: `MatchRepository` 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRepository : JpaRepository<Match, Long> {
    fun findByMatchApiId(matchApiId: String): Match?
    fun findByMatchState(matchState: MatchState): List<Match>
}
```

- [ ] **Step 4: `LeagueRecurrenceWindowRepository` 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRecurrenceWindowRepository : JpaRepository<LeagueRecurrenceWindow, Long> {
    fun findByLeague(league: League): List<LeagueRecurrenceWindow>
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/LeagueRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/TournamentRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/LeagueRecurrenceWindowRepository.kt
git commit -m "feat: add Spring Data JPA repositories for League, Tournament, Match, LeagueRecurrenceWindow"
```

---

### Task 6: `TournamentDueChecker` — recurrence window 판단 로직 (핵심 리스크)

이 태스크가 이번 설계의 핵심 리스크다. 순수 함수/클래스로 분리해 실제 스케줄러 없이 단위 테스트로 검증한다.

**판단 규칙 (spec에서 확정된 알고리즘):**
- 한 League에 속한 모든 `LeagueRecurrenceWindow`를 각각 독립적으로 평가한다.
- 특정 window의 `expectedStart = startDate.plusYears(intervalYear.toLong())`.
- "오늘의 연월(YearMonth)"이 `expectedStart`의 연월 이상이면 그 window는 **due**(지금 조회 시도 대상)다.
- window가 due이고, 해당 League에 `sequenceOrder`/`label`로 식별되는 "이번 주기"에 해당하는 Tournament가 아직 없으면 → 조회 트리거 대상.
  - "이번 주기에 해당하는 Tournament가 이미 있는가" 판단은: 해당 League의 Tournament 중 `startDate >= window.startDate`인 것이 하나라도 있으면 "이미 이번 주기는 처리됨"으로 간주한다(window의 `startDate`는 Task 1에서 리네임된 "마지막 확인 시점" 필드이므로, 그 시점 이후 시작한 Tournament가 있다는 것은 이미 새 Tournament가 잡혔다는 뜻).
- League 비활성화 조건: 해당 League의 **모든** window가 `expectedStart + 2년`을 오늘이 지났는데도 여전히 due 상태(=새 Tournament를 못 찾음)이면 `League.isActive = false`.

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/domain/sync/TournamentDueChecker.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/domain/sync/TournamentDueCheckerTest.kt`

**Interfaces:**
- Consumes: `League`, `LeagueRecurrenceWindow`, `Tournament` 엔티티, `List<LeagueRecurrenceWindow>`, `List<Tournament>` (호출자가 repository로 미리 조회해서 순수 함수에 전달 — I/O 없는 판단 로직으로 유지하기 위함)
- Produces:
  - `TournamentDueChecker.findDueWindows(windows: List<LeagueRecurrenceWindow>, existingTournaments: List<Tournament>, today: LocalDate): List<LeagueRecurrenceWindow>` — 지금 조회를 시도해야 하는 window 목록
  - `TournamentDueChecker.shouldDeactivate(windows: List<LeagueRecurrenceWindow>, existingTournaments: List<Tournament>, today: LocalDate): Boolean` — League를 비활성화해야 하는지 여부
  - `TournamentDueChecker.findMatchingWindow(windows: List<LeagueRecurrenceWindow>, tournamentStartDate: LocalDate): LeagueRecurrenceWindow?` — 새로 저장된 Tournament가 어느 window에 속하는지 찾는다("자신의 `startDate` 이전이면서 가장 늦은 `startDate`를 가진 window"라는 동일한 매칭 규칙을 `hasNewTournamentForWindow`와 공유하기 위해 별도 공개 함수로 노출)

  Task 8(Tournament 스케줄러)이 세 함수를 사용한다 — 특히 `findMatchingWindow`는 Tournament 저장 직후 그 window의 `startDate`를 새 Tournament의 `endDate`로 갱신(write-back)하는 데 사용한다.

- [ ] **Step 1: 단일 window, due 아닌 케이스 — 실패 테스트 작성**

```kotlin
package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueCycle
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TournamentDueCheckerTest {

    private fun league() = League(
        leagueName = "MSI",
        isActive = true,
        leagueApiId = "msi-api-id",
        leagueCycle = LeagueCycle.ANNUAL,
    )

    @Test
    fun `window is not due when today is before expected start year-month`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 5, 1),
            league = league,
        )
        // expectedStart = 2024-05-01 + 1y = 2025-05-01. today가 그 전이면 due 아님.
        val today = LocalDate.of(2025, 3, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = emptyList(),
            today = today,
        )

        assertTrue(dueWindows.isEmpty())
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -40`
Expected: FAIL — `TournamentDueChecker` 객체 미정의 (컴파일 에러)

- [ ] **Step 3: `TournamentDueChecker` 최소 구현 (첫 케이스만 통과)**

```kotlin
package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import java.time.LocalDate
import java.time.YearMonth

object TournamentDueChecker {

    fun findDueWindows(
        windows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): List<LeagueRecurrenceWindow> {
        return windows.filter { window -> isWindowDue(window, existingTournaments, today) }
    }

    fun shouldDeactivate(
        windows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        if (windows.isEmpty()) return false
        return windows.all { window -> isWindowOverdue(window, existingTournaments, today) }
    }

    private fun expectedStart(window: LeagueRecurrenceWindow): LocalDate {
        return window.startDate.plusYears(window.intervalYear.toLong())
    }

    private fun hasNewTournamentForWindow(window: LeagueRecurrenceWindow, existingTournaments: List<Tournament>): Boolean {
        return existingTournaments.any { tournament -> !tournament.startDate.isBefore(window.startDate) }
    }

    private fun isWindowDue(window: LeagueRecurrenceWindow, existingTournaments: List<Tournament>, today: LocalDate): Boolean {
        val expected = expectedStart(window)
        val isPastExpectedYearMonth = !YearMonth.from(today).isBefore(YearMonth.from(expected))
        return isPastExpectedYearMonth && !hasNewTournamentForWindow(window, existingTournaments)
    }

    private fun isWindowOverdue(window: LeagueRecurrenceWindow, existingTournaments: List<Tournament>, today: LocalDate): Boolean {
        val deactivationThreshold = expectedStart(window).plusYears(2)
        val isPastGracePeriod = !today.isBefore(deactivationThreshold)
        return isPastGracePeriod && !hasNewTournamentForWindow(window, existingTournaments)
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -40`
Expected: PASS

- [ ] **Step 5: due인 케이스 — 실패 테스트 추가**

`TournamentDueCheckerTest`에 아래 테스트 추가:

```kotlin
    @Test
    fun `window is due when today reaches expected start year-month and no tournament exists yet`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 5, 1),
            league = league,
        )
        // expectedStart = 2025-05-01. today가 그 연월 이상.
        val today = LocalDate.of(2025, 5, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = emptyList(),
            today = today,
        )

        assertEquals(1, dueWindows.size)
        assertEquals(window, dueWindows[0])
    }
```

- [ ] **Step 6: 테스트 실행해서 통과 확인 (이미 구현되어 있으므로 바로 통과해야 함)**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -40`
Expected: PASS (2개 테스트 모두)

- [ ] **Step 7: 이미 새 Tournament가 존재하면 due 아님 — 실패 테스트 추가**

```kotlin
    @Test
    fun `window is not due when a tournament already exists at or after window start date`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 5, 1),
            league = league,
        )
        val existingTournament = Tournament(
            tournamentName = "MSI 2025",
            startDate = LocalDate.of(2025, 5, 1),
            endDate = LocalDate.of(2025, 5, 20),
            tournamentApiId = "msi-2025",
            league = league,
        )
        val today = LocalDate.of(2025, 6, 1)

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(window),
            existingTournaments = listOf(existingTournament),
            today = today,
        )

        assertTrue(dueWindows.isEmpty())
    }
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -40`
Expected: PASS (3개 테스트 모두)

- [ ] **Step 9: LCK류 다회차 리그 — Spring window는 처리됐지만 Summer window는 여전히 due — 실패 테스트 추가**

```kotlin
    @Test
    fun `each window in a multi-split league is evaluated independently`() {
        val league = league()
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )
        // Spring 2025는 이미 잡힘 (startDate가 springWindow.startDate=2024-01-01 이후)
        val springTournament = Tournament(
            tournamentName = "LCK 2025 Spring",
            startDate = LocalDate.of(2025, 1, 10),
            endDate = LocalDate.of(2025, 3, 30),
            tournamentApiId = "lck-2025-spring",
            league = league,
        )
        val today = LocalDate.of(2025, 6, 15) // Summer window의 expectedStart(2025-06-01) 이후

        val dueWindows = TournamentDueChecker.findDueWindows(
            windows = listOf(springWindow, summerWindow),
            existingTournaments = listOf(springTournament),
            today = today,
        )

        assertEquals(1, dueWindows.size)
        assertEquals(summerWindow, dueWindows[0])
    }
```

**주의**: 이 테스트는 `hasNewTournamentForWindow`가 `window.startDate` 이후 시작한 Tournament를 찾는 방식이라, `springTournament.startDate(2025-01-10)`가 `summerWindow.startDate(2024-06-01)`보다도 늦어서 summerWindow 판단에도 "이미 있음"으로 잘못 걸릴 위험이 있다. 실행 후 실패하면 Step 10에서 이 갭을 수정한다.

- [ ] **Step 10: 테스트 실행 — 실패 시 알고리즘 보정**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -40`

만약 FAIL이면 (Step 9 주의사항대로 summerWindow도 "이미 처리됨"으로 잘못 판정), `hasNewTournamentForWindow`가 "Tournament가 그 window에 속한다"를 판단할 때 단순히 `startDate` 이후인지가 아니라, 여러 window 중 **가장 가까운 window**(Tournament의 `startDate`보다 이전이면서 가장 늦은 `window.startDate`를 가진 것)에만 매칭되도록 `TournamentDueChecker.kt`의 `hasNewTournamentForWindow`를 아래로 교체한다:

```kotlin
    private fun hasNewTournamentForWindow(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
    ): Boolean {
        return existingTournaments.any { tournament ->
            val matchingWindow = allWindows
                .filter { !tournament.startDate.isBefore(it.startDate) }
                .maxByOrNull { it.startDate }
            matchingWindow == window
        }
    }
```

이 변경에 맞춰 `isWindowDue`/`isWindowOverdue`/`findDueWindows`/`shouldDeactivate` 시그니처에 `allWindows: List<LeagueRecurrenceWindow>` 파라미터를 추가로 전달하도록 수정한다 (각 window가 "자신이 속한 Tournament들"을 정확히 가려낼 수 있도록, 판단 시점에 전체 window 목록이 필요하기 때문). 최종 `TournamentDueChecker.kt` 전체:

```kotlin
package io.olkkani.lolviewback.domain.sync

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.LeagueRecurrenceWindow
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import java.time.LocalDate
import java.time.YearMonth

object TournamentDueChecker {

    fun findDueWindows(
        windows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): List<LeagueRecurrenceWindow> {
        return windows.filter { window -> isWindowDue(window, windows, existingTournaments, today) }
    }

    fun shouldDeactivate(
        windows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        if (windows.isEmpty()) return false
        return windows.all { window -> isWindowOverdue(window, windows, existingTournaments, today) }
    }

    fun findMatchingWindow(
        windows: List<LeagueRecurrenceWindow>,
        tournamentStartDate: LocalDate,
    ): LeagueRecurrenceWindow? {
        return windows
            .filter { !tournamentStartDate.isBefore(it.startDate) }
            .maxByOrNull { it.startDate }
    }

    private fun expectedStart(window: LeagueRecurrenceWindow): LocalDate {
        return window.startDate.plusYears(window.intervalYear.toLong())
    }

    private fun hasNewTournamentForWindow(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
    ): Boolean {
        return existingTournaments.any { tournament -> findMatchingWindow(allWindows, tournament.startDate) == window }
    }

    private fun isWindowDue(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        val expected = expectedStart(window)
        val isPastExpectedYearMonth = !YearMonth.from(today).isBefore(YearMonth.from(expected))
        return isPastExpectedYearMonth && !hasNewTournamentForWindow(window, allWindows, existingTournaments)
    }

    private fun isWindowOverdue(
        window: LeagueRecurrenceWindow,
        allWindows: List<LeagueRecurrenceWindow>,
        existingTournaments: List<Tournament>,
        today: LocalDate,
    ): Boolean {
        val deactivationThreshold = expectedStart(window).plusYears(2)
        val isPastGracePeriod = !today.isBefore(deactivationThreshold)
        return isPastGracePeriod && !hasNewTournamentForWindow(window, allWindows, existingTournaments)
    }
}
```

- [ ] **Step 11: 전체 테스트 재실행해서 모두 통과 확인**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -60`
Expected: PASS (4개 테스트 모두)

- [ ] **Step 12: League 비활성화 — 모든 window가 유예기간 초과 시 true — 실패 테스트 추가**

```kotlin
    @Test
    fun `shouldDeactivate is true when every window exceeds its two-year grace period without a new tournament`() {
        val league = league()
        val window = LeagueRecurrenceWindow(
            label = "MSI",
            sequenceOrder = 1,
            intervalYear = 4,
            startDate = LocalDate.of(2022, 1, 1),
            league = league,
        )
        // expectedStart = 2026-01-01, +2y grace = 2028-01-01
        val today = LocalDate.of(2028, 1, 1)

        val result = TournamentDueChecker.shouldDeactivate(
            windows = listOf(window),
            existingTournaments = emptyList(),
            today = today,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldDeactivate is false when at least one window still has a live tournament cadence`() {
        val league = league()
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )
        // Spring은 정상적으로 계속 열림 (마지막 확인일이 최근으로 계속 갱신됨)
        val recentSpringTournament = Tournament(
            tournamentName = "LCK 2028 Spring",
            startDate = LocalDate.of(2028, 1, 10),
            endDate = LocalDate.of(2028, 3, 30),
            tournamentApiId = "lck-2028-spring",
            league = league,
        )
        // Summer는 2년 넘게 안 열림
        val today = LocalDate.of(2028, 6, 15)

        val result = TournamentDueChecker.shouldDeactivate(
            windows = listOf(springWindow, summerWindow),
            existingTournaments = listOf(recentSpringTournament),
            today = today,
        )

        assertFalse(result)
    }
```

- [ ] **Step 13: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -80`
Expected: PASS (6개 테스트 모두). 만약 두 번째 케이스(`shouldDeactivate is false ...`)가 실패하면, `springWindow`의 `hasNewTournamentForWindow` 매칭 로직에서 `recentSpringTournament.startDate(2028-01-10)`가 `summerWindow.startDate(2024-06-01)`보다도 늦으므로 `maxByOrNull`이 `summerWindow`를 골라 `springWindow`용으로 카운트되지 않는 문제일 수 있다 — 이 경우 `springWindow`의 `isWindowOverdue`가 여전히 true로 남아 `all { }`이 여전히 true가 될 수 있으니, 테스트 실패 시 실제 출력 로그를 보고 `hasNewTournamentForWindow`의 매칭 기준을 재검토한다 (예: `recentSpringTournament`의 시작월(1월)과 `springWindow`의 시작월(1월)이 같은 것을 근거로 `label`/`sequenceOrder` 순환 매칭을 쓰는 방식으로 보정 — 이 프로젝트는 아직 실제 다회차 시나리오 데이터가 없으므로, 이 보정은 테스트가 요구하는 만큼만 진행하고 더 정교한 매칭은 Open Question으로 남긴다).

- [ ] **Step 14: `findMatchingWindow` — 다회차 리그에서 올바른 window를 찾는지 실패 테스트 추가**

Task 8이 Tournament 저장 직후 이 함수로 write-back 대상 window를 찾는다. `hasNewTournamentForWindow`가 이미 내부적으로 같은 로직을 쓰고 있으므로(Step 10에서 `findMatchingWindow`를 호출하도록 리팩터링됨), 아래 테스트는 공개 API로서의 동작을 직접 검증한다.

```kotlin
    @Test
    fun `findMatchingWindow picks the closest preceding window for a given tournament start date`() {
        val league = league()
        val springWindow = LeagueRecurrenceWindow(
            label = "Spring",
            sequenceOrder = 1,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 1, 1),
            league = league,
        )
        val summerWindow = LeagueRecurrenceWindow(
            label = "Summer",
            sequenceOrder = 2,
            intervalYear = 1,
            startDate = LocalDate.of(2024, 6, 1),
            league = league,
        )

        val matchedForSpringTournament = TournamentDueChecker.findMatchingWindow(
            windows = listOf(springWindow, summerWindow),
            tournamentStartDate = LocalDate.of(2025, 1, 10),
        )
        val matchedForSummerTournament = TournamentDueChecker.findMatchingWindow(
            windows = listOf(springWindow, summerWindow),
            tournamentStartDate = LocalDate.of(2025, 6, 5),
        )

        assertEquals(springWindow, matchedForSpringTournament)
        assertEquals(summerWindow, matchedForSummerTournament)
    }
```

- [ ] **Step 15: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "*.TournamentDueCheckerTest" 2>&1 | tail -100`
Expected: PASS (7개 테스트 모두 — Step 10에서 이미 `findMatchingWindow`가 구현되어 있으므로 이 테스트는 새 구현 없이 바로 통과해야 한다).

- [ ] **Step 16: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/domain/sync/TournamentDueChecker.kt src/test/kotlin/io/olkkani/lolviewback/domain/sync/TournamentDueCheckerTest.kt
git commit -m "feat: add TournamentDueChecker for recurrence-window-based due/deactivation/matching logic, with tests"
```

---

### Task 7: 스케줄링 활성화

**Files:**
- Modify: `src/main/kotlin/io/olkkani/lolviewback/LolViewBackApplication.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `@EnableScheduling` 활성화 — Task 8의 `@Scheduled` 메서드가 실제로 동작하기 위한 전제 조건.

- [ ] **Step 1: 현재 파일 확인**

Run: `cat src/main/kotlin/io/olkkani/lolviewback/LolViewBackApplication.kt`

- [ ] **Step 2: `@EnableScheduling` 추가**

`LolViewBackApplication.kt`의 `@SpringBootApplication` 어노테이션 위/옆에 `@EnableScheduling`을 추가:

```kotlin
package io.olkkani.lolviewback

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class LolViewBackApplication

fun main(args: Array<String>) {
    runApplication<LolViewBackApplication>(*args)
}
```

(기존 파일 내용과 다르면 기존 구조를 유지하면서 `@EnableScheduling` import와 어노테이션만 추가한다.)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/LolViewBackApplication.kt
git commit -m "feat: enable Spring scheduling for sync jobs"
```

---

### Task 8: 스케줄러 3종 구현

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/scheduler/TournamentSyncScheduler.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/scheduler/MatchDailySyncScheduler.kt`
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/scheduler/MatchLiveSyncScheduler.kt`

**Interfaces:**
- Consumes: `LolEsportsApiClient`(Task 3), `TournamentApiResponse.toEntity()`/`MatchApiResponse.toEntity()`(Task 4), `LeagueRepository`/`TournamentRepository`/`MatchRepository`/`LeagueRecurrenceWindowRepository`(Task 5), `TournamentDueChecker`(Task 6)
- Produces: 없음(최종 소비 계층)

- [ ] **Step 1: `TournamentSyncScheduler` 작성**

매일 자정에 1회 실행(League 스캔은 자주 돌 필요 없다는 spec 판단 — cron `0 0 0 * * *`). `runBlocking`으로 coroutine 클라이언트를 동기 스케줄러 컨텍스트에서 호출한다.

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.scheduler

import io.olkkani.lolviewback.domain.sync.TournamentDueChecker
import io.olkkani.lolviewback.infastructure.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.infastructure.outbound.repository.LeagueRecurrenceWindowRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.LeagueRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.TournamentRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class TournamentSyncScheduler(
    private val leagueRepository: LeagueRepository,
    private val windowRepository: LeagueRecurrenceWindowRepository,
    private val tournamentRepository: TournamentRepository,
    private val apiClient: LolEsportsApiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0 * * *")
    fun syncDueTournaments() {
        val today = LocalDate.now()
        val activeLeagues = leagueRepository.findByIsActiveTrue()

        for (league in activeLeagues) {
            val windows = windowRepository.findByLeague(league)
            val existingTournaments = tournamentRepository.findAll().filter { it.league.id == league.id }

            if (TournamentDueChecker.shouldDeactivate(windows, existingTournaments, today)) {
                league.isActive = false
                leagueRepository.save(league)
                log.info("Deactivated league {} — no new tournament found within grace period", league.leagueApiId)
                continue
            }

            val dueWindows = TournamentDueChecker.findDueWindows(windows, existingTournaments, today)
            if (dueWindows.isEmpty()) continue

            try {
                val fetched = runBlocking { apiClient.fetchTournaments(league.leagueApiId) }
                for (apiResponse in fetched) {
                    if (tournamentRepository.findByTournamentApiId(apiResponse.apiId) != null) continue
                    val savedTournament = tournamentRepository.save(apiResponse.toEntity(league))

                    val matchingWindow = TournamentDueChecker.findMatchingWindow(windows, savedTournament.startDate)
                    if (matchingWindow != null) {
                        matchingWindow.startDate = savedTournament.endDate
                        windowRepository.save(matchingWindow)
                    } else {
                        log.warn(
                            "New tournament {} for league {} did not match any recurrence window — start_date not advanced",
                            savedTournament.tournamentApiId,
                            league.leagueApiId,
                        )
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to fetch tournaments for league {}", league.leagueApiId, e)
            }
        }
    }
}
```

(`TournamentRepository.findAll().filter { ... }`는 이번 스코프에서는 데이터량이 작아 허용 — League별 조회 메서드가 필요해지면 `findByLeague(league: League): List<Tournament>`를 Task 5에 추가하는 후속 작업으로 남긴다. Tournament 저장 직후 `findMatchingWindow`로 해당 window를 찾아 `startDate`를 새 Tournament의 `endDate`로 갱신하는 것이 spec이 확정한 write-back 규칙이다 — 이 갱신이 없으면 같은 window가 계속 due로 남아 다음 회차에도 불필요한 API 재호출이 발생한다(`findByTournamentApiId` 중복 스킵 덕분에 데이터 손상은 없음).)

- [ ] **Step 2: `MatchDailySyncScheduler` 작성**

매일 자정 5분(Tournament 스케줄러와 겹치지 않도록) 실행. 트리거 대상 Tournament를 먼저 찾고, 그 Tournament를 그대로 `tournament_id`로 사용한다(spec 확정 사항).

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.scheduler

import io.olkkani.lolviewback.infastructure.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.infastructure.outbound.client.sync.mapper.toEntity
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.TournamentRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class MatchDailySyncScheduler(
    private val tournamentRepository: TournamentRepository,
    private val matchRepository: MatchRepository,
    private val apiClient: LolEsportsApiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 5 0 * * *")
    fun syncUpcomingMatches() {
        val today = LocalDate.now()
        val allTournaments = tournamentRepository.findAll()
        val triggeringTournaments = allTournaments.filter { tournament ->
            val windowStart = tournament.startDate.minusDays(7)
            !today.isBefore(windowStart) && !today.isAfter(tournament.endDate)
        }

        val byLeague = triggeringTournaments.groupBy { it.league.id }
        for ((_, tournamentsForLeague) in byLeague) {
            if (tournamentsForLeague.size > 1) {
                log.warn(
                    "League {} has {} concurrently-triggering tournaments — premise violated, skipping",
                    tournamentsForLeague.first().league.leagueApiId,
                    tournamentsForLeague.size,
                )
                continue
            }

            val tournament = tournamentsForLeague.first()
            try {
                val fetched = runBlocking { apiClient.fetchMatchesForLeague(tournament.league.leagueApiId) }
                for (apiResponse in fetched) {
                    if (matchRepository.findByMatchApiId(apiResponse.apiId) != null) continue
                    matchRepository.save(apiResponse.toEntity(tournament))
                }
            } catch (e: Exception) {
                log.error("Failed to fetch matches for tournament {}", tournament.tournamentApiId, e)
            }
        }
    }
}
```

- [ ] **Step 3: `MatchLiveSyncScheduler` 작성**

5분 주기, `matchState = ONGOING`인 Match만 `matchApiId` 기준으로 개별 조회해서(Task 3의 `fetchMatchDetail`) 상태를 갱신한다. `tournament_id`는 기존 값을 그대로 유지한다(spec 확정 사항).

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.scheduler

import io.olkkani.lolviewback.infastructure.outbound.client.sync.LolEsportsApiClient
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MatchLiveSyncScheduler(
    private val matchRepository: MatchRepository,
    private val apiClient: LolEsportsApiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 5 * 60 * 1000)
    fun syncOngoingMatches() {
        val ongoingMatches = matchRepository.findByMatchState(MatchState.ONGOING)
        for (match in ongoingMatches) {
            try {
                val detail = runBlocking { apiClient.fetchMatchDetail(match.matchApiId) }
                match.matchState = MatchState.valueOf(detail.state)
                matchRepository.save(match)
            } catch (e: Exception) {
                log.error("Failed to refresh live match {}", match.matchApiId, e)
            }
        }
    }
}
```

**Task 2 실행 시 확인 필요**: 이 Step은 esports-api가 matchId 단위 상세 조회 엔드포인트(예: `getEventDetails`)를 제공한다고 가정한다. Task 2 스파이크에서 그런 엔드포인트가 없고 League 단위 목록 조회(`fetchMatchesForLeague`)만 가능하다고 확인되면, `fetchMatchDetail`을 다음으로 교체한다 — League 전체를 다시 가져와 `matchApiId`로 필터링:

```kotlin
    suspend fun fetchMatchDetail(matchApiId: String, leagueApiId: String): MatchApiResponse? {
        return fetchMatchesForLeague(leagueApiId).find { it.apiId == matchApiId }
    }
```

이 대체 시그니처를 쓰면 `MatchLiveSyncScheduler`가 `match.tournament.league.leagueApiId`를 두 번째 인자로 넘기고, `null` 응답(못 찾음)일 때는 로그만 남기고 스킵하도록 `syncOngoingMatches()`의 호출부를 수정한다.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileKotlin --quiet`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/scheduler/
git commit -m "feat: add tournament and match sync schedulers"
```

---

### Task 9: 수동 검증

**Files:** 없음 (코드 변경 없음, 검증 절차만)

**Interfaces:**
- Consumes: Task 1~8의 전체 결과물
- Produces: 없음

- [ ] **Step 1: 애플리케이션 기동**

Run: `./gradlew bootRun --args='--spring.profiles.active=dev'`
Expected: 정상 기동 로그, 에러 없음.

- [ ] **Step 2: 테스트용 League/window를 DB에 수동 삽입**

새 터미널에서 로컬 postgres에 접속(`docker compose ps`로 컨테이너 포트 확인 후 `psql` 또는 임의 DB 클라이언트 사용)해서 실제 leagueApiId를 가진 League 1건과 그에 딸린 window 1건을 삽입한다:

```sql
INSERT INTO leagues (id, league_name, is_active, league_api_id, league_cycle)
VALUES (1, 'LCK', true, '98767991302996019', 'MULTI_SPLIT');

INSERT INTO league_recurrence_windows (id, label, sequence_order, league_id, interval_years, start_date)
VALUES (1, 'Spring', 1, 1, 1, '2020-01-01');
```

(`start_date`를 과거로 넣어서 `expectedStart`가 이미 지난 상태를 만들어 due 조건을 강제로 트리거한다. leagueApiId는 Task 2에서 사용한 LCK ID `98767991302996019`.)

- [ ] **Step 3: Tournament 스케줄러 수동 트리거로 즉시 확인 (선택)**

cron이 자정까지 기다리지 않도록, 테스트 목적으로만 `TournamentSyncScheduler.syncDueTournaments()`에 임시 `@EventListener(ApplicationReadyEvent::class)`를 추가하거나, 액추에이터/디버거로 직접 호출한다. 확인 후 이 임시 트리거는 되돌린다(커밋하지 않음).

- [ ] **Step 4: DB에서 결과 확인**

```sql
SELECT * FROM tournaments WHERE league_id = 1;
SELECT * FROM league_recurrence_windows WHERE id = 1;
```

Expected: `tournaments`에 새 로우가 생겼고, `league_recurrence_windows.start_date`가 그 Tournament의 `end_date`로 갱신되어 있어야 한다(Task 8의 write-back 로직 검증).

- [ ] **Step 5: 재실행 후 중복 없는지 확인**

같은 스케줄러를 다시 트리거하고 `SELECT COUNT(*) FROM tournaments WHERE tournament_api_id = '<방금 조회된 apiId>'`가 여전히 1인지 확인.

Expected: 1 (중복 없음 — `findByTournamentApiId` 기반 스킵 로직 검증).

---

## 알려진 갭 (이 계획에서 의도적으로 다루지 않은 것)

1. **`MatchLiveSyncScheduler`의 개별 조회 엔드포인트**: Task 2 스파이크에서 esports-api가 matchId 단위 상세 조회를 제공하는지 확인되지 않은 상태로 계획을 작성했다 — Task 8 Step 3의 "Task 2 실행 시 확인 필요" 노트에 따라, 실제 API 구조에 맞춰 `fetchMatchDetail`을 두 방식(전용 엔드포인트 / League 목록에서 필터링) 중 하나로 확정한다.
2. League/window 수동 등록을 위한 스크립트나 관리 화면은 스코프 밖 — SQL 직접 실행으로 충분하다는 spec 결정을 따른다.
3. 알림/모니터링, rate limit 대응, 스케줄러 중복 실행 방지는 spec에서 명시적으로 스코프 아웃했다.
4. `findMatchingWindow`의 매칭 규칙(가장 가까운 선행 window)은 이 프로젝트에 실제 다회차 리그 데이터가 아직 없는 상태에서 설계된 것이라, LCK 같은 실제 데이터로 Task 9 수동 검증을 거치기 전까지는 잠정적이다 — Task 6 Step 13 주의사항 참고.
