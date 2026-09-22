package com.bolke.keyboard

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        findViewById<View>(R.id.btn_privacy_back).setOnClickListener { finish() }
    }
}
