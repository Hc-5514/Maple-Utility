# 운영 백업 파이프라인 서버 반영·검증

cron 일간(03:00)·주간(일요일 04:00) 백업 → 로컬 `BACKUP_DIR` + Cloudflare R2 업로드. 기존 `manual-backup.sh` 수동 `.dump` 절차와 별개이며 이 파이프라인은 plain SQL(gzip) 형식.

## 1. 선행 조건

- R2 토큰 재발급 완료(노출 시크릿 교체). 교체 전 R2 업로드 재개 금지
- 서버 `.env`에 새 `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, 실제 버킷의 `R2_BUCKET` 반영
- 실제 버킷의 라이프사이클 규칙(daily 30일, weekly 84일) 확인

## 2. 서버 반영

1. develop → main 배포 후 `infra/scripts`, `infra/cron` 서버 반영 확인(CI scp). cron 등록은 자동 아님
2. 서버에서 `sudo bash /opt/maple-utility/infra/scripts/setup-cron.sh`
3. `/var/log/maple-backup.log` 소유자·권한(배포 사용자, 0640), `/etc/logrotate.d/maple-backup`, `/etc/cron.d/maple-backup` 확인
4. 수동 백업 1회

```bash
bash -c 'set -a; source /opt/maple-utility/.env; set +a; /opt/maple-utility/infra/scripts/backup-daily.sh' >> /var/log/maple-backup.log 2>&1
tail -20 /var/log/maple-backup.log
ls -lh /backups
```

5. R2 `daily/` 객체 확인

```bash
bash -c 'set -a; source /opt/maple-utility/.env; set +a; source /opt/maple-utility/infra/scripts/lib/rclone-env.sh; rclone lsf "r2:$R2_BUCKET" -R'
```

403 `AccessDenied` 시 토큰 범위와 `R2_BUCKET` 일치 여부 확인.

6. 다음 날 03:00 이후 로그와 R2 `daily/` 신규 객체 재확인

## 3. dead-man ping (healthchecks.io)

백업이 실행되지 않거나 실패해도 서버 로그만으로는 알 수 없으므로 외부 서비스가 ping 부재를 감지.

1. healthchecks.io 체크 2개 생성(사용자 수행)
   - 일간: 주기 1일, 유예 2시간 (cron 03:00 기준)
   - 주간: 주기 7일, 유예 1일 (일요일 04:00 기준)
2. 알림 채널(이메일·Discord 등) 연결. 이 채널을 이후 쿼터·5xx 경보에 재사용
3. 서버 `.env`에 `HEALTHCHECK_DAILY_URL`, `HEALTHCHECK_WEEKLY_URL` 반영. ping URL은 시크릿 취급(Git·문서·채팅 기재 금지)
4. 수동 백업 1회 후 대시보드에서 ping 수신 확인
5. 의도적 감지 검증: 일간 체크 유예 시간 경과 시 알림 수신 확인

스크립트 동작: 성공 시 URL, 실패 시 `URL/fail` 호출. URL 미설정 시 경고만 출력하고 백업은 정상 진행. 설정 오류(필수 환경변수 누락)로 스크립트가 시작 전에 종료되면 ping이 없어 유예 시간 후 알림으로 감지. curl 설정은 stdin으로 전달해 URL이 `ps`에 노출되지 않음.

## 4. 복구 리허설

운영 DB 직접 복구 금지. 임시 PostgreSQL 컨테이너(`--network none`)에서 덤프 파일을 `gunzip -c <파일> | psql -v ON_ERROR_STOP=1 --single-transaction`로 적용해 주요 테이블 행 수·Flyway 이력 확인. 운영 복구는 `restore.sh`(오류 시 중단, 단일 트랜잭션, 대상 DB 기존 객체 삭제 후 재생성) 사용 전 현재 상태 수동 백업 확보.

## 5. 참고

- 백업 스크립트는 DB 접속 정보를 컨테이너 환경(`POSTGRES_USER`·`POSTGRES_DB`·`POSTGRES_PASSWORD`)에서 읽으며 호스트 명령행에 비밀번호를 싣지 않음
- rclone 접속 정보는 `lib/rclone-env.sh`의 `RCLONE_CONFIG_R2_*` 환경변수로만 전달
- `DB_PASSWORD` 교체 시 `\password maple_user` 후 `.env` 반영, 컨테이너 환경값이 낮은 값이면 postgres 컨테이너 재생성(데이터 볼륨 유지)
