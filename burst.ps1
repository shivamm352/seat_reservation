param (
    [string]$TargetUrl = "http://localhost:8080"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running Burst Load Test against: $TargetUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

py -m pip install -q aiohttp 2>$null
if ($LASTEXITCODE -ne 0) {
    python -m pip install -q aiohttp 2>$null
}

if (Get-Command py -ErrorAction SilentlyContinue) {
    py scripts\burst.py --base-url "$TargetUrl"
} else {
    python scripts\burst.py --base-url "$TargetUrl"
}
