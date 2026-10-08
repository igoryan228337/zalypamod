$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$Log = Join-Path $Root 'build.log'

function Log($Text) { $Text | Tee-Object -FilePath $Log -Append }
function Get-JavaVersion($JavaExe) {
    try {
        $s = (& $JavaExe -version 2>&1 | Out-String)
        if ($s -match 'version "([0-9]+)') { return [int]$Matches[1] }
    } catch {}
    return 0
}
function Resolve-JavaHome($JavaExe) {
    try {
        $s = (& $JavaExe -XshowSettings:properties -version 2>&1 | Out-String)
        if ($s -match '(?m)^\s*java\.home\s*=\s*(.+?)\s*$') {
            $h=$Matches[1].Trim()
            if (Test-Path (Join-Path $h 'bin\java.exe')) { return $h }
        }
    } catch {}
    $p = Split-Path $JavaExe -Parent
    return (Split-Path $p -Parent)
}
function Add-Candidate([System.Collections.Generic.List[string]]$List,[hashtable]$Seen,[string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return }
    $Path=$Path.Trim().Trim('"')
    try { $Path=[IO.Path]::GetFullPath($Path) } catch { return }
    if ((Test-Path -LiteralPath $Path -PathType Leaf) -and -not $Seen.ContainsKey($Path)) { $Seen[$Path]=$true; [void]$List.Add($Path) }
}
function Get-JavaCandidates {
    $list=New-Object 'System.Collections.Generic.List[string]'; $seen=@{}
    # Fastest: PATH and JAVA_HOME. This is where the previous working Java 25 should be found.
    if ($env:JAVA_HOME) { Add-Candidate $list $seen (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    try { foreach($x in @(where.exe java.exe 2>$null)){ Add-Candidate $list $seen ([string]$x) } } catch {}
    try { foreach($x in @(Get-Command java.exe -All -ErrorAction SilentlyContinue)){ if($x.Source){Add-Candidate $list $seen $x.Source} } } catch {}

    # Registry: direct JDK/JRE homes, no disk traversal.
    $regPaths=@('HKLM:\SOFTWARE\JavaSoft\JDK','HKLM:\SOFTWARE\JavaSoft\JRE','HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK','HKCU:\SOFTWARE\JavaSoft\JDK')
    foreach($rp in $regPaths){ try { if(Test-Path $rp){ foreach($k in @(Get-ChildItem $rp -ErrorAction SilentlyContinue)){ $h=(Get-ItemProperty $k.PSPath -ErrorAction SilentlyContinue).JavaHome; if($h){Add-Candidate $list $seen (Join-Path $h 'bin\java.exe')} } $h=(Get-ItemProperty $rp -ErrorAction SilentlyContinue).JavaHome; if($h){Add-Candidate $list $seen (Join-Path $h 'bin\java.exe')} } } catch {} }

    # Common install roots: only immediate children / max depth 2, never whole C:.
    $roots=@("$env:ProgramFiles\Java","$env:ProgramFiles\Eclipse Adoptium","$env:ProgramFiles\Microsoft\jdk","$env:LOCALAPPDATA\Programs\Eclipse Adoptium","$env:LOCALAPPDATA\Programs\Microsoft\jdk","$env:USERPROFILE\.jdks","$env:LOCALAPPDATA\PrismLauncher","$env:APPDATA\PrismLauncher","$env:ProgramFiles\PrismLauncher","$env:ProgramFiles(x86)\PrismLauncher")
    foreach($r in $roots){ if(-not $r -or -not(Test-Path -LiteralPath $r)){continue}; Add-Candidate $list $seen (Join-Path $r 'bin\java.exe'); try { Get-ChildItem -LiteralPath $r -Directory -Depth 2 -ErrorAction SilentlyContinue | ForEach-Object { Add-Candidate $list $seen (Join-Path $_.FullName 'bin\java.exe'); Add-Candidate $list $seen (Join-Path $_.FullName 'java.exe') } } catch {} }

    # Prism config: inspect only a few likely small config files, not every file recursively.
    $cfg=@("$env:APPDATA\PrismLauncher\prismlauncher.cfg","$env:LOCALAPPDATA\PrismLauncher\prismlauncher.cfg")
    foreach($f in $cfg){ if(Test-Path -LiteralPath $f){ try { $txt=Get-Content -LiteralPath $f -Raw -ErrorAction Stop; foreach($m in [regex]::Matches($txt,'(?i)(?:JavaPath|javaPath|java_path)\s*[=:]\s*["'']?([^"''\r\n]+java\.exe)')){Add-Candidate $list $seen $m.Groups[1].Value} } catch {} } }
    return $list
}

Remove-Item $Log -Force -ErrorAction SilentlyContinue
"============================================================" | Out-File $Log -Encoding utf8
"  MUSIC PLAYER 1.21.11 - ONE CLICK BUILD" | Add-Content $Log
"============================================================" | Add-Content $Log
Log ""
Log "[1/4] Using the installed Java 21 specified by the user..."
$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$JavaExe = Join-Path $JavaHome 'bin\java.exe'
if(-not (Test-Path -LiteralPath $JavaExe -PathType Leaf)) {
    throw "Java 21 was not found at the configured path: $JavaExe"
}
$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH
Log "Using Java executable: $JavaExe"
Log "Using JAVA_HOME: $env:JAVA_HOME"
$Tools = Join-Path $Root '.tools'
New-Item -ItemType Directory -Force -Path $Tools | Out-Null
$JavaVersionFile = Join-Path $Tools 'java-version.txt'
Remove-Item -LiteralPath $JavaVersionFile -Force -ErrorAction SilentlyContinue
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $JavaExe
$psi.Arguments = '-version'
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()
$stdout = $proc.StandardOutput.ReadToEnd()
$stderr = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()
$JavaExitCode = $proc.ExitCode
$verOutput = ($stdout + $stderr).Trim()
if ([string]::IsNullOrWhiteSpace($verOutput)) { $verOutput = 'Java started; no version text was returned.' }
Log $verOutput
Log "Java process exit code: $JavaExitCode"
if ($JavaExitCode -ne 0) { throw "Java 21 exists but could not be started (exit code $JavaExitCode): $JavaExe" }

Log "[2/4] Preparing Gradle..."
$GradleDir=Join-Path $Root '.tools\gradle-9.2.1';$GradleBat=Join-Path $GradleDir 'bin\gradle.bat'
if(-not(Test-Path -LiteralPath $GradleBat)) {
    $zip=Join-Path $Root '.tools\gradle-9.2.1-bin.zip'
    New-Item -ItemType Directory -Force -Path (Split-Path $zip) | Out-Null
    if(-not(Test-Path -LiteralPath $zip)) {
        Log "ERROR: Gradle 9.2.1 ZIP was not found."
        Log "Put gradle-9.2.1-bin.zip into: $Tools"
        throw "Gradle ZIP missing: $zip"
    }
    Log "Using local Gradle archive: $zip"
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $Root '.tools') -Force
}
if(-not(Test-Path -LiteralPath $GradleBat)){throw "Gradle executable not found after extracting local archive: $GradleBat"}
if(-not(Test-Path $GradleBat)){throw "Gradle executable not found: $GradleBat"}
Log "[3/4] Building mod...";Push-Location $Root;try{$ErrorActionPreference='Continue';& $GradleBat clean build --stacktrace --no-daemon 2>&1|Tee-Object -FilePath $Log -Append;$GradleExitCode=$LASTEXITCODE;if($GradleExitCode -ne 0){throw "Gradle build failed with exit code $GradleExitCode"}}finally{$ErrorActionPreference='Stop';Pop-Location}
Log "[4/4] Copying JAR to dist...";$Dist=Join-Path $Root 'dist';New-Item -ItemType Directory -Force -Path $Dist|Out-Null;Get-ChildItem (Join-Path $Root 'build\libs') -Filter '*.jar' -File|Where-Object {$_.Name -notlike '*-sources.jar'}|ForEach-Object{Copy-Item $_.FullName (Join-Path $Dist $_.Name) -Force;Log "Copied: $($_.Name)"};Log "";Log "BUILD SUCCESS";Log "JAR files are in: $Dist"
