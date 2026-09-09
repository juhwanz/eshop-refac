import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 1);
const ORDER_VUS = Number(__ENV.ORDER_VUS || 20);
const VIEW_VUS = Number(__ENV.VIEW_VUS || 10);
const DURATION = __ENV.DURATION || '30s';
const ORDER_COUNT = Number(__ENV.ORDER_COUNT || 1);
const QUEUE_TIMEOUT_MS = Number(__ENV.QUEUE_TIMEOUT_MS || 30_000);
const QUEUE_POLL_MS = Number(__ENV.QUEUE_POLL_MS || 250);
const USERS_FILE = __ENV.K6_USERS_FILE || 'load-test.users.json';
const RESULT_PATH = __ENV.RESULT_PATH || 'load-test-results/summary.json';
const RUN_ID = __ENV.TEST_RUN_ID || new Date().toISOString().replace(/[^0-9]/g, '');
const TEST_COMMIT_SHA = __ENV.TEST_COMMIT_SHA || 'unknown';
const TEST_ENVIRONMENT = __ENV.TEST_ENVIRONMENT || 'local';
const K6_VERSION = __ENV.K6_VERSION || 'unknown';
const K6_PROFILE = __ENV.K6_PROFILE || 'baseline';

if (!Number.isInteger(PRODUCT_ID) || PRODUCT_ID < 1) throw new Error('PRODUCT_ID는 양의 정수여야 합니다.');
if (!Number.isInteger(ORDER_VUS) || ORDER_VUS < 1 || !Number.isInteger(VIEW_VUS) || VIEW_VUS < 1) throw new Error('ORDER_VUS와 VIEW_VUS는 양의 정수여야 합니다.');
if (!Number.isInteger(ORDER_COUNT) || ORDER_COUNT < 1) throw new Error('ORDER_COUNT는 양의 정수여야 합니다.');
if (!Number.isFinite(QUEUE_TIMEOUT_MS) || QUEUE_TIMEOUT_MS <= 0) throw new Error('QUEUE_TIMEOUT_MS는 0보다 커야 합니다.');
if (!Number.isFinite(QUEUE_POLL_MS) || QUEUE_POLL_MS <= 0) throw new Error('QUEUE_POLL_MS는 0보다 커야 합니다.');
if (K6_PROFILE !== 'baseline' && K6_PROFILE !== 'regression') throw new Error('K6_PROFILE은 baseline 또는 regression이어야 합니다.');

const users = new SharedArray('load-test-users', () => {
    const parsed = JSON.parse(open(USERS_FILE));
    if (!Array.isArray(parsed)) throw new Error('K6_USERS_FILE은 사용자 배열이어야 합니다.');
    return parsed;
});

const queueRegistrationDuration = new Trend('queue_registration_duration');
const queueAdmissionDuration = new Trend('queue_admission_duration');
const orderDuration = new Trend('order_duration');
const productViewDuration = new Trend('product_view_duration');
const orderSuccess = new Counter('order_success');
const queueWait = new Counter('queue_wait');
const queueTimeout = new Counter('queue_timeout');
const idempotencyConflict = new Counter('idempotency_conflict');
const outOfStock = new Counter('out_of_stock');
const systemError = new Counter('system_error');
const actualErrorRate = new Rate('actual_error_rate');
const queueSystemErrorRate = new Rate('queue_system_error_rate');
const orderSystemErrorRate = new Rate('order_system_error_rate');
const productViewSystemErrorRate = new Rate('product_view_system_error_rate');
const initialStock = new Gauge('initial_stock');
const finalStock = new Gauge('final_stock');

const thresholds = {};
function positiveThreshold(name, regressionDefault) {
    const configured = __ENV[name];
    if (!configured && K6_PROFILE !== 'regression') return null;
    const value = Number(configured || regressionDefault);
    if (!Number.isFinite(value) || value <= 0) throw new Error(`${name}는 0보다 큰 숫자여야 합니다.`);
    return value;
}

const orderP95Threshold = positiveThreshold('ORDER_P95_THRESHOLD_MS', 2300);
const viewP95Threshold = positiveThreshold('VIEW_P95_THRESHOLD_MS', 5800);
const systemErrorRateThreshold = positiveThreshold('SYSTEM_ERROR_RATE_THRESHOLD', 0.04);
if (systemErrorRateThreshold !== null && systemErrorRateThreshold > 1) throw new Error('SYSTEM_ERROR_RATE_THRESHOLD는 1 이하여야 합니다.');
if (orderP95Threshold !== null) thresholds.order_duration = [`p(95)<${orderP95Threshold}`];
if (viewP95Threshold !== null) thresholds.product_view_duration = [`p(95)<${viewP95Threshold}`];
if (systemErrorRateThreshold !== null) thresholds.actual_error_rate = [`rate<${systemErrorRateThreshold}`];

export const options = {
    scenarios: {
        order_rush: {
            executor: 'constant-vus', vus: ORDER_VUS, duration: DURATION,
            gracefulStop: `${Math.ceil(QUEUE_TIMEOUT_MS / 1000) + 5}s`, exec: 'orderTask',
        },
        view_rush: {
            executor: 'constant-vus', vus: VIEW_VUS, duration: DURATION,
            gracefulStop: '5s', exec: 'viewTask',
        },
    },
    thresholds,
};

