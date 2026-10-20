# DIRECTOR X: Gemini story writing (2.07)

Step 1 now writes text scripts inside the app. Enter a personal Gemini Developer API key once in Settings → Gemini API. The app defaults to `gemini-3.5-flash-lite` and permits an explicit model name. Free quota and billing depend on the user's Google project; the app cannot detect billing tier or promise free requests. It never enables billing, switches models, or retries failed requests automatically.

The key is entered on the user's device, encrypted using Android Keystore AES-GCM, and stored in `noBackupFilesDir`. It is not bundled in the APK, passed in service intents, included in project backups, or added to URLs/logs. This personal BYOK build is not a shared production API credential. A centrally managed public deployment should use a backend proxy and server-side secrets.

Only the premise, outline, prior scene text and built-in character descriptions are sent to `generativelanguage.googleapis.com` over HTTPS. MASTER photos stay on the device and are bundled into the Flow export. No video or image generation API is invoked. The user retains their Flow workflow.

Generation first produces an outline (auto 4–7 EP or explicitly 3–7), then four batches of six scenes per episode. Each EP has 24 × 8 seconds = 3:12. A successful outline and each validated batch are atomically checkpointed. Quota/HTTP/network errors stop generation; the user can resume later, reusing completed checkpoints. Android process interruption is shown as interrupted, not successful. A foreground notification supports stopping after starting from the visible app. Resume uses the explicitly configured current model/key.

A new project is used for each new generation, with MASTER snapshots. Partial output is never imported as a complete story. The final `story.json` is atomically committed only after duration, identity mapping, speaker, field and repeated-dialogue checks. It opens automatically in the current project; the user reviews scenes and exports the existing Flow ZIP. Existing stories, imports, four production stages, video tests and MP4 assembly remain available. File sharing remains a secondary writing option.

Offline Android tests simulate the API response shape, quota failure, checkpoints/resume, complete 72-scene assembly, malformed/truncated/blocked responses, invalid durations and encrypted key deletion. No real API key is present in CI; live Google account/model access and creative output quality require a user-key test.

Official references checked 2026-10-20:
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/models
- https://ai.google.dev/api/generate-content
- https://ai.google.dev/gemini-api/docs/structured-output
- https://ai.google.dev/gemini-api/docs/api-key
