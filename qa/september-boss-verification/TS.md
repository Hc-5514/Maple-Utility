# 9월 월간 보스 조사 테스트 시나리오

합성 테스트 데이터 사용. 사용자 확인 사실과 넥슨 원본의 값 구분, 운영 DB 변경·운영 API 호출 제외.

| ID | 조건 | 기대 결과 | 검증 근거 |
|---|---|---|---|
| S-01 | 문자열·불리언 완료 플래그 | 입력 true·false 그대로 파싱 | NexonOpenApiClientTest.monthlyFlagAndRegisteredDifficultyArePreservedForBothWireFormats, 4개 매개변수 |
| S-02 | 미등록 HARD·등록 EXTREME | 등록 EXTREME만 반환 | 동일 매개변수 테스트 |
| S-03 | 월간 complete_flag 누락 | false 기본 처리 없이 NEXON_RESPONSE_INVALID | NexonOpenApiClientTest.missingMonthlyCompleteFlagIsRejectedInsteadOfDefaultingToFalse |
| S-04 | 9/1 수동 true·9/30 자동 false, 확정 없음 | 최신 자동 false 선택 및 수동 행 보존 | SchedulerBossRecordRepositoryTest.unfinalizedMonthKeepsManualEvidenceSeparateFromLatestAutomaticFalse |
| S-05 | 9/23 기준 요약용 조회 | 미래 월말 제외·수동 true 선택 | 동일 리포지터리 테스트 |
| S-06 | 10/1 완료 행 함께 존재 | 9월 조회와 10월 조회 분리 | 동일 리포지터리 테스트 |
| S-07 | 확정 월말 false·월중 자동 true | 월말 false 유지·결정 수익 0 | 기존 SchedulerServiceTest.finalizedFalseOverridesEarlierAutomaticTrueAndHasNoCrystalRevenue 재사용 |
| S-08 | 확정 월말 false·수동 true | 수동 true 우선 표시 | 기존 SchedulerServiceTest.finalizedMonthReplacesAutomaticMembershipButManualRecordWins 재사용 |
| S-09 | 월말 확정 이후 수동 숨김 | 숨김 보존 | 기존 SchedulerServiceTest.hiddenSelectionRemainsHiddenAfterLateMonthFinalization 재사용 |
| S-10 | 월중 요약과 미래 확정 월말 | 미래 월말 선반영 차단 | 기존 SchedulerServiceTest.summaryMidMonthCannotReadFutureMonthEndSnapshot 재사용 |

## 실행 결과

- 신규 파싱 매개변수 4개·누락 플래그 1개·월간 조회 1개 검증 추가
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./infra/scripts/test-postgres.sh test`: PostgreSQL 검증 포함 백엔드 335개 통과, 실패 0개·오류 0개·건너뜀 0개
- `git diff --check`: 통과
- 프런트엔드·서비스·스키마 변경 없음, 관련 기존 회귀 테스트 재사용
- 운영 API 재호출·운영 DB 수정·DB 초기화 없음

## 검증 한계

- 기존 자료는 9/30 당시 HTTP 원본이 아님
- 사용자 확인은 꼬농·말랑꼬농 처치, 도구꼬농 미처치
- 위 자동 테스트는 파싱·조회 동작의 검증이며 당시 원본 정확성의 증명 제외
- 미확정 월의 수동 raw 기록 우선순위는 REPORT.md에 한계 기록, 정책 수정 제외
