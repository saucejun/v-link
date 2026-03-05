# Windows Local Run (Mock TUN)

## Prerequisite
- JDK 17+
- `gradlew.bat` or installed `gradle`

## Start
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\win\start-control-plane.ps1
```

This starts:
- coordinator on `127.0.0.1:40000`
- edge-a on `127.0.0.1:41001`
- edge-b on `127.0.0.1:41002`

Both edges use `MockTunDevice` with files in `.\tmp`.
