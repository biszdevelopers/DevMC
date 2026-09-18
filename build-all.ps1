# build-all.ps1
# Builds every DevMC plugin in dependency order and deploys the jars to a server.
#
# The default target is the local dev server, which cannot load combat or SMP
# (they hard-depend on Citizens), so those are excluded by default. Pass
# -Exclude @() to build and deploy them too.
#
# Examples:
#   .\build-all.ps1                      # build + tests, deploy the runnable set
#   .\build-all.ps1 -SkipTests           # faster: build without running tests
#   .\build-all.ps1 -Clean               # mvn clean install each plugin
#   .\build-all.ps1 -Only plugin-world   # build just one plugin
#   .\build-all.ps1 -Exclude @()         # include combat and SMP
#   .\build-all.ps1 -PluginsDir "D:\server\plugins"
[CmdletBinding()]
param(
  [string]$PluginsDir = 'Y:\Games\Minecraft\devServer\plugins',
  [switch]$SkipTests,
  [switch]$Clean,
  [string[]]$Only,
  [string[]]$Exclude = @('plugin-combat', 'plugin-smp'),
  [string]$Maven = 'mvn'
)

$ErrorActionPreference = 'Stop'

$root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }

# Base services must be installed before dependents compile.
$order = @(
  'plugin-bundler',
  'plugin-currency',
  'plugin-items',
  'plugin-enchants',
  'plugin-combat',
  'plugin-smp',
  'plugin-world'
)

if (-not (Test-Path -LiteralPath $PluginsDir)) {
  Write-Host "Creating plugins directory: $PluginsDir"
  New-Item -ItemType Directory -Path $PluginsDir -Force | Out-Null
}

$deployed = @()
$failed = @()
$locked = @()

foreach ($plugin in $order) {
  if ($Only -and ($Only -notcontains $plugin)) { continue }
  if ($Exclude -and ($Exclude -contains $plugin)) {
    Write-Host "Skipping $plugin (-Exclude)" -ForegroundColor DarkGray
    continue
  }

  $dir = Join-Path $root $plugin
  $pom = Join-Path $dir 'pom.xml'
  if (-not (Test-Path -LiteralPath $pom)) {
    Write-Warning "Skipping $plugin (no pom.xml)"
    continue
  }

  Write-Host ''
  Write-Host "=== Building $plugin ===" -ForegroundColor Cyan

  $goals = @('-f', $pom)
  if ($Clean) { $goals += 'clean' }
  $goals += 'install'
  if ($SkipTests) { $goals += '-DskipTests' }

  & $Maven @goals
  if ($LASTEXITCODE -ne 0) {
    Write-Host "FAILED: $plugin" -ForegroundColor Red
    $failed += $plugin
    continue
  }

  # The shade plugin replaces the main artifact; ignore the pre-shade original.
  $jar = Get-ChildItem -LiteralPath (Join-Path $dir 'target') -Filter "$plugin-*.jar" -ErrorAction SilentlyContinue |
    Where-Object {
      $_.Name -notlike 'original-*' -and
      $_.Name -notlike '*-sources*' -and
      $_.Name -notlike '*-javadoc*'
    } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

  if (-not $jar) {
    Write-Warning "No built jar found for $plugin"
    $failed += $plugin
    continue
  }

  # Remove previous builds of this plugin so only one version is loaded.
  $destination = Join-Path $PluginsDir $jar.Name
  try {
    Get-ChildItem -LiteralPath $PluginsDir -Filter "$plugin-*.jar" -ErrorAction SilentlyContinue |
      Remove-Item -Force -ErrorAction Stop
    Copy-Item -LiteralPath $jar.FullName -Destination $destination -Force -ErrorAction Stop
    Write-Host "Deployed $($jar.Name)" -ForegroundColor Green
    $deployed += $jar.Name
  } catch {
    Write-Host "LOCKED $($jar.Name) (server running?)" -ForegroundColor Yellow
    $locked += $jar.Name
  }
}

Write-Host ''
Write-Host '=== Summary ===' -ForegroundColor Cyan
foreach ($name in $deployed) { Write-Host "  OK     $name" -ForegroundColor Green }
foreach ($name in $locked) { Write-Host "  LOCKED $name" -ForegroundColor Yellow }
foreach ($name in $failed) { Write-Host "  FAIL   $name" -ForegroundColor Red }

if ($locked.Count -gt 0) {
  Write-Host ''
  Write-Host 'Some jars could not be replaced because they are in use.' -ForegroundColor Yellow
  Write-Host 'Stop the server and re-run this script to deploy them.' -ForegroundColor Yellow
  exit 1
}
if ($failed.Count -gt 0) {
  exit 1
}
Write-Host "All plugins built and deployed to $PluginsDir"
