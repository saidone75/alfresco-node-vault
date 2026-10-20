@echo off
REM SPDX-License-Identifier: GPL-3.0-or-later
setlocal

set "SMOKE_KEEP_RUNNING=0"
set "SMOKE_BACKEND=s3"
set "SMOKE_BACKEND_SELECTED="
set "SMOKE_COMPOSE_ARGS=-f docker/smoke/compose.yml"

:arguments
if "%~1"=="" goto launch
if "%~1"=="--keep-running" (
    set "SMOKE_KEEP_RUNNING=1"
) else if "%~1"=="--ozone" (
    if defined SMOKE_BACKEND_SELECTED goto usage
    set "SMOKE_BACKEND_SELECTED=1"
    set "SMOKE_BACKEND=ozone"
) else if "%~1"=="--s3" (
    if defined SMOKE_BACKEND_SELECTED goto usage
    set "SMOKE_BACKEND_SELECTED=1"
    set "SMOKE_BACKEND=s3"
) else if "%~1"=="--all" (
    if defined SMOKE_BACKEND_SELECTED goto usage
    set "SMOKE_BACKEND_SELECTED=1"
    set "SMOKE_BACKEND=all"
) else if "%~1"=="--gridfs" (
    if defined SMOKE_BACKEND_SELECTED goto usage
    set "SMOKE_BACKEND_SELECTED=1"
    set "SMOKE_BACKEND=gridfs"
) else (
    goto usage
)
shift
goto arguments

:launch
if not "%SMOKE_BACKEND%"=="all" set "SMOKE_COMPOSE_ARGS=%SMOKE_COMPOSE_ARGS% -f docker/smoke/compose-%SMOKE_BACKEND%.yml"
pushd "%~dp0"
if errorlevel 1 exit /b 1

docker compose --env-file docker/.env %SMOKE_COMPOSE_ARGS% config --quiet
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if not "%SMOKE_EXIT_CODE%"=="0" goto finish

docker compose --env-file docker/.env %SMOKE_COMPOSE_ARGS% run --build --rm smoke
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if "%SMOKE_KEEP_RUNNING%"=="1" goto finish

docker compose --env-file docker/.env %SMOKE_COMPOSE_ARGS% stop
set "SMOKE_STOP_EXIT_CODE=%ERRORLEVEL%"
if "%SMOKE_EXIT_CODE%"=="0" set "SMOKE_EXIT_CODE=%SMOKE_STOP_EXIT_CODE%"

:finish
popd
endlocal & exit /b %SMOKE_EXIT_CODE%

:usage
echo Usage: %~nx0 [--gridfs^|--s3^|--ozone^|--all] [--keep-running] 1>&2
endlocal & exit /b 2
