# 필수 학습 범위

프로젝트 이해와 Java/Spring 백엔드 면접에 필요한 지식을 30개 역량으로 묶은 정적 기준표다. 세부 질문을 모두 별도 진도로 관리하지 않고, 한 역량을 설명하는 데 필요한 확인 항목으로 사용한다. 범위가 넓은 역량은 여러 세션에 걸쳐 확인한다.

현재 상태, 자신감과 복습 일정은 [`README.md`](README.md)에서만 관리한다. 질문 연습 재료는 [`interview/question-bank.md`](interview/question-bank.md)에 유지한다. 목표 문서는 첫 학습 시 필요한 것만 만들며, 파일 존재 여부와 숙련도를 동일하게 취급하지 않는다.

## 프로젝트 연계 역량

| ID | 반드시 설명할 범위 | 목표 문서 |
|---|---|---|
| P01 | 프로젝트 한 문장 정의, 사용자·시스템 문제, 재고·멱등성·취소·소유권·캐시·대기열 불변조건, 구현/비구현 및 증거 경계 | `project/invariants-and-scope.md` |
| P02 | 인증부터 응답까지 주문 요청 순서, Redis와 MariaDB의 책임, commit 이후 캐시·대기열 정리, 실패별 책임 | `project/order-request-flow.md` |
| P03 | `@Transactional` 프록시와 self-invocation, readOnly·propagation·rollback, flush와 commit, 제약 충돌 복구 경계 | `transactions/spring-transactions.md` |
| P04 | race condition과 lost update, 비관적·낙관적 락, 조건부 UPDATE, Redis 락의 선택 기준, connection pool 점유 | `concurrency/stock-and-locks.md` |
| P05 | Redisson watchdog과 lease, 락 소유 확인·interrupt 복구, Redis 단절·JVM 정지·fencing 부재, DB CHECK의 역할과 한계 | `concurrency/stock-and-locks.md` |
| P06 | timeout·retry·double click, 사용자 범위 key와 payload fingerprint, Redis SET NX·token·TTL, DB unique 최종 방어선, 실패 매트릭스 | `idempotency/order-idempotency.md` |
| P07 | ZSet·INCR·Lua의 선택 이유, waiting/active와 상품별 격리, ShedLock과 KEYS 회피, admission 권한의 의미, 결과별 정리와 Cluster 한계 | `redis/queue-and-lua.md` |
| P08 | 취소 전 소유권, 상태 전이의 단일성, 주문·취소 공통 상품 락, row lock, 중복 재고 복구 방지 | `concurrency/order-cancellation.md` |
| P09 | 영속성 컨텍스트·엔티티 상태·1차 캐시·dirty checking, LAZY N+1, fetch join과 pageable 위험, batch fetch trade-off | `persistence/jpa-loading-and-context.md` |
| P10 | Offset와 count 비용, Page와 Slice, PK 커서와 결정적 정렬, 필터 선택도, 복합 인덱스·leftmost prefix·EXPLAIN·선행 wildcard | `persistence/pagination-and-indexes.md` |
| P11 | PK·FK·unique·check 제약, local/prod update와 test create-drop, Hibernate update의 한계, DDL 위험, Flyway·validate 전환 조건 | `persistence/schema-management.md` |
| P12 | cache-aside miss·put·TTL·eviction, rollback 전에 삭제할 때의 문제, `AFTER_COMMIT` listener, eviction 실패와 stale 위험 | `performance/cache-consistency.md` |
| P13 | 인증·인가·소유권, Security filter chain, Access/Refresh token과 type·만료, rotation·blacklist, BCrypt, 로그인 잠금, CORS·CSRF·secret 경계 | `security/authentication-and-jwt.md` |
| P14 | 단위·MVC slice·통합·동시성·부하 테스트의 역할, Testcontainers, 성공/실패 수와 최종 상태 검증, 테스트가 증명하는 범위 | `testing/test-strategy.md` |
| P15 | latency·throughput·concurrency·saturation, 평균과 percentile·오류율, 동일 workload·warm-up·반복·정합성, 로컬 k6 결과의 한계 | `performance/measurement-and-load-test.md` |
| P16 | Gradle 검증 범위, 실패 artifact·repository hygiene·secret scan, commit SHA 이미지, CI와 실제 배포·rollback의 차이, log·metric·alert·SLO | `operations/delivery-and-observability.md` |
| P17 | 증상→사실→가설→검증→원인→재발 방지, 프로젝트 30초 소개와 2분 사례, 과장 없는 근거·대안·한계 답변 | `interview/answer-structure.md`, `interview/answer-boundaries.md`, `troubleshooting/` |

## Java·Spring·기본 CS 역량

| ID | 반드시 설명할 범위 | 목표 문서 |
|---|---|---|
| F01 | 캡슐화·상속·다형성·추상화, 인터페이스와 추상 클래스 선택, 불변 객체와 공개 setter 회피 | `cs/java/core-java.md` |
| F02 | List·Set·Map 선택과 복잡도, equals/hashCode 계약, checked/unchecked exception과 전파 | `cs/java/core-java.md` |
| F03 | heap·stack·metaspace, 객체 생명주기, GC 목적·stop-the-world와 기본 trade-off | `cs/java/jvm-and-concurrency.md` |
| F04 | Java Memory Model의 가시성·원자성·순서, synchronized·volatile·atomic type·thread pool | `cs/java/jvm-and-concurrency.md` |
| F05 | IoC·DI·생성자 주입, Bean 생성과 생명주기, singleton Bean의 thread-safety | `cs/spring/ioc-and-boot.md` |
| F06 | `@SpringBootApplication`, component scan·auto-configuration, profile·외부 설정·type-safe binding | `cs/spring/ioc-and-boot.md` |
| F07 | AOP와 proxy, DispatcherServlet 요청 흐름, filter·interceptor·argument resolver, validation·DTO·예외 응답 | `cs/spring/web-and-proxies.md` |
| F08 | process·thread·context switching, concurrency와 parallelism, critical section·mutex·semaphore·monitor, deadlock 네 조건 | `cs/operating-system/fundamentals.md` |
| F09 | 가상 메모리·page·page fault, blocking/non-blocking과 synchronous/asynchronous의 축 구분 | `cs/operating-system/fundamentals.md` |
| F10 | TCP 연결·종료와 신뢰성, keep-alive와 timeout, HTTP method·상태 코드·멱등성, cookie·server session·token의 상태 위치와 trade-off, HttpOnly·Secure·SameSite와 토큰 전달 방식, DNS·TLS·reverse proxy | `cs/network/fundamentals.md` |
| F11 | ACID, 격리 수준, dirty/non-repeatable/phantom read, MVCC, DB deadlock과 재시도 | `cs/database/fundamentals.md` |
| F12 | SQL 논리 실행 순서, JOIN·GROUP BY·HAVING·서브쿼리, 정규화·반정규화, PK·FK·unique·check, B-tree·복합 인덱스·선택도와 실행 계획 | `cs/database/fundamentals.md` |
| F13 | Array/List·Hash table·Set·Queue·Heap·Tree·B-tree의 특성과 주요 연산 시간 복잡도 | `cs/data-structure/fundamentals.md` |
