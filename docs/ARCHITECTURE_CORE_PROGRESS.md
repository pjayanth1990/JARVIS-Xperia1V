# JARVIS Architecture Core — implementation log

## 2026-11-27 — isolated foundation started

Development branch: architecture/core-foundation.

Added an isolated Kotlin/JVM module under architecture-core/ containing versioned orchestration data models, an initial capability registry, a pure plan validator, a task-state transition engine, and unit tests. Added a dedicated GitHub Actions workflow for this module.

Not yet verified: CI execution, integration into the extracted Android Gradle project, Worker planning endpoint, production routing migration, and on-device regression tests.

Compatibility decision: no production Android source, starter ZIP, or existing APK workflow is changed by this first implementation slice.
