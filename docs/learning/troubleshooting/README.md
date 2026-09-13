# Troubleshooting 사례

문제를 바로 수정한 결과보다 증상에서 원인에 도달한 사고 과정을 기록한다. 각 사례는 `cases/YYYY-MM-DD-<short-title>.md`로 만들고 [`../_templates/troubleshooting.md`](../_templates/troubleshooting.md)를 사용한다.

## 사례에 반드시 남길 것

- 최초 증상과 영향
- 사실과 추측의 구분
- 재현 조건
- 조사한 가설과 기각 근거
- 직접 원인과 구조적 원인
- 실제 수행한 해결과 제안만 한 해결의 구분
- 회귀 테스트 또는 관측 방법
- 남은 위험
- 30초와 2분 면접 답변

## 후보 사례

- DB 락 대기로 connection pool이 고갈되는 상황
- Redis가 없는 환경에서 Spring context가 실패한 원인
- DB commit 뒤 멱등성 캐시 저장 실패
- 고정 lease보다 트랜잭션이 오래 걸리는 상황
- 컬렉션 fetch join과 pageable의 메모리 페이징
- 전역 대기열에서 상품 간 혼잡이 전파되는 문제

후보는 학습 소재이며 현재 장애가 발생했다는 뜻이 아니다. 실제 재현이나 코드 수정은 별도 승인을 받는다.
