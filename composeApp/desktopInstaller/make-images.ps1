# Regenerates the Nuvio images of the Windows installer (WixUI banner and welcome/finish side image).
# Run from the repository root: powershell -ExecutionPolicy Bypass -File composeApp/desktopInstaller/make-images.ps1
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$logo = [System.Drawing.Image]::FromFile((Resolve-Path "$root/../src/commonMain/composeResources/drawable/app_logo_wordmark.png"))
$iconRect = New-Object System.Drawing.Rectangle 0, 0, 320, 342   # play glyph only, left part of the wordmark
$cyan = [System.Drawing.Color]::FromArgb(0x4D, 0xE3, 0xF0)
$purple = [System.Drawing.Color]::FromArgb(0xB0, 0x4D, 0xF0)

function New-Canvas($w, $h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'; $g.PixelOffsetMode = 'HighQuality'
    $g.Clear([System.Drawing.Color]::White)
    return @($bmp, $g)
}

# Welcome / finish pages: 493x312, the dialog text is drawn in black from x=135, so only the left strip is dark.
$bmp, $g = New-Canvas 493 312
$panel = New-Object System.Drawing.Rectangle 0, 0, 164, 312
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush $panel, ([System.Drawing.Color]::FromArgb(0x1A,0x1A,0x1A)), ([System.Drawing.Color]::FromArgb(0x0D,0x0D,0x0D)), 90
$g.FillRectangle($bg, $panel)
$line = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.Rectangle 0, 0, 4, 312), $cyan, $purple, 90
$g.FillRectangle($line, 160, 0, 4, 312)
$w = 132; $h = [int]($logo.Height * $w / $logo.Width)
$g.DrawImage($logo, (New-Object System.Drawing.Rectangle 14, ([int]((312 - $h) / 2) - 20), $w, $h))
$font = New-Object System.Drawing.Font 'Segoe UI', 8.5
$g.DrawString('Enhanced', $font, (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(0x96,0x9C,0xA3))), 14 + 47, [int]((312 + $h) / 2) - 14)
$g.Dispose(); $bmp.Save("$root/dialog.bmp", [System.Drawing.Imaging.ImageFormat]::Bmp); $bmp.Dispose()

# Banner of the inner pages: 493x58, title text in black on the left, Nuvio glyph on the right.
$bmp, $g = New-Canvas 493 58
$g.DrawImage($logo, (New-Object System.Drawing.Rectangle 440, 9, 37, 40), $iconRect, [System.Drawing.GraphicsUnit]::Pixel)
$bar = New-Object System.Drawing.Drawing2D.LinearGradientBrush (New-Object System.Drawing.Rectangle 0, 0, 493, 3), $cyan, $purple, 0
$g.FillRectangle($bar, 0, 55, 493, 3)
$g.Dispose(); $bmp.Save("$root/banner.bmp", [System.Drawing.Imaging.ImageFormat]::Bmp); $bmp.Dispose()
$logo.Dispose()
