package com.abel.theftguard

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/** Full-screen "this phone is lost" screen. Shows over the lock screen; only the PIN stops the alarm. */
class LostActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var current: LostActivity? = null
    }

    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B00020"))
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "THIS PHONE IS LOST"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        messageView = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        root.addView(messageView)

        val pin = EditText(this).apply {
            hint = "Owner PIN"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(pin)
        root.addView(Button(this).apply {
            text = "Stop alarm"
            setOnClickListener {
                if (Prefs.checkPin(this@LostActivity, pin.text.toString().trim())) {
                    startService(
                        Intent(this@LostActivity, GuardService::class.java).setAction(GuardService.ACTION_STOP)
                    )
                    finish()
                } else {
                    Toast.makeText(this@LostActivity, "Wrong PIN", Toast.LENGTH_SHORT).show()
                }
            }
        })
        setContentView(root)
        messageView.text = intent.getStringExtra(GuardService.EXTRA_MESSAGE) ?: ""

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* blocked */ }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        messageView.text = intent.getStringExtra(GuardService.EXTRA_MESSAGE) ?: ""
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }
}
