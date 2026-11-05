# Hamari Mehnat — Personal AI Job Hunter (Android-only V1)

This branch begins the migration from the earlier Streamlit proof-of-concept to a **single-user Android application**.

## Product scope
- Android app only: no website, web dashboard, public registration, multi-user SaaS or payments.
- Private personal job-search workspace.
- Local-first data and on-device AI; internet is used for online job discovery and current external information.
- No automatic applications or employer messages without explicit owner approval.

## Current build slice
- Native Kotlin + Jetpack Compose application shell.
- Five navigation destinations: Home, Jobs, Files, Tracker and Local AI.
- Dark, low-distraction interface.
- Android CI workflow for debug APK assembly and unit tests.
- Architecture and local-model evaluation plans.

Screens label modules that are not implemented yet. This initial slice is not a complete job-hunting agent. A debug APK will be available only after the Android CI workflow builds successfully.

## Local AI direction
First benchmark candidate: Qwen3.5-2B Instruct, GGUF Q4_K_M, through llama.cpp Android JNI/NDK. Model download will be optional, checksum-verified and stored in app-private storage. If the model exceeds the target phone's memory or thermal budget, a smaller candidate will be tested. No cloud AI key is required.

## Build
Open the project root in Android Studio with JDK 17, Android SDK 35 and Gradle 8.10.2, then run:

```bash
gradle assembleDebug testDebugUnitTest
```

## Existing prototype
The root Python modules (`app.py`, `job_sources.py`, `matching.py`) are retained temporarily as migration reference. They are not the Android application runtime. Useful logic will be ported and covered by Kotlin tests before retirement.

## Privacy
Never commit CVs, passports, certificates, private profile data, model files or API credentials. Keep the repository private before adding any personal material. Personal documents belong in app-private encrypted storage, not GitHub.
