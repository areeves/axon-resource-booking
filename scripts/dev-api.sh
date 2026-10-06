#!/bin/sh
set -eu

./mvnw -q compile

(
  while inotifywait -q -r -e close_write,create,delete,move src/main/java src/main/resources; do
    if ! ./mvnw -q compile; then
      echo "Maven compilation failed; waiting for the next source change." >&2
    fi
  done
) &

exec ./mvnw spring-boot:run
