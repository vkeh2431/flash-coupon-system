import http from 'k6/http';

// ⓪ 캘리브레이션 — C3': 앱 처리 천장 근사
//
// 두 가지를 잰다:
//   ⒜ TOKEN 없이 /actuator/health  → Spring/Tomcat 자체 상한 (인증 필터 미포함)
//   ⒝ TOKEN 붙여 /me/coupons       → JWT 검증 + user 조회까지 포함한 실제 경로에 가까운 값
// 둘의 차이가 곧 "인증 필터를 태우기로 한 결정"의 비용이다.
//
// 실행:
//   -e RATE=2000 -e URL=http://app:8080/actuator/health
//   -e RATE=1000 -e URL=http://app:8080/me/coupons -e TOKEN=<accessToken>

const RATE = parseInt(__ENV.RATE);
const DURATION = __ENV.DURATION || '60s';
const URL = __ENV.URL;
const TOKEN = __ENV.TOKEN || '';
const PRE_VUS = parseInt(__ENV.PRE_VUS || '600');
const MAX_VUS = parseInt(__ENV.MAX_VUS || '2000');

if (!RATE || !URL) {
    throw new Error('RATE와 URL 환경변수가 필요합니다.');
}

const params = TOKEN ? { headers: { Authorization: `Bearer ${TOKEN}` } } : {};

export const options = {
    discardResponseBodies: true,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        c3: {
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
    console.log(`[C3'] RATE=${RATE}/s  URL=${URL}  auth=${TOKEN ? 'yes' : 'no'}`);
}

export default function () {
    http.get(URL, params);
}
