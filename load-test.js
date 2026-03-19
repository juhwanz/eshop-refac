import http from 'k6/http';
import { check, sleep } from 'k6';

// 환경 변수로 주입받거나 기본값 사용
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const PRODUCT_ID = __ENV.PRODUCT_ID || '3'; // 만들어둔 재고 1만개짜리 상품 ID
const USER_TOKEN = __ENV.USER_TOKEN || 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHRlc3QuY29tIiwidHlwZSI6IkFDQ0VTUyIsImlhdCI6MTc3MzkxOTgzMiwiZXhwIjoxNzc0MDA2MjMyLCJhdXRoIjoiVVNFUiJ9.tkoABoEa9gdi8ZJc28R2Rm5NuzkI8e-ZVKPWYZvsFFg';

const ORDER_URL = `${BASE_URL}/api/orders`;
const PRODUCT_URL = `${BASE_URL}/api/products/${PRODUCT_ID}`;

export const options = {
    scenarios: {
        // 시나리오 1: 주문만 미친듯이 쏟아붓기 (10초간 초당 100건)
        order_only: {
            executor: 'constant-arrival-rate',
            exec: 'orderOnly',
            rate: 100,         // DB 커넥션 풀(20개)을 말려버리기 위해 초당 100건 요청
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 150,
        },
        // 시나리오 2: 주문 30% + 단순조회 70% 섞어서 공격 (10초간 초당 150건)
        mixed_read_write: {
            executor: 'constant-arrival-rate',
            exec: 'mixedReadWrite',
            rate: 150,
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 200,
            startTime: '15s', // 시나리오 1이 끝나고 5초 쉬었다가 시작
        },
    },
    thresholds: {
        // 테스트 합격 기준 (실패율 10% 이하, p95 응답속도 2초 이하)
        http_req_failed: ['rate<0.10'],
        http_req_duration: ['p(95)<2000'],
    },
};

// 주문 요청용 헤더 (토큰 포함)
function authHeaders() {
    return {
        headers: {
            Authorization: `Bearer ${USER_TOKEN}`,
            'Content-Type': 'application/json',
        },
    };
}

export function orderOnly() {
    const payload = JSON.stringify({
        productId: Number(PRODUCT_ID),
        count: 1, // OrderDto.CreateRequest 스펙에 맞춤
    });

    const res = http.post(ORDER_URL, payload, authHeaders());

    // 201(Created) 이거나, 재고 부족 등으로 실패해도 비즈니스 로직상 핸들링 된 것이므로 체크
    check(res, {
        'order status is 201 or 503(Lock Fail)': (r) => [201, 503].includes(r.status),
    });
}

export function mixedReadWrite() {
    const rand = Math.random();

    if (rand < 0.3) {
        // 30% 확률로 주문 (쓰기 트래픽)
        const payload = JSON.stringify({
            productId: Number(PRODUCT_ID),
            count: 1,
        });
        const res = http.post(ORDER_URL, payload, authHeaders());
        check(res, {
            'mixed order status is 201 or 503': (r) => [201, 503].includes(r.status),
        });
    } else {
        // 70% 확률로 상품 조회 (읽기 트래픽 - 인증 불필요)
        const res = http.get(PRODUCT_URL);
        check(res, {
            'product read status is 200 (가용성 핵심!)': (r) => r.status === 200,
        });
    }

    sleep(0.1);
}