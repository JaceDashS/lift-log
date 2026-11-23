package com.example.workouttimer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.workouttimer.OverlayService
import com.example.workouttimer.R
import com.example.workouttimer.data.WorkoutStore

/** Launcher screen. START floats the workout timer and its controls over other apps. */
class HomeActivity : AppCompatActivity() {
    private val store by lazy { WorkoutStore.get(this) }
    private var permissionRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        permissionRequested = savedInstanceState?.getBoolean("overlay_permission_requested") ?: false

        findViewById<TextView>(R.id.button_home_start).setOnClickListener { start() }
        findViewById<TextView>(R.id.button_home_end).setOnClickListener { confirmEnd() }
        findViewById<TextView>(R.id.button_home_records).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderHome()
        if (permissionRequested && Settings.canDrawOverlays(this)) {
            permissionRequested = false
            start()
        }
    }

    private fun renderHome() {
        val last = store.sessions().lastOrNull { it.sets.isNotEmpty() }
        findViewById<TextView>(R.id.text_home_last_date).text =
            (last?.date?.toString() ?: getString(R.string.empty_records)) +
                if (store.testMode) getString(R.string.test_suffix) else ""
        findViewById<TextView>(R.id.text_home_last_summary).text = last?.let {
            getString(R.string.home_summary_format, it.sets.map { set -> set.exercise }.distinct().joinToString(" · "), it.sets.size)
        } ?: getString(R.string.home_empty_hint)
        val active = store.active() != null
        findViewById<TextView>(R.id.button_home_start)
            .setText(if (active) R.string.home_resume else R.string.home_start_workout)
        findViewById<TextView>(R.id.button_home_end).isVisible = active
    }

    private fun confirmEnd() {
        val test = store.testMode
        AlertDialog.Builder(this)
            .setTitle(R.string.end_title)
            .setMessage(R.string.home_end_workout_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.home_end_workout) { _, _ ->
                try {
                    store.end(test)
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error_storage, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_RELOAD_SESSION))
                renderHome()
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("overlay_permission_requested", permissionRequested)
        super.onSaveInstanceState(outState)
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            permissionRequested = true
            Toast.makeText(this, R.string.home_overlay_permission, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        try {
            store.start(ready = true)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_storage, Toast.LENGTH_LONG).show()
            return
        }
        val displayId = window.decorView.display?.displayId ?: Display.DEFAULT_DISPLAY
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START_SERVICE
            putExtra(OverlayService.EXTRA_DISPLAY_ID, displayId)
        })
        renderHome()
        if (displayId == Display.DEFAULT_DISPLAY) {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
