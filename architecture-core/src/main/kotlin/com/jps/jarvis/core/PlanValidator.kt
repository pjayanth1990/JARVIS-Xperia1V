package com.jps.jarvis.core

data class PlanValidationResult(val errors: List<String>) { val isValid get() = errors.isEmpty() }

class PlanValidator(
    private val registry: CapabilityRegistry,
    private val maxSteps: Int = 8,
    private val maxSummaryCharacters: Int = 500,
    private val maxStepInputCharacters: Int = 2048
) {
    fun validate(request: AssistantRequest, plan: AssistantPlan): PlanValidationResult {
        val errors = mutableListOf<String>()
        if (request.schemaVersion != ContractVersion.CURRENT || plan.schemaVersion != ContractVersion.CURRENT) errors += "unsupported_schema_version"
        if (request.requestId.isBlank() || plan.requestId.isBlank() || request.requestId != plan.requestId) errors += "request_id_mismatch"
        if (plan.taskId.isBlank()) errors += "missing_task_id"
        if (plan.summary.length > maxSummaryCharacters) errors += "summary_too_long"
        if (plan.steps.size > maxSteps) errors += "too_many_steps"
        if (plan.needsUserInput && plan.steps.isNotEmpty()) errors += "steps_present_while_waiting_for_input"
        val ids = plan.steps.map { it.stepId }
        if (ids.any { it.isBlank() } || ids.distinct().size != ids.size) errors += "invalid_or_duplicate_step_id"
        val idSet = ids.toSet()
        plan.steps.forEach { step ->
            val descriptor = registry.find(step.capability)
            if (descriptor == null) errors += "unknown_capability:${step.capability}" else {
                if (!descriptor.available) errors += "capability_unavailable:${step.capability}"
                if (step.capability !in request.advertisedCapabilities) errors += "capability_not_advertised:${step.capability}"
                if (descriptor.requiresApproval && !step.requiresApproval) errors += "approval_requirement_missing:${step.stepId}"
                val inputSize = step.input.entries.sumOf { it.key.length + it.value.length }
                if (inputSize > minOf(maxStepInputCharacters, descriptor.maxInputCharacters)) errors += "step_input_too_large:${step.stepId}"
            }
            if (step.dependsOn.any { it !in idSet }) errors += "missing_dependency:${step.stepId}"
            if (step.stepId in step.dependsOn) errors += "self_dependency:${step.stepId}"
        }
        if (hasDependencyCycle(plan.steps)) errors += "dependency_cycle"
        if (request.locationMode != "user_supplied_only") errors += "unsupported_location_mode"
        return PlanValidationResult(errors.distinct())
    }
    private fun hasDependencyCycle(steps: List<PlannedStep>): Boolean {
        val dependencies = steps.associate { it.stepId to it.dependsOn }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (id in visiting) return true
            if (id in visited) return false
            visiting += id
            for (dependency in dependencies[id].orEmpty()) {
                if (dependency in dependencies && visit(dependency)) return true
            }
            visiting -= id
            visited += id
            return false
        }
        return dependencies.keys.any { visit(it) }
    }
}
