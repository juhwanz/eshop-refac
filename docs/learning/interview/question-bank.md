# 면접 질문 은행

답을 미리 읽는 목록이 아니라 진단과 복습에 사용할 질문 풀이다. 답변과 피드백은 해당 주제 문서와 세션 기록에 남긴다. 필수 범위는 [`../COVERAGE.md`](../COVERAGE.md), 현재 상태는 [`../README.md`](../README.md)가 기준이다. 기본 세션에서는 이 목록 전체가 아니라 핵심 질문 3개와 필요한 압박 질문만 사용한다.

## 프로젝트 목적과 전체 흐름

1. E-Shop을 한 문장으로 소개해주세요.
2. 이 프로젝트에서 가장 중요한 사용자 문제와 시스템 문제는 무엇인가요?
3. 반드시 지켜야 하는 비즈니스 불변조건을 다섯 가지 설명해주세요.
4. `POST /api/orders`가 인증부터 응답까지 거치는 흐름을 설명해주세요.
5. MariaDB와 Redis가 각각 어떤 상태를 담당하나요?
6. Redis 상태와 DB 상태가 다르면 무엇을 최종 기준으로 삼나요?
7. 이 프로젝트가 결제·정산 시스템이 아닌 이유는 무엇인가요?
8. 현재 구현, 테스트된 사실, 실험 결과와 추론을 어떻게 구분하나요?
9. 프로젝트에서 가장 어려웠던 의사결정과 선택 기준은 무엇이었나요?
10. 다시 설계한다면 유지할 부분과 바꿀 부분은 무엇인가요?

## Java 기본기

1. 캡슐화, 상속, 다형성과 추상화를 설명해주세요.
2. E-Shop 엔티티에서 공개 setter 대신 도메인 메서드를 사용하는 이유는 무엇인가요?
3. 인터페이스와 추상 클래스는 어떤 기준으로 선택하나요?
4. 불변 객체의 장점과 비용은 무엇인가요?
5. `equals`와 `hashCode`의 계약은 무엇인가요?
6. HashMap에서 `equals/hashCode`가 잘못 구현되면 어떤 문제가 생기나요?
7. List, Set과 Map은 어떤 상황에서 선택하나요?
8. ArrayList와 LinkedList의 주요 연산 복잡도를 비교해주세요.
9. checked exception과 unchecked exception의 차이와 사용 기준은 무엇인가요?
10. 예외를 잡아서 삼키는 것이 왜 위험한가요?
11. `finally`에서 자원을 정리할 때 주의할 점은 무엇인가요?
12. thread pool을 사용하는 이유와 크기를 무작정 늘리면 안 되는 이유는 무엇인가요?

## JVM과 Java 동시성

1. JVM의 heap, thread stack과 metaspace는 무엇을 저장하나요?
2. 객체는 언제 GC 대상이 되나요?
3. stop-the-world가 애플리케이션 latency에 어떤 영향을 줄 수 있나요?
4. Java Memory Model이 필요한 이유는 무엇인가요?
5. 가시성, 원자성과 순서 보장의 차이는 무엇인가요?
6. `volatile`이 보장하는 것과 보장하지 않는 것은 무엇인가요?
7. `synchronized`와 atomic type의 차이는 무엇인가요?
8. 여러 연산을 하나의 atomic type으로 바꾸면 항상 thread-safe한가요?
9. `InterruptedException`을 잡은 뒤 interrupt 상태를 복원해야 하는 이유는 무엇인가요?
10. E-Shop 분산 락과 JVM 내부 동기화는 어떤 범위 차이가 있나요?

## Spring 기본기

