# AI 퍼스널 뷰티 큐레이션 플랫폼

> 요즘 뜨는 그 제품, 내 피부에도 맞을까?

지금 관심이 빠르게 늘고 있는 제품과 성분을 보여주고, 그것이 내 피부 타입과 고민에 맞는지, 왜 맞는지까지 알려주는 2030 여성을 위한 트렌드 × 개인화 뷰티 큐레이션 서비스입니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4 |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, Maven |
| Database | PostgreSQL 17 (로컬: Docker, 운영: Neon) |
| Test | JUnit 5, Testcontainers |

기술 선택 이유는 [`docs/decisions`](docs/decisions)의 ADR 문서에 정리했습니다.

## 프로젝트 구조

```
.
├── frontend/            # Next.js 앱
├── backend/             # Spring Boot API
├── docs/decisions/      # 기술 의사결정 기록(ADR)
└── docker-compose.yml   # 로컬 PostgreSQL
```

## 로컬 실행 방법

### 사전 준비
- Node.js 20 이상
- JDK 21 (`JAVA_HOME`이 JDK 21을 가리켜야 합니다)
- Docker Desktop

### 포트
| 구성 요소 | 포트 |
|---|---|
| Frontend | 3100 |
| Backend | 8090 |
| PostgreSQL (Docker) | 5433 |

### 1. 데이터베이스 실행
```bash
docker compose up -d
```

### 2. 백엔드 실행
```bash
cd backend
# Windows PowerShell: $env:SPRING_PROFILES_ACTIVE="local"; .\mvnw.cmd spring-boot:run
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```
- 헬스 체크: http://localhost:8090/actuator/health
- 실행 시 Flyway가 `src/main/resources/db/migration`의 마이그레이션을 자동으로 적용합니다.

### 3. 프론트엔드 실행
```bash
cd frontend
cp .env.example .env.local   # 처음 한 번
npm install
npm run dev
```
- http://localhost:3100 에서 "백엔드 API 상태: 정상 연결"이 보이면 연결 완료입니다.

## 테스트

```bash
# 백엔드 (Docker 실행 필요: Testcontainers가 테스트용 PostgreSQL을 띄웁니다)
cd backend && ./mvnw test

# 프론트엔드
cd frontend && npm run lint && npm run build
```

## 환경 변수

| 변수 | 위치 | 설명 | 기본값 (로컬) |
|---|---|---|---|
| `DB_URL` | backend | JDBC 연결 주소 | `jdbc:postgresql://localhost:5433/aibeauty` |
| `DB_USERNAME` / `DB_PASSWORD` | backend | DB 계정 | `aibeauty` / `aibeauty` |
| `PORT` | backend | 서버 포트 | `8090` |
| `CORS_ALLOWED_ORIGINS` | backend | 허용할 프론트엔드 주소 (쉼표 구분) | `http://localhost:3100` |
| `SPRING_PROFILES_ACTIVE` | backend | 실행 프로필 | `local` |
| `API_BASE_URL` | frontend | 서버 컴포넌트가 호출할 백엔드 주소 | `http://localhost:8090` |

운영 환경의 비밀 정보는 코드 저장소에 넣지 않고 배포 플랫폼의 환경 변수로 설정합니다.
