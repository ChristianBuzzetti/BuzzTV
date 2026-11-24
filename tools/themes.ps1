# Windows twin of tools/themes.sh: keep several themes on the TV, so the launcher's Theme row (Launcher
# settings) switches between them with the remote. Nothing is applied and Home is not restarted.
#
# Usage: tools\themes.ps1 -TvHost 192.168.1.50 themes\gamer themes\cine   # add or replace these themes
#        tools\themes.ps1 -TvHost 192.168.1.50 -All                         # every folder in themes\
#        tools\themes.ps1 -TvHost 192.168.1.50 -List                        # what the library holds
#        tools\themes.ps1 -TvHost 192.168.1.50 -Remove gamer                # take one out of the library
#
# Exit status as themes.sh: 0 done; 1 a file could not be written (run the same command again); 2 nothing
# was sent.
param(
    [switch]$All,
    [switch]$List,
    [string]$Remove,
    [string]$TvHost = $env:TV_HOST,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$Folders
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$usage = 'usage: themes.ps1 <theme folder>... | -All | -List | -Remove <name>'
$modes = @($All.IsPresent, $List.IsPresent, [bool]$Remove, ($Folders -and $Folders.Count -gt 0)) | Where-Object { $_ }
if ($modes.Count -ne 1) { [Console]::Error.WriteLine($usage); exit 2 }
if (-not $TvHost) {
    [Console]::Error.WriteLine('pass -TvHost <tv ip> or set $env:TV_HOST. Nothing was sent to the TV.')
    exit 2
}
$env:TV_HOST = $TvHost
$namePattern = '^[a-z0-9][a-z0-9_-]{0,39}$'
$root = Split-Path -Parent $PSScriptRoot
$themeDirs = @()
if ($All) {
    $themeDirs = @(Get-ChildItem -LiteralPath (Join-Path $root 'themes') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'config.json') } | ForEach-Object { $_.FullName })
} elseif ($Folders) {
    foreach ($f in $Folders) {
        if ($f.StartsWith('-')) { [Console]::Error.WriteLine($usage); exit 2 }
        if (-not (Test-Path -LiteralPath $f)) { [Console]::Error.WriteLine("no folder $f. Nothing was sent to the TV."); exit 2 }
        $themeDirs += (Resolve-Path -LiteralPath $f).Path
    }
}
Set-Location -LiteralPath $PSScriptRoot
$py = Join-Path $PSScriptRoot 'venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $py)) {
    [Console]::Error.WriteLine('run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt. Nothing was sent to the TV.')
    exit 2
}
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-themes'
$notWritten = 'cyt-theme-not-written'
function Invoke-Shell($command) {
    $out = @($command | & $py -I tv.py --batch | Where-Object { "$_" -notlike '$ *' })
    if ($LASTEXITCODE -ne 0) { [Console]::Error.WriteLine('the TV did not answer; run the same command again.'); exit 1 }
    $out
}
function Stage($name) { "$tmp-$($name -replace '/', '-')" }
# The same write tools/theme.ps1 does: the launcher's provider first, a plain copy where it is refused, then a
# size check of what landed.
function Put($name) { $t = Stage $name; $d = "$dir/$name" -replace '/[^/]*$', ''; "rm -f $dir/$name 2>/dev/null; w=; { o=`$(content write --uri $uri/$name < $t 2>&1) && case `"`$o`" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac && w=1; } || { mkdir -p $d 2>/dev/null; cp $t $dir/$name 2>/dev/null && w=1; }; s=`$(wc -c 2>/dev/null < $t); rm -f $t; [ -n `"`$w`" ] && { { [ -e $dir/$name ] && [ `"`$(wc -c < $dir/$name)`" = `"`$s`" ]; } || [ `"`$(content read --uri $uri/$name 2>/dev/null | wc -c)`" = `"`$s`" ]; } || echo $notWritten $name" }
function Send($local, $name) {
    & $py -I b64push.py $local (Stage $name) | Out-Null
    if ($LASTEXITCODE -ne 0) { [Console]::Error.WriteLine("could not send $name; run the same command again."); exit 1 }
    $out = Invoke-Shell (Put $name)
    if (@($out | Where-Object { "$_".Trim() -eq "$notWritten $name" }).Count -gt 0) {
        [Console]::Error.WriteLine("The TV took neither the provider write nor the copy of $name.")
        exit 1
    }
}
function Drop-Theme($name) { Invoke-Shell "rm -rf $dir/themes/$name 2>/dev/null; content delete --uri $uri/themes/$name 2>/dev/null" | Out-Null }

