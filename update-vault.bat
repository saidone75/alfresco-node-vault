@ECHO OFF
SET COMPOSE_FILE=.\docker\docker-compose.yml

echo Building new image...
docker build -t anv:latest . -f docker/Dockerfile.vault

echo Stopping anv-vault...
docker-compose.exe -f %COMPOSE_FILE% stop anv-vault

echo Removing old anv-vault container...
docker-compose.exe -f %COMPOSE_FILE% rm -f anv-vault

echo Starting anv-vault with updated image...
docker-compose.exe -f %COMPOSE_FILE% up -d --no-build anv-vault

echo Done!