package com.jakobdrei.macrorecorder

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "macro_ch"
        const val NOTIF_ID   = 1001
        const val ACTION_STOP = "com.jakobdrei.macrorecorder.STOP_SERVICE"
    }

    private lateinit var wm: WindowManager
    private lateinit var root: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvStatus: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var tvLoops: TextView

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        setupOverlay()
        MacroManager.onStateChanged = { root.post { updateUi() } }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::root.isInitialized) wm.removeView(root)
        MacroManager.onStateChanged = null
    }

    private fun setupOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 200 }
        wm.addView(root, params)

        tvStatus  = root.findViewById(R.id.tv_status)
        btnRecord = root.findViewById(R.id.btn_record)
        btnPlay   = root.findViewById(R.id.btn_play)
        btnStop   = root.findViewById(R.id.btn_stop)
        tvLoops   = root.findViewById(R.id.tv_loop_count)

        setupDrag()
        setupButtons()
        updateUi()
    }

    private fun setupDrag() {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = e.rawX; ty = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (e.rawX - tx).toInt()
                    params.y = iy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(root, params); true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        root.findViewById<Button>(R.id.btn_close).setOnClickListener { stopSelf() }

        btnRecord.setOnClickListener {
            if (!MacroManager.isRecording) MacroManager.startRecording(this)
        }
        btnStop.setOnClickListener {
            when {
                MacroManager.isRecording -> MacroManager.stopRecording(this)
                MacroManager.isPlaying   -> MacroManager.stopPlayback(this)
            }
        }
        btnPlay.setOnClickListener {
            if (!MacroManager.isPlaying && !MacroManager.isRecording && MacroManager.hasRecording())
                MacroManager.startPlayback(this)
        }
        root.findViewById<Button>(R.id.btn_loop_minus).setOnClickListener {
            if (MacroManager.loopCount > 0) { MacroManager.loopCount--; updateUi() }
        }
        root.findViewById<Button>(R.id.btn_loop_plus).setOnClickListener {
            MacroManager.loopCount++; updateUi()
        }
    }

    private fun updateUi() {
        tvLoops.text = if (MacroManager.loopCount == 0) "∞" else MacroManager.loopCount.toString()
        when {
            MacroManager.isRecording -> {
                tvStatus.text = "● Aufnahme läuft…"
                tvStatus.setTextColor(Color.RED)
                btnRecord.isEnabled = false
                btnPlay.isEnabled   = false
                btnStop.isEnabled   = true
                setOverlayTouchable(true)
            }
            MacroManager.isPlaying -> {
                tvStatus.text = "▶ Wiedergabe…"
                tvStatus.setTextColor(Color.GREEN)
                btnRecord.isEnabled = false
                btnPlay.isEnabled   = false
                btnStop.isEnabled   = true
                setOverlayTouchable(false)
            }
            MacroManager.hasRecording() -> {
                val n = MacroManager.recordedActions.size
                tvStatus.text = "$n Aktionen aufgezeichnet"
                tvStatus.setTextColor(Color.WHITE)
                btnRecord.isEnabled = true
                btnPlay.isEnabled   = true
                btnStop.isEnabled   = false
                setOverlayTouchable(true)
            }
            else -> {
                tvStatus.text = "Bereit – tippe Aufnehmen"
                tvStatus.setTextColor(Color.LTGRAY)
                btnRecord.isEnabled = true
                btnPlay.isEnabled   = false
                btnStop.isEnabled   = false
                setOverlayTouchable(true)
            }
        }
    }

    private fun setOverlayTouchable(touchable: Boolean) {
        if (!::params.isInitialized) return
        params.flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    private fun startFg() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Macro Recorder", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Macro Recorder aktiv")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(0, "Beenden", stopIntent)
            .build()
        )
    }
}