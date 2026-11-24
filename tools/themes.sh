#!/usr/bin/env bash
# Keep several themes on the TV, so the launcher's Theme row (Launcher settings) can switch between them
# with the remote, no computer needed. Each folder goes to the TV's theme library as it is in themes/:
# config.json, screenshot.jpg (the picker's thumbnail), wallpaper.jpg and wallpapers/. Nothing is applied
# and Home is not restarted: pick the theme on the TV. tools/theme.sh still pushes and applies one theme.
#
# Usage: TV_HOST=192.168.1.50 tools/themes.sh themes/gamer themes/cine   # add or replace these themes
#        TV_HOST=192.168.1.50 tools/themes.sh --all                        # every folder in themes/
#        TV_HOST=192.168.1.50 tools/themes.sh --list                       # what the library holds
#        TV_HOST=192.168.1.50 tools/themes.sh --remove gamer               # take one out of the library
#
# The library name is the folder's name, which must be lower case letters, digits, '-' and '_' (at most
# 40). Removing a theme from the library does not change the theme on screen; tools/theme.sh --reset does.
#
# Exit status: 0 done; 1 the TV was reached and a file could not be written (the message names it; run the
# same command again); 2 nothing was sent: a bad command line, a folder that is not a theme, no TV_HOST or
# no tools/venv.
set -euo pipefail
export LC_ALL=C

usage() { echo "usage: themes.sh <theme folder>... | --all | --list | --remove <name>" >&2; exit 2; }
[ "$#" -ge 1 ] || usage

MODE=push
case "$1" in
  --list) [ "$#" -eq 1 ] || usage; MODE=list ;;
  --remove) [ "$#" -eq 2 ] || usage; MODE=remove ;;
  --all) [ "$#" -eq 1 ] || usage; MODE=all ;;
  -*) usage ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && { pwd -W 2>/dev/null || pwd; })"
FOLDERS=()
if [ "$MODE" = push ]; then
  for ARG in "$@"; do
    case "$ARG" in
      -*) usage ;;
      /*|[A-Za-z]:[\\/]*) FOLDERS+=("$ARG") ;;
      *) FOLDERS+=("$(pwd -W 2>/dev/null || pwd)/$ARG") ;;
    esac
  done
elif [ "$MODE" = all ]; then
  for D in "$ROOT"/themes/*/; do
    [ -f "$D/config.json" ] && FOLDERS+=("${D%/}")
  done
  MODE=push
fi

valid_name() { [[ "$1" =~ ^[a-z0-9][a-z0-9_-]{0,39}$ ]]; }

cd "$(dirname "$0")"
if [ -z "${TV_HOST:-}" ]; then
  echo "set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 --list. Nothing was sent to the TV." >&2
  exit 2
fi
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt." \
  "Nothing was sent to the TV." >&2; exit 2; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-themes
tv() { "$PY" -I tv.py "$@"; }

if [ "$MODE" = list ]; then
  OUT="$(tv "ls $DIR/themes 2>/dev/null")" || { echo "the TV did not answer." >&2; exit 1; }
  OUT="$(printf '%s\n' "$OUT" | tr -d '\r' | sed '/^$/d')"
  if [ -z "$OUT" ]; then
    echo "The library is empty, or this firmware keeps the shell out of Android/data (the Theme row on the TV" \
      "shows what it holds)."
  else
    printf '%s\n' "$OUT"
  fi
  exit 0
fi

if [ "$MODE" = remove ]; then
  NAME="$2"
  valid_name "$NAME" || { echo "$NAME is not a library theme name. Nothing was sent to the TV." >&2; exit 2; }
  tv "rm -rf $DIR/themes/$NAME 2>/dev/null; content delete --uri $URI/themes/$NAME 2>/dev/null" >/dev/null \
    || { echo "the TV did not answer." >&2; exit 1; }
  echo "removed $NAME"
  exit 0
fi

[ "${#FOLDERS[@]}" -gt 0 ] || { echo "no theme folders to send. Nothing was sent to the TV." >&2; exit 2; }

