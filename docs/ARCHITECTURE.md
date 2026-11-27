# JARVIS Architecture — Target Design

Status: approved design baseline; implementation is staged and must be verified in builds.

## Product objective

JARVIS is a general-purpose personal assistant, not a collection of phrase-to-command rules. It must understand natural-language requests, preserve conversation and task context, plan work across capabilities, execute only supported actions, verify outcomes, and communicate uncertainty honestly.

## Runtime boundaries

### Android application
Owns:
- Microphone, wake-word lifecycle, conversation listening and strict app-level privacy gates.
- Android permissions, user approvals and device actions.
- Local/offline commands and calculations.
- Conversation UI, speech output, local history and task-state persistence.
- Validation of every proposed action before execution.

The AI service must never be able to bypass Android-side permission or privacy checks.

### Cloudflare Worker
Owns:
- AI model access (initially Cloudflare Workers AI; no OpenAI API dependency).
- Natural-language interpretation, task planning and response generation.
- Server-side orchestration of explicitly registered remote capabilities.
- Normalised results and source metadata.

The Worker must not claim to have executed a capability unless an adapter actually returned a successful result.

### Capability adapters
Each capability has a stable contract and explicit availability:
- General answer / reasoning
- Web research
- Weather (city text only; no device location)
- Image generation
- Image editing (only when a real edit adapter is connected)
- Navigation (only when a maps adapter is connected)
- Calculations
- Android device actions (executed locally, permission-gated)
- Future integrations

Unavailable capabilities must be reported as unavailable, not simulated by a generic answer.

## Request lifecycle

1. Input gate: confirm microphone/privacy state before accepting speech or continuing a voice session.
2. Context assembly: include recent conversation turns and the active task summary.
3. Understanding: infer the user's goal, entities, constraints, and whether this is a follow-up.
4. Planning: produce a structured plan using registered capability IDs; identify missing information and approval requirements.
5. Policy gate: validate capability availability, arguments, permissions, and approval requirements.
6. Execution: run local or remote adapters; track each step and its result.
7. Verification: validate response shape, success status, source/evidence where applicable, and consistency with the requested task.
8. Response: give a concise, truthful answer and preserve task context for follow-ups.
9. History: record the request, result, and task outcome without storing unnecessary sensitive data.

## Structured plan contract

The model's plan is data, not executable code. It must be parsed and validated before use. A plan should contain:
- schema_version
- request_summary
- intent (free-form semantic label, not a hard-coded exhaustive enum)
- task_id (or a continuation reference)
- needs_clarification and clarification_question
- entities and constraints
- steps: ordered items with capability_id, arguments, and depends_on
- approval_required and reason
- user_response

Only capability IDs registered by the application/Worker are eligible. Unknown IDs, malformed arguments, unsupported operations, and low-confidence destructive actions are rejected or clarified. The model cannot directly invoke Android APIs.

## Task and conversation state

Keep these separate:
- Conversation history: recent user/assistant exchanges used to interpret language.
- Active task state: task ID, goal, selected entities, constraints, completed steps, pending step, clarification slot, and last verified result.

A short reply such as "Karnataka", "the third one", "make it darker", or "try again" must be resolved against the active task before being treated as a new standalone request. Task state is bounded, local-first, and clearable by the user. Do not infer a continuation when context is genuinely ambiguous; ask.

## Capability contract

Every adapter declares:
- stable capability ID and version
- description and input schema
- local/remote execution location
- availability and required permissions
- whether user approval is required
- timeout and retry policy
- output schema
- verification rules

Execution results use explicit states: succeeded, failed, needs_input, denied, unavailable, or timed_out. No response may be described as successful unless the adapter reports success.

## Reliability and safety invariants

- Microphone OFF means no JARVIS recording or listening, including wake-word, manual speech, conversation continuation, and late callbacks.
- Privacy and permission checks are enforced in Android, independently of model output.
- Device location is not used for weather; the user supplies a city/region.
- Never select a geocoding result solely because it is first in a list. Match locality, region and country hints; clarify when uncertain.
- Prevent duplicate execution of the same voice result/task step.
- Never invent tool use, live facts, sources, or successful actions.
- Use bounded timeouts, retries only when safe, and explicit failure states.
- Keep API credentials in server-side secrets; never embed provider secrets in the APK.
- Preserve working wake word, voice, history, image save/copy and privacy behavior during refactors.

## Staged implementation

### Stage 1 — Core foundation
Introduce typed task state, capability registry/contracts, structured plan parsing/validation, and a single request pipeline. Keep existing capability adapters behind compatibility wrappers initially. Add tests for schema validation, unknown capabilities, missing inputs, task continuation, duplicate prevention, and privacy gates.

### Stage 2 — Conversation intelligence
Improve follow-up resolution, task continuation, clarification slots, and safe task cancellation.

### Stage 3 — Knowledge and research
Connect real search/research adapters with source metadata and verification.

### Stage 4 — Capability adapters
Integrate and test weather, image creation/editing, maps/navigation, calculations, and additional services individually.

### Stage 5 — Android intelligence
Expand supported device actions through explicit Android APIs and permission-aware local execution.

### Stage 6 — Reliability
Run regression tests for privacy, wake word, conversation sessions, duplicate callbacks, task recovery, offline behavior, and signed APK installation.

## Release discipline

A stage is not complete because code compiles. A release must pass build/signing checks and its relevant behavior tests. Never label an unsupported capability as implemented. The next build is a foundation milestone, not a claim that the full JARVIS vision is complete.
