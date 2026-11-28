# JARVIS Architecture Core

This independently maintained Kotlin/JVM module has no Android, Compose, network, microphone, or app-runtime dependencies. It is not yet wired into the production app and does not change current APK behaviour.

## Implemented and tested

- Versioned request, plan, step, and execution-result contracts.
- Capability registry with duplicate/invalid ID protection.
- Plan validation for schema/request identity, advertised and available capabilities, approval declarations, bounded inputs, dependency integrity/cycles, and user-supplied-only location mode.
- Task lifecycle transition rules.
- Deterministic sequential plan executor with dependency ordering, explicit approval gating, unavailable-adapter reporting, failure isolation, dependent-step skipping, and truthful task completion status.
- JVM unit tests for validation, lifecycle, multi-step execution, approval, failure, and unavailable adapters.

## Safety boundaries

- A plan is fully validated before any executor is called.
- Model output is only a proposal; it cannot execute itself.
- Android must remain authoritative for permissions, microphone capture, user approval, and device operations.
- Missing adapters return `unavailable`; the engine does not silently substitute another capability.
- An adapter exception is reported with a stable error code, not an invented success.
- Weather/location policy remains user-supplied-only; this module does not access device location.

## Run tests

Use JDK 17 and run `gradle test --no-daemon` from this directory, or use the dedicated Architecture Core Tests GitHub Actions workflow.

## Integration gate

The executor currently runs only injected JVM test adapters. It is not connected to the Cloudflare Worker or Android app. Next integration steps are to inspect the complete starter ZIP, implement and contract-test the additive Worker `/v1/plan` endpoint, then add Android adapters through deterministic source integration. Do not change production routing until both the baseline APK build and new integration tests pass.
