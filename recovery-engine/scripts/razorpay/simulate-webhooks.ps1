# Signs sample Razorpay webhook payloads and POSTs them to POST /webhooks/razorpay.
# Use this to generate payment.failed / subscription.pending events locally —
# real Test Mode failures are rare.

param(
	[string]$WebhookUrl = "http://localhost:8080/webhooks/razorpay",
	[string]$Event = ""
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_common.ps1")

$secret = $DotEnv["RAZORPAY_WEBHOOK_SECRET"]
if ([string]::IsNullOrWhiteSpace($secret)) {
	Write-Error "RAZORPAY_WEBHOOK_SECRET is empty. Put a value in recovery-engine/.env (use the same secret you set in the Razorpay dashboard webhook)."
}

$payloadDir = Join-Path $PSScriptRoot "payloads"
$files = Get-ChildItem -LiteralPath $payloadDir -Filter "*.json" | Sort-Object Name
if ($Event) {
	$files = $files | Where-Object { $_.BaseName -eq $Event }
	if (-not $files) {
		Write-Error "No payload named '$Event'. Available: $((Get-ChildItem $payloadDir -Filter *.json).BaseName -join ', ')"
	}
}

foreach ($file in $files) {
	$bodyBytes = [System.IO.File]::ReadAllBytes($file.FullName)
	# payloads are single-line JSON; trim a trailing newline so HMAC matches the bytes we POST
	while ($bodyBytes.Length -gt 0 -and ($bodyBytes[$bodyBytes.Length - 1] -eq 10 -or $bodyBytes[$bodyBytes.Length - 1] -eq 13)) {
		$bodyBytes = [byte[]]($bodyBytes[0..($bodyBytes.Length - 2)])
	}
	$signature = Get-HmacSha256Hex -Secret $secret -BodyBytes $bodyBytes

	$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("rzp-{0}.json" -f $file.BaseName)
	[System.IO.File]::WriteAllBytes($tmp, $bodyBytes)

	Write-Host ("POST {0}  event={1}" -f $WebhookUrl, $file.BaseName)
	$curlOut = curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST $WebhookUrl `
		-H "Content-Type: application/json" `
		-H "X-Razorpay-Signature: $signature" `
		--data-binary "@$tmp"
	Write-Host $curlOut
	if ($curlOut -notmatch "HTTP_STATUS:200") {
		throw "Webhook ingest failed for $($file.BaseName)"
	}
}

Write-Host ""
Write-Host "Done. Rows are in Postgres table webhook_event."
