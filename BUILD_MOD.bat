@echo off
set "ROOT=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%BUILD_MOD.ps1"
set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" echo BUILD FAILED - see build.log
if "%EXITCODE%"=="0" echo BUILD SUCCESS - check dist\
pause
exit /b %EXITCODE%
