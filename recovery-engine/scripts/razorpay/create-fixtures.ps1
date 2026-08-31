# Creates Test Mode customers, orders, plans, subscriptions, and payment links.
# Requires RAZORPAY_KEY_ID + RAZORPAY_KEY_SECRET in recovery-engine/.env
# Real card charges cannot be created from this API (PCI). Use the payment-link URL
# with a failing test card, or Dashboard -> Subscription -> Charge this now -> Fail.

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "_common.ps1")

$keyId = $DotEnv["RAZORPAY_KEY_ID"]
$keySecret = $DotEnv["RAZORPAY_KEY_SECRET"]
if ([string]::IsNullOrWhiteSpace($keyId) -or [string]::IsNullOrWhiteSpace($keySecret)) {
	Write-Error @"
RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are empty.

1. Open https://dashboard.razorpay.com
2. Toggle Test Mode ON (top of the dashboard)
3. Account & Settings -> API Keys -> Generate Test Keys
4. Paste them into recovery-engine/.env
"@
}

$pair = "{0}:{1}" -f $keyId, $keySecret
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{
	Authorization = "Basic $basic"
	"Content-Type" = "application/json"
}

function Invoke-Razorpay {
	param(
		[string]$Method,
		[string]$Path,
		[string]$Body
	)
	$url = "https://api.razorpay.com/v1$Path"
	$args = @{
		Method = $Method
		Uri = $url
		Headers = $headers
	}
	if ($Body) {
		$args.Body = $Body
	}
	try {
		return Invoke-RestMethod @args
	} catch {
		$bodyText = ""
		if ($_.Exception.Response) {
			$reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
			$bodyText = $reader.ReadToEnd()
		}
		Write-Error "Razorpay $Method $Path failed: $($_.Exception.Message)`n$bodyText"
	}
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"

Write-Host "Creating test customer..."
$customer = Invoke-Razorpay -Method POST -Path "/customers" -Body (@{
	name = "Recovery Engine Test"
	email = "recovery.test+$stamp@example.com"
	contact = "9000000000"
	fail_existing = "0"
	notes = @{ source = "recovery-engine" }
} | ConvertTo-Json)

Write-Host "Creating test order (amount 499.00 INR)..."
$order = Invoke-Razorpay -Method POST -Path "/orders" -Body (@{
	amount = 49900
	currency = "INR"
	receipt = "rcpt_$stamp"
	notes = @{ source = "recovery-engine"; purpose = "failure-sandbox" }
} | ConvertTo-Json)

Write-Host "Creating monthly plan..."
$plan = Invoke-Razorpay -Method POST -Path "/plans" -Body (@{
	period = "monthly"
	interval = 1
	item = @{
		name = "Recovery Engine Test Plan"
		amount = 49900
		currency = "INR"
		description = "Used to generate subscription failure events"
	}
} | ConvertTo-Json)

Write-Host "Creating subscription on that plan..."
$subscription = Invoke-Razorpay -Method POST -Path "/subscriptions" -Body (@{
	plan_id = $plan.id
	customer_notify = 0
	quantity = 1
	total_count = 12
	notes = @{ source = "recovery-engine" }
} | ConvertTo-Json)

Write-Host "Creating payment link..."
$paymentLink = Invoke-Razorpay -Method POST -Path "/payment_links" -Body (@{
	amount = 49900
	currency = "INR"
	accept_partial = $false
	description = "recovery-engine test payment"
	customer = @{
		name = "Recovery Engine Test"
		email = "recovery.test+$stamp@example.com"
		contact = "+919000000000"
	}
	notify = @{ sms = $false; email = $false }
	reminder_enable = $false
	notes = @{ source = "recovery-engine" }
} | ConvertTo-Json -Depth 5)

Write-Host ""
Write-Host "=== Test Mode fixtures ==="
Write-Host ("customer_id     : {0}" -f $customer.id)
Write-Host ("order_id        : {0}" -f $order.id)
Write-Host ("plan_id         : {0}" -f $plan.id)
Write-Host ("subscription_id : {0}" -f $subscription.id)
Write-Host ("subscription_url: {0}" -f $subscription.short_url)
Write-Host ("payment_link_id : {0}" -f $paymentLink.id)
Write-Host ("payment_link_url: {0}" -f $paymentLink.short_url)
Write-Host ""
Write-Host "To generate a REAL Test Mode failure (rare via API):"
Write-Host "  1. Open the payment_link_url"
Write-Host "  2. Pay with failing card 4012001037141112 / any future expiry / any CVV"
Write-Host "  3. Or Dashboard -> Subscriptions -> Charge this now -> Fail"
Write-Host "For immediate local failure events (no dashboard needed):"
Write-Host "  .\simulate-webhooks.ps1"
