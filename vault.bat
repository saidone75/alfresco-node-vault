@ECHO OFF
SETLOCAL

SET VOLUME_PREFIX=anv
SET DOCKER_COMPOSE=docker-compose ^
                    -f docker\docker-compose.yml ^
                    -f docker\localstack\docker-compose.yml ^
                    -f docker\grafana\docker-compose.yml ^
                    --env-file docker\.env

REM Check parameter
IF "%~1"=="" (
    echo Usage: %~nx0 {build^|build_start^|start^|stop^|purge^|tail} [novault]
    GOTO END
)

IF /I "%~1"=="build" (
    CALL :build
    GOTO END
)

IF /I "%~1"=="build_start" (
    CALL :down
    CALL :build
    CALL :start %2
    CALL :tail
    GOTO END
)

IF /I "%~1"=="start" (
    CALL :start %2
    CALL :tail
    GOTO END
)

IF /I "%~1"=="stop" (
    CALL :down
    GOTO END
)

IF /I "%~1"=="purge" (
    CALL :down
    CALL :purge
    GOTO END
)

IF /I "%~1"=="tail" (
    CALL :tail
    GOTO END
)

REM If no command matches
echo Usage: %~nx0 {build|build_start|start|stop|purge|tail} [novault]

:END
ENDLOCAL
EXIT /B %ERRORLEVEL%

:start
REM Create volumes
docker volume create %VOLUME_PREFIX%-acs-volume
docker volume create %VOLUME_PREFIX%-postgres-volume
docker volume create %VOLUME_PREFIX%-ass-volume
docker volume create %VOLUME_PREFIX%-mongo-volume
docker volume create %VOLUME_PREFIX%-grafana-volume
docker volume create %VOLUME_PREFIX%-algorand-volume

echo %ENV_FILE_PATH%
IF /I "%~1"=="novault" (
    %DOCKER_COMPOSE% ^
    up --build -d --scale anv-vault=0
) ELSE (
    %DOCKER_COMPOSE% ^
    up --build -d
)
EXIT /B 0

:down
if exist "docker\docker-compose.yml" (
    %DOCKER_COMPOSE% ^
    down
)
EXIT /B 0

:build
docker build -t anv:latest . -f docker/Dockerfile.vault
EXIT /B 0

:tail
%DOCKER_COMPOSE% ^
logs -f
EXIT /B 0

:purge
docker volume rm -f %VOLUME_PREFIX%-acs-volume
docker volume rm -f %VOLUME_PREFIX%-postgres-volume
docker volume rm -f %VOLUME_PREFIX%-ass-volume
docker volume rm -f %VOLUME_PREFIX%-mongo-volume
docker volume rm -f %VOLUME_PREFIX%-grafana-volume
docker volume rm -f %VOLUME_PREFIX%-algorand-volume
EXIT /B 0