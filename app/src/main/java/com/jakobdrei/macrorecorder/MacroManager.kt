package com.jakobdrei.macrorecorder

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object MacroManager {
    var isRecording = false
        private set
    var isPlaying = false
        private set
    var loopCount = 0
    private var loopsRemaining = 0

    val recordedActions = mutableListOf<RecordedAction>()
    private var recordingStartTime = 0L
    var onStateChanged: (() -> Unit)? = null

    fun startRecording(context: Context) {
        recordedActions.clear()
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        vibrate(context, longArrayOf(0, 80, 60, 80))
        onStateChanged?.invoke()
    }

    fun stopRecording(context: Context) {
        isRecording = false
        vibrate(context, longArrayOf(0, 150))
        onStateChanged?.invoke()
    }

    fun recordTap(x: Float, y: Float, duration: Long = 50L) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(x = x, y = y, type = ActionType.TAP,
            relativeMs = System.currentTimeMillis() - recordingStartTime, duration = duration))
    }

    fun recordSwipe(x: Float, y: Float, x2: Float, y2: Float, duration: Long = 300L) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(x = x, y = y, x2 = x2, y2 = y2,
            type = ActionType.SWIPE, relativeMs = System.currentTimeMillis() - recordingStartTime, duration = duration))
    }

    fun recordLongPress(x: Float, y: Float) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(x = x, y = y, type = ActionType.LONG_PRESS,
            relativeMs = System.currentTimeMillis() - recordingStartTime, duration = 800L))
    }

    fun recordTextInput(text: String) {
        if (!isRecording) return
        recordedActions.add(RecordedAction(x = 0f, y = 0f, type = ActionType.TEXT_INPUT,
            relativeMs = System.currentTimeMillis() - recordingStartTime, text = text))
    }

    fun startPlayback(context: Context) {
        if (recordedActions.isEmpty()) return
        isPlaying = true
        loopsRemaining = if (loopCount == 0) Int.MAX_VALUE else loopCount
        onStateChanged?.invoke()
        context.sendBroadcast(Intent(MacroAccessibilityService.ACTION_START_PLAYBACK))
    }

    fun stopPlayback(context: Context) {
        isPlaying = false
        onStateChanged?.invoke()
        context.sendBroadcast(Intent(MacroAccessibilityService.ACTION_STOP_PLAYBACK))
    }

    fun onPlaybackFinished(context: Context) {
        loopsRemaining--
        if (isPlaying && loopsRemaining > 0) {
            context.sendBroadcast(Intent(MacroAccessibilityService.ACTION_START_PLAYBACK))
        } else {
            isPlaying = false
            onStateChanged?.invoke()
        }
    }

    fun hasRecording() = recordedActions.isNotEmpty()

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, pattern: LongArray) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {}
    }
}
