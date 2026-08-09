# esports-api 연동 — Tournament/Match 수집 파이프라인

## 배경

`esports-api.lolesports.com`에서 Tournament, Match 데이터를 가져와서 DB에 기록하는 기능이 필요하다. 현재 프로젝트에는 `League`, `Tournament`, `Match`, `MatchParticipant`, `Club`, `ClubProfile`, `LeagueRecurrenceWindow` 엔티티가 이미 정의되어 있다. 이 중 이번 스코프 대상인 `League`, `Tournament`, `Match`는 각각 `leagueApiId`, `tournamentApiId`, `matchApiId` 필드로 외부 API와의 연결점이 이미 마련되어 있다 (`MatchParticipant`/`Club`/`ClubProfile`에는 apiId 필드가 없음 — 이번 스코프 제외 이유 중 하나). `application.yaml`에도 `lol-api.key`, `lol-api.url.{league,tournament,match}` 설정이 이미 존재한다. 하지만 실제로 API를 호출하는 클라이언트, 응답 매핑, 저장 로직, 스케줄러, repository 계층은 아직 하나도 구현되어 있지 않다.

**League는 이번 스코프에서 자동 수집 대상이 아니다** — 사용자가 수동으로 조회하고 추가한다. 자동 수집 대상은 Tournament와 Match뿐이며, 각각 서로 다른 트리거 조건과 주기를 가진다.

이 spec은 `/office-hours`로 초안을 잡고 여러 라운드의 사용자 정정 및 adversarial review를 거친 뒤, `/superpowers:brainstorming`으로 갭을 재검토하여 확정한 결과다.

## 목표

- Tournament: League별 대회 주기(`league_recurrence_windows`)를 근거로 "지금 새 대회를 조회해야 하는가"를 판단해서 자동 수집한다.
- Match: 진행 예정/진행 중 경기를 각각 다른 주기(일별/5분)로 자동 수집한다.
- 재실행해도 중복이 생기지 않는 upsert 구조를 갖춘다.
- 비공식 API 응답 스키마 변화에 도메인 엔티티가 직접 영향받지 않도록 DTO/매퍼 계층을 분리한다.

## 결정 사항

### League — 자동 수집 대상 아님
League는 관리자가 수동으로 조회하여 DB에 추가한다. **League를 추가할 때 해당 League의 `league_recurrence_windows` 로우(들)도 함께 수동으로 입력한다** — Tournament 스케줄러가 이 테이블에 전적으로 의존하므로, League 추가와 window 입력은 한 세트의 수동 작업이다. 별도 관리 UI는 이번 스코프 밖 — SQL 직접 입력이나 간단한 스크립트로 충분하다.

`League` 엔티티에 이미 `leagueCycle`(MULTI_SPLIT/ANNUAL/QUADRENNIAL) 필드가 존재하지만, 이번 설계는 `league_recurrence_windows`만 근거로 사용하고 `leagueCycle`은 참조하지 않는다. 두 필드가 중복 정보인지, `leagueCycle`도 함께 검증에 써야 하는지는 구현 전 확정이 필요한 열린 질문이다 (아래 "구현 전 확정 필요" 참고).

### League 자동 비활성화
Tournament 스케줄러가 어떤 League에 대해 "예상 시작 연도 + 2년"이 지나도록 새 Tournament를 찾지 못하면, 해당 League의 `isActive`를 `false`로 전환한다. 예: `league_recurrence_windows.start_date`(컬럼명 변경 — 아래 "스키마 변경" 참고)가 2022년이고 `interval_years`가 4년이면 다음 Tournament는 2026년 예상이고, 2026년부터 매달 조회를 시도하되 2028년까지도 찾지 못하면 `isActive = false`로 전환한다. 이 규칙이 "언제까지 재시도할 것인가"에 대한 구체적인 상한이 된다.

대부분의 대회는 시작~종료가 같은 해에 끝나므로 이 계산이 그대로 성립하지만, 연말~연초에 걸쳐 열리는 대회라면 "+interval_years" 계산의 기준 연도가 달라질 수 있다 — 이 엣지케이스는 이번 스코프에서 별도 처리하지 않는다.

### Tournament — recurrence window 기반 주기 판단
Tournament 조회는 `league_recurrence_windows` 테이블(`interval_years`, 기준 날짜 컬럼)을 근거로 "이 League에 대해 지금 새 Tournament를 조회해야 하는가"를 판단한다.

"이미 Tournament가 존재하면 스킵"이라는 단순 규칙은 LCK처럼 1년에 여러 번(Spring/Summer 등) 열리는 리그를 놓친다. 따라서 스킵 판단은 "해당 League에 Tournament가 하나라도 있는가"가 아니라, "**해당 주기(회차)**에 대응하는 Tournament가 이미 있는가"여야 한다. 이번 구현에서는 `league_recurrence_windows`의 `sequence_order`/`label`(예: Spring, Summer)을 주기 구분 단위로 사용한다.

