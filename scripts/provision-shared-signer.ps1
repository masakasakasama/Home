$ErrorActionPreference = 'Stop'
$keyDirectory = Join-Path $env:LOCALAPPDATA 'CodexSecurityKeys/Home-Stock-20261020'
$keyFile = Join-Path $keyDirectory 'home-stock-release.p12'
$passwordFile = Join-Path $keyDirectory 'password.dpapi'
$keytool = 'C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe'
if (!(Test-Path -LiteralPath $keyDirectory)) {
    New-Item -ItemType Directory -Path $keyDirectory | Out-Null
}
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $keyDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing directory ACL' }
if (!(Test-Path -LiteralPath $keyFile)) {
    if (Test-Path -LiteralPath $passwordFile) { throw 'Partial setup exists; inspect before retrying' }
    $passwordBytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
    $signingPassword = [Convert]::ToBase64String($passwordBytes)
    [Array]::Clear($passwordBytes, 0, $passwordBytes.Length)
    $protected = ConvertTo-SecureString -String $signingPassword -AsPlainText -Force | ConvertFrom-SecureString
    [IO.File]::WriteAllText($passwordFile, $protected)
    $env:CODEX_NEW_SIGNER_PASSWORD = $signingPassword
    & $keytool -genkeypair -keystore $keyFile -storetype PKCS12 -storepass:env CODEX_NEW_SIGNER_PASSWORD -keypass:env CODEX_NEW_SIGNER_PASSWORD -alias home-stock-release -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Home Stock Private Release 2026' 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed; do not publish' }
} else {
    $securePassword = [IO.File]::ReadAllText($passwordFile) | ConvertTo-SecureString
    $signingPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
    $env:CODEX_NEW_SIGNER_PASSWORD = $signingPassword
}
try {
    $certificate = & $keytool '-J-Duser.language=en' -list -v -keystore $keyFile -storepass:env CODEX_NEW_SIGNER_PASSWORD 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw 'Private key verification failed' }
    $sha256 = [regex]::Match($certificate, 'SHA256:\s*([A-Fa-f0-9:]+)').Groups[1].Value.Replace(':', '').ToLowerInvariant()
    if ($sha256.Length -ne 64) { throw 'Signer fingerprint unavailable' }
    [IO.File]::WriteAllText((Join-Path $keyDirectory 'certificate.txt'), $certificate)
    $encodedKeystore = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyFile))
    foreach ($repoName in @('Home', 'Stock')) {
        $prefix = $repoName.ToUpperInvariant()
        $secretValues = @{
            "${prefix}_SIGNING_KEYSTORE_B64" = $encodedKeystore
            "${prefix}_SIGNING_STORE_PASSWORD" = $signingPassword
            "${prefix}_SIGNING_KEY_ALIAS" = 'home-stock-release'
            "${prefix}_SIGNING_KEY_PASSWORD" = $signingPassword
        }
        foreach ($entry in $secretValues.GetEnumerator()) {
            $startInfo = [Diagnostics.ProcessStartInfo]::new((Get-Command gh).Source)
            $startInfo.UseShellExecute = $false
            $startInfo.CreateNoWindow = $true
            $startInfo.RedirectStandardInput = $true
            foreach ($argument in @('secret', 'set', $entry.Key, '--repo', "masakasakasama/$repoName")) { $startInfo.ArgumentList.Add($argument) }
            $process = [Diagnostics.Process]::Start($startInfo)
            # Write exact bytes through stdin; avoid pipeline-added line endings and process-list secrets.
            $process.StandardInput.Write($entry.Value)
            $process.StandardInput.Close()
            $process.WaitForExit()
            if ($process.ExitCode -ne 0) { throw "Could not configure secret name $($entry.Key) for $repoName" }
        }
        & gh variable set ANDROID_SIGNING_CERT_SHA256 --body $sha256 --repo "masakasakasama/$repoName"
        if ($LASTEXITCODE -ne 0) { throw 'Could not configure public signer fingerprint' }
    }
    Write-Output "Configured same new private signer for Home and Stock. SHA-256: $sha256"
    Write-Output "Local recovery material protected by user-only ACL and Windows DPAPI: $keyDirectory"
    Write-Output 'No APK was signed or published by this script; migration gates remain closed.'
} finally {
    Remove-Item Env:CODEX_NEW_SIGNER_PASSWORD -ErrorAction SilentlyContinue
    $signingPassword = $null
    $encodedKeystore = $null
}
