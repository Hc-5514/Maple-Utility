# 최근 2주 동기화·보스 잠금 테스트 시나리오

- 연결 이슈: [#313](https://github.com/Hc-5514/Maple-Utility/issues/313)
- 검증일: 2026-10-15, Asia/Seoul
- TS 의미: Test Scenario
- 기준: 목요일~수요일 이번 주+지난주, 과거 자동 조회는 지난주 수요일 한 날짜
- 테스트 실행 환경: Java 21, Gradle wrapper, 독립 PostgreSQL 16.9, pnpm, MSW

## 자동 검증

| ID | 조건·행동 | 기대 결과 | 검증 근거 | 결과 |
|---|---|---|---|---|
| TS-01 | 목요일~수요일 각 날짜의 확정 대상 계산 | 지난주 마지막 수요일 단일 대상 | SchedulerPeriodPolicyTest | 통과 |
| TS-02 | 목요일 01:59 및 02:00 준비·실행 | 01:59 생성·조회 보류, 02:00 허용 | SchedulerPeriodPolicyTest, SchedulerWeekFinalizationServiceTest | 통과 |
| TS-03 | D-14 및 API 가능 범위 내 지지난주 요청 | 넥슨 호출 전 거절 | SchedulerSyncServiceTest | 통과 |
| TS-04 | 조회 중 수요일→목요일 전환 | 정책 밖 응답 저장 차단 | SchedulerSyncServiceTest | 통과 |
| TS-05 | 과거 응답 날짜 불일치·데이터 없음 | 기존 기록 보존 | SchedulerSyncServiceTest | 통과 |
| TS-06 | 오래된 PENDING·RETRY·NO_DATA와 FINALIZED 존재 | 미완료만 EXPIRED 전환, FINALIZED 보존 | SchedulerWeekFinalizationRepositoryPostgresTest | 통과 |
| TS-07 | 누락 작업 생성 및 같은 날짜 재시도 | 중복 생성 방지, 하루 단위 재시도 판정 | SchedulerWeekFinalizationRepositoryPostgresTest | 통과 |
| TS-08 | 확정 성공·저장 실패 | 성공 커밋·캐시 제거, 실패 롤백 후 재시도 기록 | SchedulerWeekFinalizationServiceTest | 통과 |
| TS-09 | 호출 예산 부족·사용자 동기화 잠금 충돌 | 불필요한 외부 호출 차단·재시도 이력 보존 | SchedulerWeekFinalizationServiceTest, SchedulerSyncServiceTest | 통과 |
| TS-10 | 고정 대상의 NO_DATA·배치 선처리 | 대상 재조회 없이 결과별 콜백 한 번·skipped 반영 | SchedulerWeekFinalizationServiceTest | 통과 |
| TS-11 | 현재+과거 작업 시작 | 고정 목록으로 totalCount 산정, 같은 목록 실행 | SyncJobServiceTest | 통과 |
| TS-12 | 과거 작업 포함 완료 작업의 5분 재사용 | totalCount와 즐겨찾기 수 비교 없이 재사용 | SyncJobServiceTest | 통과 |
| TS-13 | 즐겨찾기 해제·새 확정 대상·KST 자정 경과 | 기존 완료 작업 재사용 차단 | SyncJobServiceTest | 통과 |
| TS-14 | 이번 주·지난주·지지난주 선택 | 최근 2개 주차만 잠금 | SchedulerServiceTest, recentWeekPolicy.test.ts | 통과 |
| TS-15 | 10/1·10/8 및 연말·윤년의 월 잠금 | 겹치는 월 잠금, 10/8의 9월 해제 | SchedulerPeriodPolicyTest, recentWeekPolicy.test.ts | 통과 |
| TS-16 | 잠긴 기간 직접 추가·삭제·수동 등록 | BOSS_PERIOD_LOCKED 응답 | SchedulerServiceTest, recentWeekPolicy.test.ts | 통과 |
| TS-17 | 열린 화면의 KST 자정·절전 후 포커스 복귀 | 날짜·보스 쿼리 갱신 | useSeoulDay.test.tsx, useCharacterBossPolicy.test.tsx | 통과 |
| TS-18 | 날짜 갱신 도중 선택 주차·입력 유지 | 조회 키만 갱신, 기존 선택·입력 보존 | useCharacterBossPolicy.test.tsx | 통과 |

## 브라우저 검증

2026-10-15 실제 KST 날짜와 MSW 미리보기 사용. 운영 넥슨 호출 및 운영 DB 변경 제외.

| ID | 화면·행동 | 기대 결과 | 결과 |
|---|---|---|---|
| TS-19 | `/dashboard/1?week=2026-10-01` 보스 선택 열기 | 지지난주 주간 체크박스 활성, 10월 월간 체크박스 잠금·안내 표시 | 통과 |
| TS-20 | 같은 화면의 스우 드랍 아이템 열기 | 파티 인원·획득 체크박스·저장 버튼 표시 | 통과 |
| TS-21 | `/dashboard/1?week=2026-10-08` 이동 | 지난주 보스 선택 비활성, 10월 월간 잠금 | 통과 |

- [선택 모달 증거](preview-selection.png)
- [지난주 잠금 증거](preview-locked.png)
- 브라우저 검증 범위: 표시·활성 상태 및 모달 열림 확인, 운영 저장 검증 제외

## 실행 결과

- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./infra/scripts/test-postgres.sh test`: 백엔드 301개 통과, 실패 0개, 건너뜀 0개
- 프런트엔드 Vitest: 32개 파일·160개 테스트 통과
- `pnpm type-check`, `pnpm lint`, `pnpm build`: 통과
- `git diff --check`: 통과
- 기존 프런트 번들 크기 경고 유지
- 서버 보스 캐시 키 `boss:v2:` 변경 코드 확인

## 재현 및 후속 확인

- 미리보기: `cd frontend` 후 `pnpm install --frozen-lockfile`, `pnpm dev:mock --host 127.0.0.1 --port 5313 --strictPort`
- 미리보기 정책 기준: 실행 시점의 실제 서울 날짜, 테스트의 날짜 경계는 가짜 시계 사용
- 02시 기준: 기존 서비스 운영 정책, 넥슨 전일 데이터 준비 시각의 공식 보장값과 구분
- D-14 거절: 10/15 예약 실측 결과에 따른 범위 제한, 넥슨 내부 원인의 공식 확정 제외
- 9월 완료값 보정·월말 별도 확정 기능: [#314](https://github.com/Hc-5514/Maple-Utility/issues/314)·[#315](https://github.com/Hc-5514/Maple-Utility/issues/315) 후속 이슈 범위
- PR 이후 CI와 외부 리뷰 확인 필요
