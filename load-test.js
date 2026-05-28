import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080/api';

// [필수 수정] Swagger나 Postman에서 발급받은 본인의 Access Token을 넣어주세요!
export const options = {
    scenarios: {
        // 1. 주문 폭주 부대: 20명이 30초 동안 쉬지 않고 주문 버튼을 클릭
        order_rush: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'orderTask',
        },
        // 2. 일반 조회 부대: 10명이 30초 동안 상품 구경만 함
        view_rush: {
            executor: 'constant-vus',
            vus: 10,
            duration: '30s',
            exec: 'viewTask',
        },
    },
};

const TOKEN = 'Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJrNnRlc3RAdGVzdC5jb20iLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzc3MDI4NTkwLCJleHAiOjE3NzcxMTQ5OTAsImF1dGgiOiJVU0VSIn0.qzD_yNHfP7vUk9jsLWi1zsz1bQ9fo5Yf8akr0WIJEfyKx1jt2brH6QaPRgnOmOWG';

// --- 주문 폭주 로직 ---
export function orderTask() {
    const payload = JSON.stringify({ productId: 1, count: 1 });
    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': TOKEN,
            'Idempotency-Key': `k6-test-${__VU}-${__ITER}`
        },
    };

    const res = http.post(`${BASE_URL}/orders`, payload, params);

    // 💡 이 부분을 추가하세요! (정상(200/201)이 아닐 때만 에러 내용 출력)
    if (res.status !== 200 && res.status !== 201) {
        console.log(`[에러] 상태코드: ${res.status}, 내용: ${res.body}`);
    }

    check(res, { '주문 요청 처리됨': (r) => r.status !== 500 });
    sleep(0.5);
}

// --- 단순 조회 로직 ---
export function viewTask() {
    const res = http.get(`${BASE_URL}/products/1`);
    // 우리가 진짜 지켜야 할 핵심 지표: 조회가 200번(성공)을 반환하는가?
    check(res, { '조회 성공(200)': (r) => r.status === 200 });
    sleep(1);
}