@echo off
setlocal

REM Keep the script name: shift below also shifts %0.
set "VAULT_SCRIPT=%~nx0"
set "VOLUME_PREFIX=anv"
set "VAULT_BACKEND=s3"
set "VAULT_BACKEND_SELECTED="
set "VAULT_NOVAULT=0"

if "%~1"=="" goto usage
set "VAULT_COMMAND=%~1"
shift

:arguments
if "%~1"=="" goto validate
if "%~1"=="--s3" (
    if defined VAULT_BACKEND_SELECTED goto usage
    set "VAULT_BACKEND_SELECTED=1"
    set "VAULT_BACKEND=s3"
) else if "%~1"=="--gridfs" (
    if defined VAULT_BACKEND_SELECTED goto usage
    set "VAULT_BACKEND_SELECTED=1"
    set "VAULT_BACKEND=gridfs"
) else if "%~1"=="--ozone" (
    if defined VAULT_BACKEND_SELECTED goto usage
    set "VAULT_BACKEND_SELECTED=1"
    set "VAULT_BACKEND=ozone"
) else if "%~1"=="novault" (
    set "VAULT_NOVAULT=1"
) else (
    goto usage
)
shift
goto arguments

:validate
for %%C in (build build_start start stop purge tail) do if "%VAULT_COMMAND%"=="%%C" goto launch
goto usage

:launch
set "VAULT_COMPOSE=docker compose --env-file docker/.env -f docker/docker-compose.yml -f docker/mongo/docker-compose.yml -f docker/hashicorp-vault/docker-compose.yml -f docker/ganache/docker-compose.yml -f docker/grafana/docker-compose.yml"
if "%VAULT_BACKEND%"=="s3" set "VAULT_COMPOSE=%VAULT_COMPOSE% -f docker/motoserver/docker-compose.yml -f docker/storage-s3.yml"
if "%VAULT_BACKEND%"=="gridfs" set "VAULT_COMPOSE=%VAULT_COMPOSE% -f docker/storage-gridfs.yml"
if "%VAULT_BACKEND%"=="ozone" set "VAULT_COMPOSE=%VAULT_COMPOSE% -f docker/ozone/docker-compose.yml -f docker/ozone/vault.yml"
pushd "%~dp0"
if errorlevel 1 exit /b 1
goto command_%VAULT_COMMAND%

:command_build
call :build
goto finish

:command_build_start
%VAULT_COMPOSE% down
if errorlevel 1 goto finish
call :build
if errorlevel 1 goto finish
call :start
if errorlevel 1 goto finish
%VAULT_COMPOSE% logs -f
goto finish

:command_start
call :start
if errorlevel 1 goto finish
%VAULT_COMPOSE% logs -f
goto finish

:command_stop
%VAULT_COMPOSE% down --remove-orphans
goto finish

:command_purge
call :purge
goto finish

:command_tail
%VAULT_COMPOSE% logs -f
goto finish

:finish
set "VAULT_EXIT_CODE=%ERRORLEVEL%"
popd
endlocal & exit /b %VAULT_EXIT_CODE%

:build
%VAULT_COMPOSE% build anv-vault
exit /b

:start
for %%V in (acs postgres ass mongo grafana) do (
    docker volume create "%VOLUME_PREFIX%-%%V-volume"
    if errorlevel 1 exit /b 1
)
if "%VAULT_NOVAULT%"=="1" (
    %VAULT_COMPOSE% up --build -d --scale anv-vault=0
) else (
    %VAULT_COMPOSE% up --build -d
)
exit /b

:purge
%VAULT_COMPOSE% down --volumes
if errorlevel 1 exit /b
for %%V in (acs postgres ass mongo grafana) do (
    docker volume rm -f "%VOLUME_PREFIX%-%%V-volume"
    if errorlevel 1 exit /b 1
)
exit /b 0

:usage
echo Usage: %VAULT_SCRIPT% {build^|build_start^|start^|stop^|purge^|tail} [--s3^|--gridfs^|--ozone] [novault] 1>&2
endlocal & exit /b 2
