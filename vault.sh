#!/bin/sh

DOCKER_COMPOSE="docker-compose \
-f docker/docker-compose.yml \
-f docker/grafana/docker-compose.yml \
--env-file docker/.env"

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
       eval "$DOCKER_COMPOSE up --build -d --scale anv-vault=0"
    else
      eval "$DOCKER_COMPOSE up --build -d"
    fi
}

down() {
        eval "$DOCKER_COMPOSE down"
}

purge() {
    docker volume rm -f $VOLUME_PREFIX-acs-volume
    docker volume rm -f $VOLUME_PREFIX-postgres-volume
    docker volume rm -f $VOLUME_PREFIX-ass-volume
    docker volume rm -f $VOLUME_PREFIX-mongo-volume
    docker volume rm -f $VOLUME_PREFIX-grafana-volume
}

tail() {
    eval "$DOCKER_COMPOSE logs -f"
}

tail_all() {
    eval "$DOCKER_COMPOSE logs --tail=\"all\""
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