# CHANGELOG

---

## 2026-07-23

### 인증 전략 변경 — 넥슨 OAuth2 제거

**배경**
넥슨 공개 OAuth2 개발자 포털이 존재하지 않아 `NEXON_CLIENT_SECRET` 발급 불가.

**변경 전**
- 카카오 소셜 로그인
- 넥슨 소셜 로그인 (OAuth2)

**변경 후**
- 카카오 소셜 로그인 (OAuth2)
- Nexon API Key 직접 로그인

**Nexon API Key 로그인 흐름**
```
유저 API Key 입력
→ Nexon character/list API로 유효성 검증
→ oauth_id = SHA256(apiKey) 로 users 테이블 조회/생성 (oauth_provider = NEXON_APIKEY)
→ API Key AES-256-GCM 암호화 후 api_keys 테이블 저장
→ JWT Access Token + Refresh Token 발급
```

**영향 파일**

| 파일 | 변경 내용 |
|---|---|
| `docs/MVP_기획설계서.md` | §1.3 MVP 범위, §5.2 users 테이블 oauth_provider, §6.1 로그인 플로우 전면 재작성, §7.2 API 테이블 (`/auth/nexon` → `/auth/nexon-apikey`), §10.1 인증/인가 항목 |
| `docs/GitHub_Issue_List.md` | FE-04 제목·내용 (넥슨 버튼 제거, API Key 입력 폼 추가), BE-04 제목·내용 (`/auth/nexon` 제거, `/auth/nexon-apikey` 추가) |
| `.env.example` | `NEXON_CLIENT_SECRET` 항목 제거 |
| Notion 배포/환경 설정 | `NEXON_CLIENT_SECRET` 토글 → "제거됨" 표시, Spring Boot 운영 배포 필수 값 목록에서 제거 |

**제거된 항목**
- `POST /api/v1/auth/nexon` (넥슨 OAuth2 인가코드 교환)
- `NEXON_CLIENT_SECRET` 환경변수
- `users.oauth_provider = NEXON` 값

**추가된 항목**
- `POST /api/v1/auth/nexon-apikey` (Nexon API Key 직접 인증)
- `users.oauth_provider = NEXON_APIKEY` 값
- `NEXON_API_KEY_SECRET` (API Key AES-256-GCM 암호화 키, 직접 생성)

---

## 2026-07-21

### 인프라 변경 — Oracle Cloud → AWS EC2

**배경**
Oracle Cloud Free Tier 계정 설정 실패로 대안 선택.

**변경 내용**

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| 서버 | Oracle Cloud Free Tier ARM VM | AWS EC2 t3.micro (2 vCPU, 1GB RAM) |
| 인스턴스 유형 | Oracle Ampere A1 | t3.micro (t2.micro 불가 리전의 Free Tier 대체) |
| 스토리지 | gp2 (기본값) | gp3 30GB (IOPS 30배, 20% 저렴) |
| 네트워크 방화벽 | OCI Security List / VCN | AWS Security Group |
| 고정 IP | Oracle Reserved IP | AWS Elastic IP |
| 백업 스토리지 | Oracle Object Storage | Cloudflare R2 (rclone) |
| DNS | freenom | DuckDNS / nip.io |
| 배포 SSH 키 파일 | `~/.ssh/maple-utility.key` | `~/.ssh/MAPLE_UTILITY_EC2_KEY.pem` |

**영향 파일**

| 파일 | 변경 내용 |
|---|---|
| `docs/MVP_기획설계서.md` | §2.1 기술 스택, §3.1 아키텍처 다이어그램, §9.1~§9.4 인프라 비교/선택, §11 백업, 로드맵 Phase 4, 부록 전반 |
| `docs/GitHub_Issue_List.md` | BE-13 (Oracle Cloud → AWS EC2), BE-14 (SSH deploy 대상), BE-15 (Oracle Object Storage → Cloudflare R2), t2.micro → t3.micro 전체 치환 |
| Notion 배포/환경 설정 | AWS EC2 초기 세팅 토글 신규 추가 (IAM, 보안 그룹, EC2 생성, Elastic IP, SSH 접속, Swap 설정 포함) |

**EC2 확인 정보**
- 인스턴스: `ubuntu@13.125.173.235` (ip-172-31-46-226)
- 리전: ap-northeast-2 (서울)
- RAM: 911MB / Disk: 29GB gp3
