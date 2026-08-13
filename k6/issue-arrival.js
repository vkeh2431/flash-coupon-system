import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 대기열 Before — 도착률을 고정해(열린 루프) 발급 경로에 유입을 건다.
//
// issue.js와 재는 것이 다르다. issue.js는 원샷 버스트(VU 1명 = 요청 1건)로 v0~v4의 락 전략을
// 비교하는 스크립트고, 이건 "유입이 초당 R건일 때 무엇이 먼저 죽는가"를 보는 스크립트다.
// 앱 코드는 건드리지 않는다 — 바뀌는 것은 부하를 거는 방식뿐이다.
//
// 실행:
//   docker compose --profile load run --rm -e STOCK=500 -e RATE=3000 k6 run /scripts/issue-arrival.js
//
// ⚠️ dropped_iterations가 0이 아니면 그 실행은 열린 루프가 아니다. VU가 말라 발사를 못 한 것이고,
//    그때 실제 도착률은 R이 아니라 maxVUs ÷ 응답시간이라 x축이 성립하지 않는다. 조건을 고쳐 다시 잰다.

const STOCK = parseInt(__ENV.STOCK);
const RATE = parseInt(__ENV.RATE);
if (!STOCK || !RATE) {
    throw new Error('STOCK과 RATE 환경변수가 필요합니다. 예) -e STOCK=500 -e RATE=3000');
}

const DURATION = __ENV.DURATION || '10s';
const TIMEOUT = __ENV.TIMEOUT || '5s';

// in-flight 최악값 = RATE × TIMEOUT. MAX_VUS가 이보다 작으면 드롭이 난다.
const MAX_VUS = parseInt(__ENV.MAX_VUS || '15000');
// 측정 도중 VU를 새로 만들면 하필 포화 시점에 k6가 CPU를 더 쓴다. 시작 전에 전부 만들어둔다.
const PRE_VUS = parseInt(__ENV.PRE_VUS || String(MAX_VUS));

const BASE_URL = 'http://app:8080';
const CAMPAIGN_ID = 1;

const tokens = new SharedArray('tokens', () =>
    open('/scripts/tokens.txt').trim().split(/\r?\n/)   // \r이 남으면 Authorization 헤더가 통째로 거부된다
);

// 200(재생)과 429(제한)도 서버가 의도적으로 낸 응답이다. 실패로 세면 http_req_failed가 오염된다.
// 이 스크립트에서 http_req_failed는 곧 "서버가 감당하지 못한 비율"이어야 한다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

// 받아들여진 요청
const issued = new Counter('coupon_issued');
const replayed = new Counter('coupon_replayed');

// 싼 거절 — 서버가 의도적으로 빠르게 쳐낸 것
const outOfStock = new Counter('coupon_out_of_stock');
const duplicated = new Counter('coupon_duplicated');
const rateLimited = new Counter('coupon_rate_limited');
const otherRejected = new Counter('coupon_other_rejected');

// 비싼 실패 — 서버가 감당하지 못한 것.
const responseTimeout = new Counter('coupon_response_timeout');
const otherError = new Counter('coupon_other_error');
const serverError = new Counter('coupon_server_error');

const acceptedDuration = new Trend('coupon_accepted_duration', true);
const rejectedDuration = new Trend('coupon_rejected_duration', true);

export const options = {
    discardResponseBodies: false,   // 409를 품절/중복으로 가르려면 본문이 필요하다
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        arrival: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PRE_VUS,
            maxVUs: MAX_VUS,
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

    console.log(`[유입] RATE=${RATE}/s DURATION=${DURATION} TIMEOUT=${TIMEOUT} maxVUs=${MAX_VUS} 유저풀=${tokens.length}`);

    const planned = RATE * parseInt(DURATION, 10);
    if (planned > tokens.length) {
        console.log(`  경고: 예정 요청 ${planned}건 > 유저풀 ${tokens.length}명 — 토큰이 순환해 재생(200)이 섞인다`);
    }
}

export default function () {
    // 열린 루프에서는 VU가 이터레이션마다 재사용된다. issue.js처럼 VU 번호로 토큰을 고르면
    // 같은 유저가 반복 요청하게 되어 대부분이 재생(200)으로 나온다.
    // 이터레이션 번호로 골라야 "1명이 1번"이 된다. 유저풀을 넘으면 순환한다.
    const token = tokens[exec.scenario.iterationInTest % tokens.length];

    const res = http.post(`${BASE_URL}/campaigns/${CAMPAIGN_ID}/coupons`, null, {
        headers: { Authorization: `Bearer ${token}` },
        timeout: TIMEOUT,
    });

    if (res.status === 201) {
        issued.add(1);
        acceptedDuration.add(res.timings.duration);
        return;
    }

    if (res.status === 200) {
        replayed.add(1);
        acceptedDuration.add(res.timings.duration);
        return;
    }

    if (res.status === 409) {
        rejectedDuration.add(res.timings.duration);
        if (res.body.includes('OUT_OF_STOCK')) {
            outOfStock.add(1);
        } else if (res.body.includes('ALREADY_ISSUED')) {
            duplicated.add(1);
        } else {
            otherRejected.add(1);
        }
        return;
    }

    if (res.status === 429) {
        rateLimited.add(1);
        rejectedDuration.add(res.timings.duration);
        return;
    }

    if (res.status === 500) {
        serverError.add(1);
        return;
    }

    // status 0 — 응답을 못 받았다. TCP 연결조차 못 한 건도 여기 섞이는데 k6는 둘을 안 가른다
    // (연결 도중 타임아웃도 'request timeout'으로 보고된다).
    // 앱까지 들어왔는지는 measure.ps1이 재는 앱 요청 카운터 차분으로만 확정된다.
    if (res.status === 0) {
        if (res.error.includes('timeout')) {
            responseTimeout.add(1);
        } else {
            otherError.add(1);
        }
        return;
    }

    otherRejected.add(1);
}
