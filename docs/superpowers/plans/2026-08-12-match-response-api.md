# Match Response API — 어제/오늘/예정 경기 조회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /matches?range={yesterday|today|upcoming}` API를 구현해 어제/오늘/예정(내일~7일 이내) 세 구간의 경기 목록을 `MatchResponse` 리스트로 반환한다.

**Architecture:** `MatchRepository`에 KST 기준 날짜 범위로 `Match`를 조회하는 쿼리 메서드를 추가하고, `MatchParticipantRepository`를 신설해 매치별 참가자를 batch 조회한다(N+1 방지). `MatchRestController`는 `range` 파라미터를 `MatchRange` enum으로 파싱하고, `MatchQueryService`(신설)에 위임한다. `MatchQueryService`가 날짜 범위 계산, repository 조회, `MatchResponse` 변환을 조합한다.

**Tech Stack:** Kotlin, Spring Boot(`spring-boot-starter-data-jpa`, `spring-boot-starter-web`), JUnit 5, mockk, Testcontainers PostgreSQL(`@DataJpaTest`). 신규 의존성 추가 없음.

## Global Constraints

- 날짜 경계는 KST(Asia/Seoul) 자정 기준이다. `yesterday`=`[오늘 00:00 KST - 1d, 오늘 00:00 KST)`, `today`=`[오늘 00:00 KST, 오늘 00:00 KST + 1d)`, `upcoming`=`[오늘 00:00 KST + 1d, 오늘 00:00 KST + 8d)`.
- `range` 파라미터는 필수이며 `yesterday`/`today`/`upcoming` 중 하나가 아니면 400을 반환한다.
- 응답은 `startTime` 오름차순으로 정렬된 `MatchResponse` 리스트다.
- 페이지네이션, 폴링/웹소켓 등 실시간 갱신 메커니즘, 임의 날짜 범위 조회는 이번 스코프에 포함하지 않는다 — 탭 재조회 자체가 최신 상태(신규 매치 포함)를 반영하는 방식으로 충분하다.
- 자동화된 통합 테스트는 `@DataJpaTest` + Testcontainers로 repository 계층까지 커버한다. 컨트롤러/서비스는 mockk 기반 단위 테스트로 검증한다.

---

### Task 1: `MatchRange` — range 파라미터와 KST 날짜 범위 계산

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/dto/MatchRange.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/dto/MatchRangeTest.kt`

**Interfaces:**
- Consumes: 없음 (최초 태스크)
- Produces: `enum class MatchRange { YESTERDAY, TODAY, UPCOMING }`, `MatchRange.from(value: String): MatchRange`(잘못된 값이면 `IllegalArgumentException`), `MatchRange.toDateRange(today: LocalDate): Pair<ZonedDateTime, ZonedDateTime>` (start inclusive, end exclusive, KST) — Task 2(Repository)와 Task 4(Service)가 이 시그니처를 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.web.dto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchRangeTest {

    private val kst = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 8, 12)

    @Test
    fun `from parses valid values case-sensitively`() {
        assertEquals(MatchRange.YESTERDAY, MatchRange.from("yesterday"))
        assertEquals(MatchRange.TODAY, MatchRange.from("today"))
        assertEquals(MatchRange.UPCOMING, MatchRange.from("upcoming"))
    }

    @Test
    fun `from throws on invalid value`() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchRange.from("tomorrow")
        }
    }

    @Test
    fun `yesterday range is the day before today in KST`() {
        val (start, end) = MatchRange.YESTERDAY.toDateRange(today)

        assertEquals(ZonedDateTime.of(2026, 8, 11, 0, 0, 0, 0, kst), start)
        assertEquals(ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst), end)
    }

    @Test
    fun `today range is today 00_00 to tomorrow 00_00 in KST`() {
        val (start, end) = MatchRange.TODAY.toDateRange(today)

        assertEquals(ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst), start)
        assertEquals(ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst), end)
    }

    @Test
    fun `upcoming range is tomorrow through 7 days later in KST`() {
        val (start, end) = MatchRange.UPCOMING.toDateRange(today)

        assertEquals(ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst), start)
        assertEquals(ZonedDateTime.of(2026, 8, 20, 0, 0, 0, 0, kst), end)
    }
}
```

