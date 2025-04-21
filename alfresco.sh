#!/bin/sh

export COMPOSE_FILE_PATH="${PWD}/docker/docker-compose.yml"
VOLUME_PREFIX=anv

start() {
    docker volume create $VOLUME_PREFIX-acs-volume
    docker volume create $VOLUME_PREFIX-postgres-volume
    docker volume create $VOLUME_PREFIX-ass-volume
    docker volume create $VOLUME_PREFIX-mongo-volume
    docker-compose -f "$COMPOSE_FILE_PATH" --env-file ./docker/.env up --build -d
}

down() {
    if [ -f "$COMPOSE_FILE_PATH" ]; then
        docker-compose -f "$COMPOSE_FILE_PATH" --env-file ./docker/.env down
    fi
}

purge() {
    docker volume rm -f $VOLUME_PREFIX-acs-volume
    docker volume rm -f $VOLUME_PREFIX-postgres-volume
    docker volume rm -f $VOLUME_PREFIX-ass-volume
    docker volume rm -f $VOLUME_PREFIX-mongo-volume
}

tail() {
    docker-compose -f "$COMPOSE_FILE_PATH" --env-file ./docker/.env logs -f
}

tail_all() {
    docker-compose -f "$COMPOSE_FILE_PATH" --env-file ./docker/.env logs --tail="all"
}

case "$1" in
  start)
    start
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
    echo "Usage: $0 {start|stop|purge|tail}"
esac