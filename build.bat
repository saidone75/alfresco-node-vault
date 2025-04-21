@echo off

SET DIST_DIR=anp

IF EXIST %DIST_DIR% RMDIR /S /Q %DIST_DIR%
MKDIR %DIST_DIR%\log
MKDIR %DIST_DIR%\config
CALL mvn package -DskipTests -Dlicense.skip=true
COPY target\anp.jar %DIST_DIR%
COPY src\main\resources\application.yml %DIST_DIR%\config
COPY src\main\resources\example*.json %DIST_DIR%