import http from 'k6/http';

// ⓪ 캘리브레이션 — C0 확정용 (고정 도착률)
//
// c0.js(램프)로 무릎의 대략적 위치를 잡은 뒤, 여기서 한 점씩 찍어 확정한다.
// 판정 기준은 단순하다:
//   요약의 http_reqs rate ≈ RATE  →  그 도착률을 감당한 것
//   http_reqs rate  <  RATE       →  천장을 넘어선 것
//
// 실행:
//   docker compose --profile calibration run --rm -e RATE=20000 k6 run /scripts/c0-fixed.js
//   docker compose --profile calibration run --rm -e RATE=20000 -e DURATION=60s ... (기본 60s)
//
// 본문은 고치지 않는다. 바뀌는 건 주입값뿐 — 그래야 점들끼리 비교가 성립한다.

const RATE = parseInt(__ENV.RATE);
const DURATION = __ENV.DURATION || '60s';

// VU는 "요청을 실제로 실행하는 일꾼"이다. 모자라면 k6 CPU가 남아돌아도 못 쏘고,
// 그러면 내가 만든 가짜 천장을 C0로 착각하게 된다.
//   Little's Law: 필요 VU ≈ 도착률 × 평균 응답시간
// 기본값은 넉넉하게 두되, 과하면 VU 자체가 k6 CPU를 먹으므로 무한정 키우지 않는다.
const PRE_VUS = parseInt(__ENV.PRE_VUS || '500');
const MAX_VUS = parseInt(__ENV.MAX_VUS || '2000');

if (!RATE) {
    throw new Error('RATE 환경변수가 필요합니다. 예) -e RATE=20000');
}

export const options = {
    // c0.js와 동일하게 유지 — 조건이 다르면 두 측정을 비교할 수 없다.
    discardResponseBodies: true,
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],

    scenarios: {
        c0_fixed: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PRE_VUS,
            maxVUs: MAX_VUS,
        },
    },
};

// 실행 조건을 출력에 남긴다 — 나중에 로그만 보고도 어떤 점을 잰 건지 알 수 있게.
// ⚠️ init 컨텍스트는 VU 하나당 한 번씩 실행되므로 여기 두면 VU 수만큼 찍힌다.
//    setup()은 테스트 전체에서 딱 한 번만 실행된다.
export function setup() {
    console.log(`[C0] RATE=${RATE}/s  DURATION=${DURATION}  preAllocatedVUs=${PRE_VUS}  maxVUs=${MAX_VUS}`);
}

export default function () {
    http.get('http://nginx/');
}
