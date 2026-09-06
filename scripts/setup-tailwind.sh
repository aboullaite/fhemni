#!/usr/bin/env sh
set -eu

TAILWIND_VERSION="${TAILWIND_VERSION:-v4.3.1}"
DAISYUI_VERSION="${DAISYUI_VERSION:-v5.7.16}"
TOOLS_DIR="${FHEMNI_TAILWIND_TOOLS_DIR:-.tailwind}"

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64) tailwind_asset="tailwindcss-macos-arm64" ;;
    Darwin-x86_64) tailwind_asset="tailwindcss-macos-x64" ;;
    Linux-aarch64|Linux-arm64) tailwind_asset="tailwindcss-linux-arm64" ;;
    Linux-x86_64) tailwind_asset="tailwindcss-linux-x64" ;;
    *)
        echo "Unsupported platform: $(uname -s) $(uname -m)" >&2
        exit 1
        ;;
esac

mkdir -p "$TOOLS_DIR"

download() {
    url="$1"
    target="$2"
    temporary="${target}.download"
    curl --fail --location --silent --show-error "$url" --output "$temporary"
    mv "$temporary" "$target"
}

versions="$(printf 'tailwind=%s\ndaisyui=%s' "$TAILWIND_VERSION" "$DAISYUI_VERSION")"
installed_versions=""
if [ -f "$TOOLS_DIR/versions" ]; then
    installed_versions="$(sed -n '1,2p' "$TOOLS_DIR/versions")"
fi

if [ "$installed_versions" != "$versions" ] || [ ! -x "$TOOLS_DIR/tailwindcss" ]; then
    echo "Downloading Tailwind CSS ${TAILWIND_VERSION} standalone CLI..."
    download \
        "https://github.com/tailwindlabs/tailwindcss/releases/download/${TAILWIND_VERSION}/${tailwind_asset}" \
        "$TOOLS_DIR/tailwindcss"
    chmod +x "$TOOLS_DIR/tailwindcss"

    echo "Downloading DaisyUI ${DAISYUI_VERSION} standalone plugins..."
    download \
        "https://github.com/saadeghi/daisyui/releases/download/${DAISYUI_VERSION}/daisyui.mjs" \
        "$TOOLS_DIR/daisyui.mjs"
    download \
        "https://github.com/saadeghi/daisyui/releases/download/${DAISYUI_VERSION}/daisyui-theme.mjs" \
        "$TOOLS_DIR/daisyui-theme.mjs"

    printf '%s\n' "$versions" > "$TOOLS_DIR/versions"
fi