if ($List) {
    $out = @(Invoke-Shell "ls $dir/themes 2>/dev/null" | Where-Object { "$_".Trim() })
    if ($out.Count -eq 0) { Write-Host 'The library is empty, or this firmware keeps the shell out of Android/data (the Theme row on the TV shows what it holds).' }
    else { $out | ForEach-Object { Write-Host "$_".Trim() } }
    exit 0
}
if ($Remove) {
    if ($Remove -cnotmatch $namePattern) { [Console]::Error.WriteLine("$Remove is not a library theme name. Nothing was sent to the TV."); exit 2 }
    Drop-Theme $Remove
    Write-Host "removed $Remove"
    exit 0
}
if ($themeDirs.Count -eq 0) { [Console]::Error.WriteLine('no theme folders to send. Nothing was sent to the TV.'); exit 2 }
$imagePattern = '\.(jpg|jpeg|png|webp)$'
# Check every folder before the first file goes.
foreach ($t in $themeDirs) {
    $name = Split-Path -Leaf $t
    $config = Join-Path $t 'config.json'
    if (-not (Test-Path -LiteralPath $config)) { [Console]::Error.WriteLine("no config.json in $t. Nothing was sent to the TV."); exit 2 }
    if ($name -cnotmatch $namePattern) { [Console]::Error.WriteLine("${name}: a library theme's folder name is lower case letters, digits, '-' and '_', at most 40. Nothing was sent to the TV."); exit 2 }
    try { $json = Get-Content -LiteralPath $config -Raw | ConvertFrom-Json } catch { $json = $null }
    if ($json -isnot [System.Management.Automation.PSCustomObject]) { [Console]::Error.WriteLine("$config is not a JSON object of theme keys. Nothing was sent to the TV."); exit 2 }
    $wp = Join-Path $t 'wallpapers'
    if (Test-Path -LiteralPath $wp) {
        foreach ($f in Get-ChildItem -LiteralPath $wp -File) {
            if (($f.Name -match $imagePattern -or $f.Name -eq 'captions.txt') -and ($f.Name -cnotmatch '^[A-Za-z0-9_.-]+$' -or $f.Name.Contains('..') -or $f.Name.StartsWith('.'))) {
                [Console]::Error.WriteLine("unsupported wallpaper filename: $($f.FullName). Nothing was sent to the TV."); exit 2
            }
        }
    }
}
foreach ($t in $themeDirs) {
    $name = Split-Path -Leaf $t
    Drop-Theme $name
    Send (Join-Path $t 'config.json') "themes/$name/config.json"
    foreach ($f in 'screenshot.jpg', 'wallpaper.jpg') {
        $p = Join-Path $t $f
        if (Test-Path -LiteralPath $p) { Send $p "themes/$name/$f" }
    }
    $wp = Join-Path $t 'wallpapers'
    if (Test-Path -LiteralPath $wp) {
        foreach ($f in Get-ChildItem -LiteralPath $wp -File) {
            if ($f.Name -match $imagePattern -or $f.Name -eq 'captions.txt') { Send $f.FullName "themes/$name/wallpapers/$($f.Name)" }
        }
    }
    Write-Host "added $name"
}
Write-Host 'On the TV: Launcher settings > Theme.'