1. IoC와 DI를 설명하고 두 개념의 관계를 말해주세요.
2. 생성자 주입을 필드 주입보다 선호하는 이유는 무엇인가요?
3. Spring Bean의 기본 scope와 생명주기를 설명해주세요.
4. singleton Bean이 자동으로 thread-safe한가요?
5. Spring AOP와 proxy는 어떤 방식으로 동작하나요?
6. JDK dynamic proxy와 CGLIB proxy의 기본 차이는 무엇인가요?
7. `@Transactional`이 붙은 메서드가 self-invocation에서 기대대로 동작하지 않을 수 있는 이유는 무엇인가요?
8. DispatcherServlet부터 controller 호출까지 MVC 요청 흐름을 설명해주세요.
9. filter, interceptor와 argument resolver는 어느 위치에서 무엇을 담당하나요?
10. validation 오류와 비즈니스 예외를 어떻게 구분해 응답하나요?
11. controller를 얇게 유지해야 하는 이유는 무엇인가요?
12. Spring Security filter chain과 MVC interceptor의 책임은 어떻게 다른가요?
13. `@SpringBootApplication`은 어떤 구성을 묶고 있나요?
14. component scan과 auto-configuration은 어떻게 다른가요?
15. profile과 외부 설정을 사용하는 이유는 무엇인가요?
16. 요청 DTO와 응답 DTO를 entity와 분리하는 이유는 무엇인가요?
17. global exception handler와 공통 오류 응답은 어떤 책임을 가지나요?

## 운영체제와 동시성

1. process와 thread의 차이를 설명해주세요.
2. context switching은 왜 발생하며 어떤 비용이 있나요?
3. concurrency와 parallelism은 어떻게 다른가요?
4. race condition과 critical section은 무엇인가요?
5. mutex, semaphore와 monitor를 비교해주세요.
6. deadlock이 발생하기 위한 네 가지 조건은 무엇인가요?
7. deadlock의 예방, 회피, 탐지와 복구는 어떻게 다른가요?
8. blocking과 non-blocking, synchronous와 asynchronous를 구분해주세요.
9. 가상 메모리와 page fault의 기본 원리를 설명해주세요.
10. thread 수가 증가하면 처리량도 계속 증가하나요?
11. connection pool은 thread pool과 어떤 공통점과 차이가 있나요?
12. 락의 범위를 크게 또는 작게 잡을 때의 trade-off는 무엇인가요?

## Spring 트랜잭션과 데이터베이스

1. 트랜잭션의 ACID를 설명해주세요.
2. 격리 수준이 해결하려는 문제는 무엇인가요?
3. dirty read, non-repeatable read와 phantom read를 설명해주세요.
4. MVCC는 읽기와 쓰기 경합을 어떻게 줄이나요?
5. `@Transactional(readOnly = true)`의 의미와 한계는 무엇인가요?
6. transaction propagation은 무엇이며 `REQUIRED`와 `REQUIRES_NEW`는 어떻게 다른가요?
7. Spring은 어떤 예외에서 기본적으로 rollback하나요?
8. JPA flush와 DB commit은 어떻게 다른가요?
9. DB 유일 제약 오류를 확인하기 위해 flush가 필요한 상황은 언제인가요?
10. rollback-only가 된 트랜잭션에서 결과 조회를 계속하면 왜 문제가 되나요?
11. 비관적 락과 낙관적 락은 어떤 충돌 상황에 적합한가요?
12. 조건부 `UPDATE ... WHERE stock >= ?` 방식과 분산 락을 비교해주세요.
13. DB deadlock은 어떻게 발생하며 애플리케이션은 어떻게 대응해야 하나요?
14. 트랜잭션을 오래 유지하면 connection pool에 어떤 영향을 주나요?
15. `SELECT` 문의 논리 실행 순서를 `FROM/JOIN`, `WHERE`, `GROUP BY`, `HAVING`, `SELECT`, `ORDER BY`, `LIMIT` 기준으로 설명해주세요.
16. `INNER JOIN`과 `LEFT JOIN`은 결과 집합과 `NULL` 처리에서 어떻게 다른가요?
17. `WHERE`와 `HAVING`은 적용 시점과 용도가 어떻게 다른가요?
18. JOIN과 서브쿼리를 선택할 때 가독성뿐 아니라 실행 계획에서 무엇을 확인해야 하나요?

