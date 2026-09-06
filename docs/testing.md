# 테스트와 검증

이 문서는 로컬 테스트와 GitHub Actions가 실제로 실행하는 범위를 구분하고, 기존 성능·동시성 실험 결과를 재현할 때 필요한 조건을 설명합니다.

## Gradle 테스트 분리

| Gradle task | 포함 범위 | 필요한 인프라 | 기본 실행 여부 |
|---|---|---|---|
| `test`, `unitTest` | 도메인, 서비스, MVC slice, JWT 단위 테스트 | 없음 | 기본 |
| `verifyChange` | `unitTest` + `bootJar` | 없음 | CI |
| `integrationTest` | Spring Context, 캐시, 동시성, 조회 통합 테스트 | Docker | 별도 |
| `stressTest` | 대량 데이터와 깊은 페이지 실험 | local 프로필 MariaDB | 명시적 승인 필요 |

`test`와 `unitTest`에서는 다음 항목을 제외합니다.

- `integration/**`
- `stressTest/**`
- `EshopRefactApplicationTests`
- `OrderIdempotencyTest`

`ProductDeepPaginationTest`는 개발자 MariaDB에 많은 데이터를 만들 수 있어 `integrationTest`에서도 제외하고 `stressTest`에 포함합니다.

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

### 대량 데이터 실험

이 작업은 로컬 MariaDB 데이터를 변경할 수 있습니다. 데이터 생성과 장시간 실행을 인지하고 승인한 경우에만 실행합니다.

```bash
./gradlew stressTest -PallowStressTest
```

## 테스트가 증명하는 것

| 테스트 | 검증 대상 |
|---|---|
| `OrderConcurrencyIntegrationTest` | 동시 주문 시 성공/실패 수와 최종 재고, DB 락과 Redis 락 비교 |
| `OrderAvailabilityIntegrationTest` | 주문 경합 중 조회 요청의 생존 여부 |
| `OrderIdempotencyTest` | 동일 사용자·동일 키 재요청이 기존 주문 응답을 반환하는지 |
| `OrderQueryIntegrationTest` | 주문 목록 결과와 batch fetch, fetch join 메모리 페이징 비교 |
| `ProductCacheIntegrationTest` | Cache Miss → Put → AFTER_COMMIT Evict → 최신 값 재조회 |
| `ProductRepositoryIntegrationTest` | QueryDSL 조건 검색과 No-Offset 커서 경계 |
| `ProductDeepPaginationTest` | Offset 깊이에 따른 비용과 No-Offset 비교 |

## 기존 실험 결과

아래 값은 저장소 테스트를 개발 환경에서 실행했을 때 기록한 사례입니다. 고정된 CI benchmark가 아니므로 하드웨어, 데이터 분포, JVM warm-up과 실행 시점에 따라 절대값이 달라질 수 있습니다.

### 재고 정합성

- 초기 재고: 40개
- 동시 주문: 45건
- 성공: 40건
- 실패: 5건
- 최종 재고: 0개

핵심 판정은 처리 시간보다 성공 수와 최종 재고가 초기 재고를 위반하지 않는지입니다.

### 주문 경합 중 조회 가용성

| 방식 | 관찰된 총 소요 시간 | 조회 성공 | 조회 실패 |
|---|---:|---:|---:|
| DB 비관적 락 | 418ms | 0 | 20 |
| Redis 분산 락 | 8,766ms | 20 | 0 |

테스트는 AOP로 트랜잭션 내부 지연을 주입해 커넥션 풀이 작은 상황을 재현합니다. 이 결과는 Redis 방식이 무조건 빠르다는 뜻이 아니라, 락 대기를 DB 밖으로 옮겼을 때 조회 커넥션을 보존할 수 있음을 보여줍니다.

### 락 비용 비교 사례

| 비교 | DB 비관적 락 | Redis 분산 락 |
|---|---:|---:|
| 순수 락 오버헤드 | 199ms | 432ms |
| 전체 주문 흐름 | 67ms | 456ms |

서로 수행 범위가 완전히 같은 운영 benchmark는 아닙니다. 성능 결론은 동일 workload와 MariaDB 실행 계획을 수집하는 [#17](https://github.com/juhwanz/eshop-refac/issues/17), [#18](https://github.com/juhwanz/eshop-refac/issues/18), [#25](https://github.com/juhwanz/eshop-refac/issues/25)에서 보강할 예정입니다.

### 깊은 페이지 조회 사례

| 조회 | 관찰 시간 |
|---|---:|
| Offset 첫 페이지 | 5ms |
| Offset 40만 번째 구간 | 36ms |
| Offset 40만 건 스캔 비교 | 33ms |
| No-Offset 인덱스 비교 | 25ms |

Offset 검색은 전체 개수를 위한 count query를 사용할 수 있고, No-Offset 검색은 `id < lastProductId`와 `Slice`로 count query 없이 다음 구간을 조회합니다.

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
