# 공통: 저장소 최상위 .env 를 읽어 해시테이블로 돌려준다 (다른 스크립트가 . 으로 불러 씀)
$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $RepoRoot ".env"

function Read-DotEnv {
    if (-not (Test-Path $EnvFile)) {
        throw ".env 가 없습니다. 먼저 'Copy-Item .env.example .env' 후 값을 채우세요. ($EnvFile)"
    }
    $values = @{}
    foreach ($line in Get-Content $EnvFile -Encoding UTF8) {
        $t = $line.Trim()
        if ($t -eq "" -or $t.StartsWith("#") -or -not $t.Contains("=")) { continue }
        $i = $t.IndexOf("=")
        $values[$t.Substring(0, $i).Trim()] = $t.Substring($i + 1).Trim()
    }
    return $values
}

function Get-VenvPython {
    $py = Join-Path $RepoRoot "ai-server-python\.venv\Scripts\python.exe"
    if (Test-Path $py) { return $py }
    # conda 환경(nuada-bci 등)을 켜 둔 상태면 그 python 사용
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "Python 을 찾을 수 없습니다. scripts\setup-python.ps1 을 먼저 실행하세요."
}
