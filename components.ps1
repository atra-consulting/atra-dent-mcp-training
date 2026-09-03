# The building blocks and their ports, shared by start.ps1 and stop.ps1. Dot-
# sourced, never executed, and it expects $root to hold the repository root.
# Whoever stops the lab has to arrive at the same ports as whoever started it
# -- which includes reading the same .env, because a port may well be set
# there.

function Import-DotEnv {
  $envFile = Join-Path $root ".env"
  if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) { return }

  foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*($|#)') { continue }
    if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { continue }
    $name = $Matches[1]
    $value = $Matches[2]
    # bash reads the same file through "eval export", which strips a matching
    # pair of outer quotes. Without this a key written as KEY="abc" would
    # arrive with the quotes still on it and every call using it would fail
    # with something that looks nothing like a quoting problem.
    if ($value.Length -ge 2) {
      $first = $value[0]
      if (($first -eq '"' -or $first -eq "'") -and $value[$value.Length - 1] -eq $first) {
        $value = $value.Substring(1, $value.Length - 2)
      }
    }
    if ($null -ne [Environment]::GetEnvironmentVariable($name)) { continue }
    Set-Item -Path "Env:$name" -Value $value
  }
}

Import-DotEnv

$Components = @(
  'rechenkern', 'kernsystem', 'wissen', 'agenten-beratung',
  'agenten-schadensfall', 'agenten-orchestrator', 'sachbearbeiter-ui', 'kunden-chat'
)

function Get-EnvOrDefault([string]$name, [string]$fallback) {
  $current = [Environment]::GetEnvironmentVariable($name)
  if ([string]::IsNullOrEmpty($current)) { return $fallback }
  return $current
}

$KernsystemPort = Get-EnvOrDefault 'KERNSYSTEM_PORT' '8080'
$WissenPort = Get-EnvOrDefault 'WISSEN_PORT' '8082'
$AgentenOrchestratorPort = Get-EnvOrDefault 'AGENTEN_ORCHESTRATOR_PORT' '8084'
$AgentenBeratungPort = Get-EnvOrDefault 'AGENTEN_BERATUNG_PORT' '8085'
$RechenkernPort = Get-EnvOrDefault 'RECHENKERN_PORT' '8086'
$AgentenSchadensfallPort = Get-EnvOrDefault 'AGENTEN_SCHADENSFALL_PORT' '8087'
$SachbearbeiterUiPort = Get-EnvOrDefault 'SACHBEARBEITER_UI_PORT' '5173'
$KundenChatPort = Get-EnvOrDefault 'KUNDEN_CHAT_PORT' '5174'

$DebugHost = Get-EnvOrDefault 'DEBUG_HOST' '127.0.0.1'

$KernsystemDebugPort = Get-EnvOrDefault 'KERNSYSTEM_DEBUG_PORT' ([string]([int]$KernsystemPort + 1000))
$WissenDebugPort = Get-EnvOrDefault 'WISSEN_DEBUG_PORT' ([string]([int]$WissenPort + 1000))
$AgentenOrchestratorDebugPort = Get-EnvOrDefault 'AGENTEN_ORCHESTRATOR_DEBUG_PORT' ([string]([int]$AgentenOrchestratorPort + 1000))
$AgentenBeratungDebugPort = Get-EnvOrDefault 'AGENTEN_BERATUNG_DEBUG_PORT' ([string]([int]$AgentenBeratungPort + 1000))
$RechenkernDebugPort = Get-EnvOrDefault 'RECHENKERN_DEBUG_PORT' ([string]([int]$RechenkernPort + 1000))
$AgentenSchadensfallDebugPort = Get-EnvOrDefault 'AGENTEN_SCHADENSFALL_DEBUG_PORT' ([string]([int]$AgentenSchadensfallPort + 1000))
$SachbearbeiterUiDebugPort = Get-EnvOrDefault 'SACHBEARBEITER_UI_DEBUG_PORT' ([string]([int]$SachbearbeiterUiPort + 1000))
$KundenChatDebugPort = Get-EnvOrDefault 'KUNDEN_CHAT_DEBUG_PORT' ([string]([int]$KundenChatPort + 1000))

