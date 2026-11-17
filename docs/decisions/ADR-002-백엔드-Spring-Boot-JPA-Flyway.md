# ADR-002. 백엔드 구성: Spring Boot 4 + Spring Data JPA + Flyway

- 날짜: 2026-11-16
- 상태: 채택 (데이터 접근 기술은 ERD 확정 시 재확인)

## 문제 및 배경
API와 비즈니스 로직(궁합 점수 계산, 추천, 트렌드 집계)을 담당할 백엔드가 필요하다. 기획서에서는 데이터 접근 기술(MyBatis 또는 JPA)을 2주차에 확정하기로 했으며, 환경 구축 단계에서 기본값을 정한다.

## 선택한 방안
| 항목 | 선택 |
|---|---|
| 언어·런타임 | Java 21 (LTS) |
| 프레임워크 | Spring Boot 4.1 |
| 빌드 | Maven (Maven Wrapper 포함) |
| 데이터 접근 | Spring Data JPA (복잡한 집계는 네이티브 쿼리 또는 별도 조회 계층으로 보완) |
| 스키마 관리 | Flyway (`ddl-auto: validate`) |
| 테스트 DB | Testcontainers (PostgreSQL 17) |

## 선택 이유
- 실무에서 익숙한 Spring Boot·Maven을 사용해 기능 품질에 집중한다.
- 상품·성분·프로필 등 대부분의 기능은 단순 CRUD와 연관관계 조회라 JPA의 생산성이 높다. 추천·트렌드 집계처럼 복잡한 조회는 쿼리를 직접 작성해 보완한다.
- 스키마 변경을 Flyway 마이그레이션으로만 관리해, 빈 DB에서도 같은 스키마를 재현할 수 있다 (NFR-MNT-03). Hibernate의 자동 스키마 생성은 사용하지 않는다.
- 테스트는 Testcontainers로 실제 PostgreSQL을 띄워 실행해, 운영 DB와 다른 동작으로 인한 문제를 줄인다.

## 영향
- 로컬에서 테스트를 실행하려면 Docker가 실행 중이어야 한다.
- JAVA_HOME이 JDK 21을 가리켜야 빌드할 수 있다.

## 재검토 조건
- ERD 확정 후 집계·검색 쿼리 비중이 커서 JPA보다 SQL 중심 방식(MyBatis 등)이 명확히 유리할 때
