#!/usr/bin/env bash
# Build and run the training crackme (part of neon-royale's RE lab).
set -euo pipefail
cd "$(dirname "$0")"
cc=${CC:-gcc}
$cc -O1 -o crackme crackme.c
echo "built ./crackme"
echo "try:  strings crackme | grep hint"
echo "      objdump -d crackme | less   # look for check_key"
echo "then: ./crackme"
