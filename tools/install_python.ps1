# ==============================================================================
#  تثبيت أحدث إصدار من بايثون على Windows 10 / 11
#  1) المثبّت الرسمي المرفق داخل الملف المضغوط (python-X.Y.Z-amd64.exe) — بدون إنترنت
#  2) أو Python Install Manager (.msix) المرفق / المحمّل من python.org
#  تصميم: Hamza Alfarai
#
#  الاستخدام:
#    install_python.bat              -> يثبّت بايثون المرفق (موصى به)
#    install_python.bat -UseManager  -> يستخدم Python Install Manager بدلاً منه
# ==============================================================================
param([switch]$UseManager)

$ErrorActionPreference = "Stop"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Split-Path -Parent $Here

Write-Host "==================================================" -ForegroundColor Magenta
Write-Host "  Claude Code Desktop  —  تصميم Hamza Alfarai"     -ForegroundColor Magenta
Write-Host "  تثبيت أحدث إصدار من Python"                       -ForegroundColor Magenta
Write-Host "==================================================" -ForegroundColor Magenta

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable("Path","User") + ";" +
                [Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                "$env:LOCALAPPDATA\Microsoft\WindowsApps"
}

# ------------------------------------------------------------------------------
#  الطريقة 1: المثبّت الرسمي المرفق (أحدث إصدار) — يعمل بدون إنترنت
# ------------------------------------------------------------------------------
$PyExe = Get-ChildItem -Path $Root -Filter "python-*-amd64.exe" -ErrorAction SilentlyContinue |
         Sort-Object Name -Descending | Select-Object -First 1

if ($PyExe -and -not $UseManager) {
    Write-Host "[1/2] تثبيت $($PyExe.Name) (مرفق داخل الحزمة)..." -ForegroundColor Cyan
    $instArgs = @("/passive", "InstallAllUsers=0", "PrependPath=1", "Include_launcher=1",
              "Include_pip=1", "Include_tcltk=1", "Include_test=0", "Shortcuts=1")
    $p = Start-Process -FilePath $PyExe.FullName -ArgumentList $instArgs -Wait -PassThru
    if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) {
        throw "فشل تثبيت بايثون (رمز الخطأ $($p.ExitCode))"
    }
    Refresh-Path
    Write-Host "[2/2] التحقق من التثبيت..." -ForegroundColor Cyan
    try { & py --version } catch { & python --version }
    Write-Host ""
    Write-Host "تم! يمكنك الآن تشغيل:  run_claude_code.bat  أو  py claude_code_suite.py" -ForegroundColor Green
    exit 0
}

# ------------------------------------------------------------------------------
#  الطريقة 2: Python Install Manager (.msix)
# ------------------------------------------------------------------------------
$Msix = Get-ChildItem -Path $Root -Filter "python-manager-*.msix" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1

if (-not $Msix) {
    $Ver = "26.4"
    $MsixPath = Join-Path $Root "python-manager-$Ver.msix"
    Write-Host "[1/3] تحميل python-manager-$Ver.msix من python.org..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri "https://www.python.org/ftp/python/pymanager/python-manager-$Ver.msix" -OutFile $MsixPath -UseBasicParsing
    $Msix = Get-Item $MsixPath
} else {
    Write-Host "[1/3] تم العثور على $($Msix.Name) داخل الحزمة." -ForegroundColor Cyan
}

Write-Host "[2/3] تثبيت Python Install Manager..." -ForegroundColor Cyan
try {
    Add-AppxPackage -Path $Msix.FullName
} catch {
    Write-Host "  ! ربما هو مثبت مسبقاً: $($_.Exception.Message)" -ForegroundColor Yellow
}
Refresh-Path

Write-Host "[3/3] تثبيت أحدث إصدار مستقر من Python..." -ForegroundColor Cyan
& py install default
& py list

Write-Host ""
Write-Host "تم! يمكنك الآن تشغيل:  run_claude_code.bat  أو  py claude_code_suite.py" -ForegroundColor Green
