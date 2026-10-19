@echo off
REM SPDX-License-Identifier: GPL-3.0-or-later
setlocal

set "SMOKE_KEEP_RUNNING=0"
set "SMOKE_COMPOSE_FILE=docker/smoke/compose.yml"

:arguments
if "%~1"=="" goto launch
if "%~1"=="--keep-running" (
    set "SMOKE_KEEP_RUNNING=1"
) else if "%~1"=="--ozone" (
    set "SMOKE_COMPOSE_FILE=docker/smoke/compose-ozone.yml"
) else (
    goto usage
)
shift
goto arguments

:launch
pushd "%~dp0"
if errorlevel 1 exit /b 1

docker compose --env-file docker/.env -f "%SMOKE_COMPOSE_FILE%" config --quiet
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if not "%SMOKE_EXIT_CODE%"=="0" goto finish

docker compose --env-file docker/.env -f "%SMOKE_COMPOSE_FILE%" run --build --rm smoke
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if "%SMOKE_KEEP_RUNNING%"=="1" goto finish

docker compose --env-file docker/.env -f "%SMOKE_COMPOSE_FILE%" stop
set "SMOKE_STOP_EXIT_CODE=%ERRORLEVEL%"
if "%SMOKE_EXIT_CODE%"=="0" set "SMOKE_EXIT_CODE=%SMOKE_STOP_EXIT_CODE%"

:finish
popd
endlocal & exit /b %SMOKE_EXIT_CODE%

:usage
echo Usage: %~nx0 [--ozone] [--keep-running] 1>&2
endlocal & exit /b 2
