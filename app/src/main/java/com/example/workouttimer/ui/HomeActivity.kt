package com.example.workouttimer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.workouttimer.R

/** Launcher screen: start a workout, open records or settings. */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.text_home_last_date).text = PreviewData.LAST_SESSION_DATE
        findViewById<TextView>(R.id.text_home_last_summary).text = PreviewData.LAST_SESSION_SUMMARY
        findViewById<TextView>(R.id.button_home_start).setOnClickListener {
            startActivity(Intent(this, SessionActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_records).setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        findViewById<TextView>(R.id.button_home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
