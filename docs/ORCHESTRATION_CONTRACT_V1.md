# JARVIS Orchestration Contract v1

Status: design contract for implementation; not yet wired into the Android app or Cloudflare Worker.

## Goals

- Keep the current `/intent`, `/chat`, and `/image` endpoints operational during migration.
- Give Android and Cloudflare one stable request, plan, execution-result, and task-state vocabulary.
- Keep permission enforcement and Android-side execution on the device.
- Treat model output as untrusted proposals. Validate every plan before execution.
- Support one-step and multi-step requests without hard-coding every future capability into the intent enum.

## Request envelope

```json
{
  "schema_version": "1.0",
  "request_id": "client-generated-unique-id",
  "session_id": "conversation-session-id",
  "task_id": null,
  "input": {
    "kind": "text",
    "text": "Plan a route to the office and tell me the weather there",
    "locale": "en-IN"
  },
  "context": {
    "messages": [],
    "active_task": null
  },
  "device": {
    "platform": "android",
    "capabilities": ["weather.current", "navigation.route"]
  },
  "policy": {
    "microphone_enabled": true,
    "location_mode": "user_supplied_only",
    "allow_cloud_processing": true
  }
}
```

The client must omit unavailable or sensitive device fields. It must never send precise location merely because a model asks for it. The microphone flag is a client-side state assertion, not an authorisation token; Android must stop capture locally when microphone use is disabled.

## Plan response

```json
{
  "schema_version": "1.0",
  "request_id": "same-request-id",
  "task": {
    "task_id": "task-id",
    "status": "planned",
    "summary": "Check the weather and prepare a route",
    "needs_user_input": false,
    "clarification": null
  },
  "steps": [
    {
      "step_id": "step-1",
      "capability": "weather.current",
      "input": { "location_text": "office" },
      "depends_on": [],
      "requires_approval": false
    },
    {
      "step_id": "step-2",
      "capability": "navigation.route",
      "input": { "destination_text": "office" },
      "depends_on": ["step-1"],
      "requires_approval": false
    }
  ],
  "response_guidance": "Summarise verified results only."
}
```

Allowed task statuses: `planned`, `needs_input`, `awaiting_approval`, `running`, `succeeded`, `partially_succeeded`, `failed`, `cancelled`.

## Capability contract

Each registered capability declares:

- stable capability ID and version;
- execution owner: `android` or `cloud`;
- input and output schema versions;
- availability and required permissions;
- whether user approval is required;
- timeout and retry policy;
- verification strategy.

Initial IDs should include `weather.current`, `weather.forecast`, `image.generate`, `image.edit`, `navigation.route`, `android.settings.open`, `android.camera.open`, `device.battery.read`, and `assistant.answer`.

A model may request only capabilities advertised by the client/registry. Unknown or unavailable IDs must produce `unavailable`; they must never be silently mapped to a different action.

## Execution result

```json
{
  "schema_version": "1.0",
  "request_id": "same-request-id",
  "task_id": "task-id",
  "step_id": "step-1",
  "capability": "weather.current",
  "status": "succeeded",
  "output": {},
  "evidence": [],
  "error": null
}
```

Allowed step statuses: `succeeded`, `failed`, `needs_input`, `denied`, `unavailable`, `timed_out`, `cancelled`.

A success result means the adapter completed and its verification condition passed. A model-generated statement is not evidence of execution.

## Trust and privacy boundaries

1. Android owns microphone capture, wake-word lifecycle, Android permissions, and device actions.
2. Microphone OFF must stop/deny wake-word capture, tap-to-speak, conversation capture, and delayed/restarted recognizers. Check the gate at capture start and in callbacks.
3. Weather uses an explicitly supplied city/place unless the user separately enables and requests another location mode. No precise-location permission is required by this contract.
4. Cloudflare owns model calls and cloud-only capabilities. Secrets remain in Worker environment bindings.
5. The Worker authenticates requests, validates envelope shape/size, and returns structured errors. It must not claim Android actions were executed.
6. Model output is untrusted. Validate schema, capability IDs, step count, dependencies, input sizes, and approval requirements before execution.
7. Side-effecting actions require explicit approval where the capability registry says so.
8. Keep active task state separate from display/history messages. Persist only the minimum needed for continuity.

## Migration plan

1. Add contract models and pure validation tests on Android without changing existing routing.
2. Add a versioned Worker planning endpoint (proposed: `/v1/plan`) while preserving current endpoints.
3. Add Android capability registry and adapters; route one capability at a time through the new executor.
4. Add task-state persistence, dependency execution, cancellation, and verification.
5. Migrate existing weather, image, general-chat and device commands; then add further capabilities.
6. Remove legacy routes only after parity tests and on-device regression checks pass.

## Required tests before enabling execution

- malformed/unsupported schema version rejected;
- missing request ID rejected;
- unknown capability rejected as unavailable;
- dependency cycles and missing dependencies rejected;
- step count and input sizes bounded;
- approval-required step cannot execute without approval;
- microphone OFF blocks every capture entry point and late callback;
- no precise location sent for weather;
- failed, denied, timed-out, and partially completed tasks are represented truthfully;
- existing endpoints continue to pass regression tests.
