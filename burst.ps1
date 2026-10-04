param (
    [string]$TargetUrl = "https://ticket-booking-service-production-27ac.up.railway.app"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Running Burst Load Test against: $TargetUrl" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (Get-Command python -ErrorAction SilentlyContinue) {
    python -m pip install -q aiohttp 2>$null
    python scripts\burst.py --base-url "$TargetUrl"
} elseif (Get-Command py -ErrorAction SilentlyContinue) {
    py -m pip install -q aiohttp 2>$null
    py scripts\burst.py --base-url "$TargetUrl"
} elseif (Get-Command java -ErrorAction SilentlyContinue) {
    java scripts\BurstTest.java "$TargetUrl"
} else {
    Write-Error "Neither python nor java was found in PATH to execute the burst test."
}
