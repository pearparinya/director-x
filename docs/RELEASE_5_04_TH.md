# Director X 5.04 — Build 1120 (PR review)

## Change
- Adds `MasterReferencePreparation.prepareOriginalMastersOnly` for explicitly selected MASTER-only multi-character reference packages.
- Each character contributes exactly one real original MASTER; missing face crops are acceptable in this mode.
- Rejects blank/duplicate character names, missing/empty files, and identical MASTER bytes reused across characters.
- Reports the nine-image cap without silently dropping any character.
- Preserves the existing default `prepare` behavior; integration into the sharing UI is **not yet implemented**.

## Review checklist
- [ ] Wire the explicit MASTER-only UI selection to the new preparation function.
- [ ] Verify five distinct characters yield five references, not ten.
- [ ] Verify missing face crop does not block MASTER-only mode.
- [ ] Verify duplicated images or names produce a warning and block sending.
- [ ] Verify ten characters exceed the nine-image cap and no images are silently dropped.
- [ ] Run Android build, unit tests and real-device share tests.
- [ ] Confirm the prompt image indices match attachment order.
- [ ] Confirm version 5.04 / build 1120 and existing app upgrade.

Do not release until CI and device validation pass.