function jsonBody(response) {
    try { return response.json(); } catch (_) { return null; }
}

function responseCode(response) {
    const body = jsonBody(response);
    return body && body.code ? body.code : null;
}

function sleepWithJitter(baseMs) {
    const jitter = baseMs * 0.2;
    sleep(Math.max(50, baseMs - jitter + Math.random() * jitter * 2) / 1000);
}

function operationErrorRate(operation) {
    if (operation === 'queue') return queueSystemErrorRate;
    if (operation === 'order') return orderSystemErrorRate;
    if (operation === 'product_view') return productViewSystemErrorRate;
    throw new Error(`지원하지 않는 metric operation입니다: ${operation}`);
}

function markSystemError(operation, tags) {
    systemError.add(1, tags);
    actualErrorRate.add(1, tags);
    operationErrorRate(operation).add(1, tags);
}

function markExpectedResult(operation, tags) {
    actualErrorRate.add(0, tags);
    operationErrorRate(operation).add(0, tags);
}

function authParams(token, operation) {
    return {
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        tags: { operation },
    };
}

export function setup() {
    if (users.length < ORDER_VUS + VIEW_VUS) throw new Error(`테스트 계정 ${ORDER_VUS + VIEW_VUS}개 이상이 필요합니다.`);

    const emails = users.map((user) => user.email);
    if (new Set(emails).size !== emails.length) throw new Error('K6_USERS_FILE의 이메일은 중복될 수 없습니다.');

    const tokens = users.map((user, index) => {
        if (!user.email || !user.password) throw new Error(`사용자 ${index + 1}에 email/password가 필요합니다.`);
        const response = http.post(`${BASE_URL}/users/login`, JSON.stringify({ email: user.email, password: user.password }), {
            headers: { 'Content-Type': 'application/json' }, tags: { operation: 'setup_login' },
        });
        const body = jsonBody(response);
        const token = body && body.data && body.data.accessToken;
        if (response.status !== 200 || !token) throw new Error(`테스트 사용자 ${index + 1} 로그인에 실패했습니다. status=${response.status}`);
        return token;
    });
    if (new Set(tokens).size !== tokens.length) throw new Error('로그인 결과에 중복 토큰이 있어 VU별 계정을 분리할 수 없습니다.');

    const response = http.get(`${BASE_URL}/products/${PRODUCT_ID}`, { tags: { operation: 'setup_inventory' } });
    const body = jsonBody(response);
    const stock = body && body.data && body.data.stockQuantity;
    if (response.status !== 200 || !Number.isInteger(stock)) throw new Error(`초기 상품 재고를 확인할 수 없습니다. status=${response.status}`);
    initialStock.add(stock, { phase: 'initial' });
    return { tokens, initialStock: stock, runId: RUN_ID };
}

function waitForAdmission(token) {
    const startedAt = Date.now();
    while (Date.now() - startedAt < QUEUE_TIMEOUT_MS) {
        const response = http.get(`${BASE_URL}/products/${PRODUCT_ID}/queue`, authParams(token, 'queue_status'));
        const body = jsonBody(response);
        const status = body && body.data && body.data.status;
        if (response.status === 200 && status === 'ACTIVE') {
            queueAdmissionDuration.add(Date.now() - startedAt, { outcome: 'active' });
            markExpectedResult('queue', { operation: 'queue_status', outcome: 'active' });
            return true;
        }
        if (response.status === 200 && status === 'WAITING') {
            queueWait.add(1, { status });
            markExpectedResult('queue', { operation: 'queue_status', outcome: status.toLowerCase() });
            sleepWithJitter(QUEUE_POLL_MS);
            continue;
        }
        if (response.status === 200 && status === 'NOT_REGISTERED') {
            markSystemError('queue', { operation: 'queue_status', status, code: 'QUEUE_STATE_LOST' });
            return false;
        }
        markSystemError('queue', { operation: 'queue_status', status: String(response.status), code: responseCode(response) || 'UNKNOWN' });
        return false;
    }
    queueTimeout.add(1, { operation: 'queue_admission' });
    markSystemError('queue', { operation: 'queue_admission', code: 'QUEUE_TIMEOUT' });
    queueAdmissionDuration.add(Date.now() - startedAt, { outcome: 'timeout' });
    return false;
}

