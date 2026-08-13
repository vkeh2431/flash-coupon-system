# 4주차 대기열 Before — 도착률 계단 측정.
# 앱 실효 처리량 아래부터 그 몇 배까지 R을 올리며 무엇이 먼저 죽는지 본다.
#
# 실행: .\scripts\sweep-arrival.ps1
#       .\scripts\sweep-arrival.ps1 -Rates 500,1000 -Runs 1        (일부만)

param(
    [int[]]$Rates = @(500, 1000, 1500, 2000, 3000),
    [int]$Runs = 3,
    [int]$Stock = 10000,
    [string]$Version = 'before'
)

# maxVUs = R × 10. 이 배수를 밑돌면 드롭이 나고, 그 순간 도착률이 R이 아니라
# maxVUs ÷ 응답시간이 되어 열린 루프가 성립하지 않는다. 배수를 바꾸면 이전 계단과 비교가 끊긴다.
$VU_FACTOR = 10

$warmedUp = $false

foreach ($rate in $Rates) {
    for ($run = 1; $run -le $Runs; $run++) {
        $callArgs = @{
            Version = $Version
            Stock   = $Stock
            Rate    = $rate
            MaxVus  = $rate * $VU_FACTOR
            Run     = $run
            Script  = 'issue-arrival.js'
        }
        # 워밍업은 스윕 전체에서 맨 처음 한 번만. JIT는 한 번 데워지면 이후 회차까지 유지된다.
        if ($warmedUp) { $callArgs['SkipWarmup'] = $true }
        $warmedUp = $true

        & "$PSScriptRoot\measure.ps1" @callArgs
    }
}

Write-Host ""
Write-Host "=== 스윕 완료 ===" -ForegroundColor Green
