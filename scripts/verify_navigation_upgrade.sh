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
social_verify() {
    local phase="$1" output
    output="$(adb shell am instrument -w -e class "$package.SocialSeoUiTest#persistentDraftAcrossProcessAndReinstall" -e fixture "$fixture-social" -e social_phase "$phase" "$runner")"
    echo "$output"
    grep -Fq 'OK (1 test)' <<< "$output"
}
verify "${PREVIOUS_BUILD:-1245}" seed
social_verify seed
adb shell am force-stop "$package"
adb install -r "$RUNNER_TEMP/candidate.apk"
verify "${CANDIDATE_BUILD:-1246}" verify
social_verify restart
adb shell am force-stop "$package"
adb install -r "$RUNNER_TEMP/candidate.apk"
social_verify verify

# Run the actual social editor on the signed candidate after upgrade.
output="$(adb shell am instrument -w -e class "$package.SocialSeoUiTest#editorCopyNavigationAndDraftIsolation" "$runner")"
echo "$output"
grep -Fq 'OK (1 test)' <<< "$output"


# Require the casting regression suite on the signed upgraded candidate.
output="$(adb shell am instrument -w -e class "$package.CastingContinuityRegressionTest" "$runner")"
echo "$output"
grep -Fq 'OK (6 tests)' <<< "$output"
