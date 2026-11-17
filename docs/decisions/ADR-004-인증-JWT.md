# ADR-004. 인증 방식: JWT Access Token + Refresh Token

- 날짜: 2026-11-17
- 상태: 채택

## 문제 및 배경
회원가입·로그인·로그아웃·비밀번호 재설정(FR-AUTH-01~05)과 서버 측 권한 검증(NFR-SEC-04)이 필요하다. 프론트엔드(Next.js, Vercel)와 백엔드(Spring Boot, Render)가 서로 다른 곳에 배포되고, Next.js 서버 컴포넌트에서도 로그인 사용자 기준으로 API를 호출해야 한다(예: 궁합 배지가 붙은 트렌드 피드).

## 검토한 대안
| 대안 | 장점 | 단점 |
|---|---|---|
| 서버 세션 (Spring Session + DB) | 즉시 무효화가 쉬움, 구현이 단순 | 요청마다 세션 저장소 조회, 프론트·백엔드 도메인이 다르면 쿠키 공유 설정이 까다로움 |
| **JWT Access + Refresh Token** | API 서버가 상태 없이 토큰만으로 인증, 서버 컴포넌트·클라이언트 어디서든 같은 방식으로 호출 | Access Token은 만료 전까지 강제 무효화가 어려움, 토큰 보관·재발급 설계가 필요 |

## 선택한 방안
| 항목 | 결정 |
|---|---|
| Access Token | JWT (HS256), 유효 기간 15분, 클레임: `sub`(사용자 ID), `role`, `iat`, `exp` |
| Refresh Token | JWT가 아닌 무작위 문자열(256bit), 유효 기간 14일, DB에는 SHA-256 해시만 저장 |
| 재발급 | Refresh Token 회전: 재발급할 때마다 새 Refresh Token을 발급하고 이전 토큰은 사용 처리 |
| 탈취 대응 | 이미 사용된 Refresh Token이 다시 들어오면 같은 로그인 묶음(`family_id`)의 토큰을 모두 폐기 |
| 토큰 보관 | 두 토큰 모두 `HttpOnly; Secure; SameSite=Lax` 쿠키. 자바스크립트에서 토큰에 접근하지 못하게 해 XSS로 인한 탈취 위험을 줄임 |
| 도메인 구성 | 브라우저는 Next.js 도메인으로만 요청하고, Next.js가 `/api/**` 요청을 백엔드로 전달(rewrites). 쿠키가 같은 사이트(first-party) 쿠키가 되어, 브라우저의 서드파티 쿠키 차단 영향을 받지 않음 |
| 서버 컴포넌트 호출 | Next.js 서버가 요청 쿠키의 Access Token을 꺼내 `Authorization: Bearer` 헤더로 백엔드를 호출 |
| CSRF | `SameSite=Lax` 쿠키 + 상태 변경 요청(POST·PUT·PATCH·DELETE)에 커스텀 헤더 확인 |
| 구현 | Spring Security + OAuth2 Resource Server(JWT 검증), 서명 키는 환경 변수 `JWT_SECRET` |

## 요구사항별 처리
| 요구사항 | 처리 |
|---|---|
| 로그아웃 (NFR-SEC-03) | 해당 Refresh Token 폐기 + 쿠키 삭제 |
| 비밀번호 재설정 후 기존 로그인 해제 (FR-AUTH-05) | 해당 사용자의 Refresh Token을 모두 폐기 |
| 연속 5회 실패 시 로그인 제한 (FR-AUTH-02) | `users.failed_login_count`, `locked_until`로 처리 |
| 관리자 API (FR-ADM-01) | Access Token의 `role` 클레임으로 검사 |

## 감수하는 한계
- Access Token은 DB를 조회하지 않고 검증하므로, 로그아웃이나 비밀번호 재설정 후에도 이미 발급된 Access Token은 **최대 15분** 동안 유효하다. 유효 기간을 짧게 두고 Refresh Token을 즉시 폐기하는 것으로 위험을 제한한다.
- 즉시 차단이 꼭 필요해지면, 사용자별 토큰 버전을 캐시에 두고 검증 시 비교하는 방식을 추가한다.

## 재검토 조건
- 강제 로그아웃(관리자에 의한 즉시 차단 등)이 요구사항으로 추가될 때
- 소셜 로그인(FR-AUTH-06)을 도입해 외부 인증 서버와 연동이 필요해질 때
