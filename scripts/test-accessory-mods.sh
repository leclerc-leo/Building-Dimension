#!/usr/bin/env bash
#
# Runs the GameTest suite against every real, published Trinkets/Curios build compatible with this
# project's Minecraft version, on whichever loader each mod targets. FabricAccessoryHelper and
# NeoForgeAccessoryHelper only ever reach into those mods via reflection (see their class comments
# for why), so this is the one place that actually exercises that reflection against the genuine
# API rather than against nothing.
#
# Usage:
#   scripts/test-accessory-mods.sh              # test every known mod/loader combo
#   scripts/test-accessory-mods.sh fabric        # only combos targeting this loader
#   scripts/test-accessory-mods.sh neoforge
#
# How it works: queries the Modrinth API for every version of each mod tagged for this project's
# Minecraft version and the relevant loader, downloads each matching jar, and runs the corresponding
# runGameTest(Server) task once per jar via the "accessoryTestJar" project property that
# fabric/build.gradle and neoforge/build.gradle read (a real dependency would defeat the point: the
# whole design is that the mod is never required to be present). Requires curl and python3.
set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

mc_version="$(grep -m1 '^minecraft_version=' gradle.properties | cut -d= -f2)"
jar_dir="$repo_root/build/accessory-test-jars"
mkdir -p "$jar_dir"

loader_filter="${1:-all}"

# loader:modrinth_slug:gradle_task
combos=(
    "fabric:trinkets-updated::fabric:runGameTest"
    "neoforge:trinkets-updated::neoforge:runGameTestServer"
    "neoforge:curios::neoforge:runGameTestServer"
)

results=()
any_failed=0

fetch_versions() {
    # Prints one "version_number|filename|url" per line for every version of $1 matching loader $2
    # and this project's Minecraft version.
    local slug="$1" loader="$2"
    curl -s --fail --max-time 30 \
        "https://api.modrinth.com/v2/project/${slug}/version?loaders=%5B%22${loader}%22%5D&game_versions=%5B%22${mc_version}%22%5D" \
        | python3 -c '
import json, sys
try:
    versions = json.load(sys.stdin)
except json.JSONDecodeError:
    sys.exit(0)
for v in versions:
    files = v["files"]
    primary = next((f for f in files if f.get("primary")), files[0] if files else None)
    if primary:
        print("|".join([v["version_number"], primary["filename"], primary["url"]]))
'
}

for combo in "${combos[@]}"; do
    IFS=':' read -r loader slug task <<< "$combo"

    if [[ "$loader_filter" != "all" && "$loader_filter" != "$loader" ]]; then
        continue
    fi

    echo "== $slug ($loader) for Minecraft $mc_version =="
    versions="$(fetch_versions "$slug" "$loader")"

    if [[ -z "$versions" ]]; then
        echo "  no published build found for Minecraft $mc_version"
        results+=("$loader|$slug|(none)|SKIPPED — no build for $mc_version")
        continue
    fi

    while IFS='|' read -r version_number filename url; do
        [[ -z "$version_number" ]] && continue
        jar_path="$jar_dir/$filename"

        echo "  -> $version_number ($filename)"
        if [[ ! -f "$jar_path" ]]; then
            curl -s --fail --max-time 120 -o "$jar_path" "$url" || {
                echo "     download failed"
                results+=("$loader|$slug|$version_number|FAIL — download error")
                any_failed=1
                continue
            }
        fi

        log_file="$(mktemp)"
        if ./gradlew "$task" "-PaccessoryTestJar=$jar_path" --console=plain > "$log_file" 2>&1; then
            echo "     PASS"
            results+=("$loader|$slug|$version_number|PASS")
        else
            echo "     FAIL (see $log_file)"
            results+=("$loader|$slug|$version_number|FAIL — see $log_file")
            any_failed=1
        fi
    done <<< "$versions"
done

echo
echo "== Summary =="
printf '%-10s %-18s %-20s %s\n' "LOADER" "MOD" "VERSION" "RESULT"
for row in "${results[@]}"; do
    IFS='|' read -r loader slug version result <<< "$row"
    printf '%-10s %-18s %-20s %s\n' "$loader" "$slug" "$version" "$result"
done

exit "$any_failed"
