# ==============================================================================
#  تثبيت أحدث إصدار من بايثون على Windows 10 / 11
#  Python Install Manager 26.4 (الإصدار الرسمي الأحدث) + أحدث Python
#  تصميم: Hamza Alfarai
# ==============================================================================
$ErrorActionPreference = "Stop"
$PyManagerVersion = "26.4"
$MsixName = "python-manager-$PyManagerVersion.msix"
$MsixUrl  = "https://www.python.org/ftp/python/pymanager/$MsixName"
$Here     = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root     = Split-Path -Parent $Here
$MsixPath = Join-Path $Root $MsixName

Write-Host "==================================================" -ForegroundColor Magenta
Write-Host "  Claude Code Desktop  —  تصميم Hamza Alfarai"     -ForegroundColor Magenta
Write-Host "  تثبيت Python Install Manager $PyManagerVersion + أحدث Python" -ForegroundColor Magenta
Write-Host "==================================================" -ForegroundColor Magenta

if (-not (Test-Path $MsixPath)) {
    Write-Host "[1/3] تحميل $MsixName من موقع python.org الرسمي..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $MsixUrl -OutFile $MsixPath -UseBasicParsing
} else {
    Write-Host "[1/3] تم العثور على $MsixName داخل المجلد." -ForegroundColor Cyan
}

Write-Host "[2/3] تثبيت Python Install Manager..." -ForegroundColor Cyan
try {
    Add-AppxPackage -Path $MsixPath
} catch {
    Write-Host "  ! ربما هو مثبت مسبقاً: $($_.Exception.Message)" -ForegroundColor Yellow
}

# تحديث متغير PATH للجلسة الحالية
$env:Path = [Environment]::GetEnvironmentVariable("Path","User") + ";" + [Environment]::GetEnvironmentVariable("Path","Machine") + ";" + "$env:LOCALAPPDATA\Microsoft\WindowsApps"

Write-Host "[3/3] تثبيت أحدث إصدار مستقر من Python..." -ForegroundColor Cyan
& py install default
& py list

Write-Host ""
Write-Host "تم! يمكنك الآن تشغيل:  py claude_code_suite.py" -ForegroundColor Green
