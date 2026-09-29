#!/bin/sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
source_jar=${1:-"$HOME/Library/Application Support/REAPER/UserPlugins/drivenbymoss-libs/DrivenByMoss4Reaper-26.6.5.jar"}
output_jar=${2:-"$repo_dir/target/DrivenByMoss4Reaper-26.6.5-pasha-lyrics.jar"}
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/drivenbymoss-karaoke.XXXXXX")
classes_dir="$build_dir/classes"
original_classes_dir="$build_dir/original-classes"

cleanup() {
    rm -rf "$build_dir"
}
trap cleanup EXIT HUP INT TERM

test -s "$source_jar"
mkdir -p "$classes_dir" "$original_classes_dir" "$(dirname -- "$output_jar")"

(cd "$original_classes_dir" && jar xf "$source_jar" de/mossgrabers/reaper/MainApp.class)
python3 "$repo_dir/tools/patch-reaper-hotplug.py" \
    "$original_classes_dir/de/mossgrabers/reaper/MainApp.class" \
    "$classes_dir/de/mossgrabers/reaper/MainApp.class"

javac --release 21 -implicit:none \
    -cp "$source_jar" \
    -d "$classes_dir" \
    "$repo_dir/src/main/java/de/mossgrabers/framework/mode/Modes.java" \
    "$repo_dir/src/main/java/de/mossgrabers/controller/ableton/push/mode/KaraokeState.java" \
    "$repo_dir/src/main/java/de/mossgrabers/controller/ableton/push/mode/KaraokeComponent.java" \
    "$repo_dir/src/main/java/de/mossgrabers/controller/ableton/push/mode/KaraokeMode.java" \
    "$repo_dir/src/main/java/de/mossgrabers/controller/ableton/push/PushControllerSetup.java"

ditto "$source_jar" "$output_jar"
jar uf "$output_jar" -C "$classes_dir" de

printf '%s\n' "$output_jar"
