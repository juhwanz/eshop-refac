# API 안내

이 문서는 현재 컨트롤러가 제공하는 endpoint와 공통 인증·응답 계약을 요약합니다. 애플리케이션을 실행한 뒤 세부 요청·응답 schema는 [Swagger UI](http://localhost:8080/swagger-ui.html)에서 확인할 수 있습니다.

## 인증

- 상품 단건·목록 조회와 회원가입·로그인·토큰 재발급은 공개 endpoint입니다.
- 그 외 사용자 endpoint는 `Authorization: Bearer <Access Token>` 헤더가 필요합니다.
- 상품 등록과 가격 수정은 `ADMIN` 역할이 필요합니다.
- 주문 취소는 인증 사용자와 주문 소유자가 같아야 합니다.

## Endpoint

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| `POST` | `/api/users/signup` | 공개 | 회원가입 |
| `POST` | `/api/users/login` | 공개 | Access/Refresh Token 발급 |
| `POST` | `/api/users/reissue` | 공개 | Refresh Token 검증 및 RTR 재발급 |
| `POST` | `/api/users/logout` | 사용자 | Refresh Token 제거 및 Access Token blacklist |
| `GET` | `/api/products/{productId}` | 공개 | 상품 단건 조회 |
| `GET` | `/api/products/search` | 공개 | 조건 검색과 Offset 페이지 조회 |
| `GET` | `/api/products/search/no-offset` | 공개 | 내림차순 커서 기반 `Slice` 조회 |
| `POST` | `/api/products` | `ADMIN` | 상품 등록 |
| `PATCH` | `/api/products/{productId}/price` | `ADMIN` | 가격 수정 |
| `POST` | `/api/products/{productId}/queue` | 사용자 | 상품 대기열 등록 또는 현재 상태 반환 |
| `GET` | `/api/products/{productId}/queue` | 사용자 | 상품 대기열 상태와 현재 순번 조회 |
| `POST` | `/api/orders` | 사용자, 신규 주문은 대기열 권한 | `Idempotency-Key` 기반 주문 생성 |
| `GET` | `/api/orders` | 사용자 | 내 주문 목록 조회 |
| `PATCH` | `/api/orders/{orderId}/cancel` | 주문 소유자 | 주문 취소와 재고 복구 |

## 주문 멱등성

`POST /api/orders`는 `Idempotency-Key` 요청 헤더가 필수입니다. 같은 사용자·키·상품 ID·수량의 완료 요청은 새 주문을 만들지 않고 기존 `orderId`와 HTTP 201을 반환합니다. 같은 사용자와 키에 다른 상품 또는 수량을 사용하면 409 `IDEMPOTENCY_CONFLICT`를 반환합니다.

키 형식, 저장 기간, 재시도와 장애 처리 계약은 [주문 멱등성 문서](order-idempotency.md)를 참고하세요.

## 응답 형식

컨트롤러의 성공 응답은 `ApiResponse` 형식을 사용합니다. 전역 예외 처리기가 처리하는 비즈니스·입력 오류는 `ErrorResponse`로 반환합니다. Spring Security 필터 단계의 인증·인가 오류는 별도 보안 핸들러 또는 필터에서 반환합니다.

## 관련 문서

- [로컬 실행 가이드](getting-started.md)
- [아키텍처 상세](architecture.md)
- [주문 멱등성](order-idempotency.md)
