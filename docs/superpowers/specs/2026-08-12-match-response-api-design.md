# Match Response API — 어제/오늘/예정 경기 조회

## 배경

프론트엔드는 어제 경기, 오늘 경기, 내일~7일 이내 예정 경기를 세 개의 탭으로 나눠 보여준다. 백엔드에는 이미 `Match`/`MatchParticipant` 엔티티, `MatchRepository`(`findByMatchApiId`, `findByMatchState`), 그리고 `MatchResponse` DTO(`toResponse` 확장 함수 포함)가 존재한다. 다만 `MatchRestController`는 아직 `"hello"`를 반환하는 자리표시자 상태이고, 세 구간을 어떻게 나눠 응답할지는 정해진 바가 없었다.

## 목표

- 어제 / 오늘 / 예정(내일~7일 이내) 세 구간의 경기 목록을 조회하는 API를 하나로 통일해서 제공한다.
- 오늘 탭은 진행 중인 경기 상태(점수, `matchState`)가 자주 바뀔 수 있으므로, 탭을 다시 조회했을 때 항상 최신 상태를 받을 수 있어야 한다.
- 프론트가 필요한 구간만 선택적으로 로드할 수 있게 한다.

## 결정 사항

### 탭마다 요청하는 방식 채택

세 구간을 한 번에 묶어 반환하는 방식(`/matches/grouped` 형태로 `{yesterday, today, upcoming}`를 한 응답에 담기)도 검토했지만 채택하지 않았다. 오늘 탭은 실시간성이 필요한데, 세 구간을 하나의 응답으로 묶으면 "오늘 구간만 새로고침"하고 싶을 때도 전체를 다시 받거나 오늘 전용 API를 별도로 하나 더 만들어야 하는 이중 구조가 생긴다. 탭마다 개별 요청하는 방식이면 API 하나로 세 탭을 일관되게 처리할 수 있고, 오늘 탭에 한해 프론트가 자체적으로 짧은 주기 폴링을 거는 것도 자연스럽다.

경기 수가 하루/일주일 단위로 많아야 수십 건 이하로 규모가 작기 때문에, 탭 전환마다 매번 요청하는 방식의 비용은 무시할 만한 수준이다.

### 엔드포인트

```
GET /matches?range={yesterday|today|upcoming}
```

- `range=yesterday`: 어제 하루 경기
- `range=today`: 오늘 하루 경기
- `range=upcoming`: 내일 ~ 7일 이내 예정 경기

`range` 파라미터는 필수다. 값이 셋 중 하나가 아니면 400을 반환한다.

### 날짜 경계 기준

모든 구간의 "하루" 경계는 KST(Asia/Seoul) 자정 기준이다. `MatchResponse.toResponse()`가 이미 `startTime`을 KST로 변환해서 내려주고 있어(`MatchResponse.kt:28`), 조회 조건도 동일하게 KST 자정 기준으로 맞춰 일관성을 유지한다.

- `yesterday`: `[오늘 KST 00:00 - 1일, 오늘 KST 00:00)`
- `today`: `[오늘 KST 00:00, 오늘 KST 00:00 + 1일)`
- `upcoming`: `[오늘 KST 00:00 + 1일, 오늘 KST 00:00 + 8일)` — 내일부터 7일간

### 응답 형식

기존 `MatchResponse` 리스트를 그대로 재사용한다.

```
GET /matches?range=today
200 OK
[
  {
    "id": 123,
    "startTime": "2026-08-12T18:00:00+09:00",
    "matchState": "ONGOING",
    "matchLabel": "...",
    "clubs": [ { "name": "...", "logoUrl": "...", "score": 1 }, ... ]
  },
  ...
]
```

정렬 순서는 `startTime` 오름차순으로 고정한다.

### 구현 범위

- `MatchRepository`에 구간 조회 쿼리 메서드를 추가한다 (`startTime` 범위 기반, `List<Match>` 반환).
- `MatchRestController`의 자리표시자 `getMatches()`를 `range` 쿼리 파라미터를 받는 실제 구현으로 교체한다.
- 각 `Match`에 대해 연관된 `MatchParticipant` 목록을 조회해서 `toResponse()`로 변환한다. N+1 방지를 위해 participant는 fetch join 또는 batch 조회로 가져온다 (구현 단계에서 확정).

## 스코프 밖

- 오늘 탭의 폴링 주기, 웹소켓/SSE 등 실시간 갱신 메커니즘 — 프론트 별도 구현 사항.
- 페이지네이션 — 구간별 경기 수가 적어 이번 스코프에서는 다루지 않는다.
- `yesterday`/`today`/`upcoming` 외 임의 날짜 범위 조회 — 필요해지면 별도로 확장한다.