## 재고 정합성과 분산 락

1. 일반적인 조회 후 재고 차감에서 lost update가 어떻게 발생하나요?
2. 상품 ID를 분산 락 key로 사용한 이유는 무엇인가요?
3. DB 비관적 락 대신 Redis 분산 락을 선택한 이유는 무엇인가요?
4. 분산 락을 DB 트랜잭션 밖에서 획득해야 하는 이유는 무엇인가요?
5. 락 대기 위치가 상품 조회의 connection timeout과 어떻게 연결되나요?
6. Redisson watchdog은 무엇이며 고정 lease와 어떤 차이가 있나요?
7. watchdog이면 재고 정합성이 완전히 보장되나요?
8. 락을 실제로 획득한 소유 스레드만 해제해야 하는 이유는 무엇인가요?
9. Redis 장애 시 신규 주문은 어떻게 되나요?
10. DB `CHECK (stock_quantity >= 0)`가 보장하는 것과 보장하지 않는 것은 무엇인가요?
11. fencing token은 어떤 실패를 다루기 위한 개념인가요?
12. 재고 40개에 동시 주문 45건 테스트에서 중요한 assertion은 무엇인가요?

## 주문 취소

1. 주문 취소 전에 사용자 소유권을 검증해야 하는 이유는 무엇인가요?
2. 소유권 검증은 controller와 service 중 어디에 두는 것이 적절한가요?
3. 중복 취소에서 재고가 반복 복구되지 않도록 어떻게 처리하나요?
4. 주문 row의 비관적 락과 상품별 Redis 락은 각각 무엇을 보호하나요?
5. 주문 생성과 취소가 동시에 실행되면 어떤 경합이 생길 수 있나요?
6. 취소 commit 이후 상품 캐시를 제거해야 하는 이유는 무엇인가요?
7. 이미 취소된 주문 요청을 성공으로 볼지 오류로 볼지는 어떤 API 결정인가요?

## HTTP와 주문 멱등성

1. HTTP method의 멱등성과 비즈니스 작업의 멱등성은 같은 의미인가요?
2. client timeout과 retry가 어떻게 중복 주문을 만들 수 있나요?
3. 멱등성 키를 사용자 범위로 구성한 이유는 무엇인가요?
4. `Idempotency-Key`에 길이와 문자 계약이 필요한 이유는 무엇인가요?
5. 같은 키에 다른 상품이나 수량이 들어오면 어떻게 처리하나요?
6. payload fingerprint는 무엇이며 원본 요청 전체를 저장하는 것과 어떤 차이가 있나요?
7. Redis `SET NX`만으로 주문 멱등성을 보장할 수 있나요?
8. Redis 처리 표시의 TTL은 왜 필요하나요?
9. 처리 표시를 소유 token과 일치할 때만 제거해야 하는 이유는 무엇인가요?
10. DB의 `UNIQUE (user_id, idempotency_key)`가 필요한 이유는 무엇인가요?
11. cache miss나 TTL 만료 후 기존 주문을 어떻게 찾나요?
12. DB commit 뒤 Redis 완료 캐시 저장이 실패하면 어떻게 되나요?
13. 같은 키의 동시 요청이 유일 제약에서 충돌하면 결과를 어떻게 복구하나요?
14. 취소된 주문에 같은 멱등성 키를 다시 보내면 어떻게 처리하나요?

## Redis와 상품별 대기열

