@echo off
REM SPDX-License-Identifier: GPL-3.0-or-later
setlocal

pushd "%~dp0"
if errorlevel 1 exit /b 1

docker compose --env-file docker/.env -f docker/smoke/compose.yml config --quiet
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"
if not "%SMOKE_EXIT_CODE%"=="0" goto finish

docker compose --env-file docker/.env -f docker/smoke/compose.yml run --build --rm smoke
set "SMOKE_EXIT_CODE=%ERRORLEVEL%"

:finish
popd
endlocal & exit /b %SMOKE_EXIT_CODE%
