package com.example.workouttimer.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.MainActivity
import com.example.workouttimer.R
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings screen. UI preview only: the test mode switch shows or hides the
 * sample-record form, and nothing is saved.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val testDataPanel = findViewById<View>(R.id.panel_settings_test_data)
        val testModeSwitch = findViewById<SwitchMaterial>(R.id.switch_settings_test_mode)
        testModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            testDataPanel.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        testDataPanel.visibility = View.GONE

        findViewById<View>(R.id.row_settings_overlay_timer).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
