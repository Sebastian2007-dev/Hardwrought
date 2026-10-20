param(
    [string]$Source = 'art_source/soul_compass/source.png',
    [string]$MinecraftJar = '.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-6974b2190e/26.3/minecraft-clientOnly-6974b2190e-26.3.jar'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $root 'src/main/resources/assets/hardwrought'
$sourceImage = [Drawing.Bitmap]::FromFile((Join-Path $root $Source))
$base = New-Object Drawing.Bitmap 16,16
# Sample the centers of the enlarged logical pixels without interpolation.
for ($y=0; $y -lt 16; $y++) {
    for ($x=0; $x -lt 16; $x++) {
        $c = $sourceImage.GetPixel([int][Math]::Floor(($x+0.5)*$sourceImage.Width/16), [int][Math]::Floor(($y+0.5)*$sourceImage.Height/16))
        if ($c.A -ge 128) { $base.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,$c.R,$c.G,$c.B)) }
    }
}
$sourceImage.Dispose()
$base.Save((Join-Path $root 'art_source/soul_compass/base.png'))
$zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $root $MinecraftJar))
try {
    for ($i=0; $i -lt 32; $i++) {
        $name = 'soul_compass_{0:00}' -f $i
        $entry = $zip.GetEntry(('assets/minecraft/textures/item/compass_{0:00}.png' -f $i))
        $stream = $entry.Open()
        $vanilla = [Drawing.Bitmap]::FromStream($stream)
        $frame = $base.Clone()
        # Keep Minecraft's exact needle positions and direction convention.
        for ($y=0; $y -lt 16; $y++) {
            for ($x=0; $x -lt 16; $x++) {
                $c = $vanilla.GetPixel($x,$y)
                if ($c.R -gt 100 -and $c.G -lt 50 -and $c.B -lt 50) {
                    $color = if ($c.R -gt 220) { [Drawing.Color]::FromArgb(255,123,235,225) } else { [Drawing.Color]::FromArgb(255,43,151,155) }
                    $frame.SetPixel($x,$y,$color)
                } elseif (($c.ToArgb() -band 0xFFFFFF) -eq 0x646464) {
                    $frame.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,130,118,150))
                }
            }
        }
        $frame.Save((Join-Path $assets "textures/item/$name.png"))
        $frame.Dispose()
        $vanilla.Dispose()
        $stream.Dispose()
        $model = @{ parent='minecraft:item/generated'; textures=@{layer0="hardwrought:item/$name"} } | ConvertTo-Json
        [IO.File]::WriteAllText((Join-Path $assets "models/item/$name.json"), $model + "`n")
    }
} finally { $zip.Dispose(); $base.Dispose() }
$definition = Join-Path $assets 'items/soul_compass.json'
$json = [IO.File]::ReadAllText($definition).Replace('minecraft:item/compass_', 'hardwrought:item/soul_compass_')
[IO.File]::WriteAllText($definition, $json)
