# ==========================================================
# Python 가상환경(ai-server-python\.venv) 을 만들고 재생기 + RAG 패키지를 설치한다 (처음 한 번)
#   - Python 3.11 권장 (모델을 학습한 conda 환경과 같은 버전). 없으면 설치된 python 사용
#   - conda 환경 nuada-bci 를 쓰는 경우: 이 스크립트 대신
#       conda activate nuada-bci
#       pip install -r ai-server-python\requirements.txt
#       pip install -r ai-server-python\requirements-rag.txt   (두 번에 나눠서)
# 사용: .\scripts\setup-python.ps1
# ==========================================================
. "$PSScriptRoot\_env.ps1"
$dir = Join-Path $RepoRoot "ai-server-python"
$venv = Join-Path $dir ".venv"

if (-not (Test-Path $venv)) {
    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if ($launcher -and (& py -3.11 --version 2>$null)) {
        Write-Host "Python 3.11 로 가상환경을 만듭니다."
        & py -3.11 -m venv $venv
    } else {
        Write-Host "Python 3.11 이 없어 기본 python 으로 만듭니다: $(python --version)"
        & python -m venv $venv
    }
}
$py = Join-Path $venv "Scripts\python.exe"
& $py -m pip install --upgrade pip
# 두 목록을 한 번에 넣으면 pip 의존성 계산이 너무 깊어져 실패하므로 나눠서 설치
& $py -m pip install -r (Join-Path $dir "requirements.txt")
& $py -m pip install -r (Join-Path $dir "requirements-rag.txt")
& $py -c "import mne, moabb, sklearn, chromadb, fastapi, google.genai, websockets; print('설치 확인 완료')"
