# Exposes localhost:8080 so Razorpay can POST /webhooks/razorpay.
# Prefers cloudflared (no account). Falls back to ngrok if installed.

param(
	[int]$Port = 8080
)

$ErrorActionPreference = "Stop"

function Find-Command {
	param([string]$Name)
	return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Install-Cloudflared {
	Write-Host "cloudflared not found. Installing via winget..."
	winget install --id Cloudflare.cloudflared -e --accept-package-agreements --accept-source-agreements
	$machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
	$user = [Environment]::GetEnvironmentVariable("Path", "User")
	$env:Path = "$machine;$user"
}

if (-not (Find-Command "cloudflared") -and -not (Find-Command "ngrok")) {
	if (Find-Command "winget") {
		Install-Cloudflared
	}
}

if (Find-Command "cloudflared") {
	Write-Host "Starting cloudflared quick tunnel to http://localhost:$Port"
	Write-Host "Copy the https://*.trycloudflare.com URL, then in Razorpay Dashboard (Test Mode):"
	Write-Host "  Account & Settings -> Webhooks -> Add New Endpoint"
	Write-Host "  URL:    https://<tunnel-host>/webhooks/razorpay"
	Write-Host "  Secret: same value as RAZORPAY_WEBHOOK_SECRET in .env"
	Write-Host "  Events: payment.failed, payment.captured, order.paid,"
	Write-Host "          subscription.pending, subscription.halted, subscription.charged,"
	Write-Host "          invoice.paid, invoice.expired"
	Write-Host "Test-mode webhook OTP is 754081."
	Write-Host "Then in another window prove HMAC from Razorpay servers:"
	Write-Host "  .\prove-live-webhook.ps1"
	Write-Host ""
	& cloudflared tunnel --url "http://localhost:$Port"
	exit $LASTEXITCODE
}

if (Find-Command "ngrok") {
	Write-Host "Starting ngrok http $Port"
	Write-Host "Use https://<ngrok-host>/webhooks/razorpay in the Razorpay dashboard."
	& ngrok http $Port
	exit $LASTEXITCODE
}

Write-Error @"
Neither cloudflared nor ngrok is installed.

Install one of:
  winget install --id Cloudflare.cloudflared -e
  https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/
  https://ngrok.com/download

Then re-run this script.
"@
