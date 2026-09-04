#!/usr/bin/env bash

set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
violations=()

while IFS= read -r -d '' path; do
    lowercase_path="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
    basename="${lowercase_path##*/}"

    case "/$lowercase_path" in
        */mysql-data/*)
            violations+=("$path (MySQL runtime data directory)")
            continue
            ;;
    esac

    case "$basename" in
        binlog|binlog.*|*.binlog|*.binlog.*)
            violations+=("$path (MySQL binary log)")
            continue
            ;;
    esac

    case "$basename" in
        *.pem|*.key|*.crt|*.cer|*.p12|*.pfx)
            violations+=("$path (certificate or private-key file)")
            ;;
    esac
done < <(git -C "$repository_root" ls-files -z)

if ((${#violations[@]} > 0)); then
    printf 'Repository hygiene check failed. Forbidden tracked files:\n' >&2
    printf ' - %s\n' "${violations[@]}" >&2
    printf 'Remove them from the Git index without deleting local runtime data.\n' >&2
    exit 1
fi

printf 'Repository hygiene check passed.\n'
