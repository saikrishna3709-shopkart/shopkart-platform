[CmdletBinding()]
param(
    [ValidateSet('start', 'test', 'validate', 'status', 'stop')]
    [string]$Action = 'start',
    [ValidateRange(1024, 65535)]
    [int]$Port = 8088,
    [ValidateRange(1024, 65530)]
    [int]$ServicePortBase = 18000,
    [string]$Maven = 'mvn',
    [string]$Java = 'java',
    [string]$Helm = 'helm',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$demoDirectory = Join-Path $root '.demo'
$stateFile = Join-Path $demoDirectory 'state.json'

function Read-DemoState {
    if (-not (Test-Path $stateFile)) {
        throw 'No managed demo exists. Run this script with -Action start first.'
    }
    Get-Content $stateFile -Raw | ConvertFrom-Json
}

function Stop-Demo {
    $state = Read-DemoState
    foreach ($service in $state.services) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($service.processId)"
        if ($null -eq $process) {
            continue
        }
        if (-not $process.CommandLine.Contains("-Dshopkart.demo.id=$($state.runId)")) {
            throw "Process $($service.processId) is not owned by this demo; it was not stopped."
        }
        Stop-Process -Id $service.processId -ErrorAction SilentlyContinue
    }
    Remove-Item $stateFile
    Write-Output 'Demo stopped. Data and logs are retained in .demo.'
}

function Test-Demo {
    param($State)
    & $Maven --batch-mode --no-transfer-progress -f (Join-Path $root 'tests/smoke/pom.xml') test `
        '-Dtest=PublicApiSmokeTest' "-Dshopkart.baseUrl=$($State.url)" `
        '-Dshopkart.service=storefront-service' '-Dshopkart.expectedRevision=local'
    if ($LASTEXITCODE -ne 0) {
        throw "Demo smoke checks failed. Inspect $demoDirectory and tests/smoke/target/surefire-reports."
    }
}

if ($Action -eq 'stop') {
    Stop-Demo
    return
}
if ($Action -eq 'status') {
    $state = Read-DemoState
    foreach ($service in $state.services) {
        $health = 'unavailable'
        try {
            $health = (Invoke-RestMethod "http://127.0.0.1:$($service.port)/readyz" -TimeoutSec 3).status
        } catch {
            $health = 'unavailable'
        }
        [pscustomobject]@{ Service = $service.name; Port = $service.port; Status = $health }
    }
    Write-Output $state.url
    return
}

if ($Maven -eq 'mvn' -and -not (Get-Command $Maven -ErrorAction SilentlyContinue)) {
    foreach ($mavenHome in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($mavenHome -and (Test-Path (Join-Path $mavenHome 'bin/mvn.cmd'))) {
            $Maven = Join-Path $mavenHome 'bin/mvn.cmd'
            break
        }
    }
}
if ($Java -eq 'java' -and -not (Get-Command $Java -ErrorAction SilentlyContinue)) {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        $Java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    }
}
$null = Get-Command $Maven -ErrorAction Stop
$javaCommand = (Get-Command $Java -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path (Split-Path $javaCommand -Parent) -Parent
if ($Action -eq 'validate') {
    $helmCommand = (Get-Command $Helm -ErrorAction Stop).Source
    $manifests = (Resolve-Path (Join-Path $root '../shopkart-manifests')).Path
    & $Maven --batch-mode --no-transfer-progress -f (Join-Path $root 'tests/smoke/pom.xml') test `
        '-Dtest=DeploymentConfigurationTest' "-Dshopkart.manifests=$manifests" "-Dshopkart.helm=$helmCommand"
    if ($LASTEXITCODE -ne 0) {
        throw 'Delivery configuration checks failed. See tests/smoke/target/surefire-reports.'
    }
    return
}
if ($Action -eq 'test') {
    Test-Demo (Read-DemoState)
    return
}
if (Test-Path $stateFile) {
    throw 'A managed demo already exists. Use -Action status or -Action stop before starting again.'
}

$definitions = @(
    @{ name = 'catalog-service'; port = $ServicePortBase + 1 },
    @{ name = 'inventory-service'; port = $ServicePortBase + 2 },
    @{ name = 'payment-service'; port = $ServicePortBase + 3 },
    @{ name = 'order-service'; port = $ServicePortBase + 4 },
    @{ name = 'notification-service'; port = $ServicePortBase + 5 },
    @{ name = 'storefront-service'; port = $Port }
)
if (($definitions.port | Select-Object -Unique).Count -ne 6) {
    throw 'The storefront port must not overlap the five backend ports.'
}
foreach ($service in $definitions) {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $service.port)
    try {
        $listener.Start()
    } catch {
        throw "Port $($service.port) is already in use. Choose another -Port or -ServicePortBase."
    } finally {
        $listener.Stop()
    }
}

$null = [System.IO.Directory]::CreateDirectory($demoDirectory)
$null = [System.IO.Directory]::CreateDirectory((Join-Path $demoDirectory 'data'))
if (-not $SkipBuild) {
    Write-Output 'Building all six services and enforcing their test and coverage gates.'
    & $Maven --batch-mode --no-transfer-progress --log-file (Join-Path $demoDirectory 'build.log') `
        -f (Join-Path $root 'pom.xml') verify
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed. See $demoDirectory/build.log. No demo services were started."
    }
}

