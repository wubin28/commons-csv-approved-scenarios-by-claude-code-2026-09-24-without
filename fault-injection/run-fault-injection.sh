#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

cd "$(dirname "$0")/.."
report=target/surefire-reports/TEST-org.apache.commons.csv.RequiredHeadersTest.xml
expected_file=fault-injection/expected.md
run_log=$(mktemp)
summary_file=$(mktemp)
marker=$(mktemp)
active_patch=

cleanup() {
    if [ -n "$active_patch" ]; then
        git apply -R "$active_patch" || printf 'FAILED TO RESTORE %s\n' "$active_patch" >&2
    fi
    rm -f "$run_log" "$summary_file" "$marker"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Save output outside the repository first: redirecting into tracked last-run.log dirties the tree before this check.
# Local preparation may leave only T4 artifacts/docs uncommitted; normal runs require a fully clean worktree.
if [ -z "${FAULT_INJECTION_ALLOW_DIRTY:-}" ] && [ -n "$(git status --porcelain)" ]; then
    printf 'ERROR: working tree must be clean before fault injection.\n' >&2
    exit 1
fi
if ! git diff --quiet -- src/main || ! git diff --cached --quiet -- src/main; then
    printf 'ERROR: src/main must be clean before fault injection.\n' >&2
    exit 1
fi

# The log itself is an ASF-licensed repository artifact.
sed -n '2,15p' "$0"
printf '\nT4 fault injection\n'
if mvn test -Dtest=RequiredHeadersTest -Dsurefire.failIfNoSpecifiedTests=false >"$run_log" 2>&1; then
    printf 'Baseline: RequiredHeadersTest PASS\n'
else
    printf 'Baseline: RequiredHeadersTest FAIL\n'
    tail -20 "$run_log"
    exit 1
fi

printf 'Mutation | Expected | Actual | Match?\n'
mismatches=0
count=0
for patch in fault-injection/[0-9][0-9]-*.patch; do
    id=${patch##*/}
    id=${id%%-*}
    expected=$(awk -F'|' -v id="$id" '$2 ~ "^[[:space:]]*" id "[[:space:]]*$" {print $5}' "$expected_file" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//;s/`//g')
    if [ -z "$expected" ]; then
        printf 'ERROR: no prediction for %s\n' "$patch" >&2
        exit 1
    fi
    expected_sorted=$(printf '%s\n' "$expected" | tr ',' '\n' | LC_ALL=C sort -u | paste -sd, -)

    git apply "$patch"
    active_patch=$patch
    touch "$marker"
    if mvn test -Dtest=RequiredHeadersTest -Dsurefire.failIfNoSpecifiedTests=false >"$run_log" 2>&1; then
        test_status=0
    else
        test_status=$?
    fi
    if [ ! -f "$report" ] || [ ! "$report" -nt "$marker" ]; then
        printf 'ERROR: fresh Surefire report missing for %s (mvn exit %s)\n' "$patch" "$test_status" >&2
        tail -20 "$run_log" >&2
        exit 1
    fi
    actual=$(perl -0777 -ne 'while (/<testcase\b([^>]*?)(?:\/>|>(.*?)<\/testcase>)/sg) { my ($attrs, $body) = ($1, $2); next unless defined $body && $body =~ /<(?:failure|error)\b/; my ($name) = $attrs =~ /\bname="([^"]+)"/; next unless defined $name; $name =~ s/\(.*$//; print "$name\n"; }' "$report" | LC_ALL=C sort -u | paste -sd, -)
    if [ "$actual" = "$expected_sorted" ]; then
        match=yes
    else
        match=no
        mismatches=$((mismatches + 1))
    fi
    printf '%s | %s | %s | %s\n' "$id" "$expected_sorted" "${actual:-<none>}" "$match" >>"$summary_file"
    count=$((count + 1))
    git apply -R "$patch"
    active_patch=
done

sed -n '1,$p' "$summary_file"
printf 'Total: %s mutations; %s mismatches\n' "$count" "$mismatches"
if ! git diff --quiet -- src/main || ! git diff --cached --quiet -- src/main; then
    printf 'ERROR: injected changes remain in src/main.\n' >&2
    exit 1
fi
[ "$mismatches" -eq 0 ]
