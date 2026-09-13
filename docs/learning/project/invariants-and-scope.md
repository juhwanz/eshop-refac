# 프로젝트 범위와 비즈니스 불변조건

- 분류: 프로젝트
- 영역: 도메인·포트폴리오
- 관련 역량 ID: P01
- 관련 세션: [`../sessions/2026-09-10-project-invariants-and-scope.md`](../sessions/2026-09-10-project-invariants-and-scope.md)

## 학습 목표

- 기능 목록보다 먼저 시스템이 반드시 지켜야 하는 조건을 설명한다.
- 현재 구현 범위와 결제·정산·실제 운영처럼 구현하지 않은 범위를 구분한다.
- 테스트가 증명한 사실, 특정 환경의 실험과 추론을 구분한다.

## 먼저 답할 진단 질문

> E-Shop에서 반드시 지켜야 하는 비즈니스 불변조건을 다섯 가지 말하고, 각 조건이 깨지면 어떤 문제가 발생하는지 설명해주세요.

## 확인할 문서

- `[DOC]` [`../../architecture.md`](../../architecture.md)
- `[DOC]` [`../../order-idempotency.md`](../../order-idempotency.md)
- `[DOC]` [`../../stock-protection.md`](../../stock-protection.md)
- `[DOC]` [`../../testing.md`](../../testing.md)
- `[ADR]` [`../../adr/README.md`](../../adr/README.md)

## 30초 답변

E-Shop은 고트래픽 주문 상황에서 재고 초과 판매와 중복 주문을 방지하는 데 집중한 Spring Boot 기반 이커머스 백엔드 PoC다. 동시 주문에도 재고가 음수가 되거나 실제 수량보다 많이 판매되면 안 되고, 동일한 주문 요청은 한 번만 처리되어야 하며, 주문 소유자만 취소할 수 있어야 한다. 실제 운영 환경과 결제 기능은 구현하지 않았으므로 운영 서비스나 결제 시스템으로 소개하지 않는다.

## 핵심 불변조건

- 재고: 동시 주문에서도 재고는 음수가 될 수 없고 실제 재고보다 많이 판매해서는 안 된다. `[CODE]` `Product.removeStock()`과 DB CHECK가 음수 재고를 방어하고, 상품별 Redisson 락이 지원 주문 경로의 차감과 취소를 직렬화한다.
- 멱등성: 같은 사용자·키·payload의 재요청은 새 주문을 만들지 않고 기존 `orderId`를 반환하며, 같은 키의 다른 payload는 거절한다. `[CODE]` Redis는 최적화 계층이고 `(user_id, idempotency_key)` DB 유일 제약이 최종 방어선이다.
- 실패 후 재시도: 실패한 요청은 자신이 소유한 Redis 처리 토큰만 제거해야 한다. 현재 코드는 다른 요청의 `PROCESSING` 토큰을 삭제하지 않으며, 선점 실패만으로 요청을 거절하지 않고 DB 확인과 상품 락 경로를 계속한다. `[CODE]`
- 취소와 소유권: 주문 소유자만 취소할 수 있고, 같은 주문을 중복 취소해 재고를 두 번 복구해서는 안 된다. `[CODE]`
- 캐시: 상품 데이터 변경에 따른 캐시 삭제는 DB 커밋 이후에 수행해야 한다. `[CODE]` `ProductCacheEvictEvent`는 `AFTER_COMMIT`에서 처리된다.
- 대기열: 활성 권한은 사용자와 상품 범위가 일치해야 하며, 주문 경로는 자신이 책임지는 사용자의 해당 상품 권한만 정리해야 한다. `[CODE]`

## 구현 범위와 비범위

