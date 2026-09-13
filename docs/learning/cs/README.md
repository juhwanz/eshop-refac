# 기본 CS 학습 범위

백엔드 신입 면접과 E-Shop 설명에 직접 연결되는 기초부터 학습한다. CS 전체를 빠짐없이 다루기보다 원리를 자신의 말로 설명하고 프로젝트 사례에 연결하는 것을 목표로 한다.

## Java/JVM

- 객체지향, 다형성, 인터페이스와 추상 클래스
- `equals`와 `hashCode`, 불변 객체와 Collection
- checked/unchecked exception
- JVM 메모리 구조와 GC
- Java Memory Model, thread, `synchronized`, `volatile`과 atomic operation
- thread pool과 interrupt

## 운영체제

- process, thread와 context switching
- concurrency와 parallelism
- race condition과 critical section
- mutex, semaphore와 monitor
- deadlock의 조건과 대응
- 가상 메모리, page와 page fault
- blocking, non-blocking과 timeout

## 네트워크와 HTTP

- TCP 연결, keep-alive와 timeout
- HTTP 요청·응답과 상태 코드
- HTTP method와 멱등성
- retry가 중복 작업을 만드는 이유
- cookie, session, token과 HTTPS 기초
- `HttpOnly`, `Secure`, `SameSite`와 토큰 전달 방식

## 데이터베이스

- ACID, 격리 수준과 MVCC
- dirty read, non-repeatable read와 phantom read
- 낙관적·비관적 락과 deadlock
- SQL 논리 실행 순서, JOIN, GROUP BY와 HAVING
- 서브쿼리와 JOIN의 선택 및 실행 계획 확인
- B-tree, 복합 인덱스와 실행 계획
- unique, check와 foreign key
- Offset과 keyset pagination

## 자료구조

- Array/List, Hash table, Set와 Queue
- Heap, Tree와 B-tree
- 탐색·삽입·삭제의 시간 복잡도
- Redis String, Set과 Sorted Set의 사용 조건

각 주제 문서는 필요할 때 하위 디렉터리에 생성한다. 알고리즘 풀이 기록은 별도 요청이 없다면 이 범위에 포함하지 않는다.
