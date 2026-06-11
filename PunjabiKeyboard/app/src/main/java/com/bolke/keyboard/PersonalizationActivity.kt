package com.bolke.keyboard

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bolke.keyboard.util.PreferencesManager

/**
 * Personalization activity to manage Quick Replies and Word Shortcuts (Slang).
 */
class PersonalizationActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private val quickRepliesList = ArrayList<Pair<String, String>>()
    private val slangMappingsList = ArrayList<Pair<String, String>>()

    private lateinit var qrListContainer: LinearLayout
    private lateinit var slangListContainer: LinearLayout
    private lateinit var editNewQRPunjabi: EditText
    private lateinit var editNewQRPunglish: EditText
    private lateinit var editNewSlangTarget: EditText
    private lateinit var editNewSlangReplacement: EditText

    override fun attachBaseContext(newBase: Context) {
        val prefs = PreferencesManager(newBase)
        val scale = prefs.appScale
        val config = newBase.resources.configuration
        config.fontScale = scale
        val metrics = newBase.resources.displayMetrics
        config.densityDpi = (metrics.densityDpi * scale).toInt()
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personalization)

        prefsManager = PreferencesManager(this)

        qrListContainer = findViewById(R.id.qr_list_container)
        slangListContainer = findViewById(R.id.slang_list_container)
        editNewQRPunjabi = findViewById(R.id.new_qr_punjabi)
        editNewQRPunglish = findViewById(R.id.new_qr_punglish)
        editNewSlangTarget = findViewById(R.id.new_slang_target)
        editNewSlangReplacement = findViewById(R.id.new_slang_replacement)

        findViewById<View>(R.id.btn_add_qr).setOnClickListener { addQuickReply() }
        findViewById<View>(R.id.btn_add_slang).setOnClickListener { addSlangMapping() }
        findViewById<View>(R.id.btn_reset_qr).setOnClickListener { resetQuickReplyDefaults() }
        findViewById<View>(R.id.btn_reset_slang).setOnClickListener { resetSlangDefaults() }
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        loadData()
    }

    private fun loadData() {
        // Load Quick Replies
        quickRepliesList.clear()
        prefsManager.quickReplies.split("\n").filter { it.isNotBlank() }.forEach {
            val parts = it.split("|")
            if (parts.size == 2) quickRepliesList.add(Pair(parts[0], parts[1]))
        }
        refreshQRUI()

        // Load Slang Mappings
        slangMappingsList.clear()
        prefsManager.slangMappings.split("\n").filter { it.isNotBlank() }.forEach {
            val parts = it.split(":")
            if (parts.size == 2) slangMappingsList.add(Pair(parts[0], parts[1]))
        }
        refreshSlangUI()
    }

    private fun saveData() {
        prefsManager.quickReplies = quickRepliesList.joinToString("\n") { "${it.first}|${it.second}" }
        prefsManager.slangMappings = slangMappingsList.joinToString("\n") { "${it.first}:${it.second}" }
    }

    private fun addQuickReply() {
        val punjabi = editNewQRPunjabi.text.toString().trim()
        val punglish = editNewQRPunglish.text.toString().trim()
        if (punjabi.isNotEmpty() && punglish.isNotEmpty()) {
            quickRepliesList.add(Pair(punjabi, punglish))
            editNewQRPunjabi.text.clear()
            editNewQRPunglish.text.clear()
            refreshQRUI()
            saveData()
        } else {
            Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeQuickReply(punjabi: String, punglish: String) {
        quickRepliesList.removeAll { it.first == punjabi && it.second == punglish }
        refreshQRUI()
        saveData()
    }

    private fun resetQuickReplyDefaults() {
        quickRepliesList.clear()
        val defaultReplies = "ਕਿੱਥੇ ਆਗਿਆ?|kithe aagya?\nਮੈਂ ਚੱਲ ਪਈ!|mai chalpyi!\nਪੁੱਤ ਕਿੱਥੇ ਆਂ?|putt kithe aa?\nਹਾਂਜੀ|hanji\nਨਾਜੀ|naji\nਠੀਕ ਹੈ|thik hai\nਸਤਿ ਸ੍ਰੀ ਅਕਾਲ|sat sri akal\nਕੀ ਹਾਲ ਹੈ?|ki haal hai?"
        defaultReplies.split("\n").forEach {
            val parts = it.split("|")
            if (parts.size == 2) quickRepliesList.add(Pair(parts[0], parts[1]))
        }
        refreshQRUI()
        saveData()
    }

    private fun refreshQRUI() {
        qrListContainer.removeAllViews()
        quickRepliesList.forEach { pair ->
            qrListContainer.addView(createQRRow(pair.first, pair.second))
        }
    }

    private fun createQRRow(punjabi: String, punglish: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(8.toPx(), 8.toPx(), 8.toPx(), 8.toPx())
            setBackgroundResource(R.drawable.mode_pill_bg)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 4.toPx(), 0, 4.toPx())
            layoutParams = params
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textLayout.addView(TextView(this).apply {
            text = punjabi
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        textLayout.addView(TextView(this).apply {
            text = punglish
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
        })

        val deleteBtn = TextView(this).apply {
            text = "🗑️"
            textSize = 18f
            setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
            setOnClickListener { removeQuickReply(punjabi, punglish) }
        }

        row.addView(textLayout)
        row.addView(deleteBtn)
        return row
    }

    private fun addSlangMapping() {
        val target = editNewSlangTarget.text.toString().trim()
        val replacement = editNewSlangReplacement.text.toString().trim()
        if (target.isNotEmpty() && replacement.isNotEmpty()) {
            slangMappingsList.removeAll { it.first.lowercase() == target.lowercase() }
            slangMappingsList.add(Pair(target, replacement))
            editNewSlangTarget.text.clear()
            editNewSlangReplacement.text.clear()
            refreshSlangUI()
            saveData()
        } else {
            Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeSlangMapping(target: String) {
        slangMappingsList.removeAll { it.first == target }
        refreshSlangUI()
        saveData()
    }

    private fun resetSlangDefaults() {
        slangMappingsList.clear()
        val defaultSlangs = "karo:kro\nchalo:chlo\nkarda:krda\nkardi:krdi\nkarde:krde\njaldi:jldi"
        defaultSlangs.split("\n").forEach {
            val parts = it.split(":")
            if (parts.size == 2) slangMappingsList.add(Pair(parts[0], parts[1]))
        }
        refreshSlangUI()
        saveData()
    }

    private fun refreshSlangUI() {
        slangListContainer.removeAllViews()
        slangMappingsList.forEach { pair ->
            slangListContainer.addView(createSlangRow(pair.first, pair.second))
        }
    }

    private fun createSlangRow(target: String, replacement: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(8.toPx(), 4.toPx(), 8.toPx(), 4.toPx())
        }

        row.addView(TextView(this).apply {
            text = target
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(TextView(this).apply {
            text = " ➔ "
            setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
            textSize = 12f
        })

        row.addView(TextView(this).apply {
            text = replacement
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val deleteBtn = TextView(this).apply {
            text = "🗑️"
            textSize = 16f
            setPadding(12.toPx(), 8.toPx(), 12.toPx(), 8.toPx())
            setOnClickListener { removeSlangMapping(target) }
        }

        row.addView(deleteBtn)
        return row
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()
}
