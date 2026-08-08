import http from 'k6/http';

// k6가 낼 수 있는 최대 도착률

export const options = {
    // 응답 본문을 버려 k6의 CPU/메모리를 아낀다. 천장을 재는 게 목적이므로 켠다.
    discardResponseBodies: true,

    // 기본 요약에는 p99가 없다. 포화 판정에 필요하므로 추가.
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],

    scenarios: {
        c0: {
            executor: 'ramping-arrival-rate',
            timeUnit: '1s',
            startRate: 1000,

            // 도착률 기반이어도 요청을 실행하는 주체는 VU다.
            // VU가 모자라면 "쏘고 싶어도 못 쏘는" 가짜 천장이 생긴다.
            //   Little's Law: 필요 VU ≈ 도착률 × 평균 응답시간
            //   50,000/s × 1ms ≈ 50 VU, 포화로 10ms까지 늘어져도 ≈ 500 VU
            // → 1500이면 충분히 넉넉하다. 무작정 더 키우면 VU 자체가 k6 CPU를 먹어 C0를 깎는다.
            preAllocatedVUs: 300,
            maxVUs: 1500,

            // 계단마다 "올린 뒤 유지"를 쌍으로 둔다.
            // 유지 구간이 없으면 스쳐 지나가서 그 도착률을 실제로 감당했는지 알 수 없다.
            stages: [
                { target: 5000, duration: '20s' },   // ramp
                { target: 5000, duration: '20s' },   // hold
                { target: 15000, duration: '20s' },
                { target: 15000, duration: '20s' },
                { target: 30000, duration: '20s' },
                { target: 30000, duration: '20s' },
                { target: 50000, duration: '20s' },
                { target: 50000, duration: '20s' },
            ],
        },
    },
};

export default function () {
    // compose 네트워크 안에서는 서비스 이름이 곧 호스트명이다 (localhost/포트 매핑 아님).
    http.get('http://nginx/');
}
