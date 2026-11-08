# Nuvio desktop updater. Started by the app (AppUpdaterPlatform.desktop.kt) right before it exits.
# Shows a Nuvio-styled window, waits for the app to close, removes the old version, installs the
# new MSI silently into the same folder, checks that Nuvio.exe is really there and reopens Nuvio.
# ASCII only: Windows PowerShell 5.1 reads BOM-less scripts with the ANSI code page.
param(
    [Parameter(Mandatory = $true)][string]$Msi,
    [Parameter(Mandatory = $true)][string]$InstallDir,
    [int]$AppPid = 0,
    [string]$Lang = 'en',
    [string]$LogDir = $env:TEMP
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase, System.Drawing

$texts = @{
    en = @{
        title = 'Updating Nuvio'
        waiting = 'Waiting for Nuvio to close...'
        removing = 'Removing the previous version...'
        installing = 'Installing the new version...'
        done = 'Update complete. Opening Nuvio...'
        failed = 'The update did not complete.'
        details = 'Details in the log:'
        close = 'Close'
        retry = 'Try again'
    }
    it = @{
        title = 'Aggiornamento di Nuvio'
        waiting = 'Attendo la chiusura di Nuvio...'
        removing = 'Rimozione della versione precedente...'
        installing = 'Installazione della nuova versione...'
        done = 'Aggiornamento completato. Avvio di Nuvio...'
        failed = 'Aggiornamento non riuscito.'
        details = 'Dettagli nel registro:'
        close = 'Chiudi'
        retry = 'Riprova'
    }
}
$t = if ($texts.ContainsKey($Lang)) { $texts[$Lang] } else { $texts['en'] }

$exePath = Join-Path $InstallDir 'Nuvio.exe'
$logFile = Join-Path $LogDir 'nuvio-updater.log'
$msiLog = Join-Path $LogDir 'nuvio-msi.log'

function Write-Log([string]$message) {
    try { Add-Content -Path $logFile -Value ("{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $message) } catch {}
}
Set-Content -Path $logFile -Value '' -ErrorAction SilentlyContinue
Write-Log "Updater started: msi=$Msi installDir=$InstallDir pid=$AppPid"

# The icon must be read now: the old Nuvio.exe is removed during the update.
$iconSource = $null
try {
    if (Test-Path $exePath) {
        $icon = [System.Drawing.Icon]::ExtractAssociatedIcon($exePath)
        $iconSource = [System.Windows.Interop.Imaging]::CreateBitmapSourceFromHIcon(
            $icon.Handle, [System.Windows.Int32Rect]::Empty,
            [System.Windows.Media.Imaging.BitmapSizeOptions]::FromEmptyOptions())
    }
} catch { Write-Log "Icon not available: $_" }

[xml]$xaml = @"
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Nuvio" Width="440" SizeToContent="Height" WindowStartupLocation="CenterScreen"
        WindowStyle="None" AllowsTransparency="True" Background="Transparent"
        ResizeMode="NoResize" Topmost="True" ShowInTaskbar="True">
  <Border Background="#0D0D0D" CornerRadius="16" BorderBrush="#252A2A" BorderThickness="1" Padding="28,26,28,24">
    <StackPanel>
      <StackPanel Orientation="Horizontal" Margin="0,0,0,22">
        <Image x:Name="Logo" Width="40" Height="40" Margin="0,0,14,0" VerticalAlignment="Center"/>
        <StackPanel VerticalAlignment="Center">
          <TextBlock Text="Nuvio" Foreground="#F5F7F8" FontFamily="Segoe UI Variable Display, Segoe UI" FontSize="22" FontWeight="SemiBold"/>
          <TextBlock x:Name="Subtitle" Foreground="#969CA3" FontFamily="Segoe UI" FontSize="13"/>
        </StackPanel>
      </StackPanel>
      <TextBlock x:Name="Status" Foreground="#F5F7F8" FontFamily="Segoe UI" FontSize="14" TextWrapping="Wrap" Margin="0,0,0,12"/>
      <ProgressBar x:Name="Progress" Height="6" IsIndeterminate="True" Foreground="#F5F7F8" Background="#242424" BorderThickness="0"/>
      <TextBlock x:Name="Details" Foreground="#969CA3" FontFamily="Segoe UI" FontSize="12" TextWrapping="Wrap" Margin="0,12,0,0" Visibility="Collapsed"/>
      <StackPanel x:Name="Buttons" Orientation="Horizontal" HorizontalAlignment="Right" Margin="0,20,0,0" Visibility="Collapsed">
        <Button x:Name="Retry" Padding="18,8" Margin="0,0,10,0" Background="#F5F7F8" Foreground="#111111" BorderThickness="0" FontFamily="Segoe UI" FontSize="13" FontWeight="SemiBold" Cursor="Hand"/>
        <Button x:Name="Close" Padding="18,8" Background="#242424" Foreground="#F5F7F8" BorderThickness="0" FontFamily="Segoe UI" FontSize="13" Cursor="Hand"/>
      </StackPanel>
    </StackPanel>
  </Border>
</Window>
"@

$window = [System.Windows.Markup.XamlReader]::Load((New-Object System.Xml.XmlNodeReader $xaml))
$ui = @{}
foreach ($name in 'Logo', 'Subtitle', 'Status', 'Progress', 'Details', 'Buttons', 'Retry', 'Close') {
    $ui[$name] = $window.FindName($name)
}
if ($iconSource) { $ui.Logo.Source = $iconSource } else { $ui.Logo.Visibility = 'Collapsed' }
$ui.Subtitle.Text = $t.title
$ui.Retry.Content = $t.retry
$ui.Close.Content = $t.close
$window.Add_MouseLeftButtonDown({ try { $window.DragMove() } catch {} })

$script:busy = $true
$script:step = 'wait'
$script:proc = $null
$script:waitStarted = Get-Date

$window.Add_Closing({ param($s, $e) if ($script:busy) { $e.Cancel = $true } })
$ui.Close.Add_Click({ $script:busy = $false; $window.Close() })

function Find-OldProductCodes {
    $roots = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
             'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
             'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall'
    $codes = @()
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        Get-ChildItem $root -ErrorAction SilentlyContinue | ForEach-Object {
            $p = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue
            if ($p -and $p.DisplayName -eq 'Nuvio' -and $_.PSChildName -match '^\{[0-9A-Fa-f-]{36}\}$') {
                $codes += $_.PSChildName
            }
        }
    }
    return $codes | Select-Object -Unique
}

function Start-Msi([string[]]$arguments) {
    Write-Log ("msiexec " + ($arguments -join ' '))
    $p = Start-Process -FilePath 'msiexec.exe' -ArgumentList $arguments -PassThru -WindowStyle Hidden
    $null = $p.Handle  # keeps ExitCode readable after exit
    return $p
}

function Show-Failure([string]$reason) {
    Write-Log "FAILED: $reason"
    $script:busy = $false
    $script:step = 'failed'
    $ui.Status.Text = $t.failed
    $ui.Progress.IsIndeterminate = $false
    $ui.Progress.Value = 0
    $ui.Details.Text = "$reason`n$($t.details) $logFile"
    $ui.Details.Visibility = 'Visible'
    $ui.Buttons.Visibility = 'Visible'
    $ui.Retry.Focus() | Out-Null
}

function Start-Install {
    $ui.Status.Text = $t.installing
    $script:proc = Start-Msi @('/i', "`"$Msi`"", '/qn', '/norestart', "INSTALLDIR=`"$InstallDir`"", '/l*v', "`"$msiLog`"")
    $script:step = 'install'
}

$ui.Retry.Add_Click({
    $ui.Details.Visibility = 'Collapsed'
    $ui.Buttons.Visibility = 'Collapsed'
    $ui.Progress.IsIndeterminate = $true
    $script:busy = $true
    Start-Install
})

$timer = New-Object System.Windows.Threading.DispatcherTimer
$timer.Interval = [TimeSpan]::FromMilliseconds(400)
$timer.Add_Tick({
    try {
        switch ($script:step) {
            'wait' {
                $ui.Status.Text = $t.waiting
                $running = $AppPid -gt 0 -and (Get-Process -Id $AppPid -ErrorAction SilentlyContinue)
                if ($running -and ((Get-Date) - $script:waitStarted).TotalSeconds -lt 60) { return }
                if ($running) {
                    Write-Log "App still running after 60 s, stopping it"
                    Stop-Process -Id $AppPid -Force -ErrorAction SilentlyContinue
                    Start-Sleep -Milliseconds 800
                }
                if (-not (Test-Path $Msi)) { Show-Failure "Installer not found: $Msi"; return }
                $codes = @(Find-OldProductCodes)
                Write-Log ("Installed versions: " + ($codes -join ', '))
                if ($codes.Count -gt 0) {
                    # Uninstall first, then install: the in-place MSI upgrade could leave an empty folder.
                    $ui.Status.Text = $t.removing
                    $script:pending = [System.Collections.ArrayList]@($codes)
                    $code = $script:pending[0]; $script:pending.RemoveAt(0)
                    $script:proc = Start-Msi @('/x', $code, '/qn', '/norestart', '/l*v', "`"$LogDir\nuvio-msi-remove.log`"")
                    $script:step = 'remove'
                } else {
                    Start-Install
                }
            }
            'remove' {
                if (-not $script:proc.HasExited) { return }
                Write-Log "Uninstall exit code $($script:proc.ExitCode)"
                if ($script:proc.ExitCode -notin 0, 1605, 3010) {
                    Show-Failure "Uninstall exit code $($script:proc.ExitCode)"; return
                }
                if ($script:pending.Count -gt 0) {
                    $code = $script:pending[0]; $script:pending.RemoveAt(0)
                    $script:proc = Start-Msi @('/x', $code, '/qn', '/norestart')
                    return
                }
                Start-Install
            }
            'install' {
                if (-not $script:proc.HasExited) { return }
                $exit = $script:proc.ExitCode
                Write-Log "Install exit code $exit"
                if ($exit -notin 0, 3010) { Show-Failure "Install exit code $exit"; return }
                if (-not (Test-Path $exePath)) { Show-Failure "Nuvio.exe missing after install: $exePath"; return }
                $script:busy = $false
                $script:step = 'done'
                $ui.Status.Text = $t.done
                $ui.Progress.IsIndeterminate = $false
                $ui.Progress.Value = 100
                Write-Log "Update complete, launching $exePath"
                Start-Process -FilePath $exePath -WorkingDirectory $InstallDir
                $script:doneAt = Get-Date
            }
            'done' {
                if (((Get-Date) - $script:doneAt).TotalSeconds -ge 2) { $timer.Stop(); $window.Close() }
            }
        }
    } catch {
        Show-Failure "$_"
    }
})

$window.Add_ContentRendered({ $timer.Start() })
[void]$window.ShowDialog()
