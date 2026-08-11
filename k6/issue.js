import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// 본 측정 — 선착순 발급 경로 (v0~v4 공통)
// 실행: docker compose --profile load run --rm -e STOCK=500 k6 run /scripts/issue.js

const STOCK = parseInt(__ENV.STOCK);
if (!STOCK) {
    throw new Error('STOCK 환경변수가 필요합니다. 예) -e STOCK=500');
}

const BASE_URL = 'http://app:8080';
const CAMPAIGN_ID = 1;      // 리셋 스크립트가 AUTO_INCREMENT를 되돌려 항상 1로 맞춘다
const VUS = 5000;           // 동시 요청 수. Tomcat maxConnections(8192) 아래로 잡는다
const TIMEOUT = '30s';      // 이 값이 곧 실행 시간의 상한이 된다

const tokens = new SharedArray('tokens', () =>
    open('/scripts/tokens.txt').trim().split(/\r?\n/)   // \r이 남으면 Authorization 헤더가 통째로 거부된다
);

http.setResponseCallback(http.expectedStatuses(201, 409));

const issued = new Counter('coupon_issued');
const outOfStock = new Counter('coupon_out_of_stock');
const duplicated = new Counter('coupon_duplicated');
const dbError = new Counter('coupon_db_error');
const timedOut = new Counter('coupon_timeout');
const connectionError = new Counter('coupon_connection_error');
const unexpected = new Counter('coupon_unexpected');

const issuedDuration = new Trend('coupon_issued_duration', true);
const rejectedDuration = new Trend('coupon_rejected_duration', true);

export const options = {
    discardResponseBodies: false,   // 409를 품절/중복으로 가르려면 본문이 필요하다
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        burst: {
            executor: 'per-vu-iterations',   // VU 하나가 딱 한 번. 오픈 시각의 수직 상승
            vus: VUS,
            iterations: 1,
            maxDuration: '90s',              // TIMEOUT보다 넉넉해야 한다. 여기 걸리면 꼬리가 잘린다
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
    console.log(`[발급] STOCK=${STOCK} VUS=${VUS} TIMEOUT=${TIMEOUT} 유저풀=${tokens.length}`);
}

export default function () {
    const token = tokens[exec.vu.idInTest - 1];    // VU 1명 = 유저 1명, 겹치지 않는다

    const res = http.post(`${BASE_URL}/campaigns/${CAMPAIGN_ID}/coupons`, null, {
        headers: { Authorization: `Bearer ${token}` },
        timeout: TIMEOUT,
    });

    if (res.status === 201) {
        issued.add(1);
        issuedDuration.add(res.timings.duration);
        return;
    }

    if (res.status === 409) {
        rejectedDuration.add(res.timings.duration);
        // 에러 코드는 본문에만 있다. JSON 파싱보다 부분 문자열 검사가 싸다.
        if (res.body.includes('OUT_OF_STOCK')) {
            outOfStock.add(1);
        } else if (res.body.includes('ALREADY_ISSUED')) {
            duplicated.add(1);
        } else {
            unexpected.add(1);
        }
        return;
    }

    if (res.status === 500) {
        dbError.add(1);
        return;
    }

    // status 0 — 응답 자체가 없다. 연결 실패가 섞이면 Tomcat 상한을 건드렸다는 신호다.
    if (res.status === 0) {
        if (res.error.includes('timeout')) {
            timedOut.add(1);
        } else {
            connectionError.add(1);
        }
        return;
    }

    unexpected.add(1);
}
