# Maple Utility

## 로컬 PostgreSQL 테스트

Docker Compose와 Java 21 설치 필요. `JAVA_HOME` 설정 시 해당 경로의 Java 21 사용, 미설정 시 PATH의 Java 21 사용.

```bash
./infra/scripts/test-postgres.sh init
./infra/scripts/test-postgres.sh up
./infra/scripts/test-postgres.sh test
./infra/scripts/test-postgres.sh down
```

`test` 명령은 DB 준비 상태 확인 후 `backend/gradlew test --rerun-tasks`로 전체 테스트 재실행. `up`은 별도 실행 없이 `test`에서 자동 수행 가능. 스크립트는 호출 위치와 무관하게 저장소 경로 기준 실행.

| 환경변수 | 테스트 설정 |
|---|---|
| `FLYWAY_TEST_URL` | `jdbc:postgresql://127.0.0.1:5433/maple_flyway_test` |
| `FLYWAY_TEST_USER` | `maple_test` |
| `FLYWAY_TEST_PASSWORD` | `init`에서 생성한 64자리 임의 16진수 값 |

연결 정보는 저장소 루트의 `.env.test.local`에 권한 `600`으로 저장하며 Git 제외. 기존 파일은 덮어쓰지 않고 검증 후 보존. 세 변수는 테스트 프로세스에만 적용하며 비밀번호 출력 금지. `.env.example`의 빈 필드는 안내용으로 사용.

`maple-utility-test` Compose 프로젝트의 `postgres-test` 서비스는 CI와 동일한 `postgres:16.9-alpine` 사용. 호스트의 `127.0.0.1:5433`에만 연결하며 기존 개발 DB의 `5432` 포트 및 컨테이너와 분리. 다른 DB 주소·사용자, 누락·중복 설정 및 심볼릭 링크 설정 파일은 실행 전 거부.

테스트 데이터는 tmpfs 저장. `test` 종료 후 컨테이너 유지, `down`으로 테스트 프로젝트의 컨테이너와 네트워크만 제거. 컨테이너 종료·제거 후 데이터 초기화, 재기동 시 새 DB 생성. 실제 비밀번호 변경은 테스트 DB 종료 후 로컬 설정 파일 별도 관리 필요.

PostgreSQL 연결 정보 없이 건너뛰는 다음 3개 테스트의 실제 실행 확인:

- `FlywayMigrationOrderTest`: 신규 스키마의 전체 마이그레이션 적용 및 뒤늦은 V9 마이그레이션의 `outOfOrder` 동작 (2개)
- `HuntingRecordPostgresTest`: PostgreSQL의 날짜 필터·사용자·즐겨찾기 조건 및 정렬 조회 (1개)

검증 결과는 `backend/build/test-results/test/TEST-*.xml`과 `backend/build/reports/tests/test/index.html`에서 확인. 테스트 환경에서는 임의 스키마 생성·삭제가 발생하므로 테스트 전용 DB 사용 필수.

## 브랜치 전략

| 브랜치 | 역할 |
|---|---|
| `main` | 운영 배포. push 시 배포 workflow 실행 |
| `develop` | 운영 반영 전 변경 통합 및 테스트. 기본 브랜치 |

- 작업 흐름: feature 브랜치 → PR → `develop` → PR → `main`
- `main`·`develop` 직접 push 및 force-push 금지 (ruleset 적용)
- feature → `develop`: merge commit 또는 squash, `develop` → `main`: merge commit만 허용
- 스테이징 서버 배포는 별도 이슈(#348)에서 진행

## 릴리스

- 버전 표기: SemVer `vMAJOR.MINOR.PATCH` (기능 추가 minor, 버그 수정 patch, 호환성 변경 major)
- 절차: `develop` → `main` PR 머지 → `main` 최신 커밋에 태그 push → GitHub Release 자동 생성

```bash
git switch main && git pull
git tag v1.0.0
git push origin v1.0.0
```
