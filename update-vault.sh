#!/bin/bash

COMPOSE_FILE=./docker/docker-compose.yml

echo "Stopping anv-vault..."
docker-compose -f "$COMPOSE_FILE" stop anv-vault

echo "Building new image..."
docker build -t anv:latest . -f docker/Dockerfile.vault

echo "Removing old anv-vault container..."
docker-compose -f "$COMPOSE_FILE" rm -f anv-vault

echo "Starting anv-vault with updated image..."
docker-compose -f "$COMPOSE_FILE" up -d --no-build anv-vault

echo "Done!"