Tournament를 새로 저장할 때 해당 window의 기준 날짜 컬럼을 그 새 Tournament의 `start_date` 값으로 갱신한다 — 즉 "다음 조회를 언제부터 시도할지"의 기준점은 "마지막으로 확인된 Tournament의 시작일"이다.

**(구현 중 정정, Task 6 검증 결과 반영)**: 최초 설계는 `end_date`로 갱신하는 것으로 정했으나, 구현 단계(Task 6, `TournamentDueChecker`)에서 이 값이 "window가 어느 계절(Spring/Summer 등)에 속하는지"를 판단하는 앵커로도 재사용된다는 게 드러났다. `end_date`는 대회마다 시작일로부터 몇 달 뒤이므로, 매 회차 갱신할 때마다 window의 "계절 위치"가 조금씩 밀린다 — 실제 LCK류 다회차 리그를 여러 해에 걸쳐 시뮬레이션한 결과 4번째 대회쯤부터 서로 다른 window의 계절 앵커가 수렴/역전되어 매칭이 틀어지는 것을 확인했다. `start_date`로 갱신하면 대회의 시작 월/일이 매년 거의 같은 자리로 되돌아오므로 이 드리프트가 발생하지 않는다(15년치 시뮬레이션으로 검증). 이 갱신 값은 "다음 조회 시도 시점" 판단(`isWindowDue`/`isWindowOverdue`)에는 영향이 없다 — 그쪽은 경과 시간만 보기 때문이다. `start_date`/`end_date` 표기가 window 매칭에 미치는 영향을 이후에 다시 바꾸려면, `TournamentDueChecker`의 매칭 로직도 함께 재검토해야 한다.

**스키마 변경**: `league_recurrence_windows.last_known_start_date` 컬럼은 이름과 달리 실제로는 Tournament의 `end_date`가 저장되므로, 컬럼명을 **`start_date`로 변경**한다 (마이그레이션에서 `start_date`로 리네임하고, 실제 의미는 "다음 조회 시도를 시작할 기준 날짜"로 통일). 엔티티 필드명(`lastKnownStartDate` → `startDate`)도 함께 변경한다. 프로젝트가 아직 실서비스 전이고 마이그레이션 히스토리가 `V1__create_table.sql` 하나뿐이므로, 새 버전 마이그레이션을 추가하지 않고 **V1 파일을 직접 수정**한다.

### Match — 이중 스케줄러, 같은 API 재사용
Match는 두 가지 독립된 주기로, **동일한 API 요청을 재사용**하되 다른 목적으로 호출된다:

- **일별 1회**: DB의 Tournament 중 `today`가 `start_date - 7일` ~ `end_date` 범위에 드는 것을 찾고(1단계: 트리거 대상 Tournament 특정), 그 Tournament가 속한 League의 `leagueApiId`를 파라미터로 esports-api match 엔드포인트를 호출해(2단계) 신규/예정 경기를 가져온다. 응답받은 Match들을 저장할 때 `tournament_id`는 API 응답에서 다시 유추하지 않고, **1단계에서 이미 특정해둔 그 Tournament를 그대로 사용**한다.
- **5분 주기**: DB에서 `matchState = ONGOING`인 Match 로우를 찾아, 각각의 `matchApiId`로 개별 조회해서 세트/점수 등 진행 상태를 갱신한다 (`tournament_id`는 이미 저장된 값 그대로 유지).

**전제**: 동일 League 내에서 두 Tournament가 동시에(날짜가 겹쳐) 진행되지 않는다. 이 전제가 깨지는 경우(1단계 조회에서 동일 League의 Tournament가 2건 이상 매칭)는 이번 스코프에서 다루지 않는다 — 전제 위반으로 간주해 로그만 남기고 스킵한다.

Tournament 저장 시 목표 League가 아직 없으면(League가 수동 추가 전이면) 스킵하고 다음 회차에 재시도한다. Match도 목표 Tournament가 없으면 동일하게 스킵/재시도한다. Tournament의 경우 이 재시도에는 "예상 연도+2년" 상한이 적용되어 무한정 반복되지 않는다.

### 아키텍처
League/Tournament/Match 각각 자신만의 트리거 조건을 가진 독립 스케줄러로 구현한다. 공통 파이프라인 추상화(제네릭 `SyncPipeline<Api, Entity>`)나 범용 폴링 상태 테이블(`target_type`/`next_check_at`류 신규 테이블) 같은 인프라는 이번 스코프에서 도입하지 않는다 — 엔티티 3개뿐인 지금 단계에서는 과설계다. 다만 Tournament의 주기 판단 로직(recurrence window 매칭)만큼은 스케줄러 메서드에 인라인으로 묻지 않고 별도 함수/클래스(`TournamentDueChecker` 류)로 분리한다 — 이 로직이 LCK 예외 케이스를 포함한 가장 오류 나기 쉬운 부분이라 단위 테스트 가능한 형태로 분리할 가치가 있다.

