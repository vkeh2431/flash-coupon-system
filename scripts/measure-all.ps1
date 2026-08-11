# v0~v4 태그를 순회하며 같은 조건으로 기록 측정한다.
#
# 앱 소스만 태그 시점으로 되돌리고 측정 도구(k6 스크립트 · measure.ps1 · compose)는 현재 것을 쓴다.
# 도구까지 과거로 돌리면 버전마다 다른 자로 재게 된다.
#
# 실행: .\scripts\measure-all.ps1

param(
    [int]$Stock = 500,
    [int]$Runs = 3,
    [string]$OutDir = 'k6\results\final'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$versions = [ordered]@{
    'v0' = 'v0-no-lock'
    'v1' = 'v1-pessimistic'
    'v2' = 'v2-optimistic'
    'v3' = 'v3-distributed-lock'
    'v4' = 'v4-redis-lua'
}

# src를 통째로 되돌리므로 커밋 안 된 변경은 복구할 수 없다.
$dirty = git status --porcelain
if ($dirty) {
    Write-Host "워킹트리가 깨끗하지 않다. 커밋하거나 stash한 뒤 다시 실행할 것:" -ForegroundColor Red
    $dirty | ForEach-Object { Write-Host "  $_" }
    exit 1
}

function Wait-AppReady {
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $res = Invoke-WebRequest "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 3
            if ($res.StatusCode -eq 200) { return $true }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    return $false
}

function Restore-Source {
    Remove-Item -Recurse -Force (Join-Path $root 'src') -ErrorAction SilentlyContinue
    git checkout HEAD -- src build.gradle
}

Write-Host ""
Write-Host "=== 측정 전 확인 ===" -ForegroundColor Cyan
Write-Host "  IntelliJ / 브라우저 등 백그라운드 앱 종료"
Write-Host "  전원 어댑터 연결 + 고성능 모드"
Write-Host "  $($versions.Count)개 버전 x $Runs 회. 중간에 멈추면 그 경계를 기록할 것"
Write-Host ""

try {
    foreach ($label in $versions.Keys) {
        $tag = $versions[$label]
        Write-Host ""
        Write-Host "########## $label ($tag) ##########" -ForegroundColor Yellow

        # 지우지 않고 checkout만 하면 그 태그에 없던 파일이 남아 컴파일이 깨진다.
        Remove-Item -Recurse -Force (Join-Path $root 'src')
        git checkout $tag -- src build.gradle

        docker compose up -d --build app | Out-Null
        if (-not (Wait-AppReady)) { throw "$label 앱이 뜨지 않았다" }

        for ($r = 1; $r -le $Runs; $r++) {
            # 워밍업은 버전마다 한 번만. 2회차부터는 앞 회차가 예열을 이어받는다.
            & (Join-Path $PSScriptRoot 'measure.ps1') `
                -Version $label -Run $r -Stock $Stock -OutDir $OutDir -SkipWarmup:($r -ne 1)
        }
    }
} finally {
    Restore-Source
    docker compose up -d --build app | Out-Null
    Write-Host ""
    Write-Host "소스를 원복하고 현재 코드로 다시 띄웠다." -ForegroundColor Green
}
