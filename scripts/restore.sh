#!/data/data/com.termux/files/usr/bin/bash
# Kept for convenience: does the same as restore-original.sh
exec bash "$(dirname "$0")/restore-original.sh" "$@"
