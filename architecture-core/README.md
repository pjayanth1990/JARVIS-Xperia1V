# JARVIS Architecture Core (isolated foundation)

This independently maintained Kotlin/JVM module has no Android, Compose, network, microphone, or app-runtime dependencies. It is not yet wired into the production app and does not change current APK behaviour.

Included: versioned request/plan/result models; capability registry; pure plan validation for schema, request identity, advertised/available capabilities, bounded inputs, dependency integrity/cycles and user-supplied-only location mode; task-state transitions; JVM unit tests.

Run Gradle test from this directory with JDK 17, or use the dedicated Architecture Core Tests GitHub Actions workflow.

These primitives do not execute Android actions. Android remains authoritative for permissions, microphone capture, user approval and device operations. Model output is untrusted until validated on-device.

Integration gate: audit the complete starter project, pass CI, then add deterministic source-copy into the existing APK build. Do not change production routing until the isolated module and baseline APK build are both green.
