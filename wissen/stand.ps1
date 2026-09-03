# Windows equivalent of wissen/stand.sh. Dot-sourced, never executed --
# defines Get-FingerprintOf/Get-ComputeStatus. The two-step hash (hash every
# file, then hash the list of "hash  path" lines) mirrors the bash version's
# sha256sum/xargs pipeline exactly, down to the value: the same sources have to
# produce the same fingerprint on both platforms, because
# wissen/generated/stand.txt is versioned and start.ps1 compares against it.
#
# Two details carry that equality and neither is cosmetic. The path is written
# exactly as it was passed in -- "find wissen/dokumente" prints
# "wissen/dokumente/...", not an absolute path -- and the sort runs over the
# paths before anything is hashed, the way "sort -z" ahead of "xargs sha256sum"
# does. Sorting the finished "hash  path" lines instead would sort by hash and
# land on a different digest.

function Get-FingerprintOf {
  param([Parameter(Mandatory)][string[]]$Paths)

  $fileOf = New-Object 'System.Collections.Generic.Dictionary[string,string]'
  foreach ($path in $Paths) {
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $base = (Resolve-Path -LiteralPath $path).ProviderPath.TrimEnd('\', '/')
    $prefix = ($path -replace '\\', '/').TrimEnd('/')
    $files = Get-ChildItem -LiteralPath $path -Recurse -File -Force |
      Where-Object { $_.Name -ne '.DS_Store' -and $_.Name -ne '.seed' }
    foreach ($file in $files) {
      $relative = ($file.FullName.Substring($base.Length).TrimStart('\', '/')) -replace '\\', '/'
      $fileOf["$prefix/$relative"] = $file.FullName
    }
  }
  if ($fileOf.Count -eq 0) { return '' }

  # Ordinal, not culture-aware -- the closest .NET equivalent to bash's
  # "LC_ALL=C sort", so the fingerprint stays deterministic regardless of the
  # machine's locale.
  $ordered = [string[]]::new($fileOf.Count)
  $fileOf.Keys.CopyTo($ordered, 0)
  [array]::Sort($ordered, [StringComparer]::Ordinal)

  $lines = @(foreach ($path in $ordered) {
    $hash = (Get-FileHash -LiteralPath $fileOf[$path] -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $path"
  })

  $joined = ($lines -join "`n") + "`n"
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($joined)
  $sha256 = [System.Security.Cryptography.SHA256]::Create()
  try {
    $digest = $sha256.ComputeHash($bytes)
  } finally {
    $sha256.Dispose()
  }
  return ([System.BitConverter]::ToString($digest) -replace '-', '').ToLowerInvariant()
}

# Relative, exactly as wissen/stand.sh passes them: every caller has already
# changed into the repository root, and the paths travel into the fingerprint.
function Get-ComputeStatus {
  Get-FingerprintOf -Paths @('wissen/dokumente', 'wissen/daten')
}
