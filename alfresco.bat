@ECHO OFF

SET COMPOSE_FILE_PATH=%CD%\docker\docker-compose.yml
SET ENV_FILE_PATH=%CD%\docker\.env
SET VOLUME_PREFIX=anv

IF [%1]==[] (
    echo "Usage: %0 {start|stop|purge|tail}"
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

echo "Usage: %0 {start|stop|purge|tail}"
:END
EXIT /B %ERRORLEVEL%

:start
    docker volume create %VOLUME_PREFIX%-acs-volume
    docker volume create %VOLUME_PREFIX%-postgres-volume
    docker volume create %VOLUME_PREFIX%-ass-volume
    docker volume create %VOLUME_PREFIX%-mongo-volume
    echo %ENV_FILE_PATH%
    docker-compose -f "%COMPOSE_FILE_PATH%" --env-file "%ENV_FILE_PATH%" up --build -d
EXIT /B 0
:down
    if exist "%COMPOSE_FILE_PATH%" (
        docker-compose -f "%COMPOSE_FILE_PATH%" --env-file "%ENV_FILE_PATH%" down
    )
EXIT /B 0
:tail
    docker-compose -f "%COMPOSE_FILE_PATH%" --env-file "%ENV_FILE_PATH%" logs -f
EXIT /B 0
:purge
    docker volume rm -f %VOLUME_PREFIX%-acs-volume
    docker volume rm -f %VOLUME_PREFIX%-postgres-volume
    docker volume rm -f %VOLUME_PREFIX%-ass-volume
    docker volume rm -f %VOLUME_PREFIX%-mongo-volume
