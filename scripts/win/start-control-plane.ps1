param(
    [string]$Psk = "demo-psk",
    [int]$CoordinatorPort = 40000,
    [int]$EdgeAPort = 41001,
    [int]$EdgeBPort = 41002
)

$ErrorActionPreference = "Stop"

function Resolve-GradleCommand {
    if (Test-Path ".\\gradlew.bat") {
        return ".\\gradlew.bat"
    }
    if (Get-Command gradle -ErrorAction SilentlyContinue) {
        return "gradle"
    }
    throw "No gradle command found. Install Gradle or add gradlew.bat wrapper."
}

$gradleCmd = Resolve-GradleCommand

New-Item -ItemType Directory -Force -Path ".\\tmp" | Out-Null
if (-not (Test-Path ".\\tmp\\edge-a-in.bin")) { New-Item -ItemType File -Path ".\\tmp\\edge-a-in.bin" | Out-Null }
if (-not (Test-Path ".\\tmp\\edge-b-in.bin")) { New-Item -ItemType File -Path ".\\tmp\\edge-b-in.bin" | Out-Null }

$coordinatorArgs = "--bind 127.0.0.1 --port $CoordinatorPort --psk $Psk"
$edgeAArgs = "--id edge-a --bind 127.0.0.1 --bindPort $EdgeAPort --coordinatorHost 127.0.0.1 --coordinatorPort $CoordinatorPort --virtualIp 10.10.0.2 --psk $Psk --tunMode mock --tunName mock-a --mockTunIn .\\tmp\\edge-a-in.bin --mockTunOut .\\tmp\\edge-a-out.bin --peers edge-b=10.10.0.3"
$edgeBArgs = "--id edge-b --bind 127.0.0.1 --bindPort $EdgeBPort --coordinatorHost 127.0.0.1 --coordinatorPort $CoordinatorPort --virtualIp 10.10.0.3 --psk $Psk --tunMode mock --tunName mock-b --mockTunIn .\\tmp\\edge-b-in.bin --mockTunOut .\\tmp\\edge-b-out.bin --peers edge-a=10.10.0.2"

Start-Process powershell -ArgumentList "-NoExit", "-Command", "$gradleCmd :coordinator:run --args=\"$coordinatorArgs\""
Start-Sleep -Seconds 1
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$gradleCmd :edge:run --args=\"$edgeAArgs\""
Start-Sleep -Seconds 1
Start-Process powershell -ArgumentList "-NoExit", "-Command", "$gradleCmd :edge:run --args=\"$edgeBArgs\""

Write-Host "Launched coordinator + edge-a + edge-b with mock tun files under .\\tmp"
