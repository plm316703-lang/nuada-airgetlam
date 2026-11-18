# ==========================================================
# 판별기 재생기 실행 (로컬). Spring 서버가 먼저 떠 있어야 한다.
#   실행 중 키 입력 + Enter 로 오류 주입:
#     e=전극 불량  n=전원 잡음  m=근육 잡음  x=판단 불일치  s=오래된 명령  d=5초 끊김  r=12초 끊김  q=종료
# 사용 예:
#   .\scripts\run-replayer.ps1
#   .\scripts\run-replayer.ps1 -Scenario electrode -At 10
# ==========================================================
param(
    [ValidateSet("", "electrode", "noise", "muscle", "mismatch", "stale", "disconnect", "reset")]
    [string]$Scenario = "",
    [int]$At = 8
)
. "$PSScriptRoot\_env.ps1"
$py = Get-VenvPython
$replayerArgs = @("realtime\replayer.py")
if ($Scenario) { $replayerArgs += @("--scenario", $Scenario, "--at", $At) }
Push-Location (Join-Path $RepoRoot "ai-server-python")
try { & $py @replayerArgs } finally { Pop-Location }