$classpathFile = Join-Path $demoDirectory 'catalog-classpath.txt'
& $Maven --batch-mode --no-transfer-progress -f (Join-Path $root 'services/catalog-service/pom.xml') `
    dependency:build-classpath "-Dmdep.outputFile=$classpathFile" '-DincludeScope=test'
if ($LASTEXITCODE -ne 0) {
    throw 'Could not resolve the H2 demo driver from the existing Maven test dependencies.'
}
$h2Driver = ((Get-Content $classpathFile -Raw).Trim() -split [System.IO.Path]::PathSeparator) |
    Where-Object { [System.IO.Path]::GetFileName($_) -match '^h2-[0-9.]+\.jar$' } |
    Select-Object -First 1
if (-not $h2Driver -or -not (Test-Path $h2Driver)) {
    throw 'The test dependency classpath does not contain the H2 driver.'
}
foreach ($service in $definitions) {
    $service.jar = Join-Path $root "services/$($service.name)/target/$($service.name)-1.0.0-SNAPSHOT.jar"
    if (-not (Test-Path $service.jar)) {
        throw "Missing JAR for $($service.name). Run without -SkipBuild."
    }
}

$state = [pscustomobject]@{
    runId = [guid]::NewGuid().ToString()
    url = "http://localhost:$Port"
    services = @()
}
try {
    foreach ($service in $definitions) {
        $arguments = @(
            '-Xms32m', '-Xmx256m', '-XX:+ExitOnOutOfMemoryError',
            "-Dshopkart.demo.id=$($state.runId)", "-Dloader.path=`"$h2Driver`"",
            '-cp', "`"$($service.jar)`"", 'org.springframework.boot.loader.launch.PropertiesLauncher',
            "--server.port=$($service.port)", '--server.address=127.0.0.1',
            '--info.app.revision=local', '--spring.main.banner-mode=off',
            "--services.catalog-url=http://127.0.0.1:$($ServicePortBase + 1)",
            "--services.inventory-url=http://127.0.0.1:$($ServicePortBase + 2)",
            "--services.payment-url=http://127.0.0.1:$($ServicePortBase + 3)",
            "--services.notification-url=http://127.0.0.1:$($ServicePortBase + 5)",
            "--storefront.catalog-url=http://127.0.0.1:$($ServicePortBase + 1)",
            "--storefront.inventory-url=http://127.0.0.1:$($ServicePortBase + 2)",
            "--storefront.payment-url=http://127.0.0.1:$($ServicePortBase + 3)",
            "--storefront.order-url=http://127.0.0.1:$($ServicePortBase + 4)",
            "--storefront.notification-url=http://127.0.0.1:$($ServicePortBase + 5)"
        )
        if ($service.name -ne 'storefront-service') {
            $database = (Join-Path $demoDirectory "data/$($service.name)").Replace('\', '/')
            $arguments += @(
                "`"--spring.datasource.url=jdbc:h2:file:$database;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE`"",
                '--spring.datasource.username=sa', '--spring.datasource.password=',
                '--spring.datasource.driver-class-name=org.h2.Driver',
                '--spring.datasource.hikari.maximum-pool-size=4', '--spring.jpa.hibernate.ddl-auto=update'
            )
        }
        $process = Start-Process -FilePath $javaCommand -ArgumentList $arguments -PassThru `
            -WorkingDirectory $root -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $demoDirectory "$($service.name).log") `
            -RedirectStandardError (Join-Path $demoDirectory "$($service.name).err.log")
        $state.services += [pscustomobject]@{
            name = $service.name; port = $service.port; processId = $process.Id
        }
        $state | ConvertTo-Json -Depth 5 | Set-Content $stateFile -Encoding UTF8
    }
    Test-Demo $state
    Write-Output "Demo ready: $($state.url)"
} catch {
    if (Test-Path $stateFile) {
        Stop-Demo
    }
    throw
}