1. Redis String, Set과 ZSet의 차이와 사용 조건은 무엇인가요?
2. waiting과 active 상태에 ZSet을 사용한 이유는 무엇인가요?
3. 등록 timestamp 대신 `INCR` sequence를 사용하는 이유는 무엇인가요?
4. Lua script가 제공하는 원자성은 어디까지인가요?
5. 대기 제거와 active 추가를 별도 명령으로 실행하면 어떤 문제가 생기나요?
6. 전역 대기열 대신 상품별 대기열로 변경한 이유는 무엇인가요?
7. 상품 A의 활성 권한으로 상품 B를 주문하면 왜 안 되나요?
8. active TTL은 동시 실행 슬롯인가요?
9. 락 획득 실패와 주문 시도 이후에 대기열 권한을 각각 어떻게 처리하나요?
10. ShedLock은 Redisson 상품 락과 무엇이 다른가요?
11. `queue:waiting-products` 인덱스가 필요한 이유는 무엇인가요?
12. Redis `KEYS` 명령을 운영 경로에서 피해야 하는 이유는 무엇인가요?
13. 현재 대기열 설계가 Redis Cluster에서 그대로 동작한다고 보장할 수 있나요?
14. 대기열이 실제 동시 실행 수를 정확히 제한하지 못하는 이유는 무엇인가요?

## JPA와 조회

1. JPA N+1 문제는 어떻게 발생하나요?
2. LAZY loading과 영속성 context의 관계를 설명해주세요.
3. collection fetch join과 pageable을 같이 사용하면 왜 위험한가요?
4. Hibernate의 메모리 페이징은 어떤 문제를 만들 수 있나요?
5. batch fetch는 연관 데이터를 어떤 SQL 형태로 조회하나요?
6. batch fetch size를 무작정 크게 잡으면 어떤 문제가 생길 수 있나요?
7. 쿼리 수만 줄면 조회 최적화가 완료됐다고 볼 수 있나요?
8. DTO projection과 entity 조회는 어떤 trade-off가 있나요?
9. QueryDSL을 사용하는 이유와 동적 쿼리의 주의점은 무엇인가요?
10. 영속성 context와 1차 cache는 무엇인가요?
11. entity의 transient, managed, detached, removed 상태를 설명해주세요.
12. dirty checking은 언제 변경을 감지하고 SQL을 실행하나요?

## 페이지 조회와 인덱스

1. 깊은 Offset pagination이 느려지는 이유는 무엇인가요?
2. `Page`와 `Slice`는 어떻게 다르나요?
3. `size + 1`건을 조회하는 이유는 무엇인가요?
4. PK 내림차순 cursor에서 다음 페이지 조건은 어떻게 구성하나요?
5. 첫 페이지와 마지막 페이지의 cursor는 어떻게 처리하나요?
6. 정렬값이 중복될 수 있을 때 결정적인 정렬이 필요한 이유는 무엇인가요?
7. No-Offset이면 항상 빠른가요?
8. B-tree 인덱스가 범위 검색에 유리한 이유는 무엇인가요?
9. 복합 인덱스의 leftmost prefix는 무엇인가요?
10. 선택도가 낮은 컬럼의 인덱스가 유용하지 않을 수 있는 이유는 무엇인가요?
11. `EXPLAIN`에서 어떤 항목을 확인해야 하나요?
12. `name contains` 검색이 일반 B-tree 인덱스에 불리한 이유는 무엇인가요?

## 캐시 정합성

1. cache-aside 패턴을 설명해주세요.
2. cache hit, miss, put, TTL과 eviction은 무엇인가요?
3. 상품 변경 트랜잭션 안에서 캐시를 먼저 제거하면 어떤 문제가 생기나요?
4. `AFTER_COMMIT` listener를 사용한 이유는 무엇인가요?
5. DB transaction이 rollback되면 cache evict event는 어떻게 되어야 하나요?
6. 주문 취소도 상품 캐시를 제거해야 하는 이유는 무엇인가요?
7. commit 후 캐시 삭제가 실패하면 어떤 위험이 남나요?
8. TTL만 사용하거나 eviction만 사용할 때의 장단점은 무엇인가요?

## 성능과 부하 테스트

