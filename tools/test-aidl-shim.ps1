# 在 app/build/tmp 的假 SDK 中测试 ARM AIDL 替身，不修改真实 Android SDK。
$ErrorActionPreference = 'Stop'
$name = 'aidl-shim-fixture-' + [guid]::NewGuid().ToString('N')
$parent = Join-Path (Get-Location) 'app\build\tmp'
$fixture = Join-Path $parent $name
$sdkTools = Join-Path $fixture 'sdk\build-tools\36.0.0'
$aidl = Join-Path $sdkTools 'aidl'
$backup = $aidl + '.x86_64.disabled'
$bash = 'C:\Program Files\Git\bin\bash.exe'
New-Item -ItemType Directory -Force -Path $sdkTools | Out-Null
$env:DSHA_AIDL_FIXTURE_NAME = $name
try {
    $script = Get-Content -LiteralPath 'build.sh' -Raw
    $start = $script.IndexOf('AIDL_SHIM_BIN=""')
    $end = $script.IndexOf('if [ -z "${DSHA_FORCE_AIDL:-}" ]; then', $start)
    if ($start -lt 0 -or $end -le $start) { throw '无法提取 build.sh 中的 AIDL 函数' }
    Set-Content -LiteralPath (Join-Path $fixture 'functions.sh') -Value $script.Substring($start, $end - $start) -Encoding utf8
    $original = "#!/usr/bin/env bash`necho original-aidl`n"
    Set-Content -LiteralPath $aidl -Value $original -NoNewline -Encoding utf8
    $runner = 'set -euo pipefail; ROOT="$(pwd)"; SDK="$ROOT/app/build/tmp/$DSHA_AIDL_FIXTURE_NAME/sdk"; source "$ROOT/app/build/tmp/$DSHA_AIDL_FIXTURE_NAME/functions.sh"; install_aidl_shim; test -f "$SDK/build-tools/36.0.0/aidl.x86_64.disabled"; head -2 "$SDK/build-tools/36.0.0/aidl" | grep -q "由 build.sh 生成的 aidl 替身"'

    & $bash -c $runner
    if ($LASTEXITCODE -ne 0) { throw '首次安装替身失败' }
    if ((Get-Content -LiteralPath $aidl -Raw) -ne $original -or (Test-Path -LiteralPath $backup)) {
        throw '正常退出没有恢复原始 aidl'
    }

    # Simulate a write/interruption window after the original has moved away.
    & $bash -c ($runner + '; rm "$SDK/build-tools/36.0.0/aidl"; exit 7') 2>$null
    if ($LASTEXITCODE -ne 7 -or (Get-Content -LiteralPath $aidl -Raw) -ne $original -or
            (Test-Path -LiteralPath $backup)) {
        throw '替身文件缺失时没有恢复原始 aidl'
    }

    Move-Item -LiteralPath $aidl -Destination $backup
    Set-Content -LiteralPath $aidl -Value "#!/usr/bin/env bash`n# 由 build.sh 生成的 aidl 替身`n" -NoNewline -Encoding utf8
    & $bash -c $runner
    if ($LASTEXITCODE -ne 0) { throw '中断现场的在位替身恢复失败' }
    if ((Get-Content -LiteralPath $aidl -Raw) -ne $original -or (Test-Path -LiteralPath $backup)) {
        throw '中断现场退出后没有恢复原始 aidl'
    }

    Set-Content -LiteralPath $backup -Value 'unrelated backup' -NoNewline -Encoding utf8
    & $bash -c $runner 2>$null
    if ($LASTEXITCODE -eq 0 -or (Get-Content -LiteralPath $aidl -Raw) -ne $original -or
            (Get-Content -LiteralPath $backup -Raw) -ne 'unrelated backup') {
        throw '未知原件冲突没有拒绝并保留现场'
    }
    Write-Output 'AIDL shim restore fixture: PASS'
} finally {
    Remove-Item Env:DSHA_AIDL_FIXTURE_NAME -ErrorAction SilentlyContinue
    $allowed = [IO.Path]::GetFullPath($parent).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $resolved = [IO.Path]::GetFullPath($fixture)
    if (-not $resolved.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Split-Path -Leaf $resolved).StartsWith('aidl-shim-fixture-')) {
        throw '拒绝清理工作区外的 AIDL 夹具'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