- [ ] **Step 2: 테스트 실행 후 실패 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRangeTest"`
Expected: FAIL (컴파일 에러 — `MatchRange`가 존재하지 않음)

- [ ] **Step 3: `MatchRange` 구현**

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.web.dto

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

enum class MatchRange {
    YESTERDAY,
    TODAY,
    UPCOMING,
    ;

    fun toDateRange(today: LocalDate): Pair<ZonedDateTime, ZonedDateTime> {
        val todayStart = today.atStartOfDay(KST)
        return when (this) {
            YESTERDAY -> todayStart.minusDays(1) to todayStart
            TODAY -> todayStart to todayStart.plusDays(1)
            UPCOMING -> todayStart.plusDays(1) to todayStart.plusDays(8)
        }
    }

    companion object {
        fun from(value: String): MatchRange {
            return entries.firstOrNull { it.name == value.uppercase() && it.name.lowercase() == value }
                ?: throw IllegalArgumentException("Unknown range: $value")
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 후 통과 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRangeTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/dto/MatchRange.kt src/test/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/dto/MatchRangeTest.kt
git commit -m "feat: add MatchRange for yesterday/today/upcoming date boundaries"
```

---

### Task 2: `MatchRepository`에 날짜 범위 조회 추가

**Files:**
- Modify: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepository.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepositoryTest.kt`

**Interfaces:**
- Consumes: 없음 (엔티티는 기존 `Match`, `Tournament` 그대로 사용)
- Produces: `MatchRepository.findByStartTimeBetween(start: ZonedDateTime, end: ZonedDateTime): List<Match>` — Task 4(`MatchQueryService`)가 사용한다.

**참고:** 이 프로젝트는 아직 `@DataJpaTest` 인프라가 없다. `libs.bundles.persistence.test.testcontainer`(Testcontainers + PostgreSQL)는 이미 의존성에 있으므로 바로 사용 가능하다.

- [ ] **Step 1: 실패하는 테스트 작성**

`Tournament`와 `League`는 각각 `@ManyToOne` 필수 연관관계이므로 테스트 fixture에 함께 생성해야 한다. 기존 엔티티 생성자를 참고해 최소 fixture를 구성한다.

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Testcontainers
@DataJpaTest
class MatchRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var matchRepository: MatchRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    private val kst = ZoneId.of("Asia/Seoul")

    private fun tournament(): Tournament {
        val league = leagueRepository.save(
            League(
                leagueName = "LCK",
                leagueLogoUrl = "https://example.com/lck.png",
                isActive = true,
                leagueApiId = "lck-api-id",
            ),
        )
        return tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Summer 2026",
                startDate = LocalDate.of(2026, 6, 1),
                endDate = LocalDate.of(2026, 8, 31),
                tournamentApiId = "lck-summer-2026",
                league = league,
            ),
        )
    }

    private fun match(startTime: ZonedDateTime, tournament: Tournament) = Match(
        startTime = startTime,
        matchType = MatchType.BO3,
        matchState = MatchState.SCHEDULED,
        matchLabel = "Week 1",
        matchApiId = "match-${startTime.toInstant().toEpochMilli()}",
        tournament = tournament,
    )

    @Test
    fun `findByStartTimeBetween returns matches within range only`() {
        val t = tournament()
        val inRange = matchRepository.save(match(ZonedDateTime.of(2026, 8, 12, 10, 0, 0, 0, kst), t))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 11, 23, 0, 0, 0, kst), t))
        matchRepository.save(match(ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst), t))

        val start = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val end = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        val result = matchRepository.findByStartTimeBetween(start, end)

        assertEquals(1, result.size)
        assertEquals(inRange.id, result[0].id)
    }
}
```

`TournamentRepository`, `LeagueRepository`가 아직 없다면 이 태스크에서 함께 생성한다 (둘 다 `JpaRepository<Entity, Long>` 최소 인터페이스면 충분).

- [ ] **Step 2: 필요한 repository 존재 여부 확인 후 없으면 생성**

```bash
ls src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/
```

`TournamentRepository.kt`, `LeagueRepository.kt`가 없으면 각각 생성:

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.springframework.data.jpa.repository.JpaRepository

interface TournamentRepository : JpaRepository<Tournament, Long>
```

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import org.springframework.data.jpa.repository.JpaRepository

