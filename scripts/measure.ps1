# 발급 측정 1회 실행. 리셋 → 워밍업 → 리셋 → 측정 → 정착 대기 → 검산까지 한 번에 한다.
#
# 실행: .\scripts\measure.ps1 -Version v0 -Run 1
#       .\scripts\measure.ps1 -Version v0 -Run 2 -SkipWarmup     (연속 반복 시 워밍업 생략)

param(
    [Parameter(Mandatory = $true)][string]$Version,
    [int]$Run = 1,
    [int]$Stock = 500,
    [string]$OutDir = 'k6\results',
    [string]$Script = 'issue.js',
    [switch]$SkipWarmup
)

# 측정 고정 조건. 늘린 근거는 노션 노트에 있다.
$WARMUP_RUNS = 3

# docker의 진행 로그는 stderr로 나온다. PowerShell 5.1에서 stderr를 리다이렉트하면
# 줄마다 에러 레코드가 되므로, stdout만 받고 종료 코드로 성공 여부를 판정한다.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$resultDir = Join-Path $root $OutDir
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

    # 캠페인 생성 API를 타면 앱이 게이트를 열어주지만, 측정용 캠페인은 SQL로 직접 넣으므로
    # 같은 상태를 여기서 만든다. 시각은 위 INSERT의 NOW() 기준과 맞춘 값이다.
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $opensAt = $now - 60000
    $closesAt = $now + 86400000
    docker compose exec -T redis redis-cli DEL campaign:1:issued campaign:1:stock campaign:1:meta | Out-Null
    docker compose exec -T redis redis-cli SET campaign:1:stock $Stock | Out-Null
    # meta를 마지막에 넣는다 — 그 전까지 발급은 NOT_FOUND로 막힌다
    docker compose exec -T redis redis-cli HSET campaign:1:meta opensAt $opensAt closesAt $closesAt paused 0 | Out-Null
}

function Get-RedisStock {
    $value = docker compose exec -T redis redis-cli GET campaign:1:stock | Select-Object -First 1
    return [int]$value
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
    for ($w = 1; $w -le $WARMUP_RUNS; $w++) {
        Write-Host "[1/4] 워밍업 $w/$WARMUP_RUNS (JIT 예열 — 기록하지 않음)"
        Reset-Campaign
        docker compose --profile load run --rm -e STOCK=$Stock k6 run "/scripts/$Script" | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Host "  워밍업 k6 종료 코드 $LASTEXITCODE" -ForegroundColor Yellow }
        Wait-Settled | Out-Null
    }
}

Write-Host "[2/4] 리셋"
Reset-Campaign

Write-Host "[3/4] 측정"
# k6 요약은 stdout으로 나온다. 진행 로그와 경고는 stderr라 파일에 섞이지 않는다.
docker compose --profile load run --rm -e STOCK=$Stock k6 run "/scripts/$Script" |
    Set-Content $resultFile -Encoding utf8
if ($LASTEXITCODE -ne 0) { Write-Host "  k6 종료 코드 $LASTEXITCODE — 요약을 확인할 것" -ForegroundColor Yellow }

Write-Host "[4/4] 정착 대기 후 검산"
$coupons = Wait-Settled

# 차감이 일어나는 곳이 버전마다 다르다. v0~v3는 DB 재고가, v4는 Redis 재고가 줄어든다.
# 둘 다 출력하고 해당하는 쪽을 읽는다 — 해당 없는 쪽은 -COUNT(*)가 나온다.
$redisStock = Get-RedisStock
$verify = Invoke-Sql @"
SELECT total_quantity, remaining_quantity,
       (SELECT COUNT(*) FROM coupon) AS issued,
       (SELECT COUNT(*) FROM coupon) - total_quantity AS over_issued,
       total_quantity - remaining_quantity - (SELECT COUNT(*) FROM coupon) AS db_mismatch,
       (SELECT TIMESTAMPDIFF(MICROSECOND, MIN(issued_at), MAX(issued_at))/1000000 FROM coupon) AS span_sec
FROM campaign WHERE id = 1;
"@
$f = ($verify | Select-Object -First 1) -split "`t"
$redisMismatch = ([int]$f[0] - $redisStock) - [int]$f[2]

$summary = @"

===== 검산 =====
재고(total)         : $($f[0])
재고(DB)            : $($f[1])
재고(Redis)         : $redisStock
COUNT(*)            : $($f[2])
초과 발급           : $($f[3])   (COUNT(*) - total)
불일치 · DB 기준    : $($f[4])   v0~v3용 (lost update)
불일치 · Redis 기준 : $redisMismatch   v4용 (drift)
발급 시간 폭        : $($f[5]) 초
================
"@

Add-Content -Path $resultFile -Value $summary -Encoding utf8
Write-Host $summary
Write-Host "결과: $resultFile" -ForegroundColor Green
