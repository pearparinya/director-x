# DIRECTOR X 2.15 — reviewed scene frames

The primary scene menu requires import of a scene image, visual comparison against that project's original MASTER images, confirmation of four review items and actual speaker/position mapping before opening or copying a ready video prompt. Missing references block approval. Approval is a human judgement, never an automated likeness score.

Approval is bound to SHA-256 of image bytes, MASTER bytes and canonical scene facts (including outfits, dialogue, positions, lighting and prompts). Editing or replacing those inputs invalidates approval even if an image timestamp is unchanged. Reopening unchanged story data preserves approval. Existing clips remain importable and mergeable.

Ready video prompts are built from current structured facts and confirmed mappings, not imported free-form video text; they use the reviewed scene as the start frame and retain appearance/outfits/composition. ZIP packages remain compact preparation packs with explicitly conditional video templates: users must review/import the actual image and use that image as the start frame. Templates do not certify images or upload/select Flow assets automatically. The separate video-test tool remains available.

Validation covers persisted approval, changed image bytes with unchanged timestamp, changed MASTER, outfits and scene facts, missing references, plus existing scene prompt and Android export tests. No claim of 100% likeness or live Flow generation testing.