interface LeagueRepository : JpaRepository<League, Long>
```

- [ ] **Step 3: 테스트 실행 후 실패 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepositoryTest"`
Expected: FAIL (컴파일 에러 — `findByStartTimeBetween` 없음)

- [ ] **Step 4: `MatchRepository`에 쿼리 메서드 추가**

`src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepository.kt`:

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface MatchRepository : JpaRepository<Match, Long> {
    fun findByMatchApiId(matchApiId: String): Match?
    fun findByMatchState(matchState: MatchState): List<Match>
    fun findByStartTimeBetween(start: ZonedDateTime, end: ZonedDateTime): List<Match>
}
```

- [ ] **Step 5: 테스트 실행 후 통과 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepositoryTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/TournamentRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/LeagueRepository.kt src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchRepositoryTest.kt
git commit -m "feat: add findByStartTimeBetween to MatchRepository"
```

(TournamentRepository/LeagueRepository가 이미 존재했다면 해당 파일은 add 목록에서 제외한다.)

---

### Task 3: `MatchParticipantRepository` — 매치별 참가자 batch 조회

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchParticipantRepository.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchParticipantRepositoryTest.kt`

**Interfaces:**
- Consumes: `Match`(Task 2 fixture와 동일하게 생성)
- Produces: `MatchParticipantRepository.findByMatchIdIn(matchIds: List<Long>): List<MatchParticipant>` — Task 4(`MatchQueryService`)가 매치 ID 목록으로 한 번에 참가자를 조회할 때 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`MatchParticipant`는 `Club`, `ClubProfile` 연관관계도 필수다. 각 엔티티의 실제 생성자는 다음과 같다:

```kotlin
// Club(id, isActive, foundedDate? = null, disbandedDate? = null)
// ClubProfile(id, clubName, abbreviation, logoUrl, effectiveFrom, effectiveTo, club? = null)
```

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Club
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.ClubProfile
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.League
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchParticipant
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Testcontainers
@DataJpaTest
class MatchParticipantRepositoryTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var matchRepository: MatchRepository

    @Autowired
    lateinit var matchParticipantRepository: MatchParticipantRepository

    @Autowired
    lateinit var tournamentRepository: TournamentRepository

    @Autowired
    lateinit var leagueRepository: LeagueRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

    @Autowired
    lateinit var clubProfileRepository: ClubProfileRepository

    private val kst = ZoneId.of("Asia/Seoul")

    private fun tournament(): Tournament {
        val league = leagueRepository.save(
            League(leagueName = "LCK", leagueLogoUrl = "https://example.com/lck.png", isActive = true, leagueApiId = "lck-api-id"),
        )
        return tournamentRepository.save(
            Tournament(
                tournamentName = "LCK Summer 2026",
                startDate = LocalDate.of(2026, 6, 1),
                endDate = LocalDate.of(2026, 8, 31),
                tournamentApiId = "lck-summer-2026",
                league = league,
            ),
        )
    }

    @Test
    fun `findByMatchIdIn returns participants for given match ids only`() {
        val t = tournament()
        val match1 = matchRepository.save(
            Match(startTime = ZonedDateTime.now(kst), matchType = MatchType.BO3, matchState = MatchState.SCHEDULED, matchLabel = "W1", matchApiId = "m1", tournament = t),
        )
        val match2 = matchRepository.save(
            Match(startTime = ZonedDateTime.now(kst), matchType = MatchType.BO3, matchState = MatchState.SCHEDULED, matchLabel = "W1", matchApiId = "m2", tournament = t),
        )

        val club = clubRepository.save(Club(isActive = true))
        val profile = clubProfileRepository.save(
            ClubProfile(
                clubName = "T1",
                abbreviation = "T1",
                logoUrl = "https://example.com/t1.png",
                effectiveFrom = LocalDate.of(2020, 1, 1),
                effectiveTo = LocalDate.of(2099, 12, 31),
                club = club,
            ),
        )

        val p1 = matchParticipantRepository.save(
            MatchParticipant(isWin = null, score = 0, match = match1, club = club, clubProfile = profile),
        )
        matchParticipantRepository.save(
            MatchParticipant(isWin = null, score = 0, match = match2, club = club, clubProfile = profile),
        )

        val result = matchParticipantRepository.findByMatchIdIn(listOf(match1.id!!))

        assertEquals(1, result.size)
        assertEquals(p1.id, result[0].id)
        assertTrue(result.all { it.match.id == match1.id })
    }
}
```

