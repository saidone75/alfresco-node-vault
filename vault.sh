#!/bin/sh

COMPOSE_FILE_PATH="${PWD}/docker/docker-compose.yml"
ENV_FILE_PATH="${PWD}/docker/.env"
VOLUME_PREFIX=anv

build() {
  docker build -t anv:latest . -f docker/Dockerfile.vault
}

start() {
    docker volume create $VOLUME_PREFIX-acs-volume
    docker volume create $VOLUME_PREFIX-postgres-volume
    docker volume create $VOLUME_PREFIX-ass-volume
    docker volume create $VOLUME_PREFIX-mongo-volume
    docker volume create $VOLUME_PREFIX-grafana-volume

    if [ "$1" = "novault" ]; then
        docker-compose -f "$COMPOSE_FILE_PATH" --env-file "$ENV_FILE_PATH" up --build -d --scale anv-vault=0
    else
        docker-compose -f "$COMPOSE_FILE_PATH" --env-file "$ENV_FILE_PATH" up --build -d
    fi
}

down() {
    if [ -f "$COMPOSE_FILE_PATH" ]; then
        docker-compose -f "$COMPOSE_FILE_PATH" --env-file "$ENV_FILE_PATH" down
    fi
}

purge() {
    docker volume rm -f $VOLUME_PREFIX-acs-volume
    docker volume rm -f $VOLUME_PREFIX-postgres-volume
    docker volume rm -f $VOLUME_PREFIX-ass-volume
    docker volume rm -f $VOLUME_PREFIX-mongo-volume
    docker volume rm -f $VOLUME_PREFIX-grafana-volume
}

tail() {
    docker-compose -f "$COMPOSE_FILE_PATH" --env-file "$ENV_FILE_PATH" logs -f
}

tail_all() {
    docker-compose -f "$COMPOSE_FILE_PATH" --env-file "$ENV_FILE_PATH" logs --tail="all"
}

case "$1" in
  build)
    build
    ;;
  build_start)
    down
    build
    start "$2"
    tail
    ;;
  start)
    start "$2"
    tail
    ;;
  stop)
    down
    ;;
  purge)
    down
    purge
    ;;
  tail)
    tail
    ;;
  *)
    echo "Usage: $0 {build|build_start|start|stop|purge|tail} [novault]"
esac