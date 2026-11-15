# Director X: low-quota development workflow

This process reduces repeated Codex usage; it does **not** increase OpenAI's weekly quota.

## Day-to-day process
1. Describe multiple related changes in one request, with acceptance criteria and affected screens.
2. Ask the coding assistant to implement changes on a feature branch and open a pull request. Avoid repeated build-only assistant requests.
3. In GitHub, open **Actions → On-demand Android verification → Run workflow**, select the feature branch, and run tests.
4. Optionally select **build_debug_apk** to compile a *debug* APK for testing. Debug APKs are not signed with the production key and must not be treated as in-app updates.
5. Review test results and changes before merging. Use the existing release/signing workflow for production updates.

## Stability safeguards
- The verification workflow runs automatically for pull requests changing Android/build files and can also be triggered manually. Unrelated documentation-only changes do not trigger it.
- Runs for the same branch are grouped; newer runs cancel older unfinished runs.
- Gradle dependencies are cached to reduce repeated setup.
- The job has a 35-minute timeout to prevent indefinitely running builds.
- It has read-only repository permissions and never publishes a release.
- Never put API keys or signing passwords into source code, logs, or prompts. Use GitHub Actions secrets as appropriate.

## Suggested request format
“On a feature branch, implement these related changes: [items]. Preserve current data and existing release/update behavior. Add or adjust tests. Open one PR with a concise change summary. Do not trigger release builds; I will run the on-demand verification workflow.”

## Important
GitHub Actions verification consumes GitHub Actions minutes according to the repository's plan and runner. It does not consume Codex task quota. An APK built with the debug key may not install over a production-signed APK.
