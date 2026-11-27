package com.jps.jarvis.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskStateMachineTest {
    @Test fun permitsNormalLifecycle() {
        assertEquals(TransitionResult.Accepted(TaskStatus.RUNNING),TaskStateMachine.transition(TaskStatus.PLANNED,TaskStatus.RUNNING))
        assertEquals(TransitionResult.Accepted(TaskStatus.SUCCEEDED),TaskStateMachine.transition(TaskStatus.RUNNING,TaskStatus.SUCCEEDED))
    }
    @Test fun permitsApprovalAndClarification() {
        assertTrue(TaskStateMachine.transition(TaskStatus.PLANNED,TaskStatus.AWAITING_APPROVAL) is TransitionResult.Accepted)
        assertTrue(TaskStateMachine.transition(TaskStatus.AWAITING_APPROVAL,TaskStatus.RUNNING) is TransitionResult.Accepted)
        assertTrue(TaskStateMachine.transition(TaskStatus.PLANNED,TaskStatus.NEEDS_INPUT) is TransitionResult.Accepted)
        assertTrue(TaskStateMachine.transition(TaskStatus.NEEDS_INPUT,TaskStatus.PLANNED) is TransitionResult.Accepted)
    }
    @Test fun rejectsTerminalMutation() {
        assertTrue(TaskStateMachine.transition(TaskStatus.SUCCEEDED,TaskStatus.RUNNING) is TransitionResult.Rejected)
        assertTrue(TaskStateMachine.isTerminal(TaskStatus.CANCELLED))
        assertTrue(TaskStateMachine.isTerminal(TaskStatus.PARTIALLY_SUCCEEDED))
    }
}
