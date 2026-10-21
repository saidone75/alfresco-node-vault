#!/bin/sh
set -eu

if [ "$#" -ne 0 ]; then
    echo "Usage: $0" >&2
    exit 2
fi

cd -- "$(dirname -- "$0")"

running=$(docker ps --filter 'name=^/anv-vault$' --filter status=running --format '{{.ID}}')
if [ -z "$running" ]; then
    echo "anv-vault is not running; nothing to update."
    exit 0
fi

label() {
    docker inspect --format "{{index .Config.Labels \"$1\"}}" anv-vault
}
compose_files=$(label com.docker.compose.project.config_files) || {
    echo "Cannot inspect anv-vault." >&2
    exit 1
}
project=$(label com.docker.compose.project)
project_directory=$(label com.docker.compose.project.working_dir)
environment_files=$(label com.docker.compose.project.environment_file)
for value in "$compose_files" "$project" "$project_directory"; do
    if [ -z "$value" ] || [ "$value" = '<no value>' ]; then
        echo "Missing Compose labels on anv-vault; update aborted." >&2
        exit 1
    fi
done
echo "Using Compose configuration from anv-vault."

compose() (
    # Compose labels store ordered, comma-separated absolute paths.
    IFS=,
    set -f
    set -- --project-name "$project" --project-directory "$project_directory"
    for file in $compose_files; do set -- "$@" -f "$file"; done
    if [ -n "$environment_files" ] && [ "$environment_files" != '<no value>' ]; then
        for file in $environment_files; do set -- "$@" --env-file "$file"; done
    fi
    if [ "$compose_action" = config ]; then
        docker compose "$@" config --quiet
    elif [ "$compose_action" = build ]; then
        docker compose "$@" build anv-vault
    else
        docker compose "$@" up -d --no-deps --no-build --force-recreate anv-vault
    fi
)

# Validate configuration before building or touching the running container.
compose_action=config
compose

echo "Building new image..."
compose_action=build
compose

echo "Recreating only anv-vault with updated image..."
compose_action=up
compose

echo "Done!"