function Get-PortOf([string]$name) {
  switch ($name) {
    'rechenkern' { $RechenkernPort }
    'kernsystem' { $KernsystemPort }
    'wissen' { $WissenPort }
    'agenten-orchestrator' { $AgentenOrchestratorPort }
    'agenten-beratung' { $AgentenBeratungPort }
    'agenten-schadensfall' { $AgentenSchadensfallPort }
    'sachbearbeiter-ui' { $SachbearbeiterUiPort }
    'kunden-chat' { $KundenChatPort }
    default { $null }
  }
}

function Get-DebugPortOf([string]$name) {
  switch ($name) {
    'rechenkern' { $RechenkernDebugPort }
    'kernsystem' { $KernsystemDebugPort }
    'wissen' { $WissenDebugPort }
    'agenten-orchestrator' { $AgentenOrchestratorDebugPort }
    'agenten-beratung' { $AgentenBeratungDebugPort }
    'agenten-schadensfall' { $AgentenSchadensfallDebugPort }
    'sachbearbeiter-ui' { $SachbearbeiterUiDebugPort }
    'kunden-chat' { $KundenChatDebugPort }
    default { $null }
  }
}

function Test-RunsOnJvm([string]$name) {
  return $name -notin @('sachbearbeiter-ui', 'kunden-chat')
}

function Test-IsComponent([string]$candidate) {
  return $Components -contains $candidate
}

# --- process helpers shared by start.ps1 and stop.ps1 -----------------------

# Reads another process's current working directory from its PEB
# (ProcessParameters.CurrentDirectory) via NtQueryInformationProcess. Windows
# has no cmdlet for this -- it is the equivalent of "lsof -a -d cwd -p PID" on
# macOS/Linux, which both start.ps1's port cleanup and stop.ps1's "does this
# process belong to the lab" guard depend on. Same-bitness processes only
# (there is no WOW64 handling here); on failure it returns $null and callers
# fall back to matching by command line instead.
if (-not ('Atra.ProcessInfo' -as [type])) {
  try {
    Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

namespace Atra {
  public static class ProcessInfo {
    [DllImport("ntdll.dll")]
    private static extern int NtQueryInformationProcess(
        IntPtr processHandle, int processInformationClass,
        IntPtr processInformation, int processInformationLength, out int returnLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern IntPtr OpenProcess(uint desiredAccess, bool inheritHandle, int processId);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);

    [DllImport("kernel32.dll")]
    private static extern bool ReadProcessMemory(
        IntPtr hProcess, IntPtr lpBaseAddress, byte[] lpBuffer, int dwSize, out int lpNumberOfBytesRead);

    private const uint PROCESS_QUERY_INFORMATION = 0x0400;
    private const uint PROCESS_VM_READ = 0x0010;

    // PEB layout: Mutant at 0x08, ImageBaseAddress at 0x10, Ldr at 0x18 and
    // ProcessParameters at 0x20 on x64; on x86 the same four sit at 0x04,
    // 0x08, 0x0C and 0x10. Reading 0x10 on x64 hands back the image base and
    // every field derived from it is then garbage.
    private const int ProcessParametersOffset64 = 0x20;
    private const int ProcessParametersOffset32 = 0x10;

    // RTL_USER_PROCESS_PARAMETERS.CurrentDirectory (a CURDIR, so a
    // UNICODE_STRING followed by a HANDLE).
    private const int CurrentDirectoryOffset64 = 0x38;
    private const int CurrentDirectoryOffset32 = 0x24;

    public static string GetCurrentDirectory(int pid) {
        IntPtr hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, pid);
        if (hProcess == IntPtr.Zero) return null;
        try {
            IntPtr pbi = Marshal.AllocHGlobal(48);
            try {
                int returnLength;
                int status = NtQueryInformationProcess(hProcess, 0, pbi, 48, out returnLength);
                if (status != 0) return null;

                IntPtr pebBaseAddress = Marshal.ReadIntPtr(pbi, IntPtr.Size == 8 ? 8 : 4);
                if (pebBaseAddress == IntPtr.Zero) return null;

                int processParametersOffset = IntPtr.Size == 8
                    ? ProcessParametersOffset64
                    : ProcessParametersOffset32;
                byte[] pebBuffer = new byte[processParametersOffset + IntPtr.Size];
                int bytesRead;
                if (!ReadProcessMemory(hProcess, pebBaseAddress, pebBuffer, pebBuffer.Length, out bytesRead)) return null;
                if (bytesRead < pebBuffer.Length) return null;
                IntPtr processParametersAddress = IntPtr.Size == 8
                    ? (IntPtr)BitConverter.ToInt64(pebBuffer, processParametersOffset)
                    : (IntPtr)BitConverter.ToInt32(pebBuffer, processParametersOffset);
                if (processParametersAddress == IntPtr.Zero) return null;

                int currentDirectoryOffset = IntPtr.Size == 8
                    ? CurrentDirectoryOffset64
                    : CurrentDirectoryOffset32;
                byte[] unicodeStringBuffer = new byte[IntPtr.Size == 8 ? 16 : 8];
                if (!ReadProcessMemory(hProcess, IntPtr.Add(processParametersAddress, currentDirectoryOffset), unicodeStringBuffer, unicodeStringBuffer.Length, out bytesRead)) return null;
                if (bytesRead < unicodeStringBuffer.Length) return null;

                ushort length = BitConverter.ToUInt16(unicodeStringBuffer, 0);
                IntPtr bufferAddress = IntPtr.Size == 8
                    ? (IntPtr)BitConverter.ToInt64(unicodeStringBuffer, 8)
                    : (IntPtr)BitConverter.ToInt32(unicodeStringBuffer, 4);
                if (length <= 0 || bufferAddress == IntPtr.Zero) return null;

                byte[] stringBuffer = new byte[length];
                if (!ReadProcessMemory(hProcess, bufferAddress, stringBuffer, length, out bytesRead)) return null;
                return Encoding.Unicode.GetString(stringBuffer, 0, bytesRead);
            } finally {
                Marshal.FreeHGlobal(pbi);
            }
        } catch {
            return null;
        } finally {
            CloseHandle(hProcess);
        }
    }
  }
}
'@
  } catch {
    # Swallowing this would be worse than the failure itself: every lab
    # membership check below would quietly answer "no", and stop.ps1 would
    # report an idle lab while the services keep running.
    [Console]::Error.WriteLine("Hinweis: Der Prozess-Helfer liess sich nicht uebersetzen ($($_.Exception.Message)). stop.ps1 erkennt Prozesse dann nur noch an ihrer Kommandozeile.")
  }
}

