#!/bin/sh
set -eu

usage() {
    echo "Usage: $0 {build|build_start|start|stop|purge|tail} [--s3|--gridfs|--ozone] [novault]" >&2
    exit 2
}

[ "$#" -gt 0 ] || usage
command=$1
shift
backend=s3
backend_selected=false
novault=false
for argument in "$@"; do
    case "$argument" in
        --s3|--gridfs|--ozone)
            [ "$backend_selected" = false ] || usage
            backend=${argument#--}
            backend_selected=true
            ;;
        novault) novault=true ;;
        *) usage ;;
    esac
done
case "$command" in
    build|build_start|start|stop|purge|tail) ;;
    *) usage ;;
esac

cd -- "$(dirname -- "$0")"
VOLUME_PREFIX=anv

compose() {
    case "$backend" in
        s3) set -- -f docker/motoserver/docker-compose.yml -f docker/storage-s3.yml "$@" ;;
        gridfs) set -- -f docker/storage-gridfs.yml "$@" ;;
        ozone) set -- -f docker/ozone/docker-compose.yml -f docker/ozone/vault.yml "$@" ;;
    esac
    docker compose --env-file docker/.env \
        -f docker/docker-compose.yml \
        -f docker/mongo/docker-compose.yml \
        -f docker/hashicorp-vault/docker-compose.yml \
        -f docker/ganache/docker-compose.yml \
        -f docker/grafana/docker-compose.yml "$@"
}

build() {
    compose build anv-vault
}

start() {
    for volume in acs postgres ass mongo grafana; do
        docker volume create "$VOLUME_PREFIX-$volume-volume"
    done
    if [ "$novault" = true ]; then
        compose up --build -d --scale anv-vault=0
    else
        compose up --build -d
    fi
}

purge() {
    compose down --volumes
    for volume in acs postgres ass mongo grafana; do
        docker volume rm -f "$VOLUME_PREFIX-$volume-volume"
    done
}

case "$command" in
    build) build ;;
    build_start) compose down; build; start; compose logs -f ;;
    start) start; compose logs -f ;;
    stop) compose down --remove-orphans ;;
    purge) purge ;;
    tail) compose logs -f ;;
esac
