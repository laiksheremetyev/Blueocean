#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../android-app"
./gradlew assembleDebug
