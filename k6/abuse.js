import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 어뷰징 방어 측정 — 정상 유저와 고빈도 어뷰저를 같은 이벤트에 동시에 넣는다.
// 실행: docker compose --profile load run --rm -e STOCK=500 k6 run /scripts/abuse.js

const STOCK = parseInt(__ENV.STOCK);
if (!STOCK) {
    throw new Error('STOCK 환경변수가 필요합니다. 예) -e STOCK=500');
}

const BASE_URL = 'http://app:8080';
const CAMPAIGN_ID = 1;
const NORMAL_VUS = 1000;        // 각자 한 번씩 — 오픈런의 축소판
const ABUSER_VUS = 50;          // 쉬지 않고 반복하는 계정
const ABUSE_DURATION = '10s';
const TIMEOUT = '30s';

const tokens = new SharedArray('tokens', () =>
    open('/scripts/tokens.txt').trim().split(/\r?\n/)
);

// 200은 재발급 재생, 429는 제한. 둘 다 정상 응답이라 실패로 세면 안 된다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

const normalOk = new Counter('normal_ok');
const normalRejected = new Counter('normal_rejected');
const normalLimited = new Counter('normal_limited');
const normalFailed = new Counter('normal_failed');
const normalDuration = new Trend('normal_duration', true);

const abuserSent = new Counter('abuser_sent');
const abuserLimited = new Counter('abuser_limited');
const abuserPassed = new Counter('abuser_passed');

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        // 둘이 같은 시각에 출발해야 어뷰저가 오픈 순간에 붙어 있는 상황이 된다.
        normal: {
            executor: 'per-vu-iterations',
            vus: NORMAL_VUS,
            iterations: 1,
            maxDuration: '90s',
            exec: 'normal',
        },
        abuser: {
            executor: 'constant-vus',
            vus: ABUSER_VUS,
            duration: ABUSE_DURATION,
            exec: 'abuser',
        },
    },
};

export function setup() {
    const res = http.get(`${BASE_URL}/campaigns/${CAMPAIGN_ID}`);
    if (res.status !== 200) {
        exec.test.abort(`캠페인 ${CAMPAIGN_ID} 조회 실패 (${res.status}) — 리셋 스크립트를 먼저 실행할 것`);
    }
    if (res.json('totalQuantity') !== STOCK) {
        exec.test.abort(`재고 불일치: 캠페인 ${res.json('totalQuantity')} vs STOCK ${STOCK}`);
    }
    console.log(`[어뷰징] STOCK=${STOCK} 정상=${NORMAL_VUS} 어뷰저=${ABUSER_VUS}x${ABUSE_DURATION} 유저풀=${tokens.length}`);
}

// VU 번호는 시나리오가 달라도 테스트 전체에서 겹치지 않는다 — 어뷰저와 정상 유저는 다른 계정이다.
function issue() {
    const token = tokens[exec.vu.idInTest - 1];
    return http.post(`${BASE_URL}/campaigns/${CAMPAIGN_ID}/coupons`, null, {
        headers: { Authorization: `Bearer ${token}` },
        timeout: TIMEOUT,
    });
}

export function normal() {
    const res = issue();
    normalDuration.add(res.timings.duration);

    if (res.status === 201 || res.status === 200) {
        normalOk.add(1);
    } else if (res.status === 429) {
        normalLimited.add(1);       // 0이어야 한다. 아니면 제한값이 정상 유저를 때리고 있다
    } else if (res.status === 409) {
        normalRejected.add(1);
    } else {
        normalFailed.add(1);
    }
}

export function abuser() {
    const res = issue();
    abuserSent.add(1);

    if (res.status === 429) {
        abuserLimited.add(1);
    } else {
        abuserPassed.add(1);
    }
}