1. latency, throughput과 concurrency의 관계를 설명해주세요.
2. 시스템 saturation은 어떤 상태인가요?
3. 평균보다 p95나 p99가 중요한 상황은 언제인가요?
4. 오류 요청을 제외하고 latency만 보면 어떤 문제가 생기나요?
5. connection pool이 모두 사용되면 요청에는 어떤 일이 생기나요?
6. 공정한 비교 실험에 동일 workload가 필요한 이유는 무엇인가요?
7. JVM과 애플리케이션 warm-up을 고려해야 하는 이유는 무엇인가요?
8. 부하 테스트를 여러 번 반복해야 하는 이유는 무엇인가요?
9. 처리량과 재고 정합성을 함께 검증해야 하는 이유는 무엇인가요?
10. 현재 k6 결과를 운영 SLO나 최대 처리량으로 사용할 수 없는 이유는 무엇인가요?
11. baseline과 regression threshold는 어떻게 다른가요?
12. 실제 시스템 오류와 재고 부족 같은 예상된 비성공을 왜 구분해야 하나요?

## 인증·인가와 보안

1. 인증과 인가는 어떻게 다른가요?
2. USER/ADMIN 권한 검사와 주문 소유권 검사는 무엇이 다른가요?
3. Spring Security filter chain에서 JWT가 어떻게 검증되나요?
4. Access Token과 Refresh Token의 역할 차이는 무엇인가요?
5. JWT의 token type을 검증해야 하는 이유는 무엇인가요?
6. Refresh Token Rotation은 어떤 공격을 줄이나요?
7. rotation과 refresh token 재사용 탐지는 같은 기능인가요?
8. stateless JWT 로그아웃에 blacklist가 필요한 이유는 무엇인가요?
9. blacklist가 만드는 Redis 의존성과 비용은 무엇인가요?
10. BCrypt가 비밀번호 저장에 적합한 이유는 무엇인가요?
11. 로그인 실패 횟수가 비즈니스 예외 rollback으로 사라지면 어떤 문제가 생기나요?
12. 임시 로그인 잠금이 자동으로 해제되어야 하는 이유는 무엇인가요?
13. 운영 secret에 기본값이나 placeholder를 허용하면 왜 위험한가요?
14. Actuator endpoint와 상세 정보를 모두 공개하면 어떤 위험이 있나요?
15. CORS와 CSRF는 어떤 문제를 다루며 서로 어떻게 다른가요?
16. stateless JWT API에서 CSRF 설정을 판단할 때 무엇을 확인해야 하나요?

## 네트워크

1. TCP가 신뢰성 있는 전송을 제공하는 기본 방법은 무엇인가요?
2. TCP 연결과 종료 과정을 설명해주세요.
3. HTTP keep-alive는 무엇이며 connection pool과 어떻게 연결되나요?
4. connection timeout과 read timeout은 어떻게 다른가요?
5. timeout 값을 무작정 길게 잡으면 어떤 문제가 생기나요?
6. DNS 이름이 IP 주소로 변환되는 흐름을 설명해주세요.
7. TLS/HTTPS가 제공하는 보안 속성은 무엇인가요?
8. reverse proxy와 Spring Boot 애플리케이션의 역할은 어떻게 다른가요?
9. Nginx upstream 전환만으로 무중단 배포가 보장되지 않는 이유는 무엇인가요?
10. cookie, server-side session과 token 기반 인증은 각각 어디에 상태를 두며, 서로 단순한 대체 관계가 아닌 이유는 무엇인가요?
11. Access Token을 cookie 또는 `Authorization` header로 전달할 때 XSS·CSRF 위험과 `HttpOnly`·`Secure`·`SameSite` 설정을 어떻게 고려해야 하나요?

## 스키마 관리

