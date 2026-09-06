#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
./scripts/setup-tailwind.sh
./.tailwind/tailwindcss \
    -i ./src/main/resources/static/css/app.css \
    -o ./src/main/resources/static/css/dist.css \
    --watch
