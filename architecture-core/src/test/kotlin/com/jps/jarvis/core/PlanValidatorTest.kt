package com.jps.jarvis.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanValidatorTest {
    private val validator = PlanValidator(CapabilityRegistry.initial())
    private fun request(ids: Set<String> = setOf("weather.current", "assistant.answer")) =
        AssistantRequest(requestId="req-1", sessionId="session-1", inputText="test", advertisedCapabilities=ids)
    private fun plan(steps: List<PlannedStep>) =
        AssistantPlan(requestId="req-1", taskId="task-1", summary="Test plan", steps=steps)

    @Test fun acceptsKnownAdvertisedCapability() {
        assertTrue(validator.validate(request(), plan(listOf(PlannedStep("s1","weather.current")))).isValid)
    }
    @Test fun rejectsUnknownCapability() {
        val r=validator.validate(request(),plan(listOf(PlannedStep("s1","android.delete_everything"))))
        assertFalse(r.isValid); assertTrue("unknown_capability:android.delete_everything" in r.errors)
    }
    @Test fun rejectsCapabilityNotAdvertised() {
        val r=validator.validate(request(emptySet()),plan(listOf(PlannedStep("s1","weather.current"))))
        assertTrue("capability_not_advertised:weather.current" in r.errors)
    }
    @Test fun rejectsUnavailableCapability() {
        val r=validator.validate(request(setOf("navigation.route")),plan(listOf(PlannedStep("s1","navigation.route"))))
        assertTrue("capability_unavailable:navigation.route" in r.errors)
    }
    @Test fun rejectsMissingDependencyAndCycle() {
        val missing=validator.validate(request(),plan(listOf(PlannedStep("s1","weather.current",dependsOn=listOf("absent")))))
        assertTrue(missing.errors.any { it.startsWith("missing_dependency") })
        val cycle=validator.validate(request(),plan(listOf(
            PlannedStep("s1","weather.current",dependsOn=listOf("s2")),
            PlannedStep("s2","assistant.answer",dependsOn=listOf("s1"))
        )))
        assertTrue("dependency_cycle" in cycle.errors)
    }
    @Test fun rejectsUnsupportedSchemaAndRequestMismatch() {
        val r=validator.validate(request(),plan(emptyList()).copy(schemaVersion="99.0",requestId="other"))
        assertTrue("unsupported_schema_version" in r.errors); assertTrue("request_id_mismatch" in r.errors)
    }
    @Test fun rejectsExcessiveStepsAndInputSize() {
        val many=(1..9).map { PlannedStep("s${it}","weather.current") }
        assertTrue("too_many_steps" in validator.validate(request(),plan(many)).errors)
        val huge=validator.validate(request(),plan(listOf(PlannedStep("s1","weather.current",mapOf("query" to "x".repeat(3000))))))
        assertTrue("step_input_too_large:s1" in huge.errors)
    }
    @Test fun rejectsPreciseLocationMode() {
        val r=validator.validate(request().copy(locationMode="precise_gps"),plan(emptyList()))
        assertTrue("unsupported_location_mode" in r.errors)
    }
}