`spring-webflux`(WebClient)가 이미 프로젝트 의존성에 포함되어 있어 새 HTTP 클라이언트 라이브러리는 필요 없다. 스케줄링은 `spring-quartz`가 의존성에 있지만 이번 설계에서는 사용하지 않는다 — 단일 인스턴스 개인 프로젝트 규모에서는 plain Spring `@Scheduled`로 충분하고, Quartz의 영속 잡스토어/클러스터링 기능이 필요 없기 때문.

### 에러 처리 / 재시도 (v1 기본값)
- API 호출 실패, 429 rate limit 등에 대한 별도 처리 없이 예외를 던지고 스케줄러가 catch-and-log 후 해당 회차만 실패 처리 (다음 회차에 자동 재시도). 백오프/서킷브레이커는 실제로 문제가 될 때 추가한다.
- 스케줄러 실행 시간이 주기보다 길어질 경우의 중복 실행(overlap) 방지는 이번 스코프에서 다루지 않는다 — 5분 주기 라이브 갱신 스케줄러가 특히 겹칠 위험이 있으나, 단일 인스턴스이고 초기 데이터량이 작아 리스크 낮음으로 판단. 문제가 되면 `ShedLock` 등 도입.
- Rate limit / API 일시 다운 대비도 이번 스코프에서 다루지 않는다 — 개인 프로젝트 규모(동시 진행 경기 수가 적음)에서는 과도한 우려로 판단.

### 검증 방법
자동화된 테스트(단위/통합)는 이번 스코프에 포함하지 않는다. 구현 후 DB를 직접 조회해서 예상한 League/Tournament/Match가 실제로 들어왔는지, 재실행 시 중복이 없는지 육안으로 확인한다.

## 변경 사항

### 1. 마이그레이션 수정 — `V1__create_table.sql`
`league_recurrence_windows` 테이블의 `last_known_start_date` 컬럼을 `start_date`로 리네임.

### 2. 엔티티 수정 — `LeagueRecurrenceWindow.kt`
`lastKnownStartDate: LocalDate` 필드를 `startDate: LocalDate`로 리네임.

### 3. 신규 — DTO
`sync/dto/` — `TournamentApiResponse`, `MatchApiResponse` 정의 (실제 esports-api 응답 구조 확인 후 필드 확정).

### 4. 신규 — API 클라이언트
`sync/client/LolEsportsApiClient.kt` — WebClient 기반, Tournament/Match 조회용 coroutine suspend 함수.

### 5. 신규 — 매퍼
`sync/mapper/` — DTO → Entity 매퍼 함수.

### 6. 신규 — Repository 계층
Spring Data JPA repository 인터페이스를 이 프로젝트 최초로 생성 (League/Tournament/Match), apiId 기준 조회 메서드 포함 (`findByLeagueApiId` 등), Match는 `matchState`로 조회하는 메서드도 포함. 프로젝트 전체의 repository 패키지 컨벤션을 여기서 정한다.

### 7. 신규 — Tournament 주기 판단 로직
`TournamentDueChecker` 류 — recurrence window와 실제 Tournament를 매칭해 "지금 조회해야 하는가"를 판단하고, 예상 연도+2년 경과 시 League 비활성화 로직도 포함.

### 8. 설정 추가
`@EnableScheduling`을 애플리케이션 설정에 추가.

### 9. 신규 — 스케줄러
`sync/scheduler/` — Tournament 스케줄러, Match 일별 스케줄러, Match 5분 스케줄러. 상위 엔티티 없으면 스킵 + 로그.

## 스코프 제외
- `MatchParticipant`, `Club`, `ClubProfile` 수집 — 후속 세션 대상.
- 범용 폴링 상태 테이블, 이벤트 기반 재시도 — 필요해지면 재검토.
- Rate limit 대응, 스케줄러 중복 실행 방지, 자동 테스트 — 위 "에러 처리/재시도", "검증 방법" 참고.

## 구현 전 확정 필요 (계획 단계에서 반드시 짚어야 함)
- window ↔ 새로 조회된 Tournament의 정확한 1:1 매핑 로직 (여러 window가 있는 League에서 어떤 window를 갱신할지) — 코드 수준으로 아직 명시되지 않음. **LCK처럼 실제 다회차 리그의 구체적인 날짜(예: 2024 Spring/Summer 실제 시작일)를 `league_recurrence_windows` 컬럼에 대입한 워크스루**를 이 로직의 acceptance 기준으로 삼는다.
- `League.leagueCycle`을 트리거 조건에 반영할지 여부.
- esports-api 실제 응답 body 스키마와 필요 헤더(`x-api-key` 외 추가 헤더 여부) — 비공식 API라 문서가 없으므로 실전 호출로 확인 필요.
- 로컬 실행을 위한 `LOL_API_KEY`, `LOL_API_URL_LEAGUE`, `LOL_API_URL_TOURNAMENT`, `LOL_API_URL_MATCH` 환경변수 값.
- Tournament 스케줄러의 정확한 cron 주기 (League 스캔은 자주 돌 필요 없어 보임 — 하루 1회 정도로 시작 제안, 관찰 후 조정).