- [ ] **Step 2: `Club`/`ClubProfile` repository 존재 여부 확인, 없으면 생성**

```bash
ls src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/ | grep -i club
```

없으면 생성:

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Club
import org.springframework.data.jpa.repository.JpaRepository

interface ClubRepository : JpaRepository<Club, Long>
```

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.ClubProfile
import org.springframework.data.jpa.repository.JpaRepository

interface ClubProfileRepository : JpaRepository<ClubProfile, Long>
```

- [ ] **Step 3: 테스트 실행 후 실패 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.outbound.repository.MatchParticipantRepositoryTest"`
Expected: FAIL (컴파일 에러 — `MatchParticipantRepository` 없음)

- [ ] **Step 4: `MatchParticipantRepository` 구현**

```kotlin
package io.olkkani.lolviewback.infastructure.outbound.repository

import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface MatchParticipantRepository : JpaRepository<MatchParticipant, Long> {
    fun findByMatchIdIn(matchIds: List<Long>): List<MatchParticipant>
}
```

- [ ] **Step 5: 테스트 실행 후 통과 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.outbound.repository.MatchParticipantRepositoryTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchParticipantRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/ClubRepository.kt src/main/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/ClubProfileRepository.kt src/test/kotlin/io/olkkani/lolviewback/infastructure/outbound/repository/MatchParticipantRepositoryTest.kt
git commit -m "feat: add MatchParticipantRepository with batch lookup by match ids"
```

(ClubRepository/ClubProfileRepository가 이미 존재했다면 add 목록에서 제외한다.)

---

### Task 4: `MatchQueryService` — 구간별 조회 + 변환 조합

**Files:**
- Create: `src/main/kotlin/io/olkkani/lolviewback/domain/match/MatchQueryService.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/domain/match/MatchQueryServiceTest.kt`

**Interfaces:**
- Consumes: `MatchRepository.findByStartTimeBetween(ZonedDateTime, ZonedDateTime): List<Match>`(Task 2), `MatchParticipantRepository.findByMatchIdIn(List<Long>): List<MatchParticipant>`(Task 3), `MatchRange.toDateRange(LocalDate): Pair<ZonedDateTime, ZonedDateTime>`(Task 1), `Match.toResponse(List<MatchParticipant>): MatchResponse`(기존 `MatchResponse.kt`)
- Produces: `MatchQueryService.findMatches(range: MatchRange): List<MatchResponse>` — Task 5(컨트롤러)가 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

mockk로 두 repository를 mocking하고, 날짜 계산은 `LocalDate.now(KST)`에 의존하므로 이 테스트는 "오늘 날짜가 무엇이든 today~today+1이 조회 범위로 쓰이는지"를 `range=TODAY`가 아닌 방식으로는 고정하기 어렵다. 대신 서비스에 `today: LocalDate`를 주입 가능한 파라미터로 설계해 테스트 결정성을 확보한다.

```kotlin
package io.olkkani.lolviewback.domain.match

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchParticipantRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Match
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchParticipant
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchType
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.Tournament
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MatchQueryServiceTest {

    private val matchRepository = mockk<MatchRepository>()
    private val matchParticipantRepository = mockk<MatchParticipantRepository>()
    private val service = MatchQueryService(matchRepository, matchParticipantRepository)
    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `findMatches queries repository with today's date range and sorts by startTime ascending`() {
        val today = LocalDate.of(2026, 8, 12)
        val tournament = mockk<Tournament>()
        val laterMatch = Match(
            id = 2L,
            startTime = ZonedDateTime.of(2026, 8, 12, 20, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.SCHEDULED,
            matchLabel = "W1",
            matchApiId = "m2",
            tournament = tournament,
        )
        val earlierMatch = Match(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 14, 0, 0, 0, kst),
            matchType = MatchType.BO3,
            matchState = MatchState.SCHEDULED,
            matchLabel = "W1",
            matchApiId = "m1",
            tournament = tournament,
        )

        val expectedStart = ZonedDateTime.of(2026, 8, 12, 0, 0, 0, 0, kst)
        val expectedEnd = ZonedDateTime.of(2026, 8, 13, 0, 0, 0, 0, kst)

        every { matchRepository.findByStartTimeBetween(expectedStart, expectedEnd) } returns listOf(laterMatch, earlierMatch)
        every { matchParticipantRepository.findByMatchIdIn(listOf(2L, 1L)) } returns emptyList<MatchParticipant>()

        val result = service.findMatches(MatchRange.TODAY, today)

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }
}
```

- [ ] **Step 2: 테스트 실행 후 실패 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.domain.match.MatchQueryServiceTest"`
Expected: FAIL (컴파일 에러 — `MatchQueryService` 없음)

