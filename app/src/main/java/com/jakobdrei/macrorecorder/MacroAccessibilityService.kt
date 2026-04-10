package com.jakobdrei.macrorecorder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MacroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MacroAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var stopRequested = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        stopPlayback()
    }

    override fun onInterrupt() { stopPlayback() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!MacroManager.isRecording || event == null) return
        // Ignore events from our own overlay so buttons don't get recorded
        if (event.packageName == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node = event.source ?: return
                val b = Rect(); node.getBoundsInScreen(b)
                if (!b.isEmpty) MacroManager.recordTap(b.centerX().toFloat(), b.centerY().toFloat())
                node.recycle()
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val node = event.source ?: return
                val b = Rect(); node.getBoundsInScreen(b)
                if (!b.isEmpty) MacroManager.recordLongPress(b.centerX().toFloat(), b.centerY().toFloat())
                node.recycle()
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val node = event.source ?: return
                val b = Rect(); node.getBoundsInScreen(b)
                if (!b.isEmpty) {
                    val cx = b.centerX().toFloat(); val cy = b.centerY().toFloat()
                    if (event.scrollY > 0) MacroManager.recordSwipe(cx, cy + 300f, cx, cy - 300f, 400L)
                    else MacroManager.recordSwipe(cx, cy - 300f, cx, cy + 300f, 400L)
                }
                node.recycle()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val t = event.text.joinToString("")
                if (t.isNotEmpty()) MacroManager.recordTextInput(t)
            }
        }
    }

    fun startPlayback() {
        stopRequested = false
        val actions = MacroManager.recordedActions.toList()
        if (actions.isEmpty()) { MacroManager.onPlaybackFinished(this); return }

        for (action in actions) {
            handler.postDelayed({
                if (!stopRequested) executeAction(action)
            }, action.relativeMs)
        }

        val last = actions.last().relativeMs + actions.last().duration + 300L
        handler.postDelayed({
            if (!stopRequested) MacroManager.onPlaybackFinished(this)
        }, last)
    }

    fun stopPlayback() {
        stopRequested = true
        handler.removeCallbacksAndMessages(null)
    }

    private fun executeAction(a: RecordedAction) = when (a.type) {
        ActionType.TAP        -> dispatchTap(a.x, a.y, a.duration)
        ActionType.LONG_PRESS -> dispatchTap(a.x, a.y, 800L)
        ActionType.SWIPE      -> dispatchSwipe(a.x, a.y, a.x2, a.y2, a.duration)
        ActionType.TEXT_INPUT -> injectText(a.text)
    }

    private fun dispatchTap(x: Float, y: Float, dur: Long) {
        val p = Path().apply { moveTo(x, y) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, dur.coerceIn(1, 9999)))
                .build(), null, null
        )
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, dur.coerceIn(1, 9999)))
                .build(), null, null
        )
    }

    private fun injectText(text: String) {
        val root = rootInActiveWindow ?: return
        fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.isFocused && n.isEditable) return n
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                val r = find(c); if (r != null) return r
                c.recycle()
            }
            return null
        }
        find(root)?.also {
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            })
            it.recycle()
        }
        root.recycle()
    }
}