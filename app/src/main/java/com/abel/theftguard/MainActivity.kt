package com.abel.theftguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var readinessSummary: TextView
    private lateinit var readinessRows: LinearLayout
    private lateinit var pinInput: EditText
    private lateinit var controllerInput: EditText
    private lateinit var backupInput: EditText

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val bgLocLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val ink = Color.rgb(18, 32, 52)
    private val muted = Color.rgb(103, 119, 138)
    private val canvas = Color.rgb(244, 247, 251)
    private val green = Color.rgb(24, 137, 104)
    private val red = Color.rgb(188, 67, 66)
    private val line = Color.rgb(229, 235, 242)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = canvas
        window.navigationBarColor = canvas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(36))
        }
        content.addView(heroCard())
        content.addView(sectionTitle("Your protection", "Complete setup before relying on remote commands"))
        content.addView(readinessCard())
        content.addView(sectionTitle("Owner security", "Only the owner should know the PIN"))
        content.addView(securityCard())
        content.addView(sectionTitle("Quick setup", "Android needs these permissions to do its job"))
        content.addView(permissionsCard())
        content.addView(sectionTitle("How to use it", "Send these commands to this phone"))
        content.addView(commandsCard())
        content.addView(sectionTitle("Try it safely", "A local test makes sound and flashes the torch"))
        content.addView(testCard())
        content.addView(TextView(this).apply {
            text = "Theft Guard is an MVP. Set your authorized controller number, test it on your own phone, and keep your backup number current."
            setTextColor(muted)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(18), dp(8), dp(4))
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(canvas)
            addView(content)
        })
    }

    override fun onResume() {
        super.onResume()
        if (::readinessRows.isInitialized) refresh()
    }

    private fun heroCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(22), dp(22), dp(22))
        background = rounded(Color.rgb(22, 42, 67), 24)

        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "TG"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(41, 68, 98), 14)
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            })
            addView(TextView(this@MainActivity).apply {
                text = "THEFT GUARD"
                textSize = 12f
                letterSpacing = 0.12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(183, 205, 224))
                setPadding(dp(12), 0, 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "PERSONAL DEVICE"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(201, 235, 222))
                background = rounded(Color.rgb(37, 81, 76), 20)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    leftMargin = dp(8)
                }
            })
        })
        addView(TextView(this@MainActivity).apply {
            text = "Your phone,\nwithin reach."
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setLineSpacing(dp(1).toFloat(), 1f)
            setPadding(0, dp(22), 0, dp(8))
        })
        addView(TextView(this@MainActivity).apply {
            text = "Use an authorized SMS command to ring this phone, flash its torch, and request its location."
            textSize = 14f
            setTextColor(Color.rgb(203, 215, 228))
            setLineSpacing(dp(3).toFloat(), 1f)
        })
    }

    private fun sectionTitle(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(2), dp(24), dp(2), dp(12))
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        addView(TextView(this@MainActivity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun readinessCard() = card().apply {
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "SETUP STATUS"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setTextColor(muted)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            readinessSummary = TextView(this@MainActivity).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = rounded(Color.rgb(238, 241, 245), 20)
            }
            addView(readinessSummary)
        })
        readinessRows = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        addView(readinessRows)
    }

    private fun securityCard() = card().apply {
        addView(fieldLabel("Secret PIN"))
        pinInput = EditText(this@MainActivity).apply {
            hint = "At least 6 characters"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(247, 249, 252), 12, line)
        }
        addView(pinInput, fullWidthTop(dp(8)))
        addView(TextView(this@MainActivity).apply {
            text = "This PIN is required in incoming commands and to stop lost mode."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(14))
        })
        addView(primaryButton("Save PIN") {
            val pin = pinInput.text.toString().trim()
            if (pin.length < 6 || pin.any { it.isWhitespace() }) {
                toast("PIN must be 6+ characters with no spaces")
            } else {
                Prefs.setPin(this@MainActivity, pin)
                pinInput.setText("")
                toast("PIN saved")
                refresh()
            }
        })

        addView(fieldLabel("Authorized controller number"), fullWidthTop(dp(18)))
        controllerInput = EditText(this@MainActivity).apply {
            hint = "+254..."
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(247, 249, 252), 12, line)
            setText(Prefs.controllerNumber(this@MainActivity) ?: "")
        }
        addView(controllerInput, fullWidthTop(dp(8)))
        addView(TextView(this@MainActivity).apply {
            text = "Only commands from this number are accepted. Enter the full international number, including + and country code."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(14))
        })
        addView(secondaryButton("Save controller number") {
            val number = controllerInput.text.toString().trim()
            val isE164 = number.matches(Regex("\\+[1-9][0-9]{7,14}"))
            if (!isE164) {
                toast("Use international format, e.g. +254712345678")
            } else {
                Prefs.setControllerNumber(this@MainActivity, number)
                toast("Controller number saved")
                refresh()
            }
        })

        addView(fieldLabel("Backup location number"), fullWidthTop(dp(18)))
        backupInput = EditText(this@MainActivity).apply {
            hint = "Include country code, e.g. +251..."
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(247, 249, 252), 12, line)
            setText(Prefs.backupNumber(this@MainActivity) ?: "")
        }
        addView(backupInput, fullWidthTop(dp(8)))
        addView(TextView(this@MainActivity).apply {
            text = "Location replies are also sent here. Only use a number you control."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(14))
        })
        addView(secondaryButton("Save backup number") {
            Prefs.setBackupNumber(this@MainActivity, backupInput.text.toString())
            toast("Saved")
        })
    }

    private fun permissionsCard() = card().apply {
        addView(permissionAction("Core permissions", "SMS, location, torch, notifications") {
            val perms = mutableListOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            )
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            permLauncher.launch(perms.toTypedArray())
        })
        addView(divider())
        addView(permissionAction("Background location", "Needed to reply while the app is closed") {
            if (Build.VERSION.SDK_INT >= 29) {
                bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                toast("This Android version does not need this permission")
            }
        })
        addView(divider())
        addView(permissionAction("Display over other apps", "Show the lost-phone screen") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        addView(divider())
        addView(permissionAction("Battery optimization", "Let alerts work in the background") {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        })
        addView(TextView(this@MainActivity).apply {
            text = "A regular Android app cannot switch mobile data on remotely. Location uses GPS and is sent by SMS, so data is not required; the phone still needs cellular service for SMS."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(14), 0, 0)
        })
        addView(TextView(this@MainActivity).apply {
            text = "Some Android 13+ devices require allowing restricted settings before SMS permissions can be granted."
            textSize = 11f
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun commandsCard() = card().apply {
        addView(commandRow("/find YOURPIN", "Loud alarm, torch flash, lost screen, location SMS every minute (up to 30)"))
        addView(divider())
        addView(commandRow("/where YOURPIN", "Send location quietly"))
        addView(divider())
        addView(commandRow("/stop YOURPIN", "Stop an active alert"))
        addView(TextView(this@MainActivity).apply {
            text = "Send commands from your saved controller number. Replace YOURPIN with your secret PIN. Location texts use SMS and may incur carrier charges."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(14), 0, 0)
        })
        addView(TextView(this@MainActivity).apply {
            text = "Security note: both the saved controller number and PIN are required. SMS is not end-to-end encrypted, so use a unique PIN and keep it private."
            textSize = 11f
            setTextColor(red)
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun testCard() = card().apply {
        addView(TextView(this@MainActivity).apply {
            text = "The local test sounds the alarm and flashes the torch, but sends no location SMS. Stop it by entering your PIN on the lost screen."
            textSize = 13f
            setTextColor(muted)
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        addView(primaryButton("Test lost mode", top = dp(14)) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Start a local test?")
                .setMessage("This phone will sound an alarm and flash the torch. The test will not send location texts. Make sure you can stop it with your PIN.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start test") { _, _ ->
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        Intent(this@MainActivity, GuardService::class.java)
                            .setAction(GuardService.ACTION_FIND)
                            .putExtra(GuardService.EXTRA_MESSAGE, "TEST — enter your PIN to stop")
                            .putExtra(GuardService.EXTRA_LOCAL_TEST, true)
                    )
                }
                .show()
        })
        addView(secondaryButton("Stop active alert", top = dp(8)) {
            startService(Intent(this@MainActivity, GuardService::class.java).setAction(GuardService.ACTION_STOP))
        })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 18, line)
        elevation = dp(1).toFloat()
    }

    private fun permissionAction(title: String, detail: String, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(ink)
                })
                addView(TextView(this@MainActivity).apply {
                    text = detail
                    textSize = 11f
                    setTextColor(muted)
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "OPEN  ›"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(green)
            })
        }

    private fun commandRow(command: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        addView(TextView(this@MainActivity).apply {
            text = command
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(green)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(239, 247, 244), 10)
        })
        addView(TextView(this@MainActivity).apply {
            text = detail
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun fieldLabel(label: String) = TextView(this).apply {
        text = label
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(ink)
    }

    private fun primaryButton(label: String, top: Int = 0, onClick: () -> Unit) =
        actionButton(label, green, Color.WHITE, top, onClick)

    private fun secondaryButton(label: String, top: Int = 0, onClick: () -> Unit) =
        actionButton(label, Color.rgb(238, 243, 248), ink, top, onClick)

    private fun actionButton(label: String, color: Int, textColor: Int, top: Int, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(textColor)
            background = rounded(color, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(48)
            setOnClickListener { onClick() }
            layoutParams = fullWidthTop(top)
        }

    private fun divider() = android.view.View(this).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        )
    }

    private fun fullWidthTop(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun refresh() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val checks = listOf(
            "Owner PIN" to Prefs.hasPin(this),
            "Authorized controller" to (Prefs.controllerNumber(this) != null),
            "SMS access" to (granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.SEND_SMS)),
            "Torch access" to granted(Manifest.permission.CAMERA),
            "Precise location" to granted(Manifest.permission.ACCESS_FINE_LOCATION),
            "Background location" to (Build.VERSION.SDK_INT < 29 ||
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
            "Lost screen overlay" to Settings.canDrawOverlays(this),
            "Background battery access" to pm.isIgnoringBatteryOptimizations(packageName),
            "Notifications" to (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS))
        )
        val ready = checks.count { it.second }
        readinessSummary.text = "$ready / ${checks.size} READY"
        val complete = ready == checks.size
        readinessSummary.setTextColor(if (complete) green else red)
        readinessSummary.background = rounded(
            if (complete) Color.rgb(232, 246, 240) else Color.rgb(252, 241, 238), 20
        )
        readinessRows.removeAllViews()
        checks.forEachIndexed { index, check ->
            readinessRows.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                addView(TextView(this@MainActivity).apply {
                    text = check.first
                    textSize = 13f
                    setTextColor(ink)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = if (check.second) "READY" else "NEEDED"
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (check.second) green else red)
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = rounded(
                        if (check.second) Color.rgb(232, 246, 240) else Color.rgb(252, 241, 238), 16
                    )
                })
            })
            if (index != checks.lastIndex) readinessRows.addView(divider())
        }
    }
}
