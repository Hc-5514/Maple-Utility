# Oracle Cloud Backend 배포

## 개요

- 대상: Oracle Cloud Free Tier ARM Ubuntu VM
- 런타임: Docker Compose 기반 Spring Boot, PostgreSQL, Redis, Nginx
- 외부 노출: 80, 443
- 내부 통신: app, postgres, redis, nginx 전용 Docker network
- 시크릿 관리: 서버의 `.env` 파일 전용, 저장소 커밋 금지

## VM 초기 설정

```bash
sudo DEPLOY_DIR=/opt/maple-utility ./infra/scripts/oracle-cloud-init.sh
```

초기 설정 항목:

- Docker Engine 설치
- Docker Compose plugin 설치
- Docker 서비스 자동 시작 설정
- 배포 디렉터리 `/opt/maple-utility` 생성
- UFW 방화벽 OpenSSH, 80/tcp, 443/tcp 허용

스크립트 실행 후 Docker 그룹 권한 반영을 위해 SSH 재접속 필요.

## 배포 파일 배치

서버의 배포 디렉터리에 다음 파일과 디렉터리 배치:

```text
/opt/maple-utility
|-- .env
|-- docker-compose.prod.yml
`-- infra
    `-- nginx
        `-- nginx.conf
```

## 환경 변수

`.env.example`을 기준으로 서버에 `.env` 작성.

필수 값:

- `APP_IMAGE`
- `DB_PASSWORD`
- `JWT_SECRET`
- `KAKAO_CLIENT_SECRET`
- `NEXON_CLIENT_SECRET`
- `NEXON_API_KEY_SECRET`

시크릿 값은 빈 값 또는 기본값 사용 금지.

## 이미지 배포

```bash
cd /opt/maple-utility
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

## 상태 확인

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
docker compose -f docker-compose.prod.yml --env-file .env logs -f app
curl -I http://localhost/health
curl http://localhost/actuator/health
```

정상 기준:

- `postgres`, `redis`, `app`, `nginx` 모두 healthy
- `http://localhost/health` 200 응답
- `app` 로그에 Spring Boot 기동 완료 기록

## 롤백

이전 이미지 태그로 `.env`의 `APP_IMAGE` 수정 후 재기동:

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull app
docker compose -f docker-compose.prod.yml --env-file .env up -d app
docker compose -f docker-compose.prod.yml --env-file .env logs -f app
```

## 운영 점검

```bash
docker system df
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=200 nginx
sudo ufw status verbose
```

디스크 사용량 증가 시 미사용 이미지 정리:

```bash
docker image prune
```
