# AWS EC2 Backend 배포

## 개요

- 대상: AWS EC2 Free Tier t3.micro Ubuntu VM
- 런타임: Docker Compose 기반 Spring Boot, PostgreSQL, Redis, Nginx, Certbot
- 외부 노출: 80, 443
- 내부 통신: app, postgres, redis, nginx 전용 Docker network
- 시크릿 관리: 서버의 `.env` 파일과 GitHub Actions Secrets 전용

## EC2 생성 기준

- AMI: Ubuntu Server 24.04 LTS 또는 22.04 LTS
- Instance type: t3.micro
- Storage: 30GB gp3 EBS
- Network: public subnet 배치
- Public IP: 운영 도메인 연결을 위해 Elastic IP 권장
- Security Group inbound: 22, 80, 443 허용

22번 포트는 가능하면 본인 IP 또는 GitHub Actions 배포 정책에 맞춘 범위로 제한.

## VM 초기 설정

```bash
sudo DEPLOY_DIR=/opt/maple-utility ./infra/scripts/aws-ec2-init.sh
```

초기 설정 항목:

- Docker Engine 설치
- Docker Compose plugin 설치
- Docker 서비스 자동 시작 설정
- 배포 디렉터리 `/opt/maple-utility` 생성
- UFW 방화벽 OpenSSH, 80/tcp, 443/tcp 허용

스크립트 실행 후 Docker 그룹 권한 반영을 위해 SSH 재접속 필요.

## 배포 파일 배치

GitHub Actions가 main push 시 다음 파일을 서버에 복사.

```text
/opt/maple-utility
|-- .env
|-- docker-compose.prod.yml
`-- infra
    |-- nginx
    |   |-- nginx.conf
    |   `-- templates
    |       `-- default.conf.template
    `-- scripts
        `-- init-letsencrypt.sh
```

서버의 `.env`는 사용자가 직접 생성하며 Actions가 덮어쓰지 않음.

## 서버 환경 변수

`.env.example`을 기준으로 서버 `/opt/maple-utility/.env` 작성.

필수 값:

- `APP_IMAGE`
- `DB_PASSWORD`
- `JWT_SECRET`
- `KAKAO_CLIENT_ID`
- `KAKAO_REDIRECT_URI`
- `KAKAO_CLIENT_SECRET`
- `APP_CORS_ALLOWED_ORIGINS`
- `NEXON_API_KEY_SECRET`
- `SERVER_NAME`
- `LETSENCRYPT_EMAIL`

예시:

```text
APP_IMAGE=ghcr.io/hc-5514/maple-utility:latest
APP_CORS_ALLOWED_ORIGINS=https://<프론트도메인>
SERVER_NAME=<백엔드도메인>
LETSENCRYPT_EMAIL=<인증서 알림 이메일>
```

시크릿 값은 빈 값 또는 기본값 사용 금지.

## GitHub Actions Secrets

Repository → Settings → Secrets and variables → Actions에 등록.

```text
EC2_HOST=<EC2 Elastic IP 또는 도메인>
EC2_USER=ubuntu
EC2_SSH_KEY=<EC2 private key 전체 내용>
DEPLOY_DIR=/opt/maple-utility
```

`EC2_SSH_KEY`는 `-----BEGIN`부터 `-----END`까지 줄바꿈을 유지해 등록.

## 최초 SSL 인증서 발급

도메인이 EC2 Elastic IP를 가리키고 80/443 inbound가 열린 뒤 실행.

```bash
cd /opt/maple-utility
./infra/scripts/init-letsencrypt.sh
```

처리 순서:

- 임시 self-signed 인증서 생성
- app, postgres, redis, nginx 기동
- 임시 인증서 제거
- Let’s Encrypt HTTP-01 인증서 발급
- Nginx reload

## 자동 배포

main 브랜치 push 시 GitHub Actions 실행.

처리 순서:

- Gradle test
- Gradle bootJar
- Docker image build
- GHCR push: `latest`, commit SHA
- EC2 SSH 접속
- 배포 파일 복사
- GHCR login
- `docker compose pull app`
- `docker compose up -d`

## 상태 확인

```bash
cd /opt/maple-utility
docker compose -f docker-compose.prod.yml --env-file .env ps
docker compose -f docker-compose.prod.yml --env-file .env logs -f app
docker compose -f docker-compose.prod.yml --env-file .env logs --tail=200 nginx
curl -I http://localhost/health
curl -I https://<백엔드도메인>/health
curl https://<백엔드도메인>/actuator/health
```

정상 기준:

- `postgres`, `redis`, `app`, `nginx`, `certbot` 기동 상태
- `http://localhost/health` 200 응답
- `https://<백엔드도메인>/health` 200 응답
- HTTPS 응답에 HSTS, X-Frame-Options, X-Content-Type-Options 헤더 포함

## 인증서 갱신

`certbot` 컨테이너가 12시간마다 `certbot renew` 실행.

`nginx` 컨테이너는 6시간마다 reload하여 갱신된 인증서를 반영.

수동 확인:

```bash
docker compose -f docker-compose.prod.yml --env-file .env run --rm certbot renew --dry-run
docker compose -f docker-compose.prod.yml --env-file .env exec nginx nginx -t
```

## 롤백

이전 이미지 태그로 app만 재배포.

```bash
cd /opt/maple-utility
APP_IMAGE=ghcr.io/hc-5514/maple-utility:<이전커밋SHA> \
  docker compose -f docker-compose.prod.yml --env-file .env pull app
APP_IMAGE=ghcr.io/hc-5514/maple-utility:<이전커밋SHA> \
  docker compose -f docker-compose.prod.yml --env-file .env up -d app
docker compose -f docker-compose.prod.yml --env-file .env logs -f app
```

## 운영 주의

- t3.micro는 1GB RAM 제한 존재
- 프론트엔드는 별도 정적 호스팅 권장
- 메모리 부족 시 swap 추가, JVM 메모리 제한, 미사용 Docker 이미지 정리 검토

```bash
docker system df
docker image prune
```
