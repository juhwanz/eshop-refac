import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// 1. 데이터 준비
const tokens = new SharedArray('jwt tokens', function () {
    return papaparse.parse(open('./tokens.csv'), { header: true }).data;
});

// 2. 트래픽
export const options = {
    scenarios: {
        order_surge: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 300 }, // 10초 만에 동시 접속자 300명
                { duration: '30s', target: 300 }, // 30초 동안 300명  클릭
                { duration: '10s', target: 0 },   // 10초 동안 서서히 빠져나감
            ],
        },
    },
};

// 3. 가상 유저의 행동
export default function () {
    const token = tokens[Math.floor(Math.random() * tokens.length)].token.trim();
    const url = 'http://localhost:8080/api/orders';

    const payload = JSON.stringify({
        productId: 1, // 방금 생성한 100개짜리 상품 ID
        count: 1,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
    };

    const res = http.post(url, payload, params);

    // 에러 코드
    console.log('Status: ' + res.status + ' Body: ' + res.body);
    // 4. 핵심 지표 검증
    check(res, {
        '성공 (201 Created)': (r) => r.status === 201,
        '대기열 차단 (429 Too Many Requests)': (r) => r.status === 429,
        '재고 소진 (400 Bad Request)': (r) => r.status === 400 && r.body.includes('재고'),
        '락 획득 실패 (503 Service Unavailable)': (r) => r.status === 503,
    });

    // 1초 쉬고 다시 클릭 (무한 반복 방지 및 현실적인 클릭 속도 반영)
    sleep(1);
}