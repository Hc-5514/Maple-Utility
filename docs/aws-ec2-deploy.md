# AWS EC2 Backend 배포

## 개요

- 대상: AWS EC2 Free Tier t2.micro Ubuntu VM
- 리소스: 1 vCPU, 1GB RAM, 30GB EBS, 12개월 한정 무료
- 런타임: Docker Compose 기반 Spring Boot, PostgreSQL, Redis, Nginx
- 외부 노출: 80, 443
- 내부 통신: app, postgres, redis, nginx 전용 Docker network
- 시크릿 관리: 서버의 `.env` 파일 전용, 저장소 커밋 금지

## EC2 생성 기준

- AMI: Ubuntu Server 24.04 LTS 또는 22.04 LTS
- Instance type: t2.micro
- Storage: 30GB gp3 EBS
- Key pair: SSH 접속용 key pair 생성 또는 기존 public key 등록
- Network: public subnet 배치
- Public IP: 테스트는 auto-assign public IP 가능, 운영은 Elastic IP 연결 권장

## Security Group

EC2 Security Group inbound rule:

```text
TCP 22   SSH 접속
TCP 80   HTTP
TCP 443  HTTPS
```

22번 포트는 가능하면 본인 IP로 제한.

Outbound rule은 기본 전체 허용 유지.

## Elastic IP

운영 도메인 연결과 GitHub Actions SSH deploy를 위해 Elastic IP 연결 권장.

Elastic IP 생성 후 EC2 instance에 associate.

Elastic IP를 생성만 하고 연결하지 않으면 과금될 수 있으므로 미사용 IP는 release.

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

## t2.micro 운영 주의

t2.micro는 1GB RAM 제한이 있어 프론트엔드는 Vercel에 분리하고 EC2는 API, DB, Redis, Nginx만 담당.

메모리 부족이 발생하면 swap 추가, JVM 메모리 제한, 미사용 Docker 이미지 정리 검토.

```bash
docker system df
docker image prune
```

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
