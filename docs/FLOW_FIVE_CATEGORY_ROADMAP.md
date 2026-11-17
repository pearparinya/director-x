# Director X — Five-Category Flow Development Plan

This roadmap is a newly agreed development structure, not a recovered deleted-chat document. All completion percentages must be evidence-based; unverified work is not 100%.

| Order | Category | Completion gate |
| --- | --- | --- |
| 1 | Script analysis and scene preparation | Validate EP/SC structure, cast, location, story day/time, action, Thai dialogue, start/end state and duration; malformed input produces actionable errors; unit and Android integration tests pass. |
| 2 | MASTER identity and continuity | Validate character/reference mapping, same-day outfits, props and cross-EP continuity; tests and manual image review pass. |
| 3 | First-frame image prompt and scene composition | Validate 9:16 composition, lighting, cast positions, source identity binding, text-free scene output; review real generated images. |
| 4 | Google Flow video prompt engine | Validate English scene/camera/action directions, unmodified Thai dialogue, sound, 8-second first-frame progression, continuity and export; test real Flow output. Existing draft PR #358 is not proof of completion. |
| 5 | Quality gates, export and signed APK | Verify reports, copy/export integrity, CI, Android device tests, signing certificate and upgrade without data loss. |

## Category 1 — acceptance checklist
- [ ] Inspect all existing script/scene data models and entry points.
- [ ] Define schema requirements and backward-compatible behavior for older stories.
- [ ] Reject malformed episode/scene arrays without crashes.
- [ ] Identify missing or duplicate EP/SC numbers, missing cast identity, speaker mismatches and absent story-day/location/time metadata.
- [ ] Preserve exact Thai dialogue; do not silently rewrite a user's script.
- [ ] Verify each scene's duration and start/end states before handing off to image/video prompts.
- [ ] Add deterministic unit tests for valid, invalid and legacy stories.
- [ ] Pass CI Android unit tests and build; verify actual device integration.

## Release rules
- Work on feature branches; no direct writes to main.
- Keep PRs draft until reviewed and CI passes.
- Do not mark any category 100% until all acceptance gates are evidenced.
- Do not publish an APK without verified signing and upgrade compatibility.
