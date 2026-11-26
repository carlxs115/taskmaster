<#
.SYNOPSIS
    Construye el instalador de Windows de TaskMaster (TaskMaster-<versión>.exe).

.DESCRIPTION
    Encadena el empaquetado de ambos módulos con el Maven Wrapper, genera un
    runtime reducido con jlink y crea el instalador con jpackage (WiX 3.x).

    Requisitos: Windows 10 o superior, JDK 21 (java, jlink y jpackage en el
    PATH; JAVA_HOME, si está definido, solo se usa para localizar jmods) y, para
    -Tipo exe, WiX Toolset 3.x (candle.exe y light.exe en el PATH).

    Todo lo temporal queda en taskmaster-frontend\target\installer, que el
    script puede borrar y recrear. Nunca borra nada fuera de esa carpeta.

    Este script vive en scripts\ y no en un directorio llamado "packaging":
    el .gitignore ignora cualquier carpeta con ese nombre.

.PARAMETER Version
    Versión del instalador (--app-version y nombre del .exe). No se deriva de
    los pom, que usan versiones SNAPSHOT que WiX no acepta.

.PARAMETER Tipo
    exe (instalador, necesita WiX) o app-image (carpeta de la aplicación, para
    depurar sin WiX).

.PARAMETER Salida
    Carpeta de destino. Por defecto taskmaster-frontend\target\installer\dist.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\build-installer.ps1 -Version 1.0.0
#>
param(
    [string]$Version = '1.0.0',
    [ValidateSet('exe', 'app-image')]
    [string]$Tipo = 'exe',
    [string]$Salida = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Versión fija del plugin de dependencias de Maven: se invoca por coordenadas
# y no está declarado en ningún pom, así que la fijamos aquí para que la
# construcción sea reproducible.
$DependencyPlugin = 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1'

# Módulos del runtime. Salen de jdeps sobre el backend (Spring Boot, Hibernate y
# SQLite se ejecutan con el mismo runtime que el frontend, porque MainApp lanza
# el backend con su propio java.home) más el module-info del frontend y
# jdk.localedata para que las fechas salgan en es-ES. El módulo del propio
# frontend no entra: va como JAR en el classpath de la aplicación.
$Modulos = @(
    'java.base', 'java.compiler', 'java.desktop', 'java.instrument',
    'java.logging', 'java.management', 'java.naming', 'java.net.http',
    'java.prefs', 'java.rmi', 'java.scripting', 'java.security.jgss',
    'java.sql', 'java.sql.rowset', 'java.xml',
    'jdk.jfr', 'jdk.localedata', 'jdk.unsupported', 'jdk.unsupported.desktop',
    'javafx.controls', 'javafx.fxml', 'javafx.swing',
    'org.controlsfx.controls', 'net.synedra.validatorfx',
    'org.kordamp.bootstrapfx.core', 'org.kordamp.ikonli.javafx',
    'org.kordamp.ikonli.fontawesome5',
    'com.fasterxml.jackson.databind', 'com.fasterxml.jackson.datatype.jsr310',
    'org.slf4j', 'ch.qos.logback.classic'
)

function Fallar([string]$Mensaje) {
    throw $Mensaje
}

function Comprobar-Comando([string]$Nombre, [string]$Ayuda) {
    if (-not (Get-Command $Nombre -ErrorAction SilentlyContinue)) {
        Fallar "No se encuentra '$Nombre' en el PATH. $Ayuda"
    }
}

function Comprobar-Salida([string]$Que) {
    if ($LASTEXITCODE -ne 0) {
        Fallar "Ha fallado: $Que (código de salida $LASTEXITCODE)."
    }
}

# Lee <artifactId>-<version>.jar de un pom.xml.
function Leer-NombreJar([string]$RutaPom) {
    [xml]$pom = Get-Content -LiteralPath $RutaPom -Encoding UTF8
    return ('{0}-{1}.jar' -f $pom.project.artifactId, $pom.project.version)
}

# --- Rutas -------------------------------------------------------------------
$raiz = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $raiz 'taskmaster-backend'
$frontend = Join-Path $raiz 'taskmaster-frontend'
$icono = Join-Path $frontend 'packaging\TaskMaster.ico'
$mainApp = Join-Path $frontend 'src\main\java\com\taskmaster\taskmasterfrontend\MainApp.java'
$instalador = Join-Path $frontend 'target\installer'
$deps = Join-Path $instalador 'deps'
$runtime = Join-Path $instalador 'runtime'
$entrada = Join-Path $instalador 'input'
$dist = Join-Path $instalador 'dist'
if ($Salida -eq '') {
    $Salida = $dist
}

# --- Requisitos --------------------------------------------------------------
Write-Host '==> Comprobando requisitos'
Comprobar-Comando 'java' 'Instala un JDK 21 y añade su carpeta bin al PATH.'
Comprobar-Comando 'jlink' 'Hace falta un JDK 21 completo, no solo un JRE.'
Comprobar-Comando 'jpackage' 'Hace falta un JDK 21 completo, no solo un JRE.'

$versionJdk = @(& jlink --version)[0]
Comprobar-Salida 'jlink --version'
if ($versionJdk -notmatch '^(\d+)') {
    Fallar "No se reconoce la versión del JDK: $versionJdk"
}
if ([int]$Matches[1] -lt 21) {
    Fallar "Se necesita JDK 21 o superior (jlink indica $versionJdk)."
}

if ($Tipo -eq 'exe') {
    Comprobar-Comando 'candle.exe' 'Instala WiX Toolset 3.x y añade su carpeta bin al PATH.'
    Comprobar-Comando 'light.exe' 'Instala WiX Toolset 3.x y añade su carpeta bin al PATH.'
}

foreach ($ruta in @($icono, $mainApp, (Join-Path $backend 'mvnw.cmd'), (Join-Path $frontend 'mvnw.cmd'))) {
    if (-not (Test-Path -LiteralPath $ruta)) {
        Fallar "Falta un fichero necesario: $ruta"
    }
}

# Carpeta jmods del JDK: JAVA_HOME si está definido y válido; si no, la del
# jlink que hay en el PATH.
$jmods = ''
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'jmods'))) {
    $jmods = Join-Path $env:JAVA_HOME 'jmods'
}
else {
    $rutaJlink = (Get-Command jlink).Source
    $jmods = Join-Path (Split-Path -Parent (Split-Path -Parent $rutaJlink)) 'jmods'
}
if (-not (Test-Path -LiteralPath $jmods)) {
    Fallar "No se encuentra la carpeta jmods del JDK ($jmods)."
}

