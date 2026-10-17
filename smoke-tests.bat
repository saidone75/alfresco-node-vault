@echo off
REM SPDX-License-Identifier: GPL-3.0-or-later
setlocal

set "SMOKE_KEEP_RUNNING=0"
if not "%~2"=="" goto usage
if "%~1"=="" goto launch
if not "%~1"=="--keep-running" goto usage
set "SMOKE_KEEP_RUNNING=1"

:launch
pushd "%~dp0"
if errorlevel 1 exit /b 1

docker compose --env-file docker/.env -f docker/smoke/compose.yml config --quiet
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if not "%SMOKE_EXIT_CODE%"=="0" goto finish

docker compose --env-file docker/.env -f docker/smoke/compose.yml run --build --rm smoke
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if "%SMOKE_KEEP_RUNNING%"=="1" goto finish

docker compose --env-file docker/.env -f docker/smoke/compose.yml stop
set "SMOKE_STOP_EXIT_CODE=%ERRORLEVEL%"
if "%SMOKE_EXIT_CODE%"=="0" set "SMOKE_EXIT_CODE=%SMOKE_STOP_EXIT_CODE%"

:finish
popd
endlocal & exit /b %SMOKE_EXIT_CODE%

:usage
echo Usage: %~nx0 [--keep-running] 1>&2
endlocal & exit /b 2
