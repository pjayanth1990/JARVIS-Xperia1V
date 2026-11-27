package com.jps.jarvis.core

/** A capability adapter is the only code allowed to perform a capability's work. */
fun interface CapabilityExecutor {
    fun execute(request: AssistantRequest, plan: AssistantPlan, step: PlannedStep): CapabilityExecutionOutput
}

data class CapabilityExecutionOutput(
    val values: Map<String, String> = emptyMap(),
    val evidence: List<String> = emptyList()
)

data class TaskExecutionReport(
    val taskId: String,
    val status: TaskStatus,
    val results: List<ExecutionResult>,
    val validationErrors: List<String> = emptyList()
)

/**
 * Deterministic, sequential executor. It validates the complete plan before invoking
 * any adapter, gates approval-required work, and never treats model text as execution.
 */
class PlanExecutionEngine(
    private val registry: CapabilityRegistry,
    executors: Collection<CapabilityExecutorBinding>
) {
    private val validator = PlanValidator(registry)
    private val executorsByCapability: Map<String, CapabilityExecutor>

    init {
        require(executors.map { it.capabilityId }.distinct().size == executors.size) {
            "Duplicate capability executor bindings"
        }
        executorsByCapability = executors.associate { it.capabilityId to it.executor }
    }

    fun execute(
        request: AssistantRequest,
        plan: AssistantPlan,
        approvedStepIds: Set<String> = emptySet()
    ): TaskExecutionReport {
        val validation = validator.validate(request, plan)
        if (!validation.isValid) {
            return TaskExecutionReport(plan.taskId, TaskStatus.FAILED, emptyList(), validation.errors)
        }
        if (plan.needsUserInput) {
            return TaskExecutionReport(plan.taskId, TaskStatus.NEEDS_INPUT, emptyList())
        }

        val approvalRequired = plan.steps.any { step ->
            step.requiresApproval || registry.find(step.capability)?.requiresApproval == true
        }
        if (approvalRequired && plan.steps.any { step ->
                (step.requiresApproval || registry.find(step.capability)?.requiresApproval == true) &&
                    step.stepId !in approvedStepIds
            }) {
            return TaskExecutionReport(plan.taskId, TaskStatus.AWAITING_APPROVAL, emptyList())
        }

        if (plan.steps.isEmpty()) {
            return TaskExecutionReport(plan.taskId, TaskStatus.SUCCEEDED, emptyList())
        }

        val pending = plan.steps.toMutableList()
        val completed = linkedMapOf<String, ExecutionResult>()

        while (pending.isNotEmpty()) {
            val ready = pending.filter { step -> step.dependsOn.all { it in completed } }
            if (ready.isEmpty()) {
                // Defensive stop: the validator should already reject this condition.
                return TaskExecutionReport(
                    plan.taskId, TaskStatus.FAILED, completed.values.toList(),
                    listOf("execution_dependency_resolution_failed")
                )
            }

            for (step in ready) {
                val failedDependency = step.dependsOn.any {
                    completed[it]?.status != StepStatus.SUCCEEDED
                }
                val descriptor = registry.find(step.capability)
                val result = when {
                    failedDependency -> result(request, plan, step, StepStatus.SKIPPED, "dependency_not_succeeded")
                    descriptor == null || !descriptor.available ->
                        result(request, plan, step, StepStatus.UNAVAILABLE, "capability_unavailable")
                    executorsByCapability[step.capability] == null ->
                        result(request, plan, step, StepStatus.UNAVAILABLE, "executor_not_registered")
                    else -> try {
                        val output = executorsByCapability.getValue(step.capability).execute(request, plan, step)
                        ExecutionResult(
                            requestId = request.requestId,
                            taskId = plan.taskId,
                            stepId = step.stepId,
                            capability = step.capability,
                            status = StepStatus.SUCCEEDED,
                            output = output.values,
                            evidence = output.evidence
                        )
                    } catch (_: Exception) {
                        result(request, plan, step, StepStatus.FAILED, "capability_execution_failed")
                    }
                }
                completed[step.stepId] = result
                pending.remove(step)
            }
        }

        val results = completed.values.toList()
        val succeededCount = results.count { it.status == StepStatus.SUCCEEDED }
        val taskStatus = when {
            succeededCount == results.size -> TaskStatus.SUCCEEDED
            succeededCount > 0 -> TaskStatus.PARTIALLY_SUCCEEDED
            else -> TaskStatus.FAILED
        }
        return TaskExecutionReport(plan.taskId, taskStatus, results)
    }

    private fun result(
        request: AssistantRequest,
        plan: AssistantPlan,
        step: PlannedStep,
        status: StepStatus,
        errorCode: String
    ) = ExecutionResult(
        requestId = request.requestId,
        taskId = plan.taskId,
        stepId = step.stepId,
        capability = step.capability,
        status = status,
        errorCode = errorCode
    )
}

data class CapabilityExecutorBinding(
    val capabilityId: String,
    val executor: CapabilityExecutor
)