function Get-ProcessWorkingDirectory([int]$processId) {
  if (-not ('Atra.ProcessInfo' -as [type])) { return $null }
  try {
    return [Atra.ProcessInfo]::GetCurrentDirectory($processId)
  } catch {
    return $null
  }
}

# True only for a process whose working directory is $root or somewhere below
# it. A command line is conclusive evidence too and is checked when the caller
# has one: Set-Location does not move the working directory of the PowerShell
# process itself, so a "powershell -File C:\repo\start.ps1" launched from
# somewhere else reports that somewhere else as its working directory and
# would otherwise go unrecognised. Both checks failing means $false -- never
# matching leaves the caller on the safe side.
function Test-BelongsToLab([int]$processId, [string]$commandLine) {
  $normalizedRoot = $root.TrimEnd('\')

  $directory = Get-ProcessWorkingDirectory $processId
  if (-not [string]::IsNullOrEmpty($directory)) {
    $directory = $directory.TrimEnd('\')
    if (($directory -eq $normalizedRoot) -or $directory.StartsWith("$normalizedRoot\", [StringComparison]::OrdinalIgnoreCase)) {
      return $true
    }
  }

  if (-not [string]::IsNullOrEmpty($commandLine)) {
    return $commandLine.IndexOf($normalizedRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0
  }

  return $false
}

# All PIDs (recursively) of processes whose ParentProcessId is $processId, as
# reported right now -- the same one-shot snapshot approach as bash's
# "pgrep -P", used so callers can signal children even after the parent
# itself has already been asked to stop.
function Get-ChildProcessIds([int]$processId) {
  $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$processId" -ErrorAction SilentlyContinue
  $result = @()
  foreach ($child in $children) {
    $result += $child.ProcessId
    $result += Get-ChildProcessIds $child.ProcessId
  }
  return $result
}

# Stops a process and every descendant it has at the moment of the call.
function Stop-ProcessTree([int]$processId) {
  if ($processId -le 0) { return }
  $children = Get-ChildProcessIds $processId
  Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
  foreach ($child in $children) {
    Stop-Process -Id $child -Force -ErrorAction SilentlyContinue
  }
}
