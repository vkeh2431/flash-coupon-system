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
INSERT INTO campaign (name, opens_at, closes_at, total_quantity, remaining_quantity, paused, version)
VALUES ('load', NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL 1 DAY, $Stock, $Stock, 0, 0);
"@ | Out-Null
}

# 서버가 발급 경로에 대해 지금까지 낸 응답 수. 재시도 중인 요청은 아직 응답을 내지 않았으므로
# 이 값이 멈췄다는 것은 서버 안에 살아 있는 요청이 없다는 뜻이다.
function Get-ServedRequests {
    try {
        $text = (Invoke-WebRequest "http://localhost:8080/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
        $sum = 0
        foreach ($line in ($text -split "`n")) {
            if ($line.StartsWith('http_server_requests_seconds_count') -and $line.Contains('/coupons"')) {
                $sum += [double]($line -split '\s+')[-1]
            }
        }
        return [long]$sum
    } catch {
        Write-Host "  경고: 서버 응답 수를 읽지 못했다 — 쿠폰 수만으로 판정한다" -ForegroundColor Yellow
        return -1
    }
}

# k6가 끝나도 서버는 남은 트랜잭션을 계속 처리한다. 여기서 기다리지 않고 세면 진행 중인 건을 놓친다.
# 쿠폰 수만 보면 안 된다 — 재시도나 대기가 있는 버전은 DB에 아무것도 쓰지 않는 구간이 길어서,
# 아직 요청이 살아 있는데도 정착으로 오판한다. 그 상태로 리셋하면 이전 실행의 요청이
# 다음 측정의 재고를 먹는다. 서버가 낸 응답 수까지 같이 멈춰야 정착으로 본다.
function Wait-Settled {
    $lastCount = -1
    $lastServed = -1
    $stable = 0
    for ($i = 0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        $count = [int]((Invoke-Sql "SELECT COUNT(*) FROM coupon;") | Select-Object -First 1)
        $served = Get-ServedRequests
        if ($count -eq $lastCount -and $served -eq $lastServed) { $stable++ } else { $stable = 0 }
        if ($stable -ge 2) { return $count }
        $lastCount = $count
        $lastServed = $served
    }
    Write-Host "  경고: 180초가 지나도 요청이 멎지 않았다" -ForegroundColor Yellow
    return $lastCount
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