export function orderTask(data) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    const registrationStartedAt = Date.now();
    const registration = http.post(`${BASE_URL}/products/${PRODUCT_ID}/queue`, null, authParams(token, 'queue_register'));
    queueRegistrationDuration.add(Date.now() - registrationStartedAt, { status: String(registration.status) });
    if (registration.status !== 200 && registration.status !== 201) {
        markSystemError('queue', { operation: 'queue_register', status: String(registration.status), code: responseCode(registration) || 'UNKNOWN' });
        sleepWithJitter(QUEUE_POLL_MS);
        return;
    }
    markExpectedResult('queue', { operation: 'queue_register', outcome: 'accepted' });
    if (!waitForAdmission(token)) {
        sleepWithJitter(QUEUE_POLL_MS);
        return;
    }

    const response = http.post(`${BASE_URL}/orders`, JSON.stringify({ productId: PRODUCT_ID, count: ORDER_COUNT }), {
        ...authParams(token, 'order'),
        headers: { ...authParams(token, 'order').headers, 'Idempotency-Key': `k6-${data.runId}-${__VU}-${__ITER}` },
    });
    orderDuration.add(response.timings.duration, { status: String(response.status) });
    const code = responseCode(response);
    if (response.status === 201) {
        orderSuccess.add(1, { outcome: 'success' });
        markExpectedResult('order', { operation: 'order', outcome: 'success' });
    } else if (code === 'QUEUE_WAITING' || response.status === 429) {
        queueWait.add(1, { status: code || '429' });
        markExpectedResult('order', { operation: 'order', outcome: 'queue_waiting' });
    } else if (code === 'IDEMPOTENCY_CONFLICT' || response.status === 409) {
        idempotencyConflict.add(1, { outcome: 'conflict' });
        markExpectedResult('order', { operation: 'order', outcome: 'idempotency_conflict' });
    } else if (code === 'OUT_OF_STOCK') {
        outOfStock.add(1, { outcome: 'out_of_stock' });
        markExpectedResult('order', { operation: 'order', outcome: 'out_of_stock' });
    } else {
        markSystemError('order', { operation: 'order', status: String(response.status), code: code || 'UNKNOWN' });
    }
    sleepWithJitter(500);
}

export function viewTask() {
    const response = http.get(`${BASE_URL}/products/${PRODUCT_ID}`, { tags: { operation: 'product_view' } });
    productViewDuration.add(response.timings.duration, { status: String(response.status) });
    if (response.status === 200) markExpectedResult('product_view', { operation: 'product_view' });
    else markSystemError('product_view', { operation: 'product_view', status: String(response.status), code: responseCode(response) || 'UNKNOWN' });
    sleepWithJitter(1000);
}

export function teardown() {
    const response = http.get(`${BASE_URL}/products/${PRODUCT_ID}`, { tags: { operation: 'teardown_inventory' } });
    const body = jsonBody(response);
    const stock = body && body.data && body.data.stockQuantity;
    if (response.status !== 200 || !Number.isInteger(stock)) throw new Error(`최종 상품 재고를 확인할 수 없습니다. status=${response.status}`);
    finalStock.add(stock, { phase: 'final' });
}

function metricValue(data, name, key) {
    return data.metrics && data.metrics[name] && data.metrics[name].values ? data.metrics[name].values[key] : undefined;
}

export function handleSummary(data) {
    const initial = metricValue(data, 'initial_stock', 'value');
    const final = metricValue(data, 'final_stock', 'value');
    const successfulOrders = metricValue(data, 'order_success', 'count') || 0;
    const expectedFinal = Number.isFinite(initial) ? initial - successfulOrders * ORDER_COUNT : null;
    const invariantPassed = expectedFinal !== null && final === expectedFinal && final >= 0;
    const results = {
        orderSuccess: successfulOrders,
        orderThroughputPerSecond: metricValue(data, 'order_success', 'rate') || 0,
        queueWait: metricValue(data, 'queue_wait', 'count') || 0,
        queueTimeout: metricValue(data, 'queue_timeout', 'count') || 0,
        idempotencyConflict: metricValue(data, 'idempotency_conflict', 'count') || 0,
        outOfStock: metricValue(data, 'out_of_stock', 'count') || 0,
        systemError: metricValue(data, 'system_error', 'count') || 0,
        actualErrorRate: metricValue(data, 'actual_error_rate', 'rate') || 0,
        queueSystemErrorRate: metricValue(data, 'queue_system_error_rate', 'rate') || 0,
        orderSystemErrorRate: metricValue(data, 'order_system_error_rate', 'rate') || 0,
        productViewSystemErrorRate: metricValue(data, 'product_view_system_error_rate', 'rate') || 0,
        orderP95Ms: metricValue(data, 'order_duration', 'p(95)') || null,
        productViewP95Ms: metricValue(data, 'product_view_duration', 'p(95)') || null,
    };
    const summary = {
        test: { runId: RUN_ID, profile: K6_PROFILE, commitSha: TEST_COMMIT_SHA, environment: TEST_ENVIRONMENT, k6Version: K6_VERSION, baseUrl: BASE_URL, productId: PRODUCT_ID, orderVus: ORDER_VUS, viewVus: VIEW_VUS, duration: DURATION, orderCount: ORDER_COUNT, k6Users: users.length },
        thresholds: { orderP95Ms: orderP95Threshold, productViewP95Ms: viewP95Threshold, systemErrorRate: systemErrorRateThreshold },
        inventory: { initialStock: initial, successfulOrders, expectedFinalStock: expectedFinal, finalStock: final, invariantPassed },
        results,
        metrics: data.metrics,
    };
    return {
        stdout: JSON.stringify({ inventory: summary.inventory, results, resultPath: RESULT_PATH }, null, 2),
        [RESULT_PATH]: JSON.stringify(summary, null, 2),
    };
}
