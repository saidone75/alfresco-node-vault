@ECHO OFF
SETLOCAL DISABLEDELAYEDEXPANSION

IF NOT "%~1"=="" GOTO USAGE

CD /D "%~dp0"
IF ERRORLEVEL 1 EXIT /B 1
SET "LABEL_FILE=%TEMP%\anv-update-%RANDOM%-%RANDOM%.txt"
docker ps --filter "name=^/anv-vault$" --filter status=running --format "{{.ID}}" > "%LABEL_FILE%"
IF ERRORLEVEL 1 (
    DEL /Q "%LABEL_FILE%"
    EXIT /B 1
)
SET "RUNNING="
SET /P RUNNING=<"%LABEL_FILE%"
IF NOT DEFINED RUNNING (
    DEL /Q "%LABEL_FILE%"
    echo anv-vault is not running; nothing to update.
    EXIT /B 0
)
docker inspect --format "FILES={{index .Config.Labels \"com.docker.compose.project.config_files\"}}{{println}}PROJECT={{index .Config.Labels \"com.docker.compose.project\"}}{{println}}DIRECTORY={{index .Config.Labels \"com.docker.compose.project.working_dir\"}}{{println}}ENV_FILES={{index .Config.Labels \"com.docker.compose.project.environment_file\"}}" anv-vault > "%LABEL_FILE%"
IF ERRORLEVEL 1 (
    DEL /Q "%LABEL_FILE%"
    echo Cannot inspect anv-vault. 1>&2
    EXIT /B 1
)
SET "FILES="
SET "PROJECT="
SET "DIRECTORY="
SET "ENV_FILES="
FOR /F "usebackq tokens=1,* delims==" %%A IN ("%LABEL_FILE%") DO SET "%%A=%%B"
DEL /Q "%LABEL_FILE%"
IF NOT DEFINED FILES GOTO MISSING_LABELS
IF NOT DEFINED PROJECT GOTO MISSING_LABELS
IF NOT DEFINED DIRECTORY GOTO MISSING_LABELS
IF "%FILES%"=="<no value>" GOTO MISSING_LABELS
IF "%PROJECT%"=="<no value>" GOTO MISSING_LABELS
IF "%DIRECTORY%"=="<no value>" GOTO MISSING_LABELS
SET DOCKER_COMPOSE=docker compose --project-name "%PROJECT%" --project-directory "%DIRECTORY%"
FOR %%F IN ("%FILES:,=" "%") DO CALL :ADD_FILE "%%~F"
IF NOT DEFINED ENV_FILES GOTO DETECTED
IF "%ENV_FILES%"=="<no value>" GOTO DETECTED
FOR %%F IN ("%ENV_FILES:,=" "%") DO CALL :ADD_ENV_FILE "%%~F"
:DETECTED
echo Using Compose configuration from anv-vault.
GOTO RUN

:ADD_FILE
SET DOCKER_COMPOSE=%DOCKER_COMPOSE% -f "%~1"
EXIT /B 0

:ADD_ENV_FILE
SET DOCKER_COMPOSE=%DOCKER_COMPOSE% --env-file "%~1"
EXIT /B 0

:MISSING_LABELS
echo Missing Compose labels on anv-vault; update aborted. 1>&2
EXIT /B 1

:RUN
REM Validate configuration before building or touching the running container.
%DOCKER_COMPOSE% config --quiet
IF ERRORLEVEL 1 EXIT /B 1

echo Building new image...
%DOCKER_COMPOSE% build anv-vault
IF ERRORLEVEL 1 EXIT /B 1

echo Recreating only anv-vault with updated image...
%DOCKER_COMPOSE% up -d --no-deps --no-build --force-recreate anv-vault
IF ERRORLEVEL 1 EXIT /B 1

echo Done!
EXIT /B 0

:USAGE
echo Usage: %~nx0 1>&2
EXIT /B 2
