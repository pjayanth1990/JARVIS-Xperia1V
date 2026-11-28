package com.jps.jarvis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanExecutionEngineTest {
    private fun request(ids: Set<String> = setOf("weather.current", "assistant.answer", "image.generate")) =
        AssistantRequest(
            requestId = "req-1",
            sessionId = "session-1",
            inputText = "Check weather and summarise it",
            advertisedCapabilities = ids
        )

    private fun plan(steps: List<PlannedStep>) =
        AssistantPlan(requestId = "req-1", taskId = "task-1", summary = "Test plan", steps = steps)

    @Test fun executesDependenciesInOrderEvenWhenPlanIsNotOrdered() {
        val executionOrder = mutableListOf<String>()
        val engine = PlanExecutionEngine(
            CapabilityRegistry.initial(),
            listOf(
                CapabilityExecutorBinding("assistant.answer", CapabilityExecutor { _, _, step ->
                    executionOrder += step.stepId
                    CapabilityExecutionOutput(mapOf("answer" to "It is clear"), listOf("verified-result"))
                }),
                CapabilityExecutorBinding("weather.current", CapabilityExecutor { _, _, step ->
                    executionOrder += step.stepId
                    CapabilityExecutionOutput(mapOf("temperature_c" to "24"))
                })
            )
        )
        val report = engine.execute(
            request(),
            plan(listOf(
                PlannedStep("s2", "assistant.answer", dependsOn = listOf("s1")),
                PlannedStep("s1", "weather.current")
            ))
        )

        assertEquals(TaskStatus.SUCCEEDED, report.status)
        assertEquals(listOf("s1", "s2"), executionOrder)
        assertEquals("24", report.results.first { it.stepId == "s1" }.output["temperature_c"])
        assertEquals(listOf("verified-result"), report.results.first { it.stepId == "s2" }.evidence)
    }

    @Test fun skipsDependentStepAfterFailureButContinuesIndependentWork() {
        val engine = PlanExecutionEngine(
            CapabilityRegistry.initial(),
            listOf(
                CapabilityExecutorBinding("weather.current", CapabilityExecutor { _, _, _ ->
                    error("simulated adapter failure")
                }),
                CapabilityExecutorBinding("assistant.answer", CapabilityExecutor { _, _, _ ->
                    CapabilityExecutionOutput(mapOf("status" to "independent complete"))
                })
            )
        )
        val report = engine.execute(
            request(),
            plan(listOf(
                PlannedStep("weather", "weather.current"),
                PlannedStep("summary", "assistant.answer", dependsOn = listOf("weather")),
                PlannedStep("independent", "assistant.answer")
            ))
        )

        assertEquals(TaskStatus.PARTIALLY_SUCCEEDED, report.status)
        assertEquals(StepStatus.FAILED, report.results.first { it.stepId == "weather" }.status)
        assertEquals(StepStatus.SKIPPED, report.results.first { it.stepId == "summary" }.status)
        assertEquals(StepStatus.SUCCEEDED, report.results.first { it.stepId == "independent" }.status)
    }

    @Test fun refusesToExecuteUntilRequiredApprovalIsGranted() {
        val registry = CapabilityRegistry(
            listOf(CapabilityDescriptor("device.action", owner = ExecutionOwner.ANDROID, requiresApproval = true))
        )
        var executions = 0
        val engine = PlanExecutionEngine(
            registry,
            listOf(CapabilityExecutorBinding("device.action", CapabilityExecutor { _, _, _ ->
                executions++
                CapabilityExecutionOutput()
            }))
        )
        val req = request(setOf("device.action"))
        val task = plan(listOf(PlannedStep("action", "device.action", requiresApproval = true)))

        val waiting = engine.execute(req, task)
        assertEquals(TaskStatus.AWAITING_APPROVAL, waiting.status)
        assertEquals(0, executions)

        val approved = engine.execute(req, task, approvedStepIds = setOf("action"))
        assertEquals(TaskStatus.SUCCEEDED, approved.status)
        assertEquals(1, executions)
    }

    @Test fun reportsMissingAdapterAsUnavailableWithoutInventingSuccess() {
        val engine = PlanExecutionEngine(CapabilityRegistry.initial(), emptyList())
        val report = engine.execute(request(), plan(listOf(PlannedStep("s1", "weather.current"))))

        assertEquals(TaskStatus.FAILED, report.status)
        assertEquals(StepStatus.UNAVAILABLE, report.results.single().status)
        assertEquals("executor_not_registered", report.results.single().errorCode)
    }

    @Test fun rejectsInvalidPlanBeforeAnyAdapterRuns() {
        var executions = 0
        val engine = PlanExecutionEngine(
            CapabilityRegistry.initial(),
            listOf(CapabilityExecutorBinding("weather.current", CapabilityExecutor { _, _, _ ->
                executions++
                CapabilityExecutionOutput()
            }))
        )
        val report = engine.execute(
            request(),
            plan(listOf(PlannedStep("s1", "weather.current", dependsOn = listOf("missing"))))
        )

        assertEquals(TaskStatus.FAILED, report.status)
        assertTrue(report.validationErrors.any { it.startsWith("missing_dependency") })
        assertEquals(0, executions)
    }

    @Test fun returnsNeedsInputWithoutRunningSteps() {
        var executions = 0
        val engine = PlanExecutionEngine(
            CapabilityRegistry.initial(),
            listOf(CapabilityExecutorBinding("weather.current", CapabilityExecutor { _, _, _ ->
                executions++
                CapabilityExecutionOutput()
            }))
        )
        val report = engine.execute(
            request(),
            plan(emptyList()).copy(needsUserInput = true, clarification = "Which city?")
        )

        assertEquals(TaskStatus.NEEDS_INPUT, report.status)
        assertEquals(0, executions)
    }
}
