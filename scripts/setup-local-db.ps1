# ==========================================================
# 로컬 MySQL 에 프로젝트용 DB 와 계정을 만든다 (처음 한 번)
#   - DB 이름·계정·비밀번호는 .env 의 MYSQL_DATABASE / MYSQL_USER / MYSQL_PASSWORD
#   - 실행하면 MySQL root 비밀번호를 물어본다 (MySQL 설치 때 정한 것)
#   - 여러 번 실행해도 안전 (이미 있으면 비밀번호만 .env 값으로 맞춤)
# 사용: PowerShell 에서  .\scripts\setup-local-db.ps1
# ==========================================================
. "$PSScriptRoot\_env.ps1"
$envs = Read-DotEnv
$db = $envs["MYSQL_DATABASE"]; $user = $envs["MYSQL_USER"]; $pw = $envs["MYSQL_PASSWORD"]
if (-not $db -or -not $user -or -not $pw) { throw ".env 에 MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD 가 모두 있어야 합니다." }
if ($pw.Contains("'")) { throw "MYSQL_PASSWORD 에 작은따옴표(') 는 쓸 수 없습니다." }

# mysql.exe 찾기: PATH → 기본 설치 폴더
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql) {
    $mysql = Get-ChildItem "C:\Program Files\MySQL\MySQL Server *\bin\mysql.exe" -ErrorAction SilentlyContinue |
             Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mysql) { throw "mysql.exe 를 찾을 수 없습니다. MySQL Server 8 을 설치했는지 확인하세요 (docs\local-run.md)." }

$sql = @"
CREATE DATABASE IF NOT EXISTS ``$db`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$user'@'localhost' IDENTIFIED BY '$pw';
ALTER USER '$user'@'localhost' IDENTIFIED BY '$pw';
GRANT ALL PRIVILEGES ON ``$db``.* TO '$user'@'localhost';
FLUSH PRIVILEGES;
"@

Write-Host "MySQL: $mysql"
Write-Host "DB '$db', 계정 '$user'@localhost 를 만듭니다. root 비밀번호를 입력하세요."
$sql | & $mysql -u root -p
if ($LASTEXITCODE -eq 0) { Write-Host "완료. 이제 .\scripts\run-backend.ps1 로 Spring 을 실행하세요." -ForegroundColor Green }
else { Write-Host "실패 (종료 코드 $LASTEXITCODE). root 비밀번호와 MySQL 서비스 실행 여부를 확인하세요." -ForegroundColor Red }