1. 현재 local/prod에서 Hibernate `ddl-auto:update`를 사용하는 이유는 무엇인가요?
2. 통합 테스트에서 `create-drop`을 사용하는 이유는 무엇인가요?
3. `ddl-auto:update`가 기존 CHECK나 index 적용을 완전히 보장하나요?
4. 기존 DB에 필요한 제약이 실제로 생성됐는지 어떻게 확인하나요?
5. DDL이 일반 데이터 transaction처럼 안전하게 rollback된다고 볼 수 있나요?
6. 운영 데이터와 여러 환경이 생기면 Flyway와 `validate`가 필요한 이유는 무엇인가요?
7. 여러 애플리케이션 인스턴스가 동시에 schema를 변경하면 어떤 문제가 생길 수 있나요?
8. migration 도입 전에 기존 데이터 정합성을 확인해야 하는 이유는 무엇인가요?

## 테스트와 CI

1. 단위, MVC slice, 통합, 동시성, stress와 부하 테스트를 구분해주세요.
2. `unitTest`, `verifyChange`와 `integrationTest`의 차이는 무엇인가요?
3. Testcontainers가 개발자 로컬 DB 직접 사용보다 안전한 이유는 무엇인가요?
4. 통합 테스트에서 MariaDB와 Redis를 모두 사용하는 이유는 무엇인가요?
5. 동시성 테스트에서 실행 시간보다 성공 수와 최종 재고가 중요한 이유는 무엇인가요?
6. assertion을 약화하거나 thread 수를 낮춰 테스트를 통과시키면 안 되는 이유는 무엇인가요?
7. 테스트 개수가 많으면 품질이 높다고 말할 수 있나요?
8. 가용성 테스트가 Redis와 DB 락의 속도 우위를 증명하지 않는 이유는 무엇인가요?
9. CI 실패 시 test artifact를 보존하는 이유는 무엇인가요?
10. 전체 Git 이력 secret scan이 필요한 이유는 무엇인가요?
11. Docker 이미지에 commit SHA 태그를 사용하는 이유는 무엇인가요?
12. 검증 job이 실패하면 이미지 게시를 막아야 하는 이유는 무엇인가요?

## 운영, 관측성과 Troubleshooting

1. 로그, metric, trace는 각각 어떤 정보를 제공하나요?
2. 주문 성공/실패 counter만으로 장애를 판단할 수 있나요?
3. queue depth, lock wait, pool usage와 p95를 함께 봐야 하는 이유는 무엇인가요?
4. SLI, SLO와 SLA는 어떻게 다른가요?
5. 현재 프로젝트에 실제 운영 dashboard와 alert가 있나요?
6. CI와 이미지 게시가 실제 배포 자동화와 다른 이유는 무엇인가요?
7. health check 없이 고정 시간 후 트래픽을 전환하면 어떤 문제가 생기나요?
8. rollback 가능한 배포를 위해 어떤 정보와 절차가 필요한가요?
9. 장애 조사에서 사실과 가설을 어떻게 구분하나요?
10. 재현되지 않는 문제를 어떤 순서로 조사하나요?
11. 최초 가설이 틀렸을 때 조사 기록을 남겨야 하는 이유는 무엇인가요?
12. 해결책뿐 아니라 재발 방지와 관측 방법을 기록해야 하는 이유는 무엇인가요?

## 답변 품질 점검

1. 답변이 문제 → 불변조건 → 대안 → 선택 → 검증 → 한계 순서로 구성됐나요?
2. 테스트한 조건을 운영 전체의 보장으로 확대하고 있지 않나요?
3. 정상 흐름뿐 아니라 실패 시나리오를 설명했나요?
4. 현재 구현과 향후 제안을 섞지 않았나요?
5. 선택하지 않은 대안과 trade-off를 설명할 수 있나요?
6. 모르는 부분을 인정한 뒤 확인 방법을 제시할 수 있나요?
7. [`answer-boundaries.md`](answer-boundaries.md)의 과장 표현을 사용하고 있지 않나요?
