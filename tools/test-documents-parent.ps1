param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Sdk='F:/DSHA/_toolchains/android-sdk',
    [string]$Jdk='F:/DSHA/_toolchains/jdk-17'
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$output=Join-Path $repo ('app/build/documents-parent/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path "$output/classes","$output/dex" | Out-Null
$platform=Join-Path $Sdk 'platforms/android-37.0/android.jar'
$sources=@(
    'tools/fixtures/documents-parent/DocumentParentFixture.java',
    'tools/fixtures/documents-parent/com/deepseekharness/app/util/UiText.java',
    'app/src/main/java/com/deepseekharness/app/util/DocumentPaths.java',
    'app/src/main/java/com/deepseekharness/app/backup/BackupLimits.java',
    'app/src/main/java/com/deepseekharness/app/backup/BackupFileSystem.java',
    'app/src/main/java/com/deepseekharness/app/backup/AndroidBackupFileSystem.java'
)
$inputs=@($sources | ForEach-Object { Join-Path $repo $_ })
& "$Jdk/bin/javac.exe" -encoding UTF-8 --release 17 -cp $platform -d "$output/classes" @inputs
if($LASTEXITCODE -ne 0){throw 'documents fixture compilation failed'}
& "$Jdk/bin/jar.exe" --create --file "$output/classes.jar" -C "$output/classes" .
if($LASTEXITCODE -ne 0){throw 'documents class archive failed'}
& "$Jdk/bin/java.exe" -cp "$Sdk/build-tools/36.0.0/lib/d8.jar" com.android.tools.r8.D8 --min-api 33 --lib $platform --output "$output/dex" "$output/classes.jar"
if($LASTEXITCODE -ne 0){throw 'documents DEX failed'}
& "$Jdk/bin/jar.exe" --create --file "$output/fixture.jar" -C "$output/dex" classes.dex
if($LASTEXITCODE -ne 0){throw 'documents DEX archive failed'}
$adb=Join-Path $Sdk 'platform-tools/adb.exe'
$api=(& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
if($LASTEXITCODE -ne 0 -or [int]$api -lt 33){throw 'this standalone fixture requires the observed API33+ device; it is not an API23 compatibility claim'}
$deviceRoot='/data/local/tmp/dsha-r2-documents-'+[guid]::NewGuid().ToString('N')
if($deviceRoot -notmatch '^/data/local/tmp/dsha-r2-documents-[0-9a-f]{32}$'){throw 'unexpected fixture directory'}
& $adb -s $Serial shell mkdir $deviceRoot
if($LASTEXITCODE -ne 0){throw 'fixture directory creation failed'}
$deviceRoot | Set-Content -LiteralPath "$output/device-path.txt" -Encoding ascii
& $adb -s $Serial push "$output/fixture.jar" "$deviceRoot/fixture.jar"
if($LASTEXITCODE -ne 0){throw 'fixture copy failed'}
& $adb -s $Serial shell chmod 444 "$deviceRoot/fixture.jar"
if($LASTEXITCODE -ne 0){throw 'fixture mode failed'}
& $adb -s $Serial shell "CLASSPATH=$deviceRoot/fixture.jar app_process /system/bin DocumentParentFixture $deviceRoot" 2>&1 | Tee-Object -FilePath "$output/result.txt"
$result=$LASTEXITCODE
$proof=@{schema=1;deviceApi=[int]$api;scope='shell UID, synthetic tree only, no APK installation or app data access';deviceRoot=$deviceRoot;exitCode=$result;inputs=@{}}
foreach($source in $sources){$proof.inputs[$source]=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repo $source)).Hash.ToLowerInvariant()}
$proof.fixtureSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath "$output/fixture.jar").Hash.ToLowerInvariant()
$proof | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$output/manifest.json" -Encoding utf8
if($result -ne 0){throw "documents fixture failed; preserved evidence: $output"}
Write-Output "PASS: $output (synthetic device directory retained for explicit checked cleanup)"
