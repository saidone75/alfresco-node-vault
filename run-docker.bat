@echo off

SET COMPOSE_FILE_PATH=%CD%\docker\docker-compose.yml

IF [%1]==[] (
    echo "Usage: %0 {build|build_start|start|stop|purge|tail}"
    GOTO END
)

IF %1==build (
    CALL :build
    GOTO END
)
IF %1==build_start (
    CALL :down
    CALL :build
    CALL :start
    CALL :tail
    GOTO END
)
IF %1==start (
    CALL :start
    CALL :tail
    GOTO END
)
IF %1==stop (
    CALL :down
    GOTO END
)
IF %1==purge (
    CALL:down
    CALL:purge
    GOTO END
)
IF %1==tail (
    CALL :tail
    GOTO END
)
echo "Usage: %0 {build_start|start|stop|purge|tail}"
:END
EXIT /B %ERRORLEVEL%

:start
    docker network create anv-shared-network 2>NUL
    docker-compose -f "%COMPOSE_FILE_PATH%" up -d
EXIT /B 0
:down
    if exist "%COMPOSE_FILE_PATH%" (
        docker-compose -f "%COMPOSE_FILE_PATH%" down
    )
EXIT /B 0
:build
	docker build -t anv:latest . -f docker/Dockerfile
EXIT /B 0
:tail
    docker-compose -f "%COMPOSE_FILE_PATH%" logs -f
EXIT /B 0
:purge
EXIT /B 0