# Prove a REAL Razorpay-signed webhook hit POST /webhooks/razorpay.
# Desk /simulate buttons skip HMAC. This script waits for origin=RAZORPAY.
#
# Prerequisites:
#   1. docker compose up (Postgres, Redis, Kafka healthy)
#   2. backend bootRun on :8080
#   3. recovery-engine/.env has RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET
#   4. In another window: .\tunnel.ps1
#   5. Razorpay Dashboard (Test Mode) webhook URL = https://<tunnel>/webhooks/razorpay
#      Secret must match RAZORPAY_WEBHOOK_SECRET. Events include payment.failed + payment.captured.

param(
	[int]$WaitSeconds = 180,
	[string]$Backend = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_common.ps1")

function Assert-BackendUp {
	try {
		$health = Invoke-RestMethod -Uri "$Backend/actuator/health" -TimeoutSec 5
	} catch {
		Write-Error "Backend is not reachable at $Backend. Start gradlew bootRun first."
	}
	if ($health.status -ne "UP") {
		Write-Error "Backend health is $($health.status). Need UP."
	}
}

function Get-CeoToken {
	$body = @{ email = "ceo@recovery.local"; password = "admin123" } | ConvertTo-Json
	$session = Invoke-RestMethod -Method POST -Uri "$Backend/api/auth/login" -ContentType "application/json" -Body $body
	if (-not $session.token) {
		Write-Error "CEO login failed. Is the desk seeder running?"
	}
	return $session.token
}

function Get-Inbox {
	param([string]$Token)
	return Invoke-RestMethod -Uri "$Backend/api/webhooks/inbox" -Headers @{ Authorization = "Bearer $Token" }
}

function New-FailingPaymentLink {
	$keyId = $DotEnv["RAZORPAY_KEY_ID"]
	$keySecret = $DotEnv["RAZORPAY_KEY_SECRET"]
	if ([string]::IsNullOrWhiteSpace($keyId) -or [string]::IsNullOrWhiteSpace($keySecret)) {
		Write-Error "RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are empty in recovery-engine/.env"
	}
	$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes(("{0}:{1}" -f $keyId, $keySecret)))
	$stamp = Get-Date -Format "yyyyMMddHHmmss"
	$payload = @{
		amount = 49900
		currency = "INR"
		accept_partial = $false
		description = "recovery-engine live HMAC proof $stamp"
		customer = @{
			name = "HMAC Proof"
			email = "hmac.proof+$stamp@example.com"
			contact = "+919000000000"
		}
		notify = @{ sms = $false; email = $false }
		reminder_enable = $false
		notes = @{ purpose = "live-hmac-proof" }
	} | ConvertTo-Json -Depth 5
	return Invoke-RestMethod -Method POST -Uri "https://api.razorpay.com/v1/payment_links" -Headers @{
		Authorization = "Basic $basic"
		"Content-Type" = "application/json"
	} -Body $payload
}

$secret = $DotEnv["RAZORPAY_WEBHOOK_SECRET"]
if ([string]::IsNullOrWhiteSpace($secret)) {
	Write-Error "RAZORPAY_WEBHOOK_SECRET is empty. Use the same secret in the Razorpay webhook."
}

Write-Host "Checking backend $Backend ..."
Assert-BackendUp
$token = Get-CeoToken
$before = Get-Inbox -Token $token
$seen = @{}
foreach ($row in @($before.events)) {
	$seen[$row.eventId] = $true
}

Write-Host ""
Write-Host "Creating a Test Mode payment link (this does not fire a webhook by itself)..."
$link = New-FailingPaymentLink

Write-Host ""
Write-Host "=== Fire a REAL signed webhook ==="
Write-Host "HMAC secret in .env     : $secret"
Write-Host "Payment link            : $($link.short_url)"
Write-Host ""
Write-Host "Do ONE of these while this script waits:"
Write-Host "  A) Fastest judge proof"
Write-Host "     Dashboard (Test Mode) -> Account & Settings -> Webhooks"
Write-Host "     Open the endpoint that points at your tunnel"
Write-Host "     Click Send Test Webhook  (event: payment.failed)"
Write-Host "     OTP if asked: 754081"
Write-Host "  B) Real failed charge"
Write-Host "     Open the payment link"
Write-Host "     Card 4012001037141112 / any future expiry / any CVV"
Write-Host ""
Write-Host "Tunnel must be up:  .\tunnel.ps1"
Write-Host "Webhook URL:        https://<tunnel-host>/webhooks/razorpay"
Write-Host "Watch the desk:     http://localhost:3000/desk  (Live signed intake strip)"
Write-Host ""
Write-Host "Waiting up to $WaitSeconds seconds for origin=RAZORPAY ..."

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$found = $null
while ((Get-Date) -lt $deadline) {
	Start-Sleep -Seconds 4
	$inbox = Get-Inbox -Token $token
	foreach ($row in @($inbox.events)) {
		if ($seen.ContainsKey($row.eventId)) {
			continue
		}
		if ($row.origin -eq "RAZORPAY" -and $row.signatureVerified) {
			$found = $row
			break
		}
		if ($row.origin -eq "LOCAL_SCRIPT") {
			Write-Host ("  saw local HMAC {0} — that is simulate-webhooks.ps1, not Razorpay servers." -f $row.eventId)
			$seen[$row.eventId] = $true
		}
	}
	if ($found) {
		break
	}
	Write-Host ("  ... signed={0} razorpay={1}" -f $inbox.signedCount, $inbox.razorpayCount)
}

if (-not $found) {
	Write-Error @"
No Razorpay-signed webhook arrived.

Checklist:
  - tunnel.ps1 is running and the dashboard URL matches it
  - webhook secret in the dashboard equals RAZORPAY_WEBHOOK_SECRET
  - you clicked Send Test Webhook, or failed the payment link
  - backend + Kafka + Redis are up
"@
}

Write-Host ""
Write-Host "=== HMAC proof ==="
Write-Host ("origin             : {0}" -f $found.origin)
Write-Host ("intake             : {0}" -f $found.intake)
Write-Host ("signatureVerified  : {0}" -f $found.signatureVerified)
Write-Host ("eventId            : {0}" -f $found.eventId)
Write-Host ("eventType          : {0}" -f $found.eventType)
Write-Host ("accountId          : {0}" -f $found.accountId)
Write-Host ("sourceId           : {0}" -f $found.sourceId)
Write-Host ("caseId             : {0}" -f $found.caseId)
Write-Host ("reason             : {0}" -f $found.reason)
Write-Host ""
Write-Host "Show the judge: desk Live signed intake chip 'Razorpay HMAC',"
Write-Host "and audit_event WEBHOOK_HMAC_VERIFIED on that case."
