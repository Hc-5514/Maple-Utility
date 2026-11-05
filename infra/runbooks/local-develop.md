# 로컬 develop Docker 통합 검증

`develop` 최신화 후 저장소 루트에서 실행. Docker Desktop 및 Python 3.9 이상 필요.

```bash
bash infra/scripts/develop-local.sh init
bash infra/scripts/develop-local.sh fetch-backup
bash infra/scripts/develop-local.sh restore "$HOME/Maple-Backups/local-develop/<백업 디렉터리>"
bash infra/scripts/develop-local.sh up
bash infra/scripts/develop-local.sh status
bash infra/scripts/develop-local.sh down
```

Chrome에서 http://localhost:3000 접속. 카카오 앱에는 http://localhost:3000/auth/callback 등록 필요. 같은 넥슨 계정의 별도 개발용 애플리케이션 Key를 화면에서 등록. 기존 운영 Key 재발급·폐기 불필요. 카카오 설정은 기존 비공개 .env의 KAKAO_CLIENT_ID/SECRET만 최초 복사. JWT·DB 비밀번호·API Key 암호화 키는 로컬 전용 생성. 프론트 전달 값은 공개 Kakao ID뿐이며 비밀 값·덤프의 Git 커밋 금지.

Compose 프로젝트 maple-develop의 프론트만 127.0.0.1:3000 공개. 기존 maple-postgres/redis 및 운영 DB 변경 없음. 로컬 야간 배치 비활성, 수동 동기화는 개발용 Nexon 애플리케이션 허용량 사용. 초기화 후 상태는 .env.develop.local(600)과 .local-develop에 보관. 일반 재빌드·down은 볼륨·기록 유지. restore만 명시적 데이터 교체이며 실행 전 로컬 안전 덤프와 이전 DB 보관. restore 실패 시 원인 확인 후 재시도, 이전 DB 수동 삭제 금지.

신규 덤프는 운영 중 pg_dump 일관된 스냅샷으로 확보. manifest 행 수는 별도 관측이므로 동시 변경 시 덤프와 차이 가능. SHA-256 검증 후 임시 DB 전체 복구 및 업무 데이터 지문 보존 검증, API Key 행만 로컬에서 삭제. 운영 암호화 키 불필요. 이후 Flyway는 현재 코드의 추가 마이그레이션 적용, 체크섬 오류 시 repair로 우회하지 않고 원인 확인.

최근 통합 회귀: 같은 날짜 사냥 기록 복수 입력, 결정 가격 수정, 상자 상세 아이템·물욕템 필터, 부분 동기화 실패·재시도, 신규 보스 매칭. MSW 비활성 및 실제 로컬 API 경유 확인. 사용자 로그인·개발 Key 준비 전 인프라 검증과 실제 인증 검증을 구분하여 기록.
