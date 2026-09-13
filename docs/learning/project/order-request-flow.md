# 주문 요청 전체 흐름

- 분류: 프로젝트
- 영역: 주문·트랜잭션·동시성·멱등성·대기열
- 관련 역량 ID: P02
- 관련 세션: -

## 학습 목표

- 인증된 주문 요청이 들어온 뒤 응답이 반환될 때까지의 순서를 설명한다.
- Redis와 MariaDB가 각각 담당하는 상태를 구분한다.
- 락, 대기열 검사와 DB 트랜잭션의 순서가 바뀌었을 때의 문제를 설명한다.
- 성공, 중복, 재고 부족, 락 획득 실패와 Redis 캐시 실패의 정리 책임을 설명한다.

## 먼저 답할 진단 질문

> `POST /api/orders` 요청이 들어오면 어떤 컴포넌트를 어떤 순서로 지나며, 각 단계가 지키려는 조건은 무엇인가요?

## 확인할 코드와 문서

- `[CODE]` `domain/order/OrderController.java`
- `[CODE]` `domain/order/OrderIdempotencyService.java`
- `[CODE]` `domain/order/RedissonLockStockFacade.java`
- `[CODE]` `domain/order/OrderService.java`
- `[CODE]` `domain/queue/WaitingQueueService.java`
- `[TEST]` `integration/OrderDurableIdempotencyIntegrationTest.java`
- `[TEST]` `integration/OrderConcurrencyIntegrationTest.java`
- `[DOC]` [`../../architecture.md`](../../architecture.md)
- `[DOC]` [`../../order-idempotency.md`](../../order-idempotency.md)

## 30초 답변

첫 학습 세션에서 사용자 답변을 교정한 뒤 작성한다.

## 동작 과정과 원리

첫 학습 세션에서 작성한다.

## 순서를 바꿨을 때의 실패

| 변경 | 발생할 수 있는 문제 |
|---|---|
|  |  |

## 보장 범위와 한계

- `[TODO]` 첫 학습 세션에서 작성한다.

## 예상 후속 질문

1. 대기열 검사를 분산 락 획득 뒤에 수행하는 이유는 무엇인가요?
2. Redis 완료 캐시 저장이 실패하면 주문 결과는 어떻게 복구하나요?
3. 락 획득에 실패했을 때 대기열 권한은 왜 유지하나요?

## 2분 답변

첫 학습 세션에서 작성한다.
