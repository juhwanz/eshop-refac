# 이미지 게시와 참고용 배포

현재 자동화 범위는 CI 검증과 Docker 이미지 게시까지입니다. 실제 운영 배포 환경은 없으며 이 문서의 Compose와 수동 배포 스크립트는 운영에서 검증된 배포 경로가 아닙니다.

## Docker 이미지

`Dockerfile`은 JDK 21 빌더와 JRE 21 런타임을 분리한 multi-stage 이미지입니다.

`.github/workflows/deploy.yml`은 `main` 대상 pull request와 `main` push에서 Gradle·Testcontainers 검증을 실행합니다. `main` push의 검증이 성공하면 Docker Hub에 다음 태그를 게시합니다.

- commit SHA 태그: 결과물을 만든 commit을 식별하는 기준
- `latest`: 편의를 위한 이동 가능한 보조 태그

별도 `.github/workflows/secret-scan.yml`은 모든 push와 pull request에서 동작하지만 현재 `deploy.yml`의 이미지 게시 job을 직접 차단하는 의존 관계는 아닙니다. 정확한 검증 단계는 [테스트와 검증](testing.md)을 참고하세요.

## Compose 파일

| 파일 | 용도 |
|---|---|
| `docker-compose.dev.yml` | 기본 로컬 실행에서 Spring Boot가 시작·중지하는 Redis |
| `docker-compose.yml` | MariaDB·Redis와 Docker Hub의 `latest` 애플리케이션 이미지를 함께 실행하는 참고용 전체 스택 |
| `docker-compose.prod.yml` | 외부 MariaDB, Redis와 blue/green 애플리케이션 컨테이너를 정의한 참고용 구성 |

`docker-compose.yml`은 현재 작업 트리의 소스를 빌드하지 않고 게시된 `latest` 이미지를 실행합니다. 따라서 [로컬 실행 가이드](getting-started.md)의 기본 개발 경로가 아닙니다.

## 수동 blue/green 스크립트

`deploy.sh`는 다음 순서의 참고용 흐름을 담고 있습니다.

1. 현재 실행 중인 blue 또는 green 컨테이너를 판별합니다.
2. 반대편 컨테이너의 최신 이미지를 받고 실행합니다.
3. 15초 동안 고정 대기합니다.
4. Nginx upstream 포트를 변경하고 reload합니다.
5. 이전 컨테이너를 중지하고 제거합니다.

고정 대기는 애플리케이션 readiness 검사가 아닙니다. 이 구성은 준비 상태 확인, 무중단 전환이나 실패 시 rollback을 보장하지 않습니다.

## 운영 프로필 요구사항

- `DB_PASSWORD`와 `JWT_SECRET_KEY`가 없거나 공백이면 애플리케이션 기동이 실패합니다.
- 실제 운영 DB에는 관리자 계정 대신 애플리케이션 전용 최소 권한 계정을 사용합니다.
- `local`, `prod` 프로필 모두 현재 Hibernate `ddl-auto: update`를 사용합니다. 실제 운영 데이터나 여러 환경의 schema version 관리가 필요해지면 migration 도구와 `validate` 전환을 재검토합니다.
- Actuator는 `/actuator/health`만 노출하며 상세 정보는 공개하지 않습니다.

자격 증명 주입과 운영 보안 원칙은 [자격 증명 관리와 유출 대응](security/credential-management.md)을 참고하세요.

## 관련 문서

- [로컬 실행 가이드](getting-started.md)
- [테스트와 검증](testing.md)
- [ADR-0002: MariaDB와 Hibernate schema 관리](adr/0002-use-mariadb-and-hibernate-schema-update.md)
- [ADR-0003: CI 검증 이후 이미지 게시](adr/0003-gate-image-publishing-on-ci-verification.md)
