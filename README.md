# DIRECTOR X

Android app for planning stories, sharing character references with ChatGPT, and preparing scene packages for Flow.

The launcher opens `StudioActivity`. The casting library contains forty-five user-supplied three-view sheets (CHAR_101–CHAR_145), each with a Thai name and an explicitly assigned fictional age. Original image bytes are preserved. No default lead portraits are installed. Version 2.45 clears existing character reference files once, including project MASTER copies, without deleting scripts or generated scene media.

Version metadata lives in `app/build.gradle.kts`. GitHub Actions runs unit tests, builds and signs the release APK, and publishes the current release for the in-app updater. The old build-time source transformations have been removed; builds compile the committed source directly.