- [ ] **Step 3: `MatchQueryService` 구현**

```kotlin
package io.olkkani.lolviewback.domain.match

import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.infastructure.inbound.web.dto.toResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchParticipantRepository
import io.olkkani.lolviewback.infastructure.outbound.repository.MatchRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

private val KST: ZoneId = ZoneId.of("Asia/Seoul")

@Service
class MatchQueryService(
    private val matchRepository: MatchRepository,
    private val matchParticipantRepository: MatchParticipantRepository,
) {

    fun findMatches(range: MatchRange, today: LocalDate = LocalDate.now(KST)): List<MatchResponse> {
        val (start, end) = range.toDateRange(today)
        val matches = matchRepository.findByStartTimeBetween(start, end)
            .sortedBy { it.startTime }

        val matchIds = matches.mapNotNull { it.id }
        val participantsByMatchId = matchParticipantRepository.findByMatchIdIn(matchIds)
            .groupBy { it.match.id }

        return matches.map { match ->
            match.toResponse(participantsByMatchId[match.id].orEmpty())
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 후 통과 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.domain.match.MatchQueryServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/domain/match/MatchQueryService.kt src/test/kotlin/io/olkkani/lolviewback/domain/match/MatchQueryServiceTest.kt
git commit -m "feat: add MatchQueryService combining range lookup and response mapping"
```

---

### Task 5: `MatchRestController` — `range` 쿼리 파라미터 엔드포인트

**Files:**
- Modify: `src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/MatchRestController.kt`
- Test: `src/test/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/MatchRestControllerTest.kt`

**Interfaces:**
- Consumes: `MatchQueryService.findMatches(MatchRange, LocalDate = today): List<MatchResponse>`(Task 4), `MatchRange.from(String): MatchRange`(Task 1)
- Produces: `GET /matches?range={value}` — 200과 `List<MatchResponse>`, 또는 400(잘못된 range 값)

- [ ] **Step 1: 실패하는 테스트 작성**

`@WebMvcTest`로 컨트롤러 slice만 로드한다. 프로젝트 의존성 카탈로그에 `springmockk`(`@MockkBean`)가 없으므로, `@TestConfiguration` + mockk 수동 빈 등록 방식으로 `MatchQueryService`를 대체한다.

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.web

import io.mockk.every
import io.mockk.mockk
import io.olkkani.lolviewback.domain.match.MatchQueryService
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchClubResponse
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchResponse
import io.olkkani.lolviewback.infastructure.outbound.repository.entity.MatchState
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.ZoneId
import java.time.ZonedDateTime

