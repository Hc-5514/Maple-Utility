# 월간 보스 월말 확정 테스트 시나리오

연결 이슈: #314 / 검증 기준: Asia/Seoul / 2026-10-15

## 정책 및 근거

- 월말 다음 날 02시부터 D-13까지 월말 수집, D-14 미완료 작업 만료
- 최근 두 주차 잠금과 별개인 월말 수집 예외, 수동 기록 우선 유지
- [넥슨 공식 안내](https://openapi.nexon.com/ko/support/notice/3482567/): 과거 날짜의 마지막 스케줄러 조회
- D-13 경계: #313의 10/14·10/15 실측 근거, 공식 문구의 D-14 포함 여부와 구분
- 02시: 서비스 운영 정책, 넥슨 준비 시각의 공식 보장 제외
- [9/17 수정 안내](https://openapi.nexon.com/ko/support/notice/3545814/): 이전 처치 오류의 소급 수정 제외, #315 원본 검증 필요

## 자동 검증 시나리오

| ID | 조건·행동 | 기대 결과 | 검증 클래스 |
|---|---|---|---|
| M-01 | 28·29·30·31일 및 연말 | 올바른 월말 산정 | SchedulerMonthlyFinalizationServiceTest |
| M-02 | D-1 01:59·02:00 및 D-2 01:00 | 최초 보류·허용 및 기존 재시도 허용 | SchedulerMonthlyFinalizationServiceTest |
| M-03 | D-13·D-14 | 포함 경계 조회·기간 밖 만료 | SchedulerMonthlyFinalizationServiceTest, SchedulerMonthlyFinalizationRepositoryPostgresTest |
| M-04 | 중복 생성·다른 소유자·미즐겨찾기 | 월별 단일 작업·대상 격리 | SchedulerMonthlyFinalizationRepositoryPostgresTest |
| M-05 | null·빈 월간 목록 | NO_DATA 및 빈 FINALIZED 구분 | SchedulerMonthlyFinalizationServiceTest, SchedulerMonthlyFinalizationRepositoryPostgresTest |
| M-06 | 월간 false·주간 true 혼합 | 월간 false 보존·주간 저장 제외 | SchedulerMonthlyFinalizationServiceTest |
| M-07 | 미지원 난이도·매핑 누락·중복 | 확정 거절 및 RETRY | NexonOpenApiClientTest, SchedulerMonthlyFinalizationServiceTest |
| M-08 | 일일 보스 포함 | 월간 수집의 불필요한 거절 방지 | NexonOpenApiClientTest |
| M-09 | 응답 날짜 불일치 | 저장 차단 및 RETRY | SchedulerMonthlyFinalizationServiceTest |
| M-10 | 호출 중 D-14 전환 | 저장 차단·EXPIRED·진행률 반영 | SchedulerMonthlyFinalizationServiceTest |
| M-11 | 예산 부족·잠금 충돌 | 미시도 RETRY·skipped 반영 | SchedulerMonthlyFinalizationServiceTest |
| M-12 | 배치 선처리·즐겨찾기 해제 | 중복 호출 차단 | SchedulerMonthlyFinalizationServiceTest |
| M-13 | 저장 실패·재시도 저장 실패 | 롤백·진행률 누락 방지 | SchedulerMonthlyFinalizationServiceTest, SchedulerMonthlyFinalizationRepositoryPostgresTest |
| M-14 | 같은 날짜 주간·월간 확정 | 단일 외부 호출·두 논리 결과 반영 | SchedulerHistoryFinalizationServiceTest |
| M-15 | 같은 날짜 조회 실패 | 오류 공유·반복 호출 차단 | SchedulerMonthlyFinalizationServiceTest |
| M-16 | 월간 확정 대상 추가 | 완료 작업 재사용 차단 | SchedulerHistoryFinalizationServiceTest, SyncJobServiceTest |
| M-17 | 빈 확정 월·이전 자동 보스 | 이전 보스 부활 방지 | SchedulerServiceTest |
| M-18 | 월말 false·수동 true | 수동 기록 우선 | SchedulerServiceTest |
| M-19 | 현재 동기화와 수동 월간 기록 | 수동 기록 갱신·삭제 방지 | SchedulerSyncServiceTest |
| M-20 | 월중 날짜 요약 | 미래 월말의 선반영 방지 | SchedulerServiceTest |
| M-21 | FINALIZED 이후 기간 경과 | 기존 완료 스냅샷 보존 | SchedulerMonthlyFinalizationRepositoryPostgresTest |
| M-22 | 01시 재시도·02시 확정 | 기존 작업 우선·신규 경로 연결 | SchedulerBatchJobTest, SchedulerWeekFinalizationServiceTest |
| M-23 | 월말 false·월중 자동 true | 월말 false 및 결정 수익 0 유지 | SchedulerServiceTest |
| M-24 | 늦은 월말 확정과 기존 숨김 | 수동 숨김 보존 | SchedulerServiceTest |
| M-25 | 활성 키 소유자 없음 | 오래된 주간·월간 작업 만료 실행 | SchedulerHistoryFinalizationServiceTest |

## 실행 결과

- 위 시나리오의 자동 검증 통과
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./infra/scripts/test-postgres.sh test`: 백엔드 329개 통과, 실패 0개·오류 0개·건너뜀 0개
- `pnpm test`: 프런트엔드 32개 파일·160개 테스트 통과
- `pnpm type-check`, `pnpm lint`: 통과
- `git diff --check`: 통과
- 공개 API·DTO·프런트 소스·pnpm 잠금 파일 변경 없음
- 실제 PostgreSQL에서 V18 DDL·제약조건·상태 전이·롤백 검증
- 운영 API 호출 및 운영 DB 수동 변경 없음

실제 미래 월말 응답은 배포 후 11/1 02시 이후 로그·DB 대조 대상. 10/15의 9/30 재조회 및 운영 완료값 보정 제외.
