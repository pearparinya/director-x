# Director X 4.92 — implementation and release gates

Status: development branch; NOT released. Proposed version 4.92 / build 1108 must be verified against current Gradle config.

## Non-negotiable reference behavior

1. Resolve every visible character to their own immutable original MASTER. Support variable cast sizes, subject to provider image limits; never silently drop a person.
2. Resolve every visible scene prop via a stable prop-master identifier (e.g. P017), not a fuzzy text-only match. Carry prop references into the actual attachment payload, not merely the prompt.
3. Label attachments by role: CHARACTER_MASTER, optional FACE_CROP, PROP_MASTER. Keep source IDs and visible attachment order aligned with prompt bindings.
4. For each character, choose exactly one wardrobe source: MASTER_OUTFIT or SCENE_OUTFIT. Persist the choice for that character and scene. Continuity reuse is explicit, with a story-day/event key.
5. Prevent conflicting instructions: FACE identity must not imply copying MASTER clothing when SCENE_OUTFIT is selected.
6. Preflight must block generation when a required reference is missing, corrupt, mismatched, or exceeds provider limits; display the reason and recovery action.
7. Preserve original MASTER assets, episode data, signing identity, and package name.

## UI workflow

01 Cast; 02 Wardrobe per character; 03 Prop MASTER; 04 Time and location; 05 Continuity; 06 Attachment review; 07 Prompt review; 08 Generate; 09 Image review.
Use numbered menu rows, explicit ready/warning/pending states, accessible labels, and Back navigation. Avoid duplicate menu destinations.

## Integration points to inspect

- SimpleProductionCommands.Task.references currently represents character JSON objects.
- MasterReferencePreparation prepares character originals and face crops.
- ScenePropBinding validates prop assignments against scene digest.
- PropSuggestionReview records approved prop links.
- Trace the final UI sharing / image provider payload and verify bytes are attached.

## Verification and release gates

- Unit/instrumentation tests: two people + P017, six people + multiple props, missing prop, duplicate IDs, master-outfit vs scene-outfit, same-day continuity, changed-day wardrobe.
- UI test: 9-step navigation and state persistence; prompt attachment count and role mapping visible.
- Real-world manual review: EP01_SC004 and 23-scene continuity, with actual generated results where provider access exists.
- Run Gradle tests and signed release APK build on GitHub Actions.
- Verify signature matches existing installed app, versionCode increments, and upgrade retains project data.
- Open PR for review; merge and publish GitHub Release only after successful checks.

## Work tracking

- [x] Isolated development branch created.
- [x] Scope and acceptance criteria recorded.
- [ ] Code integration and tests.
- [ ] Build and signature verification.
- [ ] PR review, merge, GitHub Release.
