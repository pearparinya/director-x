# DIRECTOR X 2.11 — compact Flow commands

Exports contain START.txt, one EPxx.txt per episode, three unchanged MASTER images, and BACKUP/story.json. Five episodes produce ten files. Each episode includes both cover ratios and complete scene commands. Copy one block with its reference header, not the whole episode into one generation request.

Every scene, poster and video-test command starts with the required people and REFERENCE BINDING labels CHARACTER_GAWIN, CHARACTER_RINLADA and CHARACTER_MIND. Labels denote the attached person's identity, never an upload-order number or a required filename. The user must attach and match the listed people; labels do not upload or select assets automatically. No renaming of the user's image files is required.

Exported prompts replace legacy canonical MASTER filenames (including folder prefixes and jpg/png/jpeg/webp variants) with stable labels. Physical image files retain accurate extensions. story.json retains internal filenames and also includes reference labels, profiles, hashes and actual MASTER paths as character_memory. Existing original story data is unchanged; all wardrobes and continuity fields remain in the backup and scene blocks. Scene prompt dialogs and copy actions use the same reference-first formatter. Video test UI asks only for characters and scene settings.

After updating, export the completed story again; old downloaded ZIPs are unchanged. No script regeneration or API calls are needed. Tests verify file count, cover/scene inclusion, selected-only references before prompts, legacy filename replacement and exact MASTER bytes. Actual Flow identity mapping/results remain subject to review.
