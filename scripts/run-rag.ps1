# ==========================================================
# RAG 서버 실행 (로컬) → http://localhost:8000/health
#   - .env 의 GEMINI_API_KEY 필요 (없으면 서버는 뜨지만 안내 요청에 503)
# 사용: .\scripts\run-rag.ps1        (끄기: Ctrl+C)
# ==========================================================
. "$PSScriptRoot\_env.ps1"
$py = Get-VenvPython
Push-Location (Join-Path $RepoRoot "ai-server-python")
try { & $py rag\rag_server.py } finally { Pop-Location }