# --- Coherencia del JAR del backend -------------------------------------------
# MainApp busca el backend por nombre en appdir; si el pom cambia de versión sin
# tocar la constante, el instalador arrancaría sin backend.
$jarBackend = Leer-NombreJar (Join-Path $backend 'pom.xml')
$jarFrontend = Leer-NombreJar (Join-Path $frontend 'pom.xml')
$textoMainApp = Get-Content -LiteralPath $mainApp -Raw -Encoding UTF8
if ($textoMainApp -notmatch 'BACKEND_JAR\s*=\s*"([^"]+)"') {
    Fallar 'No se encuentra la constante BACKEND_JAR en MainApp.java.'
}
if ($Matches[1] -ne $jarBackend) {
    Fallar "MainApp.BACKEND_JAR ($($Matches[1])) no coincide con el JAR del backend ($jarBackend)."
}

# --- Backend -----------------------------------------------------------------
# -DskipTests: los tests del backend se ejecutan con validar.sh, con la base de
# datos aislada; aquí solo se empaqueta.
Write-Host '==> Empaquetando el backend'
Push-Location $backend
try {
    & .\mvnw.cmd -q -B package '-DskipTests'
    Comprobar-Salida 'mvnw.cmd package del backend'
}
finally {
    Pop-Location
}
$rutaJarBackend = Join-Path $backend ('target\' + $jarBackend)
if (-not (Test-Path -LiteralPath $rutaJarBackend)) {
    Fallar "No se ha generado $rutaJarBackend"
}

# --- Frontend y dependencias ---------------------------------------------------
# Se parte de una carpeta limpia (solo dentro de target\installer).
$destinoTemporal = [System.IO.Path]::GetFullPath($instalador)
$limiteTarget = [System.IO.Path]::GetFullPath((Join-Path $frontend 'target')) + [System.IO.Path]::DirectorySeparatorChar
if (-not $destinoTemporal.StartsWith($limiteTarget, [System.StringComparison]::OrdinalIgnoreCase)) {
    Fallar "Ruta temporal inesperada: $destinoTemporal"
}
foreach ($carpeta in @($deps, $runtime, $entrada, $dist)) {
    if (Test-Path -LiteralPath $carpeta) {
        Remove-Item -LiteralPath $carpeta -Recurse -Force
    }
}
New-Item -ItemType Directory -Force -Path $deps, $entrada | Out-Null

Write-Host '==> Empaquetando el frontend y copiando sus dependencias'
Push-Location $frontend
try {
    & .\mvnw.cmd -q -B package '-DskipTests'
    Comprobar-Salida 'mvnw.cmd package del frontend'
    # En Windows Maven resuelve JavaFX con el clasificador win.
    & .\mvnw.cmd -q -B ($DependencyPlugin + ':copy-dependencies') '-DincludeScope=runtime' '-DoutputDirectory=target\installer\deps'
    Comprobar-Salida 'mvnw.cmd copy-dependencies del frontend'
}
finally {
    Pop-Location
}
$rutaJarFrontend = Join-Path $frontend ('target\' + $jarFrontend)
if (-not (Test-Path -LiteralPath $rutaJarFrontend)) {
    Fallar "No se ha generado $rutaJarFrontend"
}

# jpackage empaqueta en --input los dos JAR: el del frontend (arranque) y el del
# backend, que MainApp busca en appdir junto al del frontend.
Copy-Item -LiteralPath $rutaJarFrontend -Destination $entrada
Copy-Item -LiteralPath $rutaJarBackend -Destination $entrada

# --- Runtime (jlink) -----------------------------------------------------------
Write-Host '==> Generando el runtime con jlink'
& jlink --module-path "$jmods;$deps" --add-modules ($Modulos -join ',') --output $runtime --strip-debug --no-header-files --no-man-pages --compress zip-6
Comprobar-Salida 'jlink'

# --- Instalador (jpackage) -------------------------------------------------------
Write-Host "==> Ejecutando jpackage ($Tipo)"
New-Item -ItemType Directory -Force -Path $Salida | Out-Null

# Comillas simples en -Dappdir: $APPDIR lo expande el lanzador de jpackage, no
# PowerShell. Sin esta propiedad MainApp busca el backend en la ruta de desarrollo.
$argumentos = @(
    '--type', $Tipo,
    '--name', 'TaskMaster',
    '--app-version', $Version,
    '--input', $entrada,
    '--main-jar', $jarFrontend,
    '--main-class', 'com.taskmaster.taskmasterfrontend.MainApp',
    '--runtime-image', $runtime,
    '--java-options', '-Dappdir=$APPDIR',
    '--icon', $icono,
    '--dest', $Salida,
    '--description', 'Gestor de proyectos y tareas de escritorio',
    '--vendor', 'Carlos Riera Molina',
    '--copyright', '2026 Carlos Riera Molina'
)
if ($Tipo -eq 'exe') {
    $argumentos += @('--win-menu', '--win-shortcut', '--win-dir-chooser')
}
& jpackage @argumentos
Comprobar-Salida 'jpackage'

# --- Resultado -------------------------------------------------------------------
if ($Tipo -eq 'exe') {
    $resultado = Join-Path $Salida ('TaskMaster-' + $Version + '.exe')
}
else {
    $resultado = Join-Path $Salida 'TaskMaster'
}
if (-not (Test-Path -LiteralPath $resultado)) {
    Fallar "jpackage ha terminado, pero no existe $resultado"
}
if ($Tipo -eq 'exe') {
    $tamano = [math]::Round((Get-Item -LiteralPath $resultado).Length / 1MB, 1)
    Write-Host "Instalador generado: $resultado ($tamano MB)"
}
else {
    Write-Host "Aplicación generada: $resultado"
}
