# ==========================================================
# Spring 관문 서버 + 화면 실행 (로컬) → http://localhost:8080
#   - JDK 21 필요, 로컬 MySQL 에 DB·계정이 있어야 함 (setup-local-db.ps1)
#   - 비밀 값은 Spring 이 저장소 최상위 .env 를 직접 읽는다 (application.yaml 의 spring.config.import)
#   - IntelliJ 에서는 BackendApplication 을 그냥 실행해도 같다
# 사용: .\scripts\run-backend.ps1        (끄기: Ctrl+C)
# ==========================================================
. "$PSScriptRoot\_env.ps1"
$null = Read-DotEnv   # .env 가 있는지만 확인
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java 를 찾을 수 없습니다. JDK 21 을 설치한 뒤 터미널을 새로 여세요 (docs\local-run.md)."
}
Push-Location (Join-Path $RepoRoot "backend-spring")
try { .\gradlew.bat bootRun } finally { Pop-Location }
