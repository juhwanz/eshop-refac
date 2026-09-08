# 주문 멱등성

## API 계약

`POST /api/orders`는 인증 사용자와 `Idempotency-Key`를 하나의 요청 식별자로 사용한다.
키는 공백을 제외한 출력 가능한 ASCII(`!`부터 `~`) 1~128자이며 대소문자를 구분한다.
UUID 같은 임의 식별자를 권장한다. 키에 이메일, 전화번호, 토큰 등 개인정보나 비밀정보를 넣지 않는다.

같은 사용자·키·상품 ID·수량의 재요청은 기존 `orderId`와 HTTP 201을 반환한다.
같은 사용자·키에 다른 상품 ID 또는 수량은 `IDEMPOTENCY_CONFLICT`(409)다.
신규 주문은 기존 대기열 권한이 필요하지만 완료된 요청의 재조회에는 필요하지 않다.
락 대기 시간이 초과되면 기존 503 응답을 반환하며 같은 키로 재시도할 수 있다.

## 저장과 보존

- `orders.idempotency_key`: `VARBINARY(128)`. ASCII 원문 바이트를 저장하여 DB collation의 영향을 받지 않는다.
- `orders.request_fingerprint`: `VARCHAR(64)`. UTF-8 `v1:<productId>:<count>`의 SHA-256 소문자 hex다.
- `uk_orders_user_idempotency`: `(user_id, idempotency_key)` 유일 제약이다.
- 주문 ID가 영속 결과다. 주문과 키·fingerprint·재고 변경은 같은 트랜잭션에 속한다.
- 기존 주문과 내부 키 없는 주문 경로를 위해 새 컬럼은 nullable이다. 신규 HTTP 주문은 키를 필수 검증한다.
- DB 키에는 TTL이 없다. 주문을 보존하는 동안 키도 보존하고 주문 취소 후에도 기존 ID를 반환한다.
- 주문을 물리 삭제하면 DB 멱등성 보장도 끝난다. 현재 API에는 주문 물리 삭제 기능이 없다. 추후 삭제 기능에는 캐시 정리와 보존 정책을 함께 설계해야 한다.
- 요청 JSON과 사용자 개인정보를 별도로 복제하지 않는다. fingerprint는 암호화나 익명화를 보장하지 않으며, 사용자 ID와 키는 주문 데이터와 같은 접근 통제를 적용한다.

## 처리 경계와 장애

Redis 완료 응답에는 버전 1, fingerprint, orderId를 저장하고 TTL을 24시간으로 둔다.
구버전 응답, 손상된 값, cache miss, Redis 읽기 실패는 DB로 확인한다.
기존 Redis 응답만 있고 DB에 요청 키가 없는 과거 주문은 소급 복구할 수 없다.
적용 이전 키는 새 주문 요청에 재사용하지 않는다.

Redis `SET NX` 처리 표시는 소유 토큰과 3분 TTL을 갖는다. 선점 실패나 기존 처리 표시만으로
요청을 거절하지 않고 DB 확인·상품 락 경로를 이어간다. 자기 토큰과 일치할 때만 Lua로 삭제한다.
완료 결과가 기록된 후의 정리나 만료 후 다른 요청이 선점한 키를 삭제하지 않는다.

상품 락은 DB 쓰기 트랜잭션 전에 획득한다. 획득 후 기존 DB 결과를 재확인하고 신규 요청의
대기열 권한을 확인한다. 주문 트랜잭션에서도 재고 차감 전에 DB를 다시 확인한다.
서로 다른 상품의 동일 키 경합은 DB 유일 제약이 차단한다. 실패 트랜잭션이 롤백된 후
별도 조회 트랜잭션으로 기존 결과를 읽고 fingerprint를 비교한다. 다른 무결성 오류는 전파한다.

커밋 후 완료 캐시 저장·락 해제·대기열 정리 실패는 기록하고 주문 결과를 반환한다.
대기열 정리에 실패한 권한은 기존 600초 TTL로 만료된다.
Redis 전체 장애에서 신규 주문을 락 없이 실행하는 fallback은 제공하지 않는다.
재고 보호의 한계는 ADR-0004를 따른다. 별도 PROCESSING DB 상태, 워커, outbox는 없다.

## 스키마 적용 확인

ADR-0002에 따라 Hibernate `ddl-auto: update`를 유지한다. Testcontainers는 빈 MariaDB에
`create-drop`으로 생성된 컬럼과 유일 인덱스 및 실제 중복 INSERT 차단을 검증한다.
이 테스트가 기존 DB의 `update` 적용 성공까지 보장하지는 않는다.
기존 환경에서는 배포 기동 로그에 DDL 오류가 없는지 확인하고 다음 읽기 전용 SQL로 점검한다.

```sql
SHOW CREATE TABLE orders;
SHOW INDEX FROM orders WHERE Key_name = 'uk_orders_user_idempotency';
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
  AND COLUMN_NAME IN ('idempotency_key', 'request_fingerprint');
```

키는 varbinary 길이 128, fingerprint는 varchar 길이 64, 두 컬럼은 nullable이어야 한다.
유일 인덱스는 `Non_unique=0`, 컬럼 순서는 `user_id`, `idempotency_key`여야 한다.
제약이 누락되면 적용을 완료한 것으로 판단하지 않는다. 별도 승인 없이 기존 DB를 초기화하거나
데이터를 보정하지 않는다. 여러 인스턴스의 동시 schema 변경은 피한다.
