# 운영 데이터·ID 초기화 및 수동 백업

자동 배포·Flyway에 연결하지 않는 일회성 수동 절차. 테이블 재생성 대신 데이터와 소유 시퀀스 초기화. 운영 SQL에 기존 마이그레이션 재실행 금지.

## 1. 초기화 전 복구 지점 확보

배포 중단, 비밀번호 관리자에 현재 `NEXON_API_KEY_SECRET` 별도 보관. DB 덤프에 환경변수 포함 없음. 백업 위치는 Git·공유 폴더 외부의 암호화된 개인 저장소 사용.

```bash
bash infra/scripts/manual-backup.sh maple-postgres "$HOME/maple-manual-backups"
```

출력 디렉터리 전체를 `scp -r`로 개인 PC에 복사 후 해당 디렉터리에서 `shasum -a 256 -c SHA256SUMS` 확인. `pg_dump.log` 경고 검토. `.partial`은 실패 산출물이므로 정상 백업으로 취급 금지. `manifest.txt`는 개인정보 값 없이 테이블별 행 수만 포함하며 덤프와 별도 관측 시점 기준.

개인 PC에 PostgreSQL 16.9 검증 컨테이너 생성 (`--network none`, 포트·운영 볼륨 연결 없음). 컨테이너 초기 비밀번호는 로컬 일회성 값 사용, 셸 추적 금지. `pg_restore --exit-on-error --single-transaction --no-owner --no-privileges`로 `maple.dump` 전체 복구 후 주요 테이블과 Flyway 이력 조회. `pg_restore --list` 성공만으로 복구 검증 대체 금지. 복구 DB에서 아래 초기화까지 먼저 검증.

## 2. 기준 목록 추출

현재 배포와 동일한 마이그레이션을 **개인 PC의 새 빈 PostgreSQL DB에만** 버전순 적용. 운영 DB의 보스·드랍을 기준으로 추출 금지. Flyway 이력이 있는 새 DB 사용. Spring Boot의 현재 managed Flyway 또는 프로젝트의 기존 마이그레이션 실행 방법 사용.

```bash
bash infra/scripts/export-reset-catalog.sh maple-canonical "$HOME/Maple-Backups/reset-catalog.json"
```

JSON 계약: `bosses`는 ID·생성/수정시각을 제외한 최종 보스 업무 필드 전체, `drops`는 활성 항목의 업무 필드와 보스명·난이도, `migrations`는 Flyway 버전·타입·스크립트·체크섬·성공 여부 배열. V2/V6/V7/V9/V17 등 최종 결과 반영. JSON은 신뢰할 수 있는 추출 결과만 사용하며 파일 해시와 검토 결과 기록. 마이그레이션 비교는 버전·타입·스크립트의 C 정렬 기준이며 설치 순서 차이 허용. 버전 누락·중복·실패 또는 체크섬 차이 시 실행 중단. 실제 운영 Flyway 이력의 설치 순서·시각·ID는 원형 보존.

```bash
RESET_CONFIRM=RESET-ALL-USER-DATA RESET_BACKUP_VERIFIED=yes RESET_WRITERS_STOPPED=yes \
  bash infra/scripts/reset-user-data.sh maple-restore-verify "$HOME/Maple-Backups/reset-catalog.json"
```

복구 검증 DB에서 데이터 삭제, 보스 정렬·가격·최대 파티 인원, 활성 드랍 수·속성, 사냥터와 Flyway 데이터·ID 유지 확인. 보스 ID는 `sort_order`, EASY/NORMAL/HARD/CHAOS/EXTREME, 보스명 C 정렬 순서. 드랍 ID는 새 보스 ID·아이템명 C 정렬 순서. 기존 마이그레이션 원본 변경 없음.

## 3. 운영 초기화

1. Nginx 등 진입점에서 점검 상태 전환, 앱·배치·동기화 쓰기 프로세스 정상 종료 및 종료 확인.
2. 같은 수동 백업 명령으로 초기화 직전 덤프 추가 확보. 개인 PC 복사·체크섬·복구 확인. 다른 DB 클라이언트의 쓰기도 차단.
3. 검증한 스크립트·동일 JSON을 서버에 전달. 서버에서 `RESET_CONFIRM=RESET-ALL-USER-DATA RESET_BACKUP_VERIFIED=yes RESET_WRITERS_STOPPED=yes bash infra/scripts/reset-user-data.sh maple-postgres /안전한/경로/reset-catalog.json` 명시적 실행.
4. 알 수 없는 public 테이블, Flyway 차이, 외래 키 충돌, 카탈로그 오류 시 전체 트랜잭션 롤백. 원인 확인 전 앱 기동 금지. 잠금 10초·문장 120초 제한으로 실패 시 점검 유지 후 조사.
5. ID 재사용 전 새 `JWT_SECRET` 발급·운영 환경변수 반영. 값 출력·커밋 금지. 이후 기동할 모든 앱 인스턴스에 동일 새 값 적용.
6. Redis에서 아래 허용 패턴에만 `SCAN`으로 조회한 키를 `UNLINK` 처리. 사용 중인 Redis DB 번호·인증 방식 확인 후 실행. 키 인자 안전 처리를 지원하는 클라이언트 사용, 쉘 단어 분리나 `KEYS` 사용 금지.

   - `jwt:refresh-token:*`
   - `scheduler::*`
   - `character-basic::*`
   - `static-data::*`
   - `nexon-api::*`

   `nexon:api:daily:*` 유지. `FLUSHDB`·`FLUSHALL` 금지. 정지된 앱 상태에서 허용 패턴 재조회 결과 0 확인.
7. 새 JWT 환경으로 앱 기동·상태 확인 후 점검 해제. 기존 Access/Refresh Token 인증 거부 확인.

`hunting_grounds`, `flyway_schema_history` 및 사냥터 시퀀스 유지. 그 외 현재 25개 테이블 데이터·소유 시퀀스 초기화 후 보스·활성 드랍 재삽입. 테이블은 `RESTRICT`로 명시적 나열하며 `CASCADE` 없음.

## 4. 새 데이터와 최종 백업

실제 로그인 → API Key 등록 → 캐릭터 동기화 → 화면에서 즐겨찾기·스케줄러·사냥·보스 기록 입력. 기준 목록·파티 인원·정렬 확인. 새 데이터 준비 완료 후 짧은 점검 시간에 모든 쓰기 정지, 수동 최종 백업·개인 PC 복사·체크섬·별도 전체 복구 검증. 검증 후 앱 재개. 새 데이터 직접 입력 완료 전 최종 기준 백업으로 표시 금지.

최종 복구 검증 완료까지 초기화 전 백업 보관. R2 자격 증명 폐기·재발급 전 R2 업로드 재개 금지. 기존 백업/복구 스크립트는 이번 custom `.dump` 형식에 사용 금지.

장애 시 점검 유지, 부분 상태를 추정하여 서비스 재개 금지. 초기화 SQL 실패는 트랜잭션 롤백 결과 확인. 성공 후 복원이 필요하면 초기화 직전 덤프를 **별도 빈 DB에 복구·검증한 뒤** 운영 전환. 운영 DB에 덮어쓰기 복구 금지. 롤백 후에도 새 JWT_SECRET 유지 및 재로그인 진행.
