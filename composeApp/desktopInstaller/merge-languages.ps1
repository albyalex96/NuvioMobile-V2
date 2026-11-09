# Builds one MSI that follows the Windows display language: the English MSI is the base and the Italian one
# is stored inside it as an embedded language transform named after its LCID (1040). Windows Installer
# applies it automatically on Italian systems.
param(
    [Parameter(Mandatory = $true)][string]$BaseMsi,      # English MSI (LCID 1033)
    [Parameter(Mandatory = $true)][string]$ItalianMsi,   # Italian MSI (LCID 1040)
    [Parameter(Mandatory = $true)][string]$WixDir,       # folder with torch.exe
    [Parameter(Mandatory = $true)][string]$OutputMsi
)
$ErrorActionPreference = 'Stop'
$work = Join-Path ([System.IO.Path]::GetDirectoryName($OutputMsi)) 'merge-tmp'
New-Item -ItemType Directory -Force $work | Out-Null
# [string]: a PowerShell-wrapped path makes the COM calls fail with DISP_E_TYPEMISMATCH.
[string]$transform = Join-Path $work '1040.mst'

# -t language: only the language differs, so torch must not complain about it.
& (Join-Path $WixDir 'torch.exe') -nologo -t language $BaseMsi $ItalianMsi -out $transform
if ($LASTEXITCODE -ne 0) { throw "torch failed with exit code $LASTEXITCODE" }

Copy-Item $BaseMsi $OutputMsi -Force
$installer = New-Object -ComObject WindowsInstaller.Installer
$db = $installer.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $installer, @($OutputMsi, 1))

$view = $db.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $db, @('SELECT `Name`, `Data` FROM `_Storages`'))
$view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null)
$record = $installer.GetType().InvokeMember('CreateRecord', 'InvokeMethod', $null, $installer, @(2))
$record.GetType().InvokeMember('StringData', 'SetProperty', $null, $record, @(1, '1040'))
$record.GetType().InvokeMember('SetStream', 'InvokeMethod', $null, $record, @(2, $transform))
$view.GetType().InvokeMember('Modify', 'InvokeMethod', $null, $view, @(3, $record))   # 3 = assign
$view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null)

# Template summary property (PID 7): declare both languages so Windows looks for the embedded transform.
$summary = $db.GetType().InvokeMember('SummaryInformation', 'GetProperty', $null, $db, @(1))
$platform = ($summary.GetType().InvokeMember('Property', 'GetProperty', $null, $summary, @(7)) -split ';')[0]
$summary.GetType().InvokeMember('Property', 'SetProperty', $null, $summary, @(7, "$platform;1033,1040"))
$summary.GetType().InvokeMember('Persist', 'InvokeMethod', $null, $summary, $null)

$db.GetType().InvokeMember('Commit', 'InvokeMethod', $null, $db, $null)
[System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($db) | Out-Null
Remove-Item $work -Recurse -Force
Write-Host "Multilingual MSI written to $OutputMsi"
