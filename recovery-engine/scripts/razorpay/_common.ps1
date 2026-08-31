function Get-DotEnv {
	param([string]$Path)
	$values = @{}
	if (-not (Test-Path -LiteralPath $Path)) {
		return $values
	}
	Get-Content -LiteralPath $Path | ForEach-Object {
		$line = $_.Trim()
		if ($line -eq "" -or $line.StartsWith("#")) {
			return
		}
		$eq = $line.IndexOf("=")
		if ($eq -lt 1) {
			return
		}
		$key = $line.Substring(0, $eq).Trim()
		$value = $line.Substring($eq + 1).Trim()
		if ($value.StartsWith('"') -and $value.EndsWith('"') -and $value.Length -ge 2) {
			$value = $value.Substring(1, $value.Length - 2)
		}
		$values[$key] = $value
	}
	return $values
}

function Get-HmacSha256Hex {
	param(
		[Parameter(Mandatory = $true)][string]$Secret,
		[Parameter(Mandatory = $true)][byte[]]$BodyBytes
	)
	$hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($Secret))
	try {
		$hash = $hmac.ComputeHash($BodyBytes)
		return ([System.BitConverter]::ToString($hash) -replace "-", "").ToLowerInvariant()
	} finally {
		$hmac.Dispose()
	}
}

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:EnvFile = Join-Path $script:RepoRoot ".env"
$script:DotEnv = Get-DotEnv -Path $script:EnvFile
