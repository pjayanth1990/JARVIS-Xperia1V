# Android Source Audit — 2026-11-27

## Scope and evidence

Reviewed the current `.github/workflows/build.yml` on the default branch. The workflow extracts `JARVIS_Xperia1V_Starter.zip` and then rewrites/injects the production Android source during the GitHub Actions build. The generated `MainActivity.kt` is therefore not a normal, directly maintained source file in the repository.

This is a workflow/source-generation audit, not a full Gradle project audit: the ZIP has not yet been unpacked and independently inspected in this environment. Do not treat this document as proof that the complete Android project has been audited.

## Findings

### 1. Build/source maintainability — high priority

- The workflow generates a large `MainActivity.kt` and then applies many sequential Python string and regular-expression rewrites.
- Each rewrite depends on exact text anchors and sometimes on the output of earlier rewrites. A small source change can break a later step or produce a structurally valid but semantically incorrect result.
- Build failures such as missing dispatch/context insertion points are consistent with this design risk.
- The next implementation should add independently maintained Kotlin source files and tests to the repository, then copy them into the extracted Gradle project. Avoid adding more feature logic through fragile text replacements.
- Preserve the current build workflow as a known-good baseline until the new source path is independently validated.

### 2. Microphone privacy — existing safeguards identified

The workflow currently injects several protections:

- The saved microphone/wake-word setting is loaded at startup.
- `requestListen()` and `listenOnce()` both reject voice capture when the setting is OFF.
- The tap-to-speak button is disabled while the setting is OFF.
- Turning the setting OFF clears conversation state, cancels/destroys the active recognizer, stops the wake-word service, and updates the UI.
- A late `onResults()` callback checks the setting before processing recognized text.
- Wake-word broadcasts and service startup check the enabled state.
- The service itself checks the saved setting before starting microphone detection.
- App resume does not intentionally enable the microphone when the setting is OFF.

These are useful safeguards. They are not yet a complete verified privacy guarantee: the generated app has not been instrumented and tested on-device for toggle/callback races, service restart behavior, permission-result races, or every lifecycle path. The next test suite must explicitly exercise those cases.

### 3. Current request routing — limited orchestration

- Strictly local commands are handled by a local command function.
- Other requests go to Worker `/intent`.
- The Android dispatcher maps the returned intent to a small fixed set: weather, image generation, image editing, navigation, clarification, and general chat.
- Image editing and navigation are currently reported as unavailable rather than executed.
- The model classifies; Android dispatches directly. There is no generic capability registry, dependency-aware multi-step task state, approval policy, or structured execution-result reporting yet.

### 4. Conversation and task state

- Conversation context is reconstructed from display-history strings and sent as recent user/assistant messages.
- Conversation history is not a separate task-state store.
- Pending weather clarification is represented by a feature-specific Boolean.
- This should evolve into a general pending-input/task state rather than adding more one-off pending flags.

### 5. Backend integration

- The app calls `/intent`, `/chat`, and `/image` directly.
- The Android build workflow injects the Worker URL and client token into a generated Kotlin configuration file.
- The new orchestration endpoint must be additive first; do not remove or change legacy routes until compatibility tests pass.
- The Worker contract must be validated independently of Android before integration.

## Engineering decision

Do not retrofit orchestration into the current generated `MainActivity.kt` with another series of anchor replacements.

Preferred next sequence:

1. Independently inspect the ZIP project structure and Gradle dependencies.
2. Introduce maintained Kotlin files for the versioned contract, capability descriptors, plan validation, task state, and execution results.
3. Add pure JVM tests for validation and task-state transitions.
4. Copy those files into the extracted project through a deterministic build step and compile without changing existing routing.
5. Add the Worker planning endpoint while preserving legacy endpoints.
6. Connect one existing capability at a time through the registry and executor.
7. Run device regression tests, especially microphone OFF/ON, wake-word service, follow-up conversation, weather disambiguation, image generation, history, and offline commands.

## Current completion status

- Orchestration contract document: committed.
- Workflow/source-generation audit: performed.
- Complete extracted Android project audit: pending.
- Kotlin contract models and tests: not implemented.
- Worker planning endpoint: not implemented.
- New APK: not built.