| 구분 | 내용 | 근거 |
|---|---|---|
| 구현 | 사용자·상품·주문 API, 재고 보호, 주문 멱등성, 주문 취소 소유권, 상품별 admission queue, 상품 캐시와 조회 전략, JWT 인증 | `[DOC]` [`../../../README.md`](../../../README.md), [`../../architecture.md`](../../architecture.md) |
| 검증 범위 | 단위·슬라이스 테스트와 Testcontainers 통합·동시성 테스트, 특정 로컬 환경의 반복 부하 실험 | `[DOC]` [`../../testing.md`](../../testing.md), [`../../load-testing.md`](../../load-testing.md) |
| 비범위 | 결제·정산·배송, 실제 운영 배포 경로, 운영 데이터 기반 용량·가용성·SLO 보장 | `[DOC]` [`../../../README.md`](../../../README.md) |

## 코드와 결정 근거

- `[CODE]` [`Product`](../../../src/main/java/com/project/eshop_refact/domain/product/Product.java): 도메인 재고 검증과 DB CHECK 선언
- `[CODE]` [`OrderIdempotencyService`](../../../src/main/java/com/project/eshop_refact/domain/order/OrderIdempotencyService.java): 사용자 범위 키, Redis 선점·정리와 DB 복구 경로
- `[CODE]` [`OrderService`](../../../src/main/java/com/project/eshop_refact/domain/order/OrderService.java): 주문 트랜잭션, 소유권 검증과 취소
- `[CODE]` [`ProductCacheEventListener`](../../../src/main/java/com/project/eshop_refact/domain/product/ProductCacheEventListener.java): 커밋 이후 캐시 삭제
- `[ADR]` [`ADR-0004`](../../adr/0004-protect-stock-with-watchdog-and-check.md), [`ADR-0005`](../../adr/0005-persist-order-idempotency-in-database.md), [`ADR-0007`](../../adr/0007-use-product-scoped-redis-admission-queue.md)

## 자주 틀리는 부분

- `정합성`, `동시성`, `고가용성`은 그대로는 검증 가능한 비즈니스 불변조건이 아니다. 재고 하한, 생성되는 주문 수, 취소 권한처럼 참과 거짓을 판정할 수 있는 문장으로 바꾼다.
- JWT, 트랜잭션, Redisson과 `AFTER_COMMIT` listener는 불변조건이 아니라 조건을 지키기 위한 구현 수단이다.
- dirty checking은 JPA가 관리 엔티티의 변경을 감지하는 기능이며, 정합성이 깨졌을 때 발생하는 현상이 아니다.
- 재고 0은 정상적인 품절 상태다. 금지되는 값은 0이 아니라 음수다.
- DB CHECK는 음수 저장을 막지만 갱신 유실에 의한 초과 판매까지 완전히 막지는 못한다.
- 로컬 부하 실험과 테스트 통과를 운영 용량, 고가용성 또는 SLO 보장으로 확대하지 않는다.
- `[TODO]` [`../../architecture.md`](../../architecture.md)의 `PROCESSING → 중복 처리 오류` 상태도는 선점 실패 후에도 경로를 계속하는 현재 코드·[`../../order-idempotency.md`](../../order-idempotency.md)와 다르므로 후속 학습에서 사실 관계를 다시 확인한다.

## 예상 후속 질문

1. 이 프로젝트를 결제 시스템이라고 소개할 수 있나요?
2. DB CHECK가 있다면 분산 락은 필요 없나요?
3. 테스트 통과가 운영 정합성을 보장하나요?

## 2분 답변

`[TODO]` 다음 복습에서 문제 → 불변조건 → 구현 수단 → 검증 근거 → 비범위와 한계 순서로 직접 답한다.

## 남은 질문

- `[TODO]` DB CHECK가 있어도 Redisson 분산 락이 필요한 이유는 무엇인가?
- `[TODO]` 상품별 대기열 권한과 정리 책임은 어떤 불변조건을 지키는가?
- `[TODO]` 현재 구현이 보장하는 사실을 코드·테스트·실험 근거로 어떻게 구분할 것인가?
