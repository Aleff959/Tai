param(
    [string]$ProjectRoot = "C:\Users\Alpha\OneDrive\Documentos\Tai",
    [string]$D8Path = "C:\Users\Alpha\AppData\Local\Android\Sdk\build-tools\36.0.0\d8.bat"
)

$ErrorActionPreference = "Stop"

$kotlinClassesDir = Join-Path $ProjectRoot "app\build\tmp\kotlin-classes\release\com"
$javacClassesDir  = Join-Path $ProjectRoot "app\build\intermediates\javac\release\compileReleaseJavaWithJavac\classes\com"
$modulesDir       = Join-Path $ProjectRoot "modules"
$tempStageDir     = Join-Path $modulesDir "stage_classes"
$tempZip          = Join-Path $modulesDir "temp_classes.zip"
$outputDex        = Join-Path $modulesDir "taicore.dex"

if (Test-Path $tempStageDir) { Remove-Item $tempStageDir -Recurse -Force }
if (Test-Path $tempZip)      { Remove-Item $tempZip -Force }

New-Item -ItemType Directory -Path (Join-Path $tempStageDir "com") -Force | Out-Null

if (Test-Path $kotlinClassesDir) {
    Copy-Item -Path "$kotlinClassesDir\*" -Destination (Join-Path $tempStageDir "com") -Recurse -Force
}
if (Test-Path $javacClassesDir) {
    Copy-Item -Path "$javacClassesDir\*" -Destination (Join-Path $tempStageDir "com") -Recurse -Force
}

Compress-Archive -Path (Join-Path $tempStageDir "com") -DestinationPath $tempZip -Force
& $D8Path --min-api 26 --output $modulesDir $tempZip

$generatedDex = Join-Path $modulesDir "classes.dex"
if (Test-Path $generatedDex) {
    Move-Item -Path $generatedDex -Destination $outputDex -Force
}

Remove-Item $tempStageDir -Recurse -Force
Remove-Item $tempZip -Force

$dexItem = Get-Item $outputDex
Write-Host "taicore.dex gerado com sucesso: $($dexItem.FullName) ($($dexItem.Length) bytes)"