@WebMvcTest(MatchRestController::class)
@Import(MatchRestControllerTest.MockConfig::class)
class MatchRestControllerTest {

    @TestConfiguration
    class MockConfig {
        @Bean
        fun matchQueryService(): MatchQueryService = mockk()
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var matchQueryService: MatchQueryService

    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `GET matches with range=today returns 200 with match list`() {
        val response = MatchResponse(
            id = 1L,
            startTime = ZonedDateTime.of(2026, 8, 12, 18, 0, 0, 0, kst),
            matchState = MatchState.ONGOING,
            matchLabel = "W1",
            clubs = listOf(MatchClubResponse(name = "T1", logoUrl = "url", score = 1)),
        )
        every { matchQueryService.findMatches(MatchRange.TODAY) } returns listOf(response)

        mockMvc.get("/matches?range=today")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(1) }
                jsonPath("$[0].matchState") { value("ONGOING") }
            }
    }

    @Test
    fun `GET matches without range returns 400`() {
        mockMvc.get("/matches")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `GET matches with invalid range returns 400`() {
        mockMvc.get("/matches?range=tomorrow")
            .andExpect { status { isBadRequest() } }
    }
}
```

- [ ] **Step 2: 테스트 실행 후 실패 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.inbound.web.MatchRestControllerTest"`
Expected: FAIL (컴파일 에러 또는 404 — 현재 컨트롤러가 `range` 파라미터를 받지 않음)

- [ ] **Step 3: `MatchRestController` 구현**

`@RequestParam`이 없는 필수 파라미터는 Spring이 자동으로 400(`MissingServletRequestParameterException`)을 반환하지만, `MatchRange.from`이 던지는 `IllegalArgumentException`은 기본적으로 500으로 처리된다. 400으로 매핑하기 위해 `@ExceptionHandler`를 함께 추가한다.

```kotlin
package io.olkkani.lolviewback.infastructure.inbound.web

import io.olkkani.lolviewback.domain.match.MatchQueryService
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchRange
import io.olkkani.lolviewback.infastructure.inbound.web.dto.MatchResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class MatchRestController(
    private val matchQueryService: MatchQueryService,
) {

    @GetMapping("/matches")
    fun getMatches(@RequestParam range: String): List<MatchResponse> {
        return matchQueryService.findMatches(MatchRange.from(range))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidRange(ex: IllegalArgumentException): String {
        return ex.message ?: "Invalid request"
    }
}
```

- [ ] **Step 4: 테스트 실행 후 통과 확인**

Run: `./gradlew test --tests "io.olkkani.lolviewback.infastructure.inbound.web.MatchRestControllerTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/MatchRestController.kt src/test/kotlin/io/olkkani/lolviewback/infastructure/inbound/web/MatchRestControllerTest.kt
git commit -m "feat: implement GET /matches?range endpoint"
```

---

### Task 6: 전체 테스트 스위트 실행 및 회귀 확인

**Files:**
- 없음 (검증 전용 태스크)

**Interfaces:**
- Consumes: Task 1~5의 모든 산출물
- Produces: 없음

- [ ] **Step 1: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 모든 테스트 PASS — 기존 sync 관련 테스트(`TournamentDueCheckerTest`, `MatchMapperTest`, `TournamentMapperTest`)와 이번에 추가한 테스트 모두 포함.

- [ ] **Step 2: 실패가 있다면 원인 파악 후 수정, 재실행**

실패 시 로그를 확인하고 원인(엔티티 생성자 불일치, 마이그레이션 스키마 불일치 등)을 수정한다. 수정 후 `./gradlew test`를 다시 실행해 전체 통과를 확인한다.

- [ ] **Step 3: 최종 커밋 (수정 사항이 있었던 경우에만)**

```bash
git add -A
git commit -m "fix: resolve test failures found in full suite run"
```

(Step 1에서 바로 전체 통과했다면 이 커밋은 건너뛴다.)
