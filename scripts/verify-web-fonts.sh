#!/usr/bin/env sh
set -eu

project_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
asset_root="https://storage.googleapis.com/fhemni-public-assets-mohamed-playground/fonts"
check_dir=$(mktemp -d "${TMPDIR:-/tmp}/fhemni-font-check.XXXXXX")

cleanup() {
    rm -f "$check_dir/font.bin" "$check_dir/live.css" "$check_dir/live.headers" "$check_dir/live.html"
    rmdir "$check_dir"
}
trap cleanup EXIT HUP INT TERM

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

verify_font() {
    object=$1
    expected=$2
    curl --fail --silent --show-error --location --retry 3 --connect-timeout 5 --max-time 20 \
        "$asset_root/$object" --output "$check_dir/font.bin"
    actual=$(sha256 "$check_dir/font.bin")
    if [ "$actual" != "$expected" ]; then
        echo "Font checksum mismatch for $object: expected $expected, got $actual" >&2
        exit 1
    fi
    if ! grep -Fq "$asset_root/$object" "$project_root/src/main/resources/static/css/dist.css"; then
        echo "Compiled CSS does not reference $object" >&2
        exit 1
    fi
}

verify_font "arabswell-1.8e0450bede61.ttf" "8e0450bede61b46f8929236df464384d0e32fb2c6dd8acac9fc7e82732301e69"
verify_font "tajawal-regular.6882892da3e0.ttf" "6882892da3e03527d5db2bbab3b48bde6ef2e878a43f522d1a4eebda90010a19"
verify_font "tajawal-medium.1ff8bd943a26.ttf" "1ff8bd943a261c9dd7906bdb1993cab87b6d925a7fb5f848cdb51ed301531090"
verify_font "tajawal-bold.0342ab6b74b6.ttf" "0342ab6b74b6bd1b4b5bf7beda45a9734a2b3147a31e8a3a45b6a3417a52d9d7"
verify_font "videojs.46d5222f8568.woff" "46d5222f85688002d5b62a53790a6d7d799282dd836b8346c80178bff0fbf3cf"

if [ -n "${FHEMNI_SITE_URL:-}" ]; then
    site_url=${FHEMNI_SITE_URL%/}
    css_ref=$(sed -n 's/.*href="\([^"]*\/css\/dist\.css?v=[^"]*\)".*/\1/p' \
        "$project_root/src/main/resources/static/index.html" | head -n 1)
    curl --fail --silent --show-error --location --retry 3 --connect-timeout 5 --max-time 20 \
        --dump-header "$check_dir/live.headers" \
        "$site_url/" --output "$check_dir/live.html"
    if ! grep -Eiq '^content-security-policy:.*font-src[^;]*https://storage\.googleapis\.com' \
        "$check_dir/live.headers"; then
        echo "Live Content-Security-Policy does not allow GCS fonts" >&2
        exit 1
    fi
    if ! grep -Fq "$css_ref" "$check_dir/live.html"; then
        echo "Live page does not reference expected stylesheet $css_ref" >&2
        exit 1
    fi
    curl --fail --silent --show-error --location --retry 3 --connect-timeout 5 --max-time 20 \
        "$site_url$css_ref" --output "$check_dir/live.css"
    if ! grep -Fq "$asset_root/arabswell-1.8e0450bede61.ttf" "$check_dir/live.css"; then
        echo "Live stylesheet does not reference the pinned Arabswell asset" >&2
        exit 1
    fi
fi

echo "Verified pinned Fhemni web fonts${FHEMNI_SITE_URL:+ and live stylesheet}."
