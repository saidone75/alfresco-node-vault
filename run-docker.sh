#!/bin/bash

COMPOSE_FILE_PATH="$(pwd)/docker/docker-compose.yml"

usage() {
  echo "Usage: $0 {build|build_start|start|stop|purge|tail}"
}

if [ -z "$1" ]; then
  usage
  exit 1
fi

build() {
  docker build -t anv:latest . -f docker/Dockerfile
}

down() {
  if [ -f "$COMPOSE_FILE_PATH" ]; then
    docker-compose -f "$COMPOSE_FILE_PATH" down
  fi
}

start() {
  docker network create anv-shared-network 2>/dev/null || true
  docker-compose -f "$COMPOSE_FILE_PATH" up -d
}

tail_logs() {
  docker-compose -f "$COMPOSE_FILE_PATH" logs -f
}

purge() {
  # Add purge commands here if needed
  :
}

case "$1" in
  build)
    build
    ;;
  build_start)
    down
    build
    start
    tail_logs
    ;;
  start)
    start
    tail_logs
    ;;
  stop)
    down
    ;;
  purge)
    down
    purge
    ;;
  tail)
    tail_logs
    ;;
  *)
    usage
    exit 1
    ;;
esac
