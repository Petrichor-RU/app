#!/bin/sh
# Petrichor quality gate: runs ktlint and detekt over all Kotlin sources and exits
# non-zero on violations. Called by the ./kotlin wrapper before build/test/check;
# can also be run directly:
#
#   tools/lint.sh <project-root>          check mode (fails on violations)
#   tools/lint.sh <project-root> format   additionally auto-fixes ktlint violations
#
# The pinned, self-contained CLI jars are downloaded once into build/lint-tools/
# (checksum-verified), so each tool runs isolated and no project dependency is needed.
# Versions follow the recommendations on https://kotlinlang.org/docs/jvm-code-analysis.html
# at implementation time (ktlint 1.8.0, detekt 1.23.8).

set -e -u

root="${1:?usage: lint.sh <project-root> [format]}"
mode="${2:-check}"
root="$(cd "$root" && pwd)"

KTLINT_VERSION="1.8.0"
KTLINT_JAR="ktlint-cli-${KTLINT_VERSION}-all.jar"
KTLINT_SHA256="369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab"
KTLINT_URL="https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/${KTLINT_VERSION}/${KTLINT_JAR}"

DETEKT_VERSION="1.23.8"
DETEKT_JAR="detekt-cli-${DETEKT_VERSION}-all.jar"
DETEKT_SHA256="3afe89a11120303c73c9bdda3d8fe558dd9070a6937d27819ddc04b275381245"
DETEKT_URL="https://repo1.maven.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/${DETEKT_VERSION}/${DETEKT_JAR}"

tools_dir="$root/build/lint-tools"
mkdir -p "$tools_dir"

download_jar() {
  jar="$1"; url="$2"; sha="$3"
  target="$tools_dir/$jar"
  if [ -f "$target" ] && [ "$(sha256sum "$target" | cut -d' ' -f1)" = "$sha" ]; then
    return 0
  fi
  tmp="$tools_dir/$jar.tmp"
  echo "lint: downloading $jar..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL --retry 5 --connect-timeout 30 --output "$tmp" "$url"
  else
    wget -q --tries=5 --connect-timeout=30 -O "$tmp" "$url"
  fi
  if [ "$(sha256sum "$tmp" | cut -d' ' -f1)" != "$sha" ]; then
    rm -f "$tmp"
    echo "lint: checksum mismatch for $jar" >&2
    exit 1
  fi
  mv "$tmp" "$target"
}

download_jar "$KTLINT_JAR" "$KTLINT_URL" "$KTLINT_SHA256"
download_jar "$DETEKT_JAR" "$DETEKT_URL" "$DETEKT_SHA256"

java_bin="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [ ! -x "$java_bin" ]; then java_bin="$(command -v java)"; fi

cd "$root"

# Collect source sets: <module>/src, <module>/test and their platform-suffixed variants.
set --
detekt_inputs=""
for module in shared androidApp desktopApp iosApp webApp; do
  [ -d "$module" ] || continue
  for sub in "$module"/src "$module"/test "$module"/src@* "$module"/test@*; do
    [ -d "$sub" ] || continue
    set -- "$@" "$sub/**/*.kt"
    detekt_inputs="${detekt_inputs:+$detekt_inputs,}$sub"
  done
done

status=0

format_flag=""
if [ "$mode" = "format" ]; then
  format_flag="--format"
fi

echo "=== ktlint ${KTLINT_VERSION} ==="
# shellcheck disable=SC2086
if ! "$java_bin" -jar "$tools_dir/$KTLINT_JAR" $format_flag "$@"; then
  status=1
fi

echo "=== detekt ${DETEKT_VERSION} ==="
if ! "$java_bin" -jar "$tools_dir/$DETEKT_JAR" --config detekt.yml --input "$detekt_inputs"; then
  status=1
fi

if [ "$status" -ne 0 ]; then
  echo "lint: ktlint/detekt violations found (see above)." >&2
  exit 1
fi
echo "lint: all checks passed"
