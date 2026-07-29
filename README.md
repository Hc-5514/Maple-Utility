# Maple Utility

메이플스토리 일일/주간/보스 컨텐츠 스케줄러 및 사냥 기록 관리 서비스.

## 주요 기능

- **스케줄러 대시보드**: 다수 캐릭터의 일일·주간·보스 컨텐츠 진행률 한눈에 파악
- **보스 드랍 기록**: 보스별 드랍 아이템 획득 이력 관리
- **사냥 기록**: 날짜·캐릭터별 메소/솔 에르다/시간 기록 및 통계
- **이중 로그인**: 카카오 OAuth2 + Nexon API Key 직접 인증
- **데이터 자동 백업**: 매일/매주 PostgreSQL 백업 → Cloudflare R2

## 기술 스택

| 영역 | 스택 |
|------|------|
| Frontend | React 18 + Vite 6 + TypeScript 5 + TailwindCSS 4 + Zustand 5 + TanStack Query 5 |
| Backend | Java 21 + Spring Boot 4 + Spring Security 7 + JPA + Querydsl 5 |
| Database | PostgreSQL 16 + Redis 7 |
| Infra | AWS EC2 t3.micro + Docker Compose + Nginx + Let's Encrypt |
| CI/CD | GitHub Actions → GHCR → SSH deploy |
| Frontend Hosting | Vercel |
| Backup | Cloudflare R2 (rclone) |

## 프로젝트 구조

```
Maple-Utility/
├── frontend/          # React SPA (Vite)
├── backend/           # Spring Boot API
├── infra/             # Docker Compose, Nginx, 배포 스크립트
├── .github/workflows/ # CI/CD 파이프라인
└── docs/              # 기획서, CHANGELOG, 배포 가이드
```

## 로컬 개발 환경

### Frontend (MSW Mock 모드)

```bash
cd frontend
npm install
VITE_USE_MOCK=true npm run dev
```

### Backend

```bash
cd infra
docker compose up -d postgres redis   # DB + Redis 먼저 기동
cd ../backend
./gradlew bootRun
```

환경변수는 `.env.example` 참고.

## 배포 구조

```
GitHub Actions
  └─ (main push) → Gradle Build → Docker image → GHCR push
                 → SSH → EC2: docker compose pull & up

Vercel
  └─ (main push) → Frontend 자동 배포
```

EC2 배포 상세는 `docs/aws-ec2-deploy.md` 참고.

## 문서

- [`docs/MVP_기획설계서.md`](docs/MVP_기획설계서.md) — 전체 설계서 (API, DB 스키마, 아키텍처)
- [`docs/CHANGELOG.md`](docs/CHANGELOG.md) — 주요 변경 이력
- [`docs/aws-ec2-deploy.md`](docs/aws-ec2-deploy.md) — EC2 배포 운영 가이드
