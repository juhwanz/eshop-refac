# k6 주문·조회 부하 테스트

`load-test.js`는 상품 단위 대기열을 거친 주문과 일반 상품 조회를 동시에 실행한다. 격리된 상품과 테스트 계정을 사용해야 하며, 실행할 때마다 주문과 재고가 변경된다.

## 사전 준비

1. 애플리케이션, MariaDB, Redis를 실행한다.
2. 주문 VU와 조회 VU 수 이상의 테스트 계정을 준비한다. 아래 스크립트는 기존 계정이면 로그인을 검증하고, 없는 계정이면 회원가입한 뒤 Git 비추적 파일을 만든다.
3. 직접 계정을 관리하려면 `load-test.users.example.json`을 복사해 `load-test.users.json`을 만들고 계정 정보를 입력한다.
4. 다른 주문이 발생하지 않는 테스트 상품의 ID를 확인한다.
5. 결과 디렉터리를 만든다.

```bash
mkdir -p load-test-results
```

준비 스크립트는 기본적으로 `load-user-01@example.com`부터 30명을 만들며 `USER_COUNT`, `USER_EMAIL_PREFIX`, `USER_EMAIL_DOMAIN`, `K6_USERS_FILE`로 변경할 수 있다. 비밀번호는 명령 이력 노출을 피하도록 터미널에서 `read -s`로 입력한 뒤 환경변수로 전달할 수 있다.

```bash
read -s USER_PASSWORD
export USER_PASSWORD
./scripts/prepare-load-test-users.sh
unset USER_PASSWORD
```

상품은 `ADMIN` 계정으로 준비한다. 비밀번호를 터미널에서 입력하면 스크립트가 로그인과 상품 생성을 수행하고 상품 ID만 출력한다.

```bash
export ADMIN_EMAIL='로컬-관리자-이메일'
read -s ADMIN_PASSWORD
export ADMIN_PASSWORD
PRODUCT_NAME=k6-baseline-1 PRODUCT_STOCK=1000 ./scripts/prepare-load-test-product.sh
unset ADMIN_PASSWORD
```

반복 baseline은 동일한 초기 재고를 가진 별도 상품을 실행별로 하나씩 사용하는 것이 가장 단순하다.

## Smoke 실행

```bash
PRODUCT_ID=1 K6_USERS_FILE=load-test.users.json \
ORDER_VUS=2 VIEW_VUS=1 DURATION=10s \
K6_PROFILE=baseline \
TEST_COMMIT_SHA="$(git rev-parse HEAD)" TEST_ENVIRONMENT=local \
K6_VERSION="$(k6 version | awk '{print $2}')" \
k6 run load-test.js
```

## Baseline 실행

동일한 상품 재고, 애플리케이션 commit, VU 수, duration, k6 버전으로 최소 3회 반복한다.

```bash
PRODUCT_ID=1 K6_USERS_FILE=load-test.users.json \
ORDER_VUS=20 VIEW_VUS=10 DURATION=30s \
K6_PROFILE=baseline \
TEST_COMMIT_SHA="$(git rev-parse HEAD)" TEST_ENVIRONMENT=local \
K6_VERSION="$(k6 version | awk '{print $2}')" \
k6 run load-test.js
```

`baseline` 프로필은 성능 threshold를 적용하지 않는다. 반복 결과를 비교한 뒤 정한 잠정 로컬 회귀 기준은 `regression` 프로필에 적용된다.

- 주문 p95: 2,300ms 미만
- 상품 조회 p95: 5,800ms 미만
- 실제 시스템 오류율: 4% 미만

기본값은 아래의 3회 측정 중 가장 나쁜 값에 약 25% 여유를 주고 읽기 쉬운 값으로 올림했다. 이 기준은 동일한 로컬 환경에서 눈에 띄는 회귀를 감지하기 위한 것이며 운영 SLO나 최대 처리 용량을 의미하지 않는다.

잠정 기준은 `regression` 프로필로 적용한다.

