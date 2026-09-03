#Requires -Version 5.1

# Windows equivalent of wissen/build.sh.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

# An unset $LASTEXITCODE is $null, and "$null -ne 0" is true -- checking it
# alone would take a script that never ran a native command for a failure and
# then exit 0, skipping the rest without a word.
function Exit-OnFailure {
  if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& "$root/wissen/dokumente-build.ps1"
Exit-OnFailure
Write-Host ""
& "$root/wissen/index-build.ps1"
Exit-OnFailure
Write-Host ""

. "$root/wissen/stand.ps1"
Get-ComputeStatus | Set-Content -LiteralPath 'wissen/generated/stand.txt' -NoNewline
Write-Host "Stand der Quellen vermerkt: wissen/generated/stand.txt"
