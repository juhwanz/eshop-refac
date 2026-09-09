# 테스트와 검증

이 문서는 로컬 테스트와 GitHub Actions가 실제로 실행하는 범위를 구분하고, 기존 성능·동시성 실험 결과를 재현할 때 필요한 조건을 설명합니다.

## Gradle 테스트 분리

| Gradle task | 포함 범위 | 필요한 인프라 | 기본 실행 여부 |
|---|---|---|---|
| `test`, `unitTest` | 도메인, 서비스, MVC slice, JWT 단위 테스트 | 없음 | 기본 |
| `verifyChange` | `unitTest` + `bootJar` | 없음 | CI |
| `integrationTest` | Spring Context, 캐시, 동시성, 조회 통합 테스트 | Docker | 별도 |
| `stressTest` | 향후 로컬 대량 데이터 도구를 위한 opt-in task | local 프로필 MariaDB | 명시적 승인 필요 |

`test`와 `unitTest`에서는 다음 항목을 제외합니다.

- `integration/**`
- `stressTest/**`
- `EshopRefactApplicationTests`
- `OrderIdempotencyTest`

현재 `stressTest` 대상 테스트는 없습니다. 향후 로컬 데이터를 변경하는 도구를 추가할 때도 `-PallowStressTest` 승인 장치를 유지합니다.

## 권장 실행 순서

### 빠른 검증

```bash
./gradlew unitTest
```

실행 가능한 JAR까지 함께 확인하려면:

```bash
./gradlew verifyChange
```

### MariaDB·Redis 통합 테스트

```bash
./gradlew integrationTest
```

Testcontainers가 `mariadb:11.8.6`과 `redis:7.4.5-alpine`을 시작하고 동적 접속 정보를 주입합니다. 개발자 로컬 MariaDB·Redis는 사용하지 않으며 컨테이너는 Gradle 테스트 JVM 안에서 공유하고 실행이 끝나면 폐기합니다. Docker가 꺼져 있거나 이미지를 받을 수 없으면 컨테이너 시작 단계에서 인프라 오류로 실패합니다.

## 테스트가 증명하는 것

| 테스트 | 검증 대상 |
|---|---|
| `OrderConcurrencyIntegrationTest` | 동시 주문 시 성공/실패 수와 최종 재고 |
| `OrderAvailabilityIntegrationTest` | 제한된 커넥션 풀에서 락 대기 위치에 따른 조회 성공·실패 조건 |
| `OrderIdempotencyTest` | 동일 사용자·동일 키 재요청이 기존 주문 응답을 반환하는지 |
| `OrderQueryIntegrationTest` | 주문 목록의 연관 항목과 상품이 제한된 SQL 수로 batch fetch되는지 |
| `ProductCacheIntegrationTest` | Cache Miss → Put → AFTER_COMMIT Evict → 최신 값 재조회 |
| `ProductRepositoryIntegrationTest` | QueryDSL 조건 검색과 No-Offset 커서 경계 |

## 검증 사례와 성능 증거

아래 정합성·가용성 사례는 테스트가 판정하는 조건을 설명합니다. 경과 시간은 하드웨어, JVM 상태와 실행 시점에 따라 달라지고 합격 조건이 아니므로 성능 근거로 사용하지 않습니다. 반복 가능한 성능 자료는 실행 환경과 commit을 기록한 [#17](https://github.com/juhwanz/eshop-refac/issues/17)의 k6 결과만 사용합니다.

### 재고 정합성

- 초기 재고: 40개
- 동시 주문: 45건
- 성공: 40건
- 실패: 5건
- 최종 재고: 0개

핵심 판정은 처리 시간보다 성공 수와 최종 재고가 초기 재고를 위반하지 않는지입니다.

### 제한된 커넥션 풀의 조회 가용성

| 시나리오 | 테스트에서 확인하는 조건 |
|---|---|
| DB 비관적 락 트랜잭션이 5개 커넥션을 모두 점유 | 별도 조회 20건이 connection timeout으로 실패 |
| Redis 락 대기가 DB 트랜잭션 밖에서 발생 | 별도 조회 20건이 connection timeout 없이 완료 |

테스트는 두 경로의 처리 속도를 비교하지 않습니다. 의도적으로 작게 제한한 커넥션 풀에서 락 대기를 DB 트랜잭션 밖에 두는 경계가 조회용 커넥션을 남기는지만 확인합니다. 이 결과를 운영 가용성이나 DB 락과 Redis 락의 속도 우열로 해석하지 않습니다.

## GitHub Actions

### Secret Scan

`.github/workflows/secret-scan.yml`은 모든 push, pull request와 수동 실행에서 동작합니다.

- `repository-hygiene`: `mysql-data/`, `mariadb-data/`, binlog, 인증서와 private key 확장자의 Git 추적 차단
- `gitleaks`: 전체 Git 이력의 비밀정보 패턴 검사

### Build and Publish Image

`.github/workflows/deploy.yml`은 `main` 대상 PR과 `main` push에서 동작합니다.

```text
checkout
  → JDK 21
  → ./gradlew clean verifyChange
  → ./gradlew integrationTest
  → main push일 때만 Docker Hub 로그인
  → commit SHA와 latest 태그로 이미지 게시
```

검증과 게시 경계:

- `verifyChange`는 빠른 테스트와 실행 JAR를 검증하고, CI가 다음 단계에서 `integrationTest`를 별도로 실행합니다.
- 통합 테스트는 GitHub-hosted runner의 Docker에서 Testcontainers로 MariaDB와 Redis를 시작하므로 별도 service container를 사용하지 않습니다.
- 테스트가 실패하면 Gradle XML 결과와 HTML 보고서를 7일간 artifact로 보존합니다.
- 이미지 게시 job은 검증 job에 의존하며, 검증에 실패하거나 PR에서 실행될 때는 Docker Hub 자격 증명을 사용하지 않습니다.
- main push 이미지는 commit SHA로 추적하며 기존 사용자를 위해 `latest`도 함께 게시합니다.
- Docker 이미지 게시는 자동이지만 원격 서버의 `deploy.sh` 실행은 자동화되어 있지 않습니다.
- Testcontainers 기반 통합 테스트 도입은 [#16](https://github.com/juhwanz/eshop-refac/issues/16), CI 연결은 [#15](https://github.com/juhwanz/eshop-refac/issues/15)에서 추적합니다.

## 변경 완료 전 확인

```bash
git diff --check
git status --short
```

Java, Gradle 또는 설정 변경은 가까운 테스트부터 실행하고 위험도에 따라 `unitTest`, `verifyChange`, `integrationTest` 순으로 범위를 넓힙니다.
