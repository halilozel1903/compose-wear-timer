#!/usr/bin/env bash
# Captures README screenshots of the sample app on a running Wear OS emulator (large round).
# Taps and crown turns can't be timed reliably through adb on a fresh emulator, so the sample opens
# each screen on a fixed clock with fixed data from the `scene` extra. Each capture must show the
# expected text, be the resumed activity and pass the blank image check.
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

install_sample

fresh_launch --es scene picker
capture wear-picker "0:20"

fresh_launch --es scene running
capture wear-running "0:12"

fresh_launch --es scene rest
capture wear-rest "0:06"

fresh_launch --es scene done
capture wear-done "Workout complete"
