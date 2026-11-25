#!/usr/bin/env bash
set -euo pipefail
package=com.pearparinya.automovie
runner="$package.test/androidx.test.runner.AndroidJUnitRunner"
fixture="$(cat /proc/sys/kernel/random/uuid)"
previous="$(find "$RUNNER_TEMP/previous" -name '*.apk')"
adb install "$previous"
adb install -t "$RUNNER_TEMP/upgrade-test.apk"
verify() {
    local build="$1" phase="$2" output
    adb shell am start -W -n "$package/.StudioActivity" | grep -F 'Status: ok'
    output="$(adb shell am instrument -w -e class "$package.NavigationUpgradeTest" -e fixture "$fixture" -e build "$build" -e phase "$phase" "$runner")"
    echo "$output"
    grep -Fq 'OK (1 test)' <<< "$output"
}
verify "${PREVIOUS_BUILD:-1245}" seed
adb install -r "$RUNNER_TEMP/candidate.apk"
verify "${CANDIDATE_BUILD:-1246}" verify

# Run the actual social editor on the signed candidate after upgrade.
output="$(adb shell am instrument -w -e class "$package.SocialSeoUiTest" "$runner")"
echo "$output"
grep -Fq 'OK (1 test)' <<< "$output"
