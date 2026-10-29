package com.example.workouttimer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var startWorkoutButton: Button
    private lateinit var devModeButton: Button
    private lateinit var resetAllSettingsButton: Button
    private lateinit var clearLogButton: Button
    private lateinit var overlayPreferences: OverlayPreferences
    private var permissionRequestLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        overlayPreferences = OverlayPreferences(applicationContext)

        startWorkoutButton = findViewById(R.id.button_start_workout)
        devModeButton = findViewById(R.id.button_dev_mode)
        resetAllSettingsButton = findViewById(R.id.button_reset_all_settings)
        clearLogButton = findViewById(R.id.button_clear_log)
        startWorkoutButton.setOnClickListener {
            ensureOverlayAndStart()
        }
        devModeButton.setOnClickListener {
            startDevMode()
        }
        resetAllSettingsButton.setOnClickListener {
            resetAllSettings()
        }
        clearLogButton.setOnClickListener {
            clearSelectionDebugLog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionRequestLaunched && Settings.canDrawOverlays(this)) {
            permissionRequestLaunched = false
            startOverlayService()
        }
    }

    private fun ensureOverlayAndStart() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            return
        }

        permissionRequestLaunched = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START_SERVICE
        }
        ContextCompat.startForegroundService(this, intent)
        moveToHomeScreen()
    }

    private fun startDevMode() {
        val intent = Intent(this, WorkoutActivity::class.java)
        startActivity(intent)
    }

    private fun moveToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun resetAllSettings() {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_END_SESSION
        }
        startService(stopIntent)

        lifecycleScope.launch {
            overlayPreferences.clearAll()
            Toast.makeText(
                this@MainActivity,
                getString(R.string.all_settings_reset),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun clearSelectionDebugLog() {
        val path = SelectionDebugLogger.clear(this)
        Toast.makeText(this, "Cleared: $path", Toast.LENGTH_SHORT).show()
    }
}