# Check every folder before the first file goes, so a typo in the last one does not leave half a run.
for THEME in "${FOLDERS[@]}"; do
  NAME="$(basename "$THEME")"
  [ -f "$THEME/config.json" ] || { echo "no config.json in $THEME. Nothing was sent to the TV." >&2; exit 2; }
  valid_name "$NAME" || { echo "$NAME: a library theme's folder name is lower case letters, digits, '-' and" \
    "'_', at most 40. Nothing was sent to the TV." >&2; exit 2; }
  "$PY" -I -c 'import json,sys; t=json.load(open(sys.argv[1],encoding="utf-8-sig")); sys.exit(0 if isinstance(t,dict) else 1)' \
    "$THEME/config.json" 2>/dev/null || { echo "$THEME/config.json is not a JSON object of theme keys." \
    "Nothing was sent to the TV." >&2; exit 2; }
  if [ -d "$THEME/wallpapers" ]; then
    for IMAGE in "$THEME"/wallpapers/*; do
      [ -f "$IMAGE" ] || continue
      N=$(basename "$IMAGE")
      case "$N" in
        *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP]|captions.txt)
          if [[ ! "$N" =~ ^[A-Za-z0-9_.-]+$ || "$N" == *..* || "$N" == .* ]]; then
            echo "unsupported wallpaper filename: $THEME/wallpapers/$N. Nothing was sent to the TV." >&2
            exit 2
          fi ;;
      esac
    done
  fi
done

NOT_WRITTEN=cyt-theme-not-written
stage() { echo "$TMP-${1//\//-}"; }
# The same write tools/theme.sh does: the launcher's provider first (firmware that keeps the shell out of
# Android/data), a plain copy where the provider is refused, then a size check of what landed.
put() { echo "rm -f $DIR/$1 2>/dev/null; w=; { o=\$(content write --uri $URI/$1 < $(stage "$1") 2>&1) && case \"\$o\" in *'Unsupported operation'*|*'Error while accessing provider'*) false ;; esac && w=1; } || { mkdir -p $(dirname "$DIR/$1") 2>/dev/null; cp $(stage "$1") $DIR/$1 2>/dev/null && w=1; }; s=\$(wc -c 2>/dev/null < $(stage "$1")); rm -f $(stage "$1"); [ -n \"\$w\" ] && { { [ -e $DIR/$1 ] && [ \"\$(wc -c < $DIR/$1)\" = \"\$s\" ]; } || [ \"\$(content read --uri $URI/$1 2>/dev/null | wc -c)\" = \"\$s\" ]; } || echo $NOT_WRITTEN $1"; }
send() {   # send local file $1 to library path $2
  "$PY" -I b64push.py "$1" "$(stage "$2")" >/dev/null || { echo "could not send $2; run the same command again." >&2; exit 1; }
  local out
  out="$(tv "$(put "$2")")" || { echo "the TV stopped answering at $2; run the same command again." >&2; exit 1; }
  if printf '%s\n' "$out" | tr -d '\r' | grep -qxF "$NOT_WRITTEN $2"; then
    echo "The TV took neither the provider write nor the copy of $2." >&2
    exit 1
  fi
}

for THEME in "${FOLDERS[@]}"; do
  NAME="$(basename "$THEME")"
  # Start from an empty folder, so pictures the old copy had and this one does not are gone.
  tv "rm -rf $DIR/themes/$NAME 2>/dev/null; content delete --uri $URI/themes/$NAME 2>/dev/null" >/dev/null || true
  send "$THEME/config.json" "themes/$NAME/config.json"
  for F in screenshot.jpg wallpaper.jpg; do
    [ -f "$THEME/$F" ] && send "$THEME/$F" "themes/$NAME/$F"
  done
  if [ -d "$THEME/wallpapers" ]; then
    for IMAGE in "$THEME"/wallpapers/*; do
      [ -f "$IMAGE" ] || continue
      N=$(basename "$IMAGE")
      case "$N" in
        *.[jJ][pP][gG]|*.[jJ][pP][eE][gG]|*.[pP][nN][gG]|*.[wW][eE][bB][pP]|captions.txt) send "$IMAGE" "themes/$NAME/wallpapers/$N" ;;
      esac
    done
  fi
  echo "added $NAME"
done
echo "On the TV: Launcher settings > Theme."
