package com.jakobdrei.macrorecorder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_overlay_permission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_start_overlay).setOnClickListener {
            if (canDrawOverlays() && isAccEnabled()) {
                startForegroundService(Intent(this, OverlayService::class.java))
                finish()
            }
        }
    }

    override fun onResume() { super.onResume(); updateStatus() }

    private fun updateStatus() {
        val o = canDrawOverlays(); val a = isAccEnabled()
        findViewById<TextView>(R.id.tv_status).text =
            "${if (o) "✅" else "❌"}  Overlay-Berechtigung\n" +
            "${if (a) "✅" else "❌"}  Barrierefreiheit\n\n" +
            if (o && a) "Alles bereit! Tippe 'Overlay starten'."
            else "Bitte beide Berechtigungen erteilen."
        findViewById<Button>(R.id.btn_start_overlay).isEnabled = o && a
    }

    private fun canDrawOverlays() = Settings.canDrawOverlays(this)

    private fun isAccEnabled(): Boolean {
        val svc = "$packageName/${MacroAccessibilityService::class.java.canonicalName}"
        return try {
            if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1) {
                val s = Settings.Secure.getString(contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
                val sp = TextUtils.SimpleStringSplitter(':').also { it.setString(s) }
                while (sp.hasNext()) if (sp.next().equals(svc, true)) return true
            }
            false
        } catch (_: Exception) { false }
    }
}
