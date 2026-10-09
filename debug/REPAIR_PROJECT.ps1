$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Repo = 'https://codeload.github.com/igoryan228337/zalypamod/zip/refs/heads/main'
$Root = $PSScriptRoot
$Work = Join-Path $Root 'music-player-repair-work'
$Zip = Join-Path $Root 'zalypamod-main.zip'
$Log = Join-Path $Root 'repair-build.log'

function Say($s) { Write-Host $s -ForegroundColor Cyan }
function Replace-Text([string]$Path,[string]$Pattern,[string]$Replacement) {
    if (!(Test-Path -LiteralPath $Path)) { return }
    $old = [IO.File]::ReadAllText($Path)
    $new = [regex]::Replace($old,$Pattern,$Replacement)
    if ($new -cne $old) { [IO.File]::WriteAllText($Path,$new,(New-Object System.Text.UTF8Encoding($false))) }
}
function Replace-Literal([string]$Path,[string]$Old,[string]$New) {
    if (!(Test-Path -LiteralPath $Path)) { return }
    $text = [IO.File]::ReadAllText($Path)
    $updated = $text.Replace($Old,$New)
    if ($updated -cne $text) { [IO.File]::WriteAllText($Path,$updated,(New-Object System.Text.UTF8Encoding($false))) }
}

try {
    Say '[1/6] Downloading the latest GitHub source...'
    Remove-Item $Zip -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -Uri $Repo -OutFile $Zip -UseBasicParsing
    if (!(Test-Path $Zip) -or (Get-Item $Zip).Length -lt 1000) { throw 'GitHub source archive download failed.' }

    Say '[2/6] Extracting a clean working copy...'
    Remove-Item $Work -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $Work | Out-Null
    Expand-Archive -LiteralPath $Zip -DestinationPath $Work -Force
    $Project = Get-ChildItem $Work -Directory | Select-Object -First 1
    if (!$Project) { throw 'The GitHub archive did not contain a project directory.' }
    $Project = $Project.FullName

    Say '[3/6] Applying Minecraft 1.21.11 API migration fixes...'
    $javaFiles = Get-ChildItem (Join-Path $Project 'src') -Recurse -Filter '*.java' -File
    foreach ($f in $javaFiles) {
        # Mojang mappings for 1.21.11 renamed ResourceLocation to Identifier.
        Replace-Literal $f.FullName 'net.minecraft.resources.ResourceLocation' 'net.minecraft.resources.Identifier'
        Replace-Literal $f.FullName 'ResourceLocation.fromNamespaceAndPath' 'Identifier.fromNamespaceAndPath'
        Replace-Literal $f.FullName 'ResourceLocation.tryParse' 'Identifier.tryParse'
        # Fabric moved the HUD registry to its dedicated hud subpackage.
        Replace-Literal $f.FullName 'net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry' 'net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry'
        Replace-Literal $f.FullName 'net.fabricmc.fabric.api.client.rendering.v1.HudElement' 'net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement'
        # The 1.21.11 HUD pose stack is 2D.
        Replace-Literal $f.FullName '.pose().pushPose()' '.pose().pushMatrix()'
        Replace-Literal $f.FullName '.pose().popPose()' '.pose().popMatrix()'
        # Screen rendering background now requires mouse position and partial tick.
        Replace-Literal $f.FullName 'renderBackground(g);' 'renderBackground(g, mx, my, delta);'
        # Old window accessor.
        Replace-Literal $f.FullName 'getWindow().getWindow()' 'getWindow().handle()'
    }

    # Repair the common comparator inference and missing Path import in MusicScreen.
    $musicScreen = Join-Path $Project 'src/main/java/dev/musicplayer/ui/MusicScreen.java'
    if (Test-Path $musicScreen) {
        $txt = [IO.File]::ReadAllText($musicScreen)
        if ($txt -notmatch 'import java\.nio\.file\.Path;') {
            $txt = $txt -replace '(?m)^import java\.[^;]+;\s*', '$0' # preserve imports
            $txt = $txt -replace '(?m)^package [^;]+;\s*', ('$0' + "`r`nimport java.nio.file.Path;`r`n")
        }
        $txt = $txt.Replace('Comparator.comparing(Track::displayArtist,String.CASE_INSENSITIVE_ORDER)', 'Comparator.<Track,String>comparing(Track::displayArtist,String.CASE_INSENSITIVE_ORDER)')
        $txt = $txt.Replace('Comparator.comparing(Track::displayAlbum,String.CASE_INSENSITIVE_ORDER)', 'Comparator.<Track,String>comparing(Track::displayAlbum,String.CASE_INSENSITIVE_ORDER)')
        [IO.File]::WriteAllText($musicScreen,$txt,(New-Object System.Text.UTF8Encoding($false)))
    }

    # Fix JAVE 4.x EncodingAttributes API: output format is configured on AudioAttributes/Encoder settings differently
    # in this dependency version. Keep the source change conservative and report the relevant location for manual review.
    $jave = Join-Path $Project 'src/main/java/dev/musicplayer/audio/JaveAudioEngine.java'
    if (Test-Path $jave) {
        Replace-Literal $jave 'ea.setFormat("wav"); ' ''
    }

    # Ensure generated build output and logs don't enter the project archive.
    @'
.gradle/
build/
dist/
run/
.tools/
*.class
build.log
repair-build.log
zalypamod-main.zip
music-player-repair-work/
'@ | Set-Content -LiteralPath (Join-Path $Project '.gitignore') -Encoding UTF8

    Say '[4/6] Preparing the corrected-source archive...'
    $PatchedZip = Join-Path $Root 'zalypamod-repaired-source.zip'
    Remove-Item $PatchedZip -Force -ErrorAction SilentlyContinue
    Compress-Archive -Path (Join-Path $Project '*') -DestinationPath $PatchedZip -CompressionLevel Optimal -Force

    Say '[5/6] Attempting the build with the installed Java/Gradle...'
    '' | Set-Content -LiteralPath $Log -Encoding UTF8
    $javaCandidates = @(
        'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe',
        "$env:JAVA_HOME\bin\java.exe"
    )
    $java = $javaCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
    if (!$java) {
        try { $java = (Get-Command java.exe -ErrorAction Stop).Source } catch {}
    }
    if ($java) {
        $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
        $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
    } else {
        'Java executable not found; build skipped.' | Add-Content $Log
        throw 'Source archive created, but Java was not found. Install/use Java 21 and rerun this script.'
    }

    $gradleBat = Join-Path $Project '.tools\gradle-9.2.1\bin\gradle.bat'
    $gradleZip = Join-Path $Project '.tools\gradle-9.2.1-bin.zip'
    if (!(Test-Path $gradleBat) -and (Test-Path $gradleZip)) {
        Expand-Archive -LiteralPath $gradleZip -DestinationPath (Join-Path $Project '.tools') -Force
    }
    if (!(Test-Path $gradleBat)) {
        try { $gradleBat = (Get-Command gradle.bat -ErrorAction Stop).Source } catch {}
    }
    if (!$gradleBat) {
        'Gradle not found locally; build skipped. The source archive is still available.' | Add-Content $Log
        throw 'Source archive created, but Gradle 9.2.1 was not found. Put .tools\gradle-9.2.1-bin.zip in the project and rerun.'
    }

    Push-Location $Project
    try {
        & $gradleBat clean build --stacktrace --no-daemon 2>&1 | Tee-Object -FilePath $Log -Append
        $buildCode = $LASTEXITCODE
    } finally { Pop-Location }

    if ($buildCode -eq 0) {
        $dist = Join-Path $Project 'dist'
        New-Item -ItemType Directory -Force -Path $dist | Out-Null
        Get-ChildItem (Join-Path $Project 'build\libs') -Filter '*.jar' -File |
            Where-Object { $_.Name -notlike '*-sources.jar' } |
            ForEach-Object { Copy-Item $_.FullName (Join-Path $dist $_.Name) -Force }
        $FullZip = Join-Path $Root 'zalypamod-repaired-project-with-jar.zip'
        Remove-Item $FullZip -Force -ErrorAction SilentlyContinue
        Compress-Archive -Path (Join-Path $Project '*') -DestinationPath $FullZip -CompressionLevel Optimal -Force
        Say '[6/6] BUILD SUCCESS. Project + JAR archive created.'
        Write-Host "Project: $FullZip" -ForegroundColor Green
    } else {
        Say '[6/6] Build still reports errors. Patched source archive and repair-build.log were preserved.'
        Write-Host "Source archive: $PatchedZip" -ForegroundColor Yellow
        Write-Host "Build log: $Log" -ForegroundColor Yellow
        Write-Host 'Upload repair-build.log to GitHub or send it here for the next focused fix batch.' -ForegroundColor Yellow
    }
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host 'Check the generated source archive and repair-build.log in this folder.' -ForegroundColor Yellow
}
Read-Host 'Press Enter to close'
