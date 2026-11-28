package com.jps.jarvis.core

sealed class TransitionResult {
    data class Accepted(val status: TaskStatus) : TransitionResult()
    data class Rejected(val from: TaskStatus, val to: TaskStatus) : TransitionResult()
}

object TaskStateMachine {
    private val transitions = mapOf(
        TaskStatus.PLANNED to setOf(TaskStatus.NEEDS_INPUT, TaskStatus.AWAITING_APPROVAL, TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED),
        TaskStatus.NEEDS_INPUT to setOf(TaskStatus.PLANNED, TaskStatus.CANCELLED),
        TaskStatus.AWAITING_APPROVAL to setOf(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED),
        TaskStatus.RUNNING to setOf(TaskStatus.SUCCEEDED, TaskStatus.PARTIALLY_SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED)
    )
    fun transition(from: TaskStatus, to: TaskStatus): TransitionResult =
        if (to in transitions[from].orEmpty()) TransitionResult.Accepted(to)
        else TransitionResult.Rejected(from, to)
    fun isTerminal(status: TaskStatus) = status in setOf(
        TaskStatus.SUCCEEDED, TaskStatus.PARTIALLY_SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED
    )
}
