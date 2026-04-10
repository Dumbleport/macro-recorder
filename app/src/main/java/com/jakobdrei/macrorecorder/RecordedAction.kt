package com.jakobdrei.macrorecorder

data class RecordedAction(
    val x: Float,
    val y: Float,
    val x2: Float = -1f,
    val y2: Float = -1f,
    val type: ActionType = ActionType.TAP,
    val relativeMs: Long = 0L,
    val duration: Long = 50L,
    val text: String = ""
)

enum class ActionType { TAP, LONG_PRESS, SWIPE, TEXT_INPUT }