```bash
K6_PROFILE=regression k6 run load-test.js
```

필요하면 `ORDER_P95_THRESHOLD_MS`, `VIEW_P95_THRESHOLD_MS`, `SYSTEM_ERROR_RATE_THRESHOLD`로 회귀 기준을 명시적으로 덮어쓸 수 있다.

## 대표 baseline 결과

- 측정일: 2026-09-09
- 애플리케이션 commit: `732fbe6d8e4e1f466f25312d8ade81f8558af85a`
- 애플리케이션 이미지: `hongjuhwan/eshop-app@sha256:4fa3a68bfa1cdb9ce517625963abd159fc633be4bb952da282909df1348020d3`
- 실행 환경: macOS arm64 Docker Desktop 29.6.2, linux/amd64 애플리케이션 이미지, HikariCP 최대 10
- k6: 1.6.1
- 조건: 주문 VU 20, 조회 VU 10, 30초, 실행별 초기 재고 1,000개인 전용 상품

| 실행 | 성공 주문 | 주문 처리량/s | 주문 p95 | 조회 p95 | 실제 오류율 | 재고 정합성 |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 159 | 4.58 | 1,827.75ms | 4,075.98ms | 2.21% | 통과 |
| 2 | 161 | 4.54 | 1,439.75ms | 4,586.01ms | 2.99% | 통과 |
| 3 | 295 | 8.43 | 1,296.22ms | 4,146.96ms | 0.33% | 통과 |

세 실행 모두 queue timeout, 재고 부족, 멱등성 충돌 없이 초기 재고와 성공 주문 수에 맞는 최종 재고를 기록했다. 시스템 오류는 애플리케이션 로그상 HikariCP의 10개 연결이 모두 사용되어 약 1초 connection timeout이 발생한 결과였다. 처리량과 오류율 편차가 크므로 이 결과를 운영 용량이나 안정성 보장으로 해석하지 않는다.

잠정 threshold는 같은 조건의 별도 상품에서 최종 코드로 다시 검증했다. 주문 335건, 주문 p95 1,082.82ms, 조회 p95 2,699.10ms, 전체 시스템 오류율 0.53%였고 세 threshold와 재고 정합성이 모두 통과했다. 작업별 오류율은 대기열 0.75%, 주문 0%, 조회 0%로 기록됐다.

## 결과 해석

`load-test-results/summary.json`에는 실행 조건, 핵심 결과, 전체 metric, 예상 최종 재고, 실제 최종 재고, 재고 불변조건 결과가 저장된다. 토큰, 비밀번호, 이메일, 전체 오류 응답 본문은 결과에 기록하지 않는다.

전체 `actual_error_rate`와 함께 `queue_system_error_rate`, `order_system_error_rate`, `product_view_system_error_rate`를 확인한다. 전체 비율은 polling 요청 수에 영향을 받으므로 작업별 오류율을 함께 봐야 한다.

- 성공: HTTP 201
- 예상된 비성공: 대기, `OUT_OF_STOCK`, `IDEMPOTENCY_CONFLICT`
- 실제 오류: timeout, 예상 밖 4xx, 5xx, 네트워크 오류, 락 획득 실패

`invariantPassed`가 `true`이고 최종 재고가 음수가 아닌지 확인한다. 재고 검증은 외부 주문이 없는 격리 상품에서만 의미가 있다.

## 주의사항

- `load-test.users.json`과 `load-test-results/`는 추적하지 않는다.
- Smoke와 baseline 모두 실제 주문을 생성한다.
- 재고를 원상 복구하거나 별도 테스트 상품을 준비한 뒤 다음 반복을 실행한다.
- 운영 SLO, Grafana 대시보드, DB 락과 Redis 락 비교 benchmark는 이 테스트 범위에 포함하지 않는다.
- 반복 baseline과 threshold 관리 원칙은 [ADR-0008](adr/0008-manage-load-thresholds-from-repeatable-baselines.md)에 기록한다.
