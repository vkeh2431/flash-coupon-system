# 발급 측정 1회 실행. 리셋 → 워밍업 → 리셋 → 측정 → 정착 대기 → 검산까지 한 번에 한다.
#
# 실행: .\scripts\measure.ps1 -Version v0 -Run 1
#       .\scripts\measure.ps1 -Version v0 -Run 2 -SkipWarmup     (연속 반복 시 워밍업 생략)

param(
    [Parameter(Mandatory = $true)][string]$Version,
    [int]$Run = 1,
    [int]$Stock = 500,
    [switch]$SkipWarmup
)

# docker의 진행 로그는 stderr로 나온다. PowerShell 5.1에서 stderr를 리다이렉트하면
# 줄마다 에러 레코드가 되므로, stdout만 받고 종료 코드로 성공 여부를 판정한다.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$resultDir = Join-Path $root 'k6\results'
if (-not (Test-Path $resultDir)) { New-Item -ItemType Directory -Path $resultDir | Out-Null }
$resultFile = Join-Path $resultDir "$Version-stock$Stock-run$Run.txt"

function Invoke-Sql {
    param([string]$Sql)
    docker compose exec -T -e MYSQL_PWD=coupon mysql mysql -ucoupon coupon -N -B -e $Sql
}

function Reset-Campaign {
    Invoke-Sql @"
DELETE FROM coupon;
DELETE FROM campaign;
ALTER TABLE campaign AUTO_INCREMENT = 1;
INSERT INTO campaign (name, opens_at, closes_at, total_quantity, remaining_quantity, paused)
VALUES ('load', NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 1 DAY, $Stock, $Stock, 0);
"@ | Out-Null
}

# k6가 끝나도 서버는 남은 트랜잭션을 계속 처리한다. 여기서 기다리지 않고 세면 진행 중인 건을 놓친다.
function Wait-Settled {
    $last = -1
    $stable = 0
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $count = [int]((Invoke-Sql "SELECT COUNT(*) FROM coupon;") | Select-Object -First 1)
        if ($count -eq $last) { $stable++ } else { $stable = 0 }
        if ($stable -ge 2) { return $count }
        $last = $count
    }
    Write-Host "  경고: 120초가 지나도 발급이 멎지 않았다" -ForegroundColor Yellow
    return $last
}

Write-Host ""
Write-Host "=== 측정 전 확인 ===" -ForegroundColor Cyan
Write-Host "  IntelliJ / 브라우저 등 백그라운드 앱 종료"
Write-Host "  전원 어댑터 연결 + 고성능 모드"
Write-Host "  확인되지 않았다면 지금 중단하고(Ctrl+C) 정리 후 다시 실행할 것"
Write-Host ""
Write-Host "=== $Version / 재고 $Stock / $Run 회차 ===" -ForegroundColor Cyan

if (-not $SkipWarmup) {
    Write-Host "[1/4] 워밍업 (JIT 예열 — 기록하지 않음)"
    Reset-Campaign
    docker compose --profile load run --rm -e STOCK=$Stock k6 run /scripts/issue.js | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "  워밍업 k6 종료 코드 $LASTEXITCODE" -ForegroundColor Yellow }
    Wait-Settled | Out-Null
}

Write-Host "[2/4] 리셋"
Reset-Campaign

Write-Host "[3/4] 측정"
# k6 요약은 stdout으로 나온다. 진행 로그와 경고는 stderr라 파일에 섞이지 않는다.
docker compose --profile load run --rm -e STOCK=$Stock k6 run /scripts/issue.js |
    Set-Content $resultFile -Encoding utf8
if ($LASTEXITCODE -ne 0) { Write-Host "  k6 종료 코드 $LASTEXITCODE — 요약을 확인할 것" -ForegroundColor Yellow }

Write-Host "[4/4] 정착 대기 후 검산"
$coupons = Wait-Settled

$verify = Invoke-Sql @"
SELECT total_quantity, remaining_quantity,
       (SELECT COUNT(*) FROM coupon) AS issued,
       (SELECT COUNT(*) FROM coupon) - total_quantity AS over_issued,
       total_quantity - remaining_quantity - (SELECT COUNT(*) FROM coupon) AS mismatch,
       (SELECT TIMESTAMPDIFF(MICROSECOND, MIN(issued_at), MAX(issued_at))/1000000 FROM coupon) AS span_sec
FROM campaign WHERE id = 1;
"@
$f = ($verify | Select-Object -First 1) -split "`t"

$summary = @"

===== DB 검산 =====
재고(total)     : $($f[0])
재고(remaining) : $($f[1])
COUNT(*)        : $($f[2])
초과 발급       : $($f[3])
검산식 불일치   : $($f[4])   (total - remaining - COUNT(*), 0이어야 정상)
발급 시간 폭    : $($f[5]) 초
===================
"@

Add-Content -Path $resultFile -Value $summary -Encoding utf8
Write-Host $summary
Write-Host "결과: $resultFile" -ForegroundColor Green
