package com.jps.jarvis.core

/** Pure Kotlin contract types. No Android or network dependencies. */
object ContractVersion { const val CURRENT = "1.0" }
enum class ExecutionOwner { ANDROID, CLOUD }
enum class TaskStatus { PLANNED, NEEDS_INPUT, AWAITING_APPROVAL, RUNNING, SUCCEEDED, PARTIALLY_SUCCEEDED, FAILED, CANCELLED }
enum class StepStatus { SUCCEEDED, FAILED, NEEDS_INPUT, DENIED, UNAVAILABLE, TIMED_OUT, CANCELLED, SKIPPED }

data class AssistantRequest(
    val schemaVersion: String = ContractVersion.CURRENT,
    val requestId: String,
    val sessionId: String,
    val taskId: String? = null,
    val inputText: String,
    val locale: String = "en-IN",
    val advertisedCapabilities: Set<String>,
    val microphoneEnabled: Boolean = false,
    val locationMode: String = "user_supplied_only"
)
data class PlannedStep(
    val stepId: String,
    val capability: String,
    val input: Map<String, String> = emptyMap(),
    val dependsOn: List<String> = emptyList(),
    val requiresApproval: Boolean = false
)
data class AssistantPlan(
    val schemaVersion: String = ContractVersion.CURRENT,
    val requestId: String,
    val taskId: String,
    val status: TaskStatus = TaskStatus.PLANNED,
    val summary: String,
    val needsUserInput: Boolean = false,
    val clarification: String? = null,
    val steps: List<PlannedStep>
)
data class ExecutionResult(
    val schemaVersion: String = ContractVersion.CURRENT,
    val requestId: String,
    val taskId: String,
    val stepId: String,
    val capability: String,
    val status: StepStatus,
    val output: Map<String, String> = emptyMap(),
    val evidence: List<String> = emptyList(),
    val errorCode: String? = null
)
data class CapabilityDescriptor(
    val id: String,
    val version: Int = 1,
    val owner: ExecutionOwner,
    val available: Boolean = true,
    val requiresApproval: Boolean = false,
    val maxInputCharacters: Int = 2048
)

object InitialCapabilities {
    val all = listOf(
        CapabilityDescriptor("weather.current", owner = ExecutionOwner.CLOUD),
        CapabilityDescriptor("weather.forecast", owner = ExecutionOwner.CLOUD),
        CapabilityDescriptor("image.generate", owner = ExecutionOwner.CLOUD),
        CapabilityDescriptor("image.edit", owner = ExecutionOwner.CLOUD, available = false),
        CapabilityDescriptor("navigation.route", owner = ExecutionOwner.ANDROID, available = false),
        CapabilityDescriptor("android.settings.open", owner = ExecutionOwner.ANDROID),
        CapabilityDescriptor("android.camera.open", owner = ExecutionOwner.ANDROID),
        CapabilityDescriptor("device.battery.read", owner = ExecutionOwner.ANDROID),
        CapabilityDescriptor("assistant.answer", owner = ExecutionOwner.CLOUD)
    )
}
