# 재고 락과 DB 제약 적용

주문과 취소는 상품별 Redis 락을 획득한 뒤 DB 트랜잭션을 실행한다. 고정 lease 없이 Redisson watchdog으로 락을 갱신하며, 서비스의 커밋 또는 롤백 이후 소유 스레드가 해제한다. 획득 대기 시간은 `app.order.lock.wait-time`으로 설정한다. 기존 `lease-time` 설정은 제거했다.

watchdog은 Redis 통신 단절이나 긴 JVM 정지에서 완전한 상호 배제를 보장하지 않는다. `stock_quantity >= 0` CHECK는 음수 저장을 차단하지만 갱신 유실이나 모든 장애 상황의 초과 판매를 차단하는 제약은 아니다.

## 기존 MariaDB 테이블

새 테이블에는 Hibernate의 Product 매핑으로 `chk_products_stock_nonnegative` CHECK를 생성한다. 기존 테이블에는 `ddl-auto: update`만으로 CHECK가 추가되지 않으므로 아래 절차로 별도 적용한다. 애플리케이션 기동 시 자동 SQL 실행이나 데이터 초기화는 하지 않는다.

쓰기 요청을 중지한 유지보수 시점에 대상 데이터베이스를 확인하고 다음 조회를 실행한다.

```sql
SELECT DATABASE();
SELECT id, stock_quantity FROM products WHERE stock_quantity < 0;
SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME = 'products';
```

음수 행이 있으면 원인을 조사하고 데이터 보정 방향을 결정한다. 임의로 0으로 바꾸지 않는다. 같은 역할의 제약이 이미 있다면 중복 추가하지 않는다. 음수 행과 기존 제약이 없는 경우에만 다음 SQL을 적용하고 위 조회로 결과를 확인한다.

```sql
ALTER TABLE products
  ADD CONSTRAINT chk_products_stock_nonnegative
  CHECK (stock_quantity >= 0);
```

DDL은 테이블 잠금을 수반할 수 있으며 일반 트랜잭션 롤백으로 취소한다고 가정하지 않는다. 실제 로컬·운영 DB에 이 절차를 실행하려면 해당 DB 변경 승인이 필요하다.

## 회귀 검증

`StockProtectionIntegrationTest`는 Testcontainers MariaDB·Redis에서 긴 트랜잭션의 watchdog 갱신, 락 대기자의 커넥션 비점유, 최종 주문·재고와 활성 권한 정리, CHECK 생성·음수 쓰기 차단을 검증한다. 기존 테이블의 CHECK를 제거한 격리된 테스트에서 Hibernate update가 제약을 추가하지 않는 것도 확인하고 명시적 DDL로 복원한다.

```bash
./gradlew test --tests '*RedissonLockStockFacadeTest'
./gradlew integrationTest --tests '*StockProtectionIntegrationTest'
```
