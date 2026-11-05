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

## 3. 복구 리허설

운영 DB 직접 복구 금지. 임시 PostgreSQL 컨테이너(`--network none`)에서 덤프 파일을 `gunzip -c <파일> | psql -v ON_ERROR_STOP=1 --single-transaction`로 적용해 주요 테이블 행 수·Flyway 이력 확인. 운영 복구는 `restore.sh`(오류 시 중단, 단일 트랜잭션, 대상 DB 기존 객체 삭제 후 재생성) 사용 전 현재 상태 수동 백업 확보.

## 4. 참고

- 백업 스크립트는 DB 접속 정보를 컨테이너 환경(`POSTGRES_USER`·`POSTGRES_DB`·`POSTGRES_PASSWORD`)에서 읽으며 호스트 명령행에 비밀번호를 싣지 않음
- rclone 접속 정보는 `lib/rclone-env.sh`의 `RCLONE_CONFIG_R2_*` 환경변수로만 전달
- `DB_PASSWORD` 교체 시 `\password maple_user` 후 `.env` 반영, 컨테이너 환경값이 낮은 값이면 postgres 컨테이너 재생성(데이터 볼륨 유지)
