package com.pearparinya.automovie

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var currentEp = 1
    private var currentScene = 1
    private var repairCount = 0
    private var sceneLocked = false
    private var selectedCategory: String? = null
    private var activeStep = 1
    private var storyStartTimeMs = 0L
    private var storyEndTimeMs = 0L
    private var soundEnabled = true
    private var lastSoundStep = 0
    private var shareInProgress = false
    private var lastShareAtMs = 0L
    private var awaitingChatGptReturn = false
    private lateinit var timeLabel: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val stepButtons = mutableMapOf<Int, Button>()

    private lateinit var sceneLabel: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var nextButton: Button
    private lateinit var input: EditText
    private lateinit var titlePanel: LinearLayout
    private lateinit var nextHint: TextView
    private lateinit var confirmOutputButton: Button
    private lateinit var characterMasterButton: Button
    private var pendingCharacterKey: String? = null

    private val characterNames = linkedMapOf(
        "gawin" to "กวิน",
        "rinlada" to "รินลดา",
        "mind" to "มายด์"
    )

    private val characterPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val key = pendingCharacterKey
        pendingCharacterKey = null
        if (uri != null && key != null) {
            saveCharacterMaster(key, uri)
        }
    }

    private val navy = Color.rgb(12, 15, 38)
    private val panel = Color.rgb(37, 32, 78)
    private val gold = Color.rgb(255, 190, 74)
    private val ice = Color.rgb(248, 247, 255)
    private val muted = Color.rgb(196, 193, 222)
    private val success = Color.rgb(83, 224, 210)
    private val activeGreen = Color.rgb(46, 204, 113)

    override fun onResume() {
        super.onResume()
        if (awaitingChatGptReturn || statePrefs.getBoolean("awaitingChatGptReturn", false)) {
            awaitingChatGptReturn = false
            statePrefs.edit().putBoolean("awaitingChatGptReturn", false).apply()
            if (::status.isInitialized) {
                status.text = "✓ กลับสู่ AUTO-MOVIE แล้ว • EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • พร้อมทำฉากถัดไป"
            }
            if (::nextHint.isInitialized) {
                nextHint.text = "งานเดิมยังอยู่ • ตรวจภาพใน ChatGPT แล้วกด NEXT SCENE เพื่อทำต่อ"
            }
            saveWorkState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(navy)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(42), dp(14), dp(4))
            setBackgroundColor(navy)
        }

        // ============================================================
        // HEADER
        // ============================================================

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, 0, 0, dp(3))
        }

        headerRow.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            contentDescription = "AUTO-MOVIE ENGINE 2.0"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(dp(78), dp(78)).apply {
            marginEnd = dp(8)
        })

        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(4), 0, 0, 0)
        }

        headerText.addView(TextView(this).apply {
            text = "AUTO-MOVIE 2.0"
            textSize = 19f
            letterSpacing = 0.02f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(14, 19, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, full())

        headerText.addView(TextView(this).apply {
            text = "ระบบสร้างหนังอัตโนมัติ"
            textSize = 17f
            setTextColor(muted)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(
                12, 18, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
            setPadding(0, 0, 0, 0)
        }, full())

        headerText.addView(TextView(this).apply {
            val buildNumber = packageManager.getPackageInfo(packageName, 0).versionCode
            text = "R${appVersion()} • BUILD เวอร์ชั่น $buildNumber"
            textSize = 11f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
        }, full())

        headerText.addView(TextView(this).apply {
            text = "พัฒนาโดย : นายปริญญา จันทร์จักษุ"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(
                8, 10, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )
        }, full())

        headerText.addView(TextView(this).apply {
            text = "LEAN EXECUTION • SESSION RESUME • PRODUCTION TEST"
            textSize = 10f
            setTextColor(ice)
            gravity = Gravity.START
            maxLines = 1
            isSingleLine = true
        }, full())

        headerRow.addView(headerText, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))

        root.addView(headerRow, full())

        timeLabel = TextView(this).apply {
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            maxLines = 1
            isSingleLine = true
            setPadding(dp(8), dp(2), dp(8), dp(4))
        }
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        timeRow.addView(timeLabel, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))
        val soundButton = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setTextColor(gold)
            contentDescription = "เปิดหรือปิดเสียงแจ้งเตือน"
            setOnClickListener {
                soundEnabled = !soundEnabled
                text = if (soundEnabled) "🔊" else "🔇"
                statePrefs.edit().putBoolean("soundEnabled", soundEnabled).apply()
                if (soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP, 70)
            }
        }
        soundButton.text = if (statePrefs.getBoolean("soundEnabled", true)) "🔊" else "🔇"
        timeRow.addView(soundButton, LinearLayout.LayoutParams(dp(42), dp(32)))
        root.addView(timeRow, full())
        startClock()

        // ============================================================
        // SCENE STATUS
        // ============================================================

        sceneLabel = TextView(this).apply {
            text = "EP 01   •   SCENE 01 / 20   •   8 SEC"
            textSize = 16f
            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 17, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(
                dp(10),
                dp(4),
                dp(10),
                dp(4)
            )
            setBackgroundColor(panel)
        }

        root.addView(sceneLabel, full())

        progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 20
            progress = 1
        }

        root.addView(
            progress,
            full(dp(7))
        )

        // ============================================================
        // DIRECTOR COMMAND
        // ============================================================

        input = EditText(this).apply {
            hint = "ชื่อเรื่อง / คำสั่งผู้กำกับ…"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 1
            maxLines = 2
            gravity = Gravity.TOP

            setPadding(
                dp(14),
                dp(6),
                dp(14),
                dp(6)
            )

            setBackgroundColor(panel)
        }

        val inputParams = full()

        inputParams.setMargins(
            0,
            dp(5),
            0,
            dp(5)
        )

        root.addView(
            input,
            inputParams
        )

        root.addView(
            stepAction(1, "❶ ✨ สร้างเรื่องใหม่ • NEW STORY", "เลือกชื่อเรื่องและหมวดเรื่อง") { generateTitles() },
            full()
        )

        titlePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(panel)
        }
        root.addView(titlePanel, full())

        characterMasterButton = Button(this).apply {
            textSize = 12f
            maxLines = 1
            isSingleLine = true
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnClickListener { showCharacterMasterSetup() }
        }
        root.addView(characterMasterButton, full(dp(38)))
        refreshCharacterMasterButton()

        root.addView(section("⚡ FAST FLOW • ONE-TAP SCENE").apply { gravity = Gravity.CENTER })
        nextHint = TextView(this).apply {
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            maxLines = 1
            isSingleLine = true
            setPadding(0, 0, 0, dp(3))
        }
        root.addView(nextHint, full())

        root.addView(
            stepAction(2, "❷ 🎬 สร้างโครงเรื่อง + ฉาก 01 • START", "ส่งครั้งเดียว: วางโครง EP และสร้างภาพฉากแรกทันที") { startQuickStory() },
            full()
        )
        root.addView(
            stepAction(3, "❸ ➜ สร้างฉากถัดไป • NEXT SCENE", "สร้างฉากต่อไปทันที ไม่ต้อง CONFIRM/QC/LOCK") { generateNextQuickScene() },
            full()
        )

        // ============================================================
        // STATUS
        // ============================================================

        status = TextView(this).apply {

            text =
                "● ระบบพร้อมใช้งาน  •  เชื่อมโยงฉากอัตโนมัติ"

            textSize = 11f

            setTextColor(success)

            gravity = Gravity.CENTER

            setPadding(
                0,
                dp(6),
                0,
                dp(3)
            )
        }

        root.addView(status)

        // ============================================================
        // HELP + NEW STORY + UPDATE — ONE ROW / 3 MENUS
        // ============================================================

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        utilityRow.addView(
            Button(this).apply {
                text = "❓ วิธีใช้งาน"
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.rgb(255, 190, 74)
                )
                setOnClickListener {
                    showHelp()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            ).apply {
                marginEnd = dp(3)
            }
        )

        utilityRow.addView(
            Button(this).apply {
                text = "เริ่มเรื่องใหม่"
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.rgb(230, 65, 65)
                )
                setOnClickListener {
                    confirmNewStory()
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            ).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        )

        utilityRow.addView(
            Button(this).apply {
                text = "↪ Update"
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.rgb(35, 105, 210)
                )
                setOnClickListener {
                    checkForAppUpdate(silent = false)
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            ).apply {
                marginStart = dp(3)
            }
        )

        root.addView(utilityRow, full())

        // ============================================================
        // FOOTER
        // ============================================================

        root.addView(
            TextView(this).apply {

                text = "AUTO-CONTEXT • 8s • 16:9 • Flow/Veo 3.1 • พัฒนาโดย ปริญญา"

                textSize = 10f
                maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(
                    8, 10, 1,
                    android.util.TypedValue.COMPLEX_UNIT_SP
                )

                setTextColor(Color.GRAY)

                gravity = Gravity.CENTER

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }
        )

        scroll.addView(root)

        restoreWorkState()
        migrateWorkflowStateIfNeeded()
        refreshStepIndicator()
        setContentView(scroll)

        // ตรวจอัปเดตอัตโนมัติ 1 ครั้งเมื่อเปิดแอป
        // ถ้าไม่มีรุ่นใหม่จะไม่รบกวนผู้ใช้
        checkForAppUpdate(silent = true)
    }

    // ============================================================
    // AI TITLE GENERATOR
    // ============================================================

    private var categoryIndex = 0

    private val storyCategories = listOf(
        "บันทึกรอยร้าวชีวิตคู่",
        "เกมรักซ่อนลวง",
        "ความลับใต้ชายคา",
        "ซ่อมรักหลังบ้าน",
        "หักมุมระทึกขวัญจิตวิทยา"
    )

    private val titleBanks = listOf(
        listOf(
            "วันที่เราเริ่มไม่เหมือนเดิม",
            "บ้านหลังเดิมที่ไม่มีคำว่าเรา",
            "คนข้างกายที่ไกลกว่าเดิม",
            "รอยร้าวใต้คำว่าครอบครัว",
            "เมื่อความเงียบเข้ามาแทนที่รัก",
            "คำว่ารักที่หายไปจากบ้าน",
            "ระยะห่างบนเตียงเดียวกัน",
            "วันที่หัวใจไม่กลับบ้าน",
            "ครอบครัวที่เหลือเพียงชื่อ",
            "ก่อนเราจะกลายเป็นคนแปลกหน้า"
        ),
        listOf(
            "รักนี้มีคนซ่อนอยู่",
            "เกมหัวใจที่ไม่มีคนชนะ",
            "คำโกหกในคืนที่ไว้ใจ",
            "คนรักหรือคนลวง",
            "เงาของใครในหัวใจเธอ",
            "เมื่อรักกลายเป็นเกม",
            "ข้อความลับหลังคำว่ารัก",
            "คนที่สามในความเงียบ",
            "รักซ้อนในบ้านเดียวกัน",
            "คืนที่ความจริงเปิดเผย"
        ),
        listOf(
            "ความลับหลังประตูบ้าน",
            "สิ่งที่ซ่อนอยู่ใต้ชายคา",
            "บ้านนี้มีเรื่องที่ไม่เคยพูด",
            "เสียงกระซิบในครอบครัว",
            "ความจริงในห้องที่ปิดตาย",
            "เมื่อบ้านเก็บความลับไม่ไหว",
            "เรื่องที่ไม่มีใครกล้าถาม",
            "ความลับบนโต๊ะอาหาร",
            "คนในบ้านที่ฉันไม่เคยรู้จัก",
            "ใต้หลังคาเดียวกันคนละความจริง"
        ),
        listOf(
            "กลับมารักกันอีกครั้ง",
            "บ้านที่เรายังซ่อมได้",
            "ก่อนรักจะสายเกินไป",
            "ขอโอกาสให้คำว่าเรา",
            "วันที่เราเลือกเริ่มใหม่",
            "ซ่อมหัวใจใต้หลังคาเดิม",
            "มือที่ยังไม่ยอมปล่อย",
            "รักที่ยังเหลือทางกลับ",
            "เมื่อเราหันหน้ามาคุยกัน",
            "บ้านเดิมกับหัวใจดวงใหม่"
        ),
        listOf(
            "คนที่ยืนอยู่หลังฉัน",
            "คืนที่บ้านไม่เหมือนเดิม",
            "เสียงเรียกจากห้องว่าง",
            "ความจริงที่จำไม่ได้",
            "คนแปลกหน้าในกระจก",
            "ก่อนประตูบานนั้นจะเปิด",
            "ใครบางคนรู้ทุกอย่าง",
            "ความเงียบที่กำลังโกหก",
            "เงื่อนงำในคืนฝนตก",
            "เมื่อคนใกล้ตัวไม่ใช่คนเดิม"
        )
    )

    private val statePrefs by lazy {
        getSharedPreferences("auto_movie_state", Context.MODE_PRIVATE)
    }

    private fun characterMasterFile(key: String): File {
        val dir = File(filesDir, "character_masters")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$key.master")
    }

    private fun characterMastersReady(): Boolean =
        characterNames.keys.all { characterMasterFile(it).exists() }

    // Identity-safe selective master engine.
    // The app owns the cast schedule, so it can attach only the original
    // masters that are actually allowed to appear in the current scene.
    private fun sceneCastKeys(scene: Int): List<String> {
        // BUILD 695: Dynamic Active Cast.
        // START establishes all three masters. NEXT SCENE rotates the minimum
        // useful cast so scenes do not default to the same 3-person composition.
        // The story engine may keep continuity, but only these masters are sent.
        return when (scene.coerceIn(1, 20)) {
            1 -> listOf("gawin", "rinlada", "mind")
            2 -> listOf("rinlada")
            3 -> listOf("mind")
            4 -> listOf("gawin")
            5, 9, 13, 17 -> listOf("gawin", "rinlada")
            6, 10, 14, 18 -> listOf("rinlada", "mind")
            7, 11, 15, 19 -> listOf("gawin", "mind")
            else -> listOf("gawin", "rinlada", "mind")
        }
    }

    private fun sceneCastNames(scene: Int): String =
        sceneCastKeys(scene).mapNotNull { characterNames[it] }.joinToString(" + ")

    private fun sceneCastPlan(): String =
        (1..20).joinToString("\n") { scene ->
            "SCENE ${fmt(scene)} = ${sceneCastNames(scene)}"
        }

    private fun shareableCharacterMasterFile(key: String): File? {
        val source = characterMasterFile(key)
        if (!source.exists()) return null

        // ChatGPT/Android receivers may reject the private ".master" extension
        // even when the bytes are a valid image. Export an attachment copy with
        // a real image extension while keeping the protected original unchanged.
        val exportDir = File(cacheDir, "character_master_exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val displayName = when (key) {
            "gawin" -> "GAWIN_MASTER.png"
            "rinlada" -> "RINLADA_MASTER.png"
            "mind" -> "MIND_MASTER.png"
            else -> "${key.uppercase()}_MASTER.png"
        }
        val exported = File(exportDir, displayName)
        source.inputStream().use { input ->
            exported.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return exported
    }

    private fun characterMasterUris(keys: List<String>): ArrayList<Uri> {
        val uris = arrayListOf<Uri>()
        keys.forEach { key ->
            shareableCharacterMasterFile(key)?.let { file ->
                uris.add(
                    FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )
                )
            }
        }
        return uris
    }

    private fun characterMasterUris(): ArrayList<Uri> =
        characterMasterUris(characterNames.keys.toList())

    private fun refreshCharacterMasterButton() {
        if (!::characterMasterButton.isInitialized) return
        val count = characterNames.keys.count { characterMasterFile(it).exists() }
        characterMasterButton.text =
            if (count == 3) "👥 ตัวละครหลักพร้อม 3/3 • CHARACTER MASTER"
            else "👥 ตั้งค่าตัวละครหลัก $count/3 • CHARACTER MASTER"
        characterMasterButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(
                if (count == 3) activeGreen else gold
            )
    }

    private fun showCharacterMasterSetup() {
        val labels = characterNames.map { (key, name) ->
            val ready = characterMasterFile(key).exists()
            "${if (ready) "✓" else "○"} $name"
        }.toTypedArray()

        val dialog = AlertDialog.Builder(this)
            .setTitle("ตัวละครหลัก • CHARACTER MASTER")
            .setSingleChoiceItems(labels, -1, null)
            .setNegativeButton("ปิด", null)
            .setPositiveButton("เลือกรูป", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val which = dialog.listView.checkedItemPosition
                if (which == android.widget.AdapterView.INVALID_POSITION) {
                    Toast.makeText(this, "แตะเลือก กวิน / รินลดา / มายด์ ก่อน", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                pendingCharacterKey = characterNames.keys.elementAt(which)
                dialog.dismiss()
                characterPicker.launch(arrayOf("image/*"))
            }
        }
        dialog.show()
    }

    private fun saveCharacterMaster(key: String, uri: Uri) {
        try {
            val target = characterMasterFile(key)
            contentResolver.openInputStream(uri)?.use { inputStream ->
                target.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("เปิดรูปไม่ได้")
            refreshCharacterMasterButton()
            val name = characterNames[key] ?: key
            val count = characterNames.keys.count { characterMasterFile(it).exists() }
            status.text = "✓ บันทึก $name แล้ว • CHARACTER MASTER $count/3"
            playTone(ToneGenerator.TONE_PROP_ACK, 90)
        } catch (_: Exception) {
            status.text = "⛔ บันทึกรูป CHARACTER MASTER ไม่สำเร็จ"
        }
    }

    private fun confirmNewStory() {
        val currentTitle = if (::input.isInitialized) input.text.toString().trim() else ""
        val label = if (currentTitle.isNotBlank()) "“$currentTitle”" else "งานปัจจุบัน"

        android.app.AlertDialog.Builder(this)
            .setTitle("เริ่มเรื่องใหม่?")
            .setMessage("$label จะถูกยกเลิก และ AUTO-MOVIE จะกลับไปเริ่มขั้นตอน ①\n\nแชทใน ChatGPT จะไม่ถูกลบ")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("เริ่มเรื่องใหม่") { _, _ ->
                startNewStory()
            }
            .show()
    }

    private fun startNewStory() {
        // ยกเลิกเฉพาะสถานะงานใน AUTO-MOVIE ไม่แตะต้องแชท ChatGPT
        input.setText("")
        titlePanel.removeAllViews()
        titlePanel.visibility = android.view.View.GONE
        selectedCategory = null
        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        activeStep = 1
        storyStartTimeMs = 0L
        storyEndTimeMs = 0L

        statePrefs.edit()
            .remove("story")
            .remove("category")
            .putInt("ep", 1)
            .putInt("scene", 1)
            .putInt("repair", 0)
            .putBoolean("locked", false)
            .putInt("activeStep", 1)
            .putLong("storyStartTimeMs", 0L)
            .putLong("storyEndTimeMs", 0L)
            .apply()

        sceneLabel.text = "EP 01   •   SCENE 01 / 20   •   8 SEC"
        progress.progress = 1
        updateTimeLabel()
        updateNextButton()
        refreshStepIndicator()
        status.text = "● พร้อมเริ่มเรื่องใหม่ • เริ่มที่ ① TITLES"
        playTone(ToneGenerator.TONE_PROP_ACK, 100)
    }

    private fun sceneLedgerKey(ep: Int, scene: Int) = "sceneLedger_${ep}_${scene}"

    private fun saveSceneLedger(ep: Int, scene: Int) {
        val previous = if (scene > 1) "EP ${fmt(ep)} SCENE ${fmt(scene - 1)}" else if (ep > 1) "EP ${fmt(ep - 1)} SCENE 20 HANDOFF" else "STORY START"
        val ledger = "CURRENT=EP ${fmt(ep)} SCENE ${fmt(scene)}; PREVIOUS=$previous; STORY=${if (::input.isInitialized) input.text.toString().trim() else ""}; CATEGORY=${selectedCategory.orEmpty()}"
        statePrefs.edit().putString(sceneLedgerKey(ep, scene), ledger).apply()
    }

    private fun currentSceneLedger(): String =
        statePrefs.getString(sceneLedgerKey(currentEp, currentScene), "").orEmpty()

    private fun saveWorkState() {
        statePrefs.edit()
            .putString("story", if (::input.isInitialized) input.text.toString() else "")
            .putString("category", selectedCategory)
            .putInt("ep", currentEp)
            .putInt("scene", currentScene)
            .putInt("repair", repairCount)
            .putBoolean("locked", sceneLocked)
            .putInt("categoryIndex", categoryIndex)
            .putInt("activeStep", activeStep)
            .putLong("storyStartTimeMs", storyStartTimeMs)
            .putLong("storyEndTimeMs", storyEndTimeMs)
            .apply()
    }

    private fun restoreWorkState() {
        soundEnabled = statePrefs.getBoolean("soundEnabled", true)
        selectedCategory = statePrefs.getString("category", null)
        currentEp = statePrefs.getInt("ep", 1).coerceIn(1, 5)
        currentScene = statePrefs.getInt("scene", 1).coerceIn(1, 20)
        repairCount = statePrefs.getInt("repair", 0).coerceIn(0, 3)
        sceneLocked = statePrefs.getBoolean("locked", false)
        categoryIndex = statePrefs.getInt("categoryIndex", 0).coerceIn(0, storyCategories.lastIndex)
        activeStep = statePrefs.getInt("activeStep", 1).let { old ->
            when {
                old <= 1 -> 1
                old == 2 -> 2
                else -> 3
            }
        }
        storyStartTimeMs = statePrefs.getLong("storyStartTimeMs", 0L)
        storyEndTimeMs = statePrefs.getLong("storyEndTimeMs", 0L)
        updateTimeLabel()

        val savedStory = statePrefs.getString("story", "").orEmpty()
        if (savedStory.isNotBlank()) {
            input.setText(savedStory)
            input.setSelection(input.text.length)
        }

        sceneLabel.text = "EP ${fmt(currentEp)}   •   SCENE ${fmt(currentScene)} / 20   •   8 SEC"
        progress.progress = currentScene
        updateNextButton()

        if (savedStory.isNotBlank()) {
            status.text = "✓ กู้คืนงานเดิมแล้ว • SCENE ${fmt(currentScene)}"
        }
        refreshStepIndicator()
    }

    private fun generateTitles() {
        if (storyInProgress()) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text = "⛔ STORY ปัจจุบันยังไม่จบ • ทำ EP 01–05 ให้ครบก่อน"
            return
        }
        if (!requireStep(1)) return
        val category = storyCategories[categoryIndex]
        selectedCategory = category
        val bank = titleBanks[categoryIndex]

        // หมวดหมู่จะหมุนเมื่อ CREATE EP สำเร็จ ไม่ใช่ตอนเพียงเปิดรายชื่อ Titles
        val titles = bank.shuffled().take(5)
        titlePanel.removeAllViews()
        titlePanel.visibility = android.view.View.VISIBLE

        titlePanel.addView(TextView(this).apply {
            text = "CATEGORY • $category\nแตะชื่อเรื่องที่ต้องการ"
            textSize = 13f
            setTextColor(gold)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(10))
        })

        titles.forEachIndexed { index, title ->
            titlePanel.addView(Button(this).apply {
                text = "${index + 1}.  $title"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textSize = 14f
                setTextColor(ice)
                backgroundTintList = android.content.res.ColorStateList.valueOf(navy)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener {
                    input.setText(title)
                    input.setSelection(input.text.length)
                    titlePanel.visibility = android.view.View.GONE
                    setActiveStep(2)
                    saveWorkState()
                    status.text = "✓ เลือกชื่อเรื่องแล้ว • $title • $category"
                }
            }, full())
        }

        status.text = "✦ สร้าง 5 ชื่อแล้ว • $category"
    }

    private fun appVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "6.8"
        } catch (_: Exception) {
            "6.8"
        }
    }

    private fun startQuickStory() {
        if (!requireStep(2)) return
        if (!characterMastersReady()) {
            status.text = "⚠ ตั้งค่าตัวละครหลัก 3/3 ก่อนเริ่มเรื่อง"
            showCharacterMasterSetup()
            return
        }
        val story = input.text.toString().trim()
        if (story.isBlank()) { status.text = "⚠ เลือกชื่อเรื่องก่อน"; return }
        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        storyStartTimeMs = System.currentTimeMillis()
        storyEndTimeMs = 0L
        val category = selectedCategory ?: storyCategories[categoryIndex]
        selectedCategory = category
        val usedIndex = storyCategories.indexOf(category)
        categoryIndex = if (usedIndex >= 0) (usedIndex + 1) % storyCategories.size else (categoryIndex + 1) % storyCategories.size
        setActiveStep(3)
        saveSceneLedger(currentEp, currentScene)
        updateUi()
        saveWorkState()
        share(safeNormalizePrompt(buildQuickStartCommand(story, category)), characterKeys = characterNames.keys.toList())
        status.text = "⚡ FAST START • SCENE 01 • BODY IDENTITY ON"
    }

    private fun generateNextQuickScene() {
        if (!requireStep(3)) return
        if (!characterMastersReady()) { showCharacterMasterSetup(); return }

        // EP boundary is deliberate: Scene 20 creates a handoff and stops.
        // The next press starts the following EP at Scene 01 using that handoff contract.
        if (currentScene < 20) {
            currentScene++
            saveSceneLedger(currentEp, currentScene)
            updateUi()
            saveWorkState()
            share(safeNormalizePrompt(buildQuickSceneCommand()), characterKeys = activeSceneCastKeys(currentScene))
            status.text = "⚡ EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • ส่งแล้ว"
            return
        }

        if (currentEp >= 5) {
            storyEndTimeMs = System.currentTimeMillis()
            selectedCategory = null
            setActiveStep(1)
            saveWorkState()
            updateUi()
            status.text = "🏁 STORY COMPLETE • 5 EP / 100 SCENES"
            return
        }

        currentEp++
        currentScene = 1
        saveSceneLedger(currentEp, currentScene)
        updateUi()
        saveWorkState()
        share(safeNormalizePrompt(buildQuickSceneCommand()), characterKeys = activeSceneCastKeys(currentScene))
        status.text = "🎬 HANDOFF → EP ${fmt(currentEp)} • SCENE 01"
    }

    // BUILD 694: normalize generated instructions before sharing to ChatGPT.
    // This keeps identity/continuity requirements while removing unnecessary
    // body-focused wording that is not needed to create the scene.
    private fun safeNormalizePrompt(command: String): String {
        return command
            .replace("natural body build+proportions", "overall natural appearance")
            .replace("natural body proportions", "overall natural appearance")
            .replace("รูปร่าง/สัดส่วนธรรมชาติ", "ภาพลักษณ์โดยรวมตามธรรมชาติ")
            .replace("สัดส่วนธรรมชาติ", "ภาพลักษณ์โดยรวม")
            .replace("รูปร่างระหว่างกัน", "ภาพลักษณ์ระหว่างกัน")
            .replace("รูปร่าง/สัดส่วน", "ภาพลักษณ์โดยรวม")
            .replace("เน้นสัดส่วนทางเพศ", "เน้นรายละเอียดทางกายภาพที่ไม่จำเป็น")
            .replace(Regex("(?i)FEMALE BODY ZERO-RESHAPE:[^\\n]*"), "CHARACTER APPEARANCE LOCK: preserve the same adult character identity and overall natural appearance from the attached ORIGINAL MASTER.")
            .replace(Regex("(?i)FEMALE FACE PRIORITY[^\\n]*"), "FEMALE IDENTITY PRIORITY: preserve face, hairstyle, approximate age and overall appearance from that character's ORIGINAL MASTER.")
    }

    private fun buildQuickStartCommand(story: String, category: String): String {
        return """
AUTO-MOVIE • START

สร้างละครเรื่อง “$story” หมวด “$category” จำนวน 5 EP โดย EP ละ 20 ฉาก ฉากละประมาณ 8 วินาที

ใช้บุคคลผู้ใหญ่จากรูปที่แนบเป็นตัวละครเดิม:
รูป 1 = กวิน
รูป 2 = รินลดา
รูป 3 = มายด์

สร้างโครงเรื่องแบบกระชับสำหรับทั้งเรื่องและ EP 01 แล้วสร้างภาพ SCENE 01 ทันที
ให้เหตุการณ์ของฉากเป็นตัวกำหนดว่าใครต้องอยู่ในภาพ การกระทำ สถานที่ อารมณ์ และเสื้อผ้า
ใช้รูปที่แนบเป็นต้นฉบับของตัวละครแต่ละคน คงใบหน้า ทรงผม สีผม ความยาวผม รูปร่างตามภาพอ้างอิง โดยจับคู่รูปกับชื่อตามลำดับเดิม
เสื้อผ้าเปลี่ยนได้ตามเรื่อง โดยสิ่งที่ไม่ได้สั่งให้เปลี่ยนให้คงความต่อเนื่องจากตัวละครและเรื่องเดิม
จัดฉากแบบละครไทยสมจริง ให้ตัวละครกำลังทำสิ่งที่ทำให้เรื่องเดินหน้า ไม่ใช่เพียงยืนโพส
ภาพแนวนอน 16:9 กล้องนิ่ง

หลังภาพ ให้ระบุสั้น ๆ ว่า SCENE 01 จบด้วยเหตุการณ์อะไร เพื่อใช้ต่อ SCENE 02
        """.trimIndent()
    }

    // BUILD 713: STORY ACTION PROGRESSION ENGINE + SOURCE-ANCHORED IDENTITY
    // Each NEXT SCENE gets a concrete visible action that advances the drama.
    // Character/body identity and wardrobe memory remain unchanged.
    private fun wardrobeKey(key: String) = "wardrobe_current_" + key

    private fun defaultWardrobe(key: String): String = SceneWardrobe.defaultFor(key)

    private fun currentWardrobe(key: String): String =
        statePrefs.getString(wardrobeKey(key), null)?.takeIf { it.isNotBlank() }
            ?: defaultWardrobe(key).also {
                statePrefs.edit().putString(wardrobeKey(key), it).apply()
            }

    private fun explicitWardrobeText(): String =
        sceneCastKeys(currentScene).joinToString("\n") { key ->
            "${characterNames[key] ?: key} ใส่${currentWardrobe(key)}"
        }

    // BUILD 717: BODY IDENTITY CONTINUITY + CHARACTER IDENTITY ANCHOR
    // The current scene is derived from the previous scene's unresolved hook.
    // Visual identity, body appearance and wardrobe logic are intentionally unchanged.
    private data class StoryBeat(
        val cast: List<String>,
        val action: String,
        val discovery: String,
        val unresolved: String,
        val nextHook: String
    )

    private fun storyBeat(scene: Int): StoryBeat {
        val n = scene.coerceIn(1, 20)
        val cycle = (n - 1) % 10
        return when (cycle) {
            0 -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "กวิน รินลดา และมายด์อยู่ในโถงภายในบ้าน ระหว่างการพูดคุย โทรศัพท์ของกวินมีข้อความใหม่เข้ามา ทุกคนหันไปมองหน้าจอพร้อมกัน",
                "มีข้อความใหม่ที่เกี่ยวข้องกับเหตุการณ์ล่าสุด",
                "ยังไม่รู้ว่าข้อมูลในข้อความหมายถึงอะไร",
                "กวินต้องเปิดข้อมูลให้รินลดาและมายด์เห็นชัดเจน"
            )
            1 -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "กวินเปิดข้อความในโทรศัพท์ให้รินลดาและมายด์ดู รินลดาสังเกตเห็นรายละเอียดที่เกี่ยวข้องกับมายด์ ขณะที่มายด์ชะงักและมีสีหน้ากังวล",
                "รายละเอียดในข้อความเชื่อมโยงไปถึงมายด์",
                "มายด์ยังไม่ได้อธิบายความเกี่ยวข้อง",
                "รินลดาต้องถามมายด์ตรง ๆ ถึงข้อมูลในข้อความ"
            )
            2 -> StoryBeat(
                listOf("rinlada", "mind"),
                "รินลดากับมายด์อยู่ที่โต๊ะในบ้าน รินลดาถามตรง ๆ ถึงข้อมูลในโทรศัพท์ มายด์จึงวางสิ่งของสำคัญลงตรงหน้ารินลดา แต่ยังลังเลที่จะอธิบายที่มา",
                "มายด์มีสิ่งของที่เกี่ยวข้องกับข้อมูลในโทรศัพท์",
                "ยังไม่รู้ว่าสิ่งของนั้นมาจากไหนและเกี่ยวข้องอย่างไร",
                "รินลดาต้องตรวจสิ่งของและถามถึงที่มา"
            )
            3 -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "รินลดากำลังตรวจสิ่งของที่มายด์นำออกมา กวินเดินกลับเข้ามาและเห็นสิ่งนั้น รินลดายื่นให้กวินดู ขณะที่มายด์เฝ้ามองด้วยความกังวล",
                "กวินได้รับรู้เรื่องสิ่งของที่มายด์นำออกมาแล้ว",
                "ที่มาของสิ่งของยังไม่ถูกอธิบาย",
                "กวินต้องเปรียบเทียบสิ่งของกับข้อมูลในโทรศัพท์"
            )
            4 -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "กวินเปรียบเทียบสิ่งของกับข้อมูลในโทรศัพท์และพบรายละเอียดที่ตรงกัน รินลดาหันไปมองมายด์เพื่อรอคำตอบ ขณะที่มายด์เริ่มอธิบาย",
                "สิ่งของตรงกับข้อมูลบางส่วนในโทรศัพท์",
                "คำอธิบายของมายด์ยังไม่ครบ",
                "มายด์ต้องเปิดเผยว่าได้รับสิ่งของมาจากใคร"
            )
            5 -> StoryBeat(
                listOf("rinlada", "mind"),
                "มายด์บอกที่มาของสิ่งของให้รินลดาฟังและชี้รายละเอียดสำคัญบนสิ่งนั้น รินลดาฟังอย่างตั้งใจแต่ยังมีข้อสงสัย",
                "รินลดารู้ที่มาของสิ่งของแล้ว",
                "ยังมีรายละเอียดหนึ่งที่ขัดกับข้อมูลเดิม",
                "รินลดาต้องนำข้อมูลที่ขัดกันไปถามกวิน"
            )
            6 -> StoryBeat(
                listOf("gawin", "rinlada"),
                "รินลดานำสิ่งของและข้อมูลในโทรศัพท์มาให้กวินเปรียบเทียบ ทั้งคู่พบรายละเอียดที่ไม่ตรงกันและเริ่มสงสัยว่ามีข้อมูลบางส่วนถูกปิดบัง",
                "กวินและรินลดาพบข้อมูลที่ขัดกัน",
                "ยังไม่รู้ว่าใครให้ข้อมูลไม่ครบ",
                "ทั้งคู่ต้องกลับไปถามมายด์พร้อมหลักฐาน"
            )
            7 -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "กวินกับรินลดานำหลักฐานกลับมาถามมายด์ มายด์กำลังจะตอบ แต่โทรศัพท์ของกวินมีข้อความใหม่เข้ามาและทุกคนหันไปมองหน้าจอ",
                "มีข้อมูลใหม่เข้ามาระหว่างการเผชิญหน้า",
                "ยังไม่มีใครได้เปิดอ่านข้อความใหม่",
                "กวินต้องเปิดข้อความให้ทุกคนดู"
            )
            8 -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "กวินเปิดข้อความใหม่ให้รินลดาและมายด์ดู ข้อมูลในข้อความทำให้รินลดาตกใจ ขณะที่มายด์มีท่าทีเหมือนจำบางอย่างได้",
                "ข้อความใหม่เปิดเผยข้อมูลที่เปลี่ยนความเข้าใจเดิม",
                "มายด์ยังไม่ได้บอกสิ่งที่เพิ่งนึกออก",
                "มายด์ต้องอธิบายข้อมูลที่เพิ่งจำได้"
            )
            else -> StoryBeat(
                listOf("gawin", "rinlada", "mind"),
                "มายด์อธิบายสิ่งที่เพิ่งนึกออกและวางหลักฐานไว้กลางโต๊ะ กวินกับรินลดาตรวจดูพร้อมกัน ก่อนพบรายละเอียดใหม่ที่นำไปสู่คำถามถัดไป",
                "ทั้งสามมีข้อมูลร่วมกันมากขึ้น",
                "รายละเอียดใหม่ยังต้องตรวจสอบ",
                "ใช้รายละเอียดใหม่นี้เป็นเหตุของฉากถัดไป"
            )
        }
    }

    private fun activeSceneCastKeys(scene: Int): List<String> = storyBeat(scene).cast

    private fun activeSceneCastNames(scene: Int): String =
        activeSceneCastKeys(scene).mapNotNull { characterNames[it] }.joinToString(" + ")

    private fun explicitStoryWardrobeText(scene: Int): String =
        activeSceneCastKeys(scene).joinToString("\n") { key ->
            "${characterNames[key] ?: key} ใส่${currentWardrobe(key)}"
        }

    private fun storyActionProgressionText(): String = storyBeat(currentScene).action

    private fun storyMemoryText(): String {
        val beat = storyBeat(currentScene)
        val previous = if (currentScene > 1) storyBeat(currentScene - 1) else null
        return buildString {
            if (previous != null) {
                append("ต่อจากฉากก่อน: ")
                append(previous.nextHook)
                append("\n")
            }
            append("สิ่งที่เพิ่งรู้: ")
            append(beat.discovery)
            append("\nเรื่องที่ยังค้าง: ")
            append(beat.unresolved)
            append("\nจุดส่งต่อฉากถัดไป: ")
            append(beat.nextHook)
        }
    }

    private fun buildQuickSceneCommand(): String {
        val cast = activeSceneCastNames(currentScene)
        val wardrobe = explicitStoryWardrobeText(currentScene)
        val action = storyActionProgressionText()
        val memory = storyMemoryText()

        return ImagePromptPolicy.scene("ตัวละคร: $cast\n$wardrobe", "สถานที่ตามฉากปัจจุบันในบท", action,
            continuity=memory)
    }

    private fun createEp() {

        if (storyInProgress()) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text = "⛔ มี STORY กำลังทำอยู่ • ไม่สามารถสร้างทับได้"
            return
        }
        if (!requireStep(2)) return
        val story =
            input.text.toString().trim()

        if (story.isEmpty()) {

            status.text =
                "⚠ กรุณาใส่ชื่อเรื่องหรือคำสั่ง"

            return
        }

        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        storyStartTimeMs = System.currentTimeMillis()
        storyEndTimeMs = 0L
        updateTimeLabel()

        updateUi()
        saveWorkState()

        val categoryForThisEp =
            selectedCategory ?: storyCategories[categoryIndex]

        status.text =
            "🎬 กำลังเริ่ม EP • $categoryForThisEp"

        if (!characterMastersReady()) {
            status.text = "⚠ ตั้งค่าตัวละครหลัก 3 คนก่อนเริ่ม CREATE EP"
            showCharacterMasterSetup()
            return
        }

        share(
            buildMasterPrompt(story, categoryForThisEp),
            includeCharacterMasters = true
        )

        // ROTATING CATEGORY:
        // หมุน CATEGORY ครั้งเดียวเมื่อเริ่ม "เรื่องใหม่"
        // EP 01–05 ของเรื่องเดียวกันใช้ CATEGORY เดิมทั้งหมด
        val usedIndex = storyCategories.indexOf(categoryForThisEp)
        categoryIndex =
            if (usedIndex >= 0) {
                (usedIndex + 1) % storyCategories.size
            } else {
                (categoryIndex + 1) % storyCategories.size
            }

        // ล็อก CATEGORY ตลอด STORY ปัจจุบัน (EP 01–05)
        // categoryIndex ถูกเลื่อนไว้ล่วงหน้าสำหรับ "เรื่องใหม่" ครั้งถัดไปเท่านั้น
        selectedCategory = categoryForThisEp
        status.text = "⏳ ส่ง CREATE EP แล้ว • รอยืนยัน EP MASTER"
        refreshStepIndicator()
        saveWorkState()
    }

    // ============================================================
    // REPAIR
    // ============================================================

    private fun repair() {

        if (sceneLocked) {

            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text =
                "🔒 Scene นี้ถูกล็อกแล้ว"

            return
        }

        if (repairCount >= 3) {

            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text =
                "⛔ REPAIR ครบ 3 ครั้ง — ต้อง QC/ตัดสินใจใหม่"

            return
        }

        repairCount++

        status.text =
            "🛠 REPAIR $repairCount / 3 • SCENE ${fmt(currentScene)}"

        share(
            buildRepairCommand()
        )
        setActiveStep(4)
    }

    // ============================================================
    // PASS & LOCK
    // ============================================================

    private fun lockScene() {

        sceneLocked = true
        playTone(ToneGenerator.TONE_PROP_ACK, 100)
        val storyCompleteNow = currentEp == 5 && currentScene == 20
        if (storyCompleteNow && storyStartTimeMs > 0L && storyEndTimeMs == 0L) {
            storyEndTimeMs = System.currentTimeMillis()
            updateTimeLabel()
        }

        if (storyCompleteNow) {
            selectedCategory = null
            setActiveStep(1)
        } else {
            setActiveStep(7)
        }

        updateNextButton()

        saveWorkState()

        status.text =
            if (currentScene == 20) {
                if (currentEp < 5) {
                    "✅ EP ${fmt(currentEp)} COMPLETE • HANDOFF → EP ${fmt(currentEp + 1)}"
                } else {
                    "🏁 EP 05 COMPLETE • STORY COMPLETE"
                }
            } else {
                "✅ PASS & LOCK — EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)}"
            }

        share(buildLockCommand())
    }

    // ============================================================
    // NEXT SCENE
    // ============================================================

    private fun nextScene() {

        if (!sceneLocked) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120)
            status.text = "⛔ ต้อง PASS & LOCK ก่อน"
            return
        }

        if (currentScene < 20) {
            currentScene++
        } else if (currentEp < 5) {
            // Scene 20 ของ EP เดิมผ่านแล้ว: ส่งต่อด้วย HANDOFF ไป EP ถัดไป
            currentEp++
            currentScene = 1
        } else {
            status.text = "🏁 STORY COMPLETE — EP 01–05 COMPLETE"
            updateNextButton()
            return
        }

        repairCount = 0
        sceneLocked = false
        setActiveStep(3)
        updateUi()
        saveWorkState()

        status.text = "▶ EP ${fmt(currentEp)} • SCENE ${fmt(currentScene)} • AUTO-CONTEXT"
        share(buildNextSceneCommand())
    }

    // ============================================================
    // UPDATE UI
    // ============================================================

    private fun updateUi() {

        sceneLabel.text =
            "EP ${fmt(currentEp)}   •   SCENE ${fmt(currentScene)} / 20   •   8 SEC"

        progress.progress =
            currentScene

        updateNextButton()
        refreshStepIndicator()

        status.text =
            "● SYSTEM READY  •  SCENE ${fmt(currentScene)}  •  AUTO-CONTEXT ON"
    }

    // ============================================================
    // SHARE TO CHATGPT
    // ============================================================

    private fun share(
        text: String,
        includeCharacterMasters: Boolean = false,
        characterKeys: List<String>? = null
    ) {

        hideKeyboard()

        // BUILD 693 — SEND RELIABILITY GUARD
        // Prevent accidental double-dispatch while Android/ChatGPT is still
        // consuming a multi-attachment share payload.
        val nowMs = System.currentTimeMillis()
        if (shareInProgress || nowMs - lastShareAtMs < 1800L) {
            status.text = "⏳ กำลังส่งคำสั่งเดิม • กรุณารอสักครู่"
            return
        }
        shareInProgress = true
        lastShareAtMs = nowMs

        // Diagnostic/fail-safe: attachment preparation must never make START
        // fail silently. If PNG export/FileProvider fails, show the real error
        // and still allow the text payload to reach Android's share UI.
        val masterUris: ArrayList<Uri> = try {
            when {
                characterKeys != null -> characterMasterUris(characterKeys)
                includeCharacterMasters -> characterMasterUris()
                else -> arrayListOf()
            }
        } catch (e: Exception) {
            shareInProgress = false
            status.text = "⚠ CHARACTER MASTER แนบไม่สำเร็จ: ${e.javaClass.simpleName}"
            Toast.makeText(
                this,
                "แนบ CHARACTER MASTER ไม่สำเร็จ: ${e.message ?: e.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            arrayListOf()
        }

        // Validate every prepared URI before dispatch. A stale/unreadable master
        // must not be sent as a partially broken 3-image payload.
        if ((includeCharacterMasters || characterKeys != null) && masterUris.isEmpty()) {
            shareInProgress = false
            status.text = "⚠ MASTER ยังไม่พร้อม • กรุณาเลือกภาพต้นฉบับใหม่"
            return
        }
        val unreadableMaster = masterUris.firstOrNull { uri ->
            try {
                contentResolver.openInputStream(uri)?.use { it.read() } == null
            } catch (_: Exception) {
                true
            }
        }
        if (unreadableMaster != null) {
            shareInProgress = false
            status.text = "⚠ พบ MASTER ที่อ่านไม่ได้ • เลือกภาพต้นฉบับใหม่แล้วลองอีกครั้ง"
            Toast.makeText(this, "CHARACTER MASTER บางภาพอ่านไม่ได้", Toast.LENGTH_LONG).show()
            return
        }

        // START now sends all three original masters in one payload so ChatGPT
        // receives a stable fixed-cast registry before STORY BIBLE/SCENE 01.
        // One image uses ACTION_SEND; 2+ images use ACTION_SEND_MULTIPLE.
        val sendAction =
            if (masterUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND

        val sendIntent = Intent(sendAction).apply {
            type = if (masterUris.isNotEmpty()) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)

            // BUILD 702 RETURN-FLOW FIX:
            // Keep ChatGPT in its own Android task. This prevents the AUTO-MOVIE
            // Recents card from turning into a ChatGPT screen and preserves the
            // AUTO-MOVIE activity/state underneath as a separate task.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            when (masterUris.size) {
                0 -> Unit
                1 -> {
                    putExtra(Intent.EXTRA_STREAM, masterUris.first())
                    clipData = android.content.ClipData.newUri(
                        contentResolver,
                        "AUTO-MOVIE CHARACTER MASTER",
                        masterUris.first()
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    try { grantUriPermission("com.openai.chatgpt", masterUris.first(), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                }
                else -> {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, masterUris)
                    val clip = android.content.ClipData.newUri(
                        contentResolver,
                        "AUTO-MOVIE CHARACTER MASTERS",
                        masterUris.first()
                    )
                    masterUris.drop(1).forEach { uri ->
                        clip.addItem(android.content.ClipData.Item(uri))
                    }
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    masterUris.forEach { uri ->
                        try { grantUriPermission("com.openai.chatgpt", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    }
                }
            }
        }

        // QUICK FLOW: open ChatGPT directly when its Android share target
        // accepts the exact payload. Keep Android Share Sheet only as fallback.
        val chatGptPackage = "com.openai.chatgpt"
        try {
            val directIntent = Intent(sendIntent).apply {
                setPackage(chatGptPackage)
            }

            if (directIntent.resolveActivity(packageManager) != null) {
                saveWorkState()
                awaitingChatGptReturn = true
                statePrefs.edit().putBoolean("awaitingChatGptReturn", true).apply()
                startActivity(directIntent)
                status.text = "↗ ส่งคำสั่งไป ChatGPT แล้ว • หากเครือข่ายสะดุดให้กด Retry ใน ChatGPT"
                Handler(Looper.getMainLooper()).postDelayed({
                    shareInProgress = false
                }, 2200L)
            } else {
                val chooserIntent = Intent.createChooser(
                    sendIntent,
                    "ส่งคำสั่งไปยัง ChatGPT"
                )
                saveWorkState()
                awaitingChatGptReturn = true
                statePrefs.edit().putBoolean("awaitingChatGptReturn", true).apply()
                startActivity(chooserIntent)
                status.text = "↗ เปิดเมนูแชร์สำรอง • เลือก ChatGPT"
                Handler(Looper.getMainLooper()).postDelayed({ shareInProgress = false }, 2200L)
            }
        } catch (directError: Exception) {
            try {
                val chooserIntent = Intent.createChooser(
                    sendIntent,
                    "ส่งคำสั่งไปยัง ChatGPT"
                )
                saveWorkState()
                awaitingChatGptReturn = true
                statePrefs.edit().putBoolean("awaitingChatGptReturn", true).apply()
                startActivity(chooserIntent)
                status.text = "↗ เปิด ChatGPT โดยตรงไม่ได้ • ใช้เมนูแชร์สำรอง"
                Handler(Looper.getMainLooper()).postDelayed({ shareInProgress = false }, 2200L)
            } catch (fallbackError: Exception) {
                shareInProgress = false
                status.text = "⛔ เปิด ChatGPT/เมนูแชร์ไม่สำเร็จ: ${fallbackError.javaClass.simpleName}"
                Toast.makeText(
                    this,
                    "เปิด ChatGPT ไม่สำเร็จ: ${fallbackError.message ?: fallbackError.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // MASTER PRODUCTION RULES
    // ============================================================

    private fun rules(): String {
        return """
AUTO-MOVIE ENGINE 2.0
R${appVersion()} • BUILD เวอร์ชั่น ${packageManager.getPackageInfo(packageName, 0).versionCode}
พัฒนาโดย : นายปริญญา จันทร์จักษุ
ANDROID PROFESSIONAL • LEAN MASTER • AUTO-CONTEXT

CHANNEL:
${ChannelCatalog.channel}

SYSTEM MODE:
LEAN MASTER PROMPT = ON
AUTO-CONTEXT = ON
SESSION RESUME = ON

RETURN FLOW:
- ก่อนส่งงานไป ChatGPT ให้บันทึก EP / SCENE / STORY / workflow state ใน AUTO-MOVIE
- เมื่อผู้ใช้กลับเข้า AUTO-MOVIE ให้คืนสถานะงานเดิมและพร้อมกด NEXT SCENE
- ห้ามรีเซ็ตเรื่องหรือ CHARACTER MASTER เพียงเพราะมีการสลับไป ChatGPT

QUICK FLOW:
① NEW STORY → ② START → ③ NEXT SCENE

CORE PRINCIPLE:
- ใช้ ORIGINAL MASTER ที่แนบเป็นตัวละครผู้ใหญ่คนเดิมโดยตรง
- ภาพอ้างอิงเป็นแหล่งข้อมูลหลักของใบหน้า ทรงผม อายุโดยประมาณ และภาพลักษณ์โดยรวม
- อย่าเขียนคำบรรยายใบหน้า/รูปร่างใหม่จากข้อความ และอย่าปรับตัวละครให้เป็น generic actor/model
- เปลี่ยนเฉพาะสิ่งที่ STORY/SCENE สั่ง เช่น เสื้อผ้า การกระทำ สีหน้า สถานที่ และเวลา
- เสื้อผ้าใน ORIGINAL MASTER ไม่ใช่ Identity; เปลี่ยนชุดได้เต็มชุดตามฉาก
- ถ้าฉากต่อเนื่องในวัน/เหตุการณ์เดียวกัน ให้คงชุดล่าสุดจนกว่าเรื่องจะมีเหตุผลให้เปลี่ยน
- เสื้อผ้าใหม่ต้องเป็นธรรมชาติ เหมาะกับสถานการณ์ และไม่ใช้เพื่อ redesign หรือเน้นรูปลักษณ์ของบุคคล
- ใช้เฉพาะ ORIGINAL MASTER ของ ACTIVE CAST; คนที่ไม่อยู่ใน ACTIVE CAST ห้ามเพิ่มเอง
- ถ้ามีหลาย MASTER ให้รักษาแต่ละคนแยกกัน ห้ามผสมหรือสลับอัตลักษณ์
- ห้ามใช้ภาพ Scene Output ก่อนหน้าแทน ORIGINAL MASTER
- ให้ STORY BEAT เป็นตัวกำหนดจำนวนตัวละคร การกระทำ blocking และพร็อพ
- ACTION FIRST: ฉากใหม่ต้องมีการกระทำที่ทำให้เรื่องเดินหน้า ไม่ใช่เพียงยืนโพสหรือเปลี่ยนสีหน้า
- CONTINUITY: เวลา สถานที่ ชุด พร็อพ ข้อมูลที่ตัวละครรู้ และอารมณ์ ต้องต่อจากฉากก่อนอย่างสมเหตุผล
- MODERN BLOCKING: ไม่บังคับตัวละครยืนเรียงหน้ากล้อง; จัดตำแหน่งจากเหตุการณ์และความสัมพันธ์
- กล้อง Locked-off static camera; ห้าม zoom/pan/tilt/roll/dolly/tracking/cut/reframe
- ทุกฉาก 8 วินาที • แนวนอน 16:9 • One Scene / One Image • Flow/Veo 3.1
- ใช้ภาษาภาพและเสื้อผ้าที่เป็นกลาง เหมาะกับเนื้อเรื่อง และเคารพข้อกำหนดความปลอดภัย
- ถ้ารายละเอียดย่อยไม่ชัด ให้เลือกค่าที่สอดคล้องกับ canon ล่าสุดและเดินเรื่องต่อ ห้ามถามซ้ำเมื่อข้อมูลพอ
- START สร้าง STORY BIBLE + EP PLAN + SCENE 01 ในคำสั่งเดียว
- NEXT SCENE สร้างฉากปัจจุบันทันทีแบบ SELF-CONTAINED
- OUTPUT MINIMAL: แสดงเฉพาะข้อมูลฉากที่จำเป็น + IMAGE 16:9 + Flow/Veo prompt + NEXT HOOK
- INTERNAL QC: ตรวจ cast, identity continuity, wardrobe continuity, action, hands/anatomy และจำนวนคนก่อนส่ง โดยไม่พิมพ์รายงานยาว
- หากระบบภาพรักษาความเหมือนจาก MASTER ไม่ได้อย่างน่าเชื่อถือ ห้ามประกาศว่าตรงต้นฉบับแบบสมบูรณ์

CAST REGISTRY:
MASTER 01 = กวิน — ผมสั้นสีดำตาม ORIGINAL MASTER
MASTER 02 = รินลดา — ผมยาวสีดำตาม ORIGINAL MASTER
MASTER 03 = มายด์ — ผมบ๊อบสั้นสีน้ำตาลตาม ORIGINAL MASTER

CHARACTER IDENTITY ANCHOR:
- ทรงผม สีผม และความยาวผมเป็นส่วนหนึ่งของ Identity ของแต่ละตัวละคร
- ทุกฉากให้ยึด Face + Hair + Body Identity + Overall Appearance จาก ORIGINAL MASTER ของคนนั้น
- รักษาสัดส่วนรูปร่างโดยรวมตาม ORIGINAL MASTER โดยไม่ออกแบบสรีระขึ้นใหม่ ${ImagePromptPolicy.referenceAppeal}
- เสื้อผ้า สถานที่ ท่าทาง และอารมณ์เปลี่ยนตามเรื่องได้ โดยไม่เปลี่ยน Identity
- เมื่อมีหลายตัวละคร ให้จับคู่ MASTER กับชื่อของแต่ละคนแยกกันก่อนสร้างภาพ

REFERENCE RULE:
คิดแบบคำสั่งสั้น:
“ใช้คนเดิมจากไฟล์ที่แนบ + ทำ ACTION ของฉาก + ใส่ WARDROBE ของฉาก + อยู่ LOCATION ของฉาก + 16:9”
รายละเอียดที่ไม่ได้สั่งให้เปลี่ยน ให้คงจาก ORIGINAL MASTER/continuity โดยไม่บรรยายซ้ำเกินจำเป็น
        """.trimIndent()
    }

    // ============================================================
    // CREATE MASTER PROMPT
    // ============================================================

    private fun buildMasterPrompt(
        story: String,
        category: String
    ): String {

        return rules() + """

STORY MASTER + EP 01 MASTER:

TITLE: $story
CATEGORY: $category
TOTAL EP: 5
SCENES PER EP: 20
TOTAL STORY SCENES: 100

กฎ STORY:
- เรื่องนี้ต้องดำเนินต่อเนื่องตั้งแต่ EP 01 ถึง EP 05
- CATEGORY และ TITLE ต้องคงเดิมตลอดทั้ง 5 EP
- ห้ามหมุน CATEGORY ระหว่าง EP
- แต่ละ EP มี 20 Scene
- เมื่อจบ Scene 20 ของแต่ละ EP ต้องสร้าง EP HANDOFF FILE เฉพาะ EP เพื่อส่งต่อ EP ถัดไป

DIRECTOR COMMAND:

$story

ดำเนินการสร้าง EP 01 ทันที

สร้าง EP 01 ให้ครบ 20 Scene

ทุก Scene ต้องมีข้อมูลครบ:

1. SCENE NUMBER
2. DURATION = 8 SECONDS
3. LOCATION
4. CHARACTERS
5. VISUAL / ACTION
6. EMOTION
7. DIALOGUE หรือ NARRATION
8. CAMERA
9. FLOW / VEO 3.1 PROMPT

AUTO-CONTEXT MEMORY:

CATEGORY ของ EP นี้คือ:
$category

ต้องแสดง CATEGORY ไว้ในส่วนหัวของ EP MASTER
และคง CATEGORY เดิมตลอด GENERATE SCENE / QC / REPAIR / PASS & LOCK / NEXT SCENE
ห้ามเปลี่ยนหมวดหมู่ระหว่าง EP

บททั้ง 20 Scene
ที่สร้างจากคำสั่งนี้
คือ EP MASTER
สำหรับ Workflow นี้

เมื่อได้รับคำสั่ง
GENERATE SCENE
QC
REPAIR
PASS & LOCK
หรือ NEXT SCENE

ให้ใช้ EP MASTER
จากบทสนทนานี้
เป็น Story Context

ห้ามขอให้ Director
คัดลอกบท Scene
กลับมาให้อีกครั้ง

ใช้ภาษาไทยธรรมชาติ
อ่านง่าย

เนื้อเรื่องต้องต่อเนื่อง
ตลอดทั้ง EP

เมื่อสร้างบทครบ 20 Scene:

ให้แสดงสถานะ:
SCENES 01–20 = PLANNED & LOCKED
NEXT = GENERATE SCENE 01

จากนั้น STOP
เมื่อ EP MASTER ถูกสร้างสำเร็จ ให้ตอบท้ายสุดว่า:
EP MASTER READY — RETURN TO AUTO-MOVIE AND PRESS CONFIRM
ห้ามถือว่าเข้าสู่ GENERATE SCENE จน Director ยืนยันใน AUTO-MOVIE

ห้ามแสดง SAVE_EP / HANDOFF / EP COMPLETE ในขั้น CREATE EP
เพราะคำเหล่านี้ใช้ได้เฉพาะหลัง SCENE 20 ผ่าน QC และ PASS & LOCK แล้วเท่านั้น

ห้ามสร้างภาพเองจนกว่าจะได้รับ GENERATE SCENE
        """.trimIndent()
    }

    // ============================================================
    // GENERATE SCENE — AUTO CONTEXT
    // ============================================================

    private fun buildSceneCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
GENERATE CURRENT SCENE

ACTIVE WORK STATE — SOURCE OF TRUTH:
TITLE: ${input.text.toString().trim()}
CATEGORY: ${selectedCategory ?: storyCategories[categoryIndex]}
CURRENT EP: ${fmt(currentEp)} / 05
CURRENT SCENE: ${fmt(currentScene)} / 20
DURATION: 8 SEC
FORMAT: 16:9

APP ↔ CHATGPT SYNC RULE:
- ค่าจาก ACTIVE WORK STATE คือค่าที่แอปกำลังใช้งานจริง
- TITLE / CATEGORY / CURRENT SCENE ต้องตรงกับ EP MASTER ในแชท
- ห้าม ChatGPT เปลี่ยน CATEGORY, TITLE หรือเลข Scene เอง
- ถ้า EP MASTER ในแชทไม่ตรงกับ ACTIVE WORK STATE ให้หยุดและรายงาน SYNC MISMATCH ห้ามสร้างภาพผิดเรื่อง
- CATEGORY ต้องคงเดิมตลอด STORY EP 01–05 และจะหมุนเฉพาะเมื่อ STORY COMPLETE แล้ว Director เริ่มเรื่องใหม่เท่านั้น

ขั้นตอนบังคับ:

1. ใช้ ACTIVE WORK STATE ด้านบนเป็นตัวระบุงานปัจจุบันเสมอ

2. ถ้าบทสนทนานี้มี EP MASTER ของ TITLE เดียวกัน ให้ดึง SCENE ${fmt(currentScene)} จาก EP MASTER เดิมโดยอัตโนมัติ

3. เมื่อสร้างภาพ SCENE ${fmt(currentScene)} สำเร็จจริงแล้ว ให้ตอบท้ายสุดว่า:
   SCENE OUTPUT READY — RETURN TO AUTO-MOVIE AND PRESS CONFIRM
   ห้ามถือว่าเข้าสู่ QC จน Director ยืนยันใน AUTO-MOVIE

4. ถ้าไม่พบ EP MASTER ในบทสนทนานี้ ห้ามเดาหรือสร้าง Scene ใหม่ และห้ามสลับไปใช้เรื่องอื่น ให้ตอบสั้น ๆ ว่า:
   EP MASTER NOT FOUND — TITLE: ${input.text.toString().trim()}
   กรุณากลับไปยังบทสนทนาที่สร้าง EP MASTER เรื่องนี้ แล้วสั่ง GENERATE SCENE ${fmt(currentScene)}

4. ห้ามถาม Director ให้ส่งบท SCENE ${fmt(currentScene)} ซ้ำ ถ้า EP MASTER ของเรื่องนี้มีอยู่แล้วในบทสนทนา

6. ห้ามแต่ง Scene ใหม่แทน Scene เดิม

6. ต้องยึดข้อมูลจาก Scene เดิม:

   - LOCATION
   - CHARACTERS
   - VISUAL / ACTION
   - EMOTION
   - DIALOGUE / NARRATION
   - CAMERA
   - FLOW / VEO 3.1 PROMPT
   - STORY CONTINUITY

7. ตรวจว่า Scene นี้
   ต้องใช้ตัวละครใครบ้าง

8. ตรวจ ORIGINAL IDENTITY MASTER
   ของตัวละครเหล่านั้น

9. ถ้า ORIGINAL IDENTITY MASTER
   ครบทั้งหมด:

   สร้างภาพ
   SCENE ${fmt(currentScene)}
   ทันที

   ห้ามถามคำถามเพิ่มเติม

10. ถ้า ORIGINAL IDENTITY MASTER
   ไม่ครบ:

   แจ้งเฉพาะชื่อ
   ตัวละครที่ยังขาด
   ORIGINAL IDENTITY MASTER

   ห้ามถามหาบท Scene

11. Identity Master
    ใช้สำหรับ:

    - Face
    - Skin
    - Natural Body
    - Hair
    - Approximate Age
    - Identity

    เท่านั้น

12. WARDROBE FIREWALL

    ก่อนสร้างภาพ ต้องแยก IDENTITY ออกจาก WARDROBE อย่างเด็ดขาด
    ORIGINAL IDENTITY MASTER ห้ามเป็นแหล่งข้อมูลเสื้อผ้า สีชุด รูปแบบชุด รองเท้า หรือความโป๊/ความปิดของชุด

    WARDROBE SOURCE PRIORITY:
    1. CURRENT SCENE MASTER
    2. SAME-DAY CONTINUITY จาก EP MASTER
    3. STORY CONTEXT
    4. DIRECTOR COMMAND

    SCENE STATE SNAPSHOT — BUILD 692:
    ให้แปลงข้อมูลที่มีอยู่แล้วใน CURRENT SCENE MASTER / EP MASTER เป็นข้อเท็จจริงของฉากปัจจุบันแบบสั้นเท่านั้น:
    - CURRENT_LOCATION = สถานที่ของ Scene นี้
    - CURRENT_WARDROBE = ชุดของตัวละครแต่ละคนตาม Scene/continuity
    - ACTIVE_PROP = วัตถุสำคัญที่ Scene นี้ระบุว่ากำลังใช้อยู่
    - CURRENT_ACTION = การกระทำหลักของ Scene นี้
    ใช้ Snapshot นี้เป็นข้อมูลฉากธรรมดาสำหรับการสร้างภาพ ไม่ต้องเพิ่มคำสั่ง continuity, validation หรือ repair loop ใหม่ และห้ามเดาข้อมูลที่ EP MASTER ไม่ได้ระบุ

    ถ้าชุดในภาพที่จะสร้างเหมือนชุดจาก ORIGINAL IDENTITY MASTER
    โดยไม่มีแหล่งข้อมูลข้างต้นรองรับ ต้องเปลี่ยนเป็นชุดที่ถูกต้องก่อนสร้าง Output

13. ห้ามใช้ Output
    จาก Scene ก่อนหน้า
    เป็น Identity Master

14. One Scene / One Image

15. หลังสร้างภาพเสร็จ:

    STOP

    รอ QC CHECK

ห้ามดำเนินการ
SCENE ${fmt(currentScene + 1)}
อัตโนมัติ
        """.trimIndent()
    }

    // ============================================================
    // QC CHECK
    // ============================================================

    private fun buildQcCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
QC CURRENT SCENE

CURRENT SCENE:
${fmt(currentScene)}

ค้นหา EP MASTER
และบท SCENE ${fmt(currentScene)}
จากบทสนทนาเดิม
โดยอัตโนมัติ

ห้ามถาม Director
ให้ส่งบท Scene ซ้ำ

ตรวจภาพ Output
ที่แนบจริง

ใช้ FAIL-CLOSED

ตรวจ:

- Identity
- Face
- Natural Body
- Skin
- Hair
- Approximate Age
- Character Consistency
- Wardrobe Source Priority
- Wardrobe Continuity
- Wardrobe Leakage เทียบกับ ORIGINAL IDENTITY MASTER
- WLF-01 Detection
- Location Continuity
- Story Continuity
- Action
- Emotion
- Composition
- Mouth
- Lip Sync
- Speaker Assignment
- Narration Rule
- Camera Compatibility
- Locked-off Compatibility
- 16:9 Compatibility
- One Scene / One Image

ผลการตรวจต้องเป็น:

PASS

หรือ

FAIL

ถ้า PASS:

ระบุว่า

SCENE ${fmt(currentScene)}
QC = PASS

พร้อมสำหรับ
PASS & LOCK

ถ้า FAIL:

ระบุเฉพาะ
จุดที่ไม่ผ่าน

ถ้าพบว่าเสื้อผ้า Output มาจาก/เลียนแบบ ORIGINAL IDENTITY MASTER
โดยไม่มี Scene/Continuity/Director รองรับ:
FAIL CODE:
WLF-01 — IDENTITY MASTER WARDROBE LEAK

สร้าง
DELTA REPAIR LIST

ห้ามแก้ส่วนที่ผ่านแล้ว

ห้ามสร้าง Scene ถัดไป
        """.trimIndent()
    }

    // ============================================================
    // REPAIR COMMAND
    // ============================================================

    private fun buildRepairCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
REPAIR CURRENT SCENE

CURRENT SCENE:
${fmt(currentScene)}

REPAIR ATTEMPT:
$repairCount / 3

ค้นหาโดยอัตโนมัติ:

1. EP MASTER
2. บท SCENE ${fmt(currentScene)}
3. ภาพ Output ล่าสุด
4. QC RESULT ล่าสุด
5. DELTA REPAIR LIST ล่าสุด

จากบทสนทนาเดียวกัน

ห้ามถาม Director
ให้ส่งข้อมูลเดิมซ้ำ
ถ้าข้อมูลมีอยู่แล้ว

REPAIR MODE:

DELTA-ONLY

แก้เฉพาะ
จุดที่ QC ไม่ผ่าน

รักษาทุกส่วน
ที่ผ่านแล้ว

ห้ามเปลี่ยนโดยไม่จำเป็น:

- Identity
- Face
- Natural Body
- Skin
- Hair
- Wardrobe Continuity
- Location
- Composition
- Action
- Story Continuity

ใช้ ORIGINAL IDENTITY MASTER
เท่านั้น

ห้ามใช้ Output
จาก Scene ก่อนหน้า
เป็น Identity Master

ถ้า QC RESULT ล่าสุดมี:
WLF-01 — IDENTITY MASTER WARDROBE LEAK

ให้ REPAIR เฉพาะ WARDROBE:
- เปลี่ยนชุดตาม CURRENT SCENE MASTER
- ถ้า Scene ไม่ระบุชุด ให้ใช้ SAME-DAY CONTINUITY จาก EP MASTER
- ห้ามคัดลอกชุดจาก ORIGINAL IDENTITY MASTER
- คง Face / Identity / Natural Body / Hair / Skin
- คง Location / Camera / Composition / Action / Emotion เท่าที่ไม่ขัดกับการแก้ Wardrobe

POST-REPAIR VALIDATION:
หลังสร้างภาพ Repair ใหม่ ต้องตรวจ defect เดิมซ้ำทันที

ถ้า defect เดิมยังอยู่:
REPAIR VALIDATION = FAIL
ห้ามถือว่า Repair สำเร็จ
ห้าม PASS & LOCK
ให้รอ REPAIR ครั้งถัดไป โดยนับตาม REPAIR ATTEMPT ปัจจุบัน สูงสุด 3 ครั้งต่อ Scene

ถ้า defect เดิมหาย:
REPAIR VALIDATION = CLEARED
จากนั้น STOP
รอ QC CHECK ใหม่

ห้าม PASS อัตโนมัติ
ห้ามไป Scene ถัดไป
        """.trimIndent()
    }

    // ============================================================
    // PASS & LOCK
    // ============================================================

    private fun buildLockCommand(): String {

        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
PASS & LOCK

ACTIVE WORK STATE:
TITLE: ${input.text.toString().trim()}
CATEGORY: ${selectedCategory ?: storyCategories[categoryIndex]}
EP: ${fmt(currentEp)} / 05
SCENE: ${fmt(currentScene)} / 20

ก่อนล็อก ต้องยืนยันว่า QC RESULT ล่าสุดของ EP ${fmt(currentEp)} SCENE ${fmt(currentScene)} = PASS อย่างชัดเจน
ถ้า QC = FAIL, มี WLF-01 หรือ POST-REPAIR VALIDATION ยังไม่ CLEARED ให้หยุด ห้าม LOCK

เมื่อ QC = PASS:
ล็อก Output ล่าสุดเป็นภาพที่ผ่าน QC ของ EP ${fmt(currentEp)} SCENE ${fmt(currentScene)}

${if (currentScene == 20 && currentEp < 5) """
EP ${fmt(currentEp)} COMPLETE

สร้างไฟล์เฉพาะ:
EP${fmt(currentEp)}_HANDOFF

HANDOFF FILE ต้องบันทึก:
- STORY TITLE และ CATEGORY
- EP ที่จบ และ EP ถัดไป
- สรุปเหตุการณ์สำคัญของ EP นี้
- สถานะตัวละครและความสัมพันธ์ล่าสุด
- จุดค้าง/ปมที่ต้องส่งต่อ
- Timeline / วัน / เวลา / สถานที่ล่าสุด
- Wardrobe Continuity ที่ต้องส่งต่อ
- Identity Master ที่ต้องใช้ต่อ
- ข้อเท็จจริงที่ห้ามเปลี่ยน
- OPEN LOOPS สำหรับ EP ถัดไป

HANDOFF TO: EP ${fmt(currentEp + 1)}
NEXT = CREATE EP ${fmt(currentEp + 1)} FROM HANDOFF
STOP
ห้ามเปลี่ยน TITLE หรือ CATEGORY
ห้ามเริ่ม EP ถัดไปเอง
""".trimIndent()
else if (currentScene == 20 && currentEp == 5) """
EP 05 COMPLETE
สร้างไฟล์เฉพาะ: EP05_HANDOFF_FINAL
SAVE STORY MASTER
STORY COMPLETE — 5 EP / 100 SCENES
STOP
ห้ามเริ่มเรื่องใหม่เอง
""".trimIndent()
else """
STOP
NEXT = NEXT SCENE
ห้ามเริ่ม Scene ถัดไปอัตโนมัติ
""".trimIndent()}
        """.trimIndent()
    }

    // ============================================================
    // NEXT SCENE — AUTO CONTEXT
    // ============================================================

    private fun buildNextSceneCommand(): String {

        val isNewEp = currentScene == 1 && currentEp > 1
        return rules() + """

AUTO-CONTEXT MODE:
ON

COMMAND:
${if (isNewEp) "CREATE NEXT EP FROM HANDOFF" else "NEXT SCENE"}

ACTIVE WORK STATE:
TITLE: ${input.text.toString().trim()}
CATEGORY: ${selectedCategory ?: storyCategories[categoryIndex]}
CURRENT EP: ${fmt(currentEp)} / 05
CURRENT SCENE: ${fmt(currentScene)} / 20

${if (isNewEp) """
EP TRANSITION:
- ค้นหา EP${fmt(currentEp - 1)}_HANDOFF จากบทสนทนาเดียวกัน
- ใช้ HANDOFF นั้นสร้าง EP ${fmt(currentEp)} MASTER จำนวน 20 Scene
- ต้องต่อจากเหตุการณ์/ความสัมพันธ์/Timeline/Wardrobe/Open Loops เดิม
- TITLE และ CATEGORY ต้องเหมือน STORY MASTER เดิม 100%
- ห้ามหมุน CATEGORY
- ห้ามสร้างเรื่องใหม่
- เมื่อสร้าง EP ${fmt(currentEp)} MASTER ครบ ให้ STOP
- NEXT = GENERATE SCENE 01
""".trimIndent()
else """
PREVIOUS SCENE: ${fmt(currentScene - 1)} = PASS & LOCK
- ใช้ EP ${fmt(currentEp)} MASTER เดิม
- ดึง SCENE ${fmt(currentScene)} โดยอัตโนมัติ
- รักษา Story / Character / Wardrobe / Location / Emotion / Timeline Continuity
- ตรวจ ORIGINAL IDENTITY MASTER ของตัวละครที่ต้องปรากฏ
- One Scene / One Image
- หลังดำเนินการ STOP และรอ QC CHECK
- ห้ามข้ามไป Scene ถัดไป
""".trimIndent()}
        """.trimIndent()
    }

    // ============================================================
    // APP UPDATE
    // ============================================================

    private fun checkForAppUpdate(silent: Boolean = false) {
        if (!silent) {
            status.text = "🔄 กำลังตรวจสอบเวอร์ชันล่าสุด…"
        }

        Thread {
            try {
                // Every build now has an immutable release tag such as R6.24-B642.
                // Read the release marked "latest" and cache-bust the request.
                val noCacheUrl =
                    "https://api.github.com/repos/pearparinya/auto-movie-r6-4/releases/latest?cb=" +
                    System.currentTimeMillis()
                val connection = (URL(noCacheUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "AUTO-MOVIE-Android")
                    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("If-None-Match", "")
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("GitHub HTTP $responseCode")
                }

                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val release = JSONObject(json)
                val rawTag = release.optString("tag_name")
                val tag = Regex("""(?:R|v)?(\d+(?:\.\d+)+)""", RegexOption.IGNORE_CASE)
                    .find(rawTag)?.groupValues?.getOrNull(1) ?: appVersion()
                val releaseBody = release.optString("body")
                val remoteBuild = Regex("""BUILD\s*[:#]?\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(releaseBody)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val assets = release.optJSONArray("assets")
                    ?: throw IllegalStateException("ไม่พบไฟล์ APK")

                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    throw IllegalStateException("ไม่พบไฟล์ APK ในรุ่นล่าสุด")
                }

                runOnUiThread {
                    val localBuild = packageManager.getPackageInfo(packageName, 0).versionCode
                    val versionIsNewer = isNewerVersion(tag, appVersion())
                    val sameVersionNewerBuild = !versionIsNewer &&
                        !isNewerVersion(appVersion(), tag) &&
                        remoteBuild > localBuild
                    if (!versionIsNewer && !sameVersionNewerBuild) {
                        if (!silent) {
                            status.text = "✓ AUTO-MOVIE R${appVersion()} • BUILD ${packageManager.getPackageInfo(packageName, 0).versionCode} เป็นเวอร์ชันล่าสุดแล้ว"
                        }
                    } else {
                        showUpdateDialog(tag, remoteBuild, apkUrl)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!silent) {
                        status.text = "⚠ ตรวจสอบอัปเดตไม่สำเร็จ"
                        AlertDialog.Builder(this)
                            .setTitle("ตรวจสอบอัปเดตไม่สำเร็จ")
                            .setMessage("กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง\n\n${e.message ?: ""}")
                            .setPositiveButton("ตกลง", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun showUpdateDialog(version: String, build: Int, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("พบ AUTO-MOVIE R$version • BUILD $build")
            .setMessage("มีเวอร์ชันใหม่พร้อมใช้งาน\n\nกด “ดาวน์โหลดและติดตั้ง” เพื่อเริ่มอัปเดต")
            .setNegativeButton("ไว้ภายหลัง", null)
            .setPositiveButton("ดาวน์โหลดและติดตั้ง") { _, _ ->
                downloadAndInstallUpdate(version, apkUrl)
            }
            .show()
    }

    private fun downloadAndInstallUpdate(version: String, apkUrl: String) {
        val buildNumber = try {
            Regex("""BUILD-(\d+)""", RegexOption.IGNORE_CASE)
                .find(apkUrl)?.groupValues?.getOrNull(1) ?: "update"
        } catch (_: Exception) {
            "update"
        }
        val fileName = "AUTO-MOVIE-R$version-BUILD-$buildNumber.apk"
        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir == null) {
            status.text = "⛔ ไม่พบพื้นที่สำหรับดาวน์โหลด"
            return
        }
        val targetFile = File(downloadDir, fileName)

        status.text = "⬇ กำลังดาวน์โหลด AUTO-MOVIE R$version…"

        Thread {
            try {
                if (targetFile.exists()) targetFile.delete()

                val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    useCaches = false
                    setRequestProperty("User-Agent", "AUTO-MOVIE-Android")
                    setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream,*/*")
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IllegalStateException("ดาวน์โหลด HTTP $responseCode")
                }

                val total = connection.contentLengthLong
                var downloaded = 0L
                var lastPercent = -1L

                connection.inputStream.use { input ->
                    targetFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            downloaded += count

                            if (total > 0L) {
                                val percent = ((downloaded * 100L) / total).coerceIn(0L, 100L)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    runOnUiThread {
                                        status.text = "↓ AUTO-MOVIE R$version • $percent%"
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }
                connection.disconnect()

                if (!targetFile.exists() || targetFile.length() < 100_000L) {
                    throw IllegalStateException("ไฟล์ APK ที่ดาวน์โหลดไม่สมบูรณ์")
                }

                runOnUiThread {
                    status.text = "✓ ดาวน์โหลดเสร็จแล้ว • เปิดหน้าติดตั้ง"
                    installDownloadedApk(targetFile)
                }
            } catch (e: Exception) {
                try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                runOnUiThread {
                    status.text = "⛔ ดาวน์โหลดอัปเดตไม่สำเร็จ"
                    AlertDialog.Builder(this)
                        .setTitle("ดาวน์โหลดอัปเดตไม่สำเร็จ")
                        .setMessage("ระบบดาวน์โหลดในแอปทำงานไม่สำเร็จ\n\n${e.message ?: e.javaClass.simpleName}")
                        .setPositiveButton("ตกลง", null)
                        .show()
                }
            }
        }.start()
    }

    private fun installDownloadedApk(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            status.text = "⛔ ไม่พบไฟล์อัปเดต"
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            status.text = "⚠ กรุณาอนุญาตติดตั้งแอปจาก AUTO-MOVIE"
            AlertDialog.Builder(this)
                .setTitle("ต้องอนุญาตการติดตั้ง")
                .setMessage("กด “เปิดการตั้งค่า” แล้วเปิด “อนุญาตจากแหล่งนี้” จากนั้นกลับมาที่ AUTO-MOVIE และกด Update อีกครั้ง")
                .setNegativeButton("ยกเลิก", null)
                .setPositiveButton("เปิดการตั้งค่า") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    }
                }
                .show()
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Prefer Android's system package installer directly so the
            // "Open with" chooser is skipped when multiple APK handlers exist.
            val handlers = packageManager.queryIntentActivities(installIntent, 0)
            val systemInstaller = handlers.firstOrNull { info ->
                (info.activityInfo.applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            }

            if (systemInstaller != null) {
                installIntent.setClassName(
                    systemInstaller.activityInfo.packageName,
                    systemInstaller.activityInfo.name
                )
            }

            startActivity(installIntent)
        } catch (e: Exception) {
            status.text = "⚠ เปิดตัวติดตั้งไม่สำเร็จ"
            AlertDialog.Builder(this)
                .setTitle("เปิดตัวติดตั้งไม่สำเร็จ")
                .setMessage("Android อาจต้องอนุญาตให้ AUTO-MOVIE ติดตั้งแอปจากแหล่งนี้ก่อน")
                .setPositiveButton("ตกลง", null)
                .show()
        }
    }

    // ============================================================
    // HELP
    // ============================================================

    private fun showHelp() {

        AlertDialog.Builder(this)
            .setTitle("วิธีใช้งาน AUTO-MOVIE • QUICK FLOW")
            .setMessage(
                """
ใช้งานเพียง 3 ขั้นตอน

① NEW STORY
กด “สร้างเรื่องใหม่” แล้วเลือกชื่อเรื่องที่ต้องการ ระบบจะล็อกชื่อเรื่องและหมวดเรื่องไว้ตลอด EP 01–05

CHARACTER MASTER
ตั้งค่ารูป กวิน / รินลดา / มายด์ เพียงครั้งเดียว แอปเก็บรูปต้นฉบับไว้ในเครื่องและแนบให้อัตโนมัติทุกครั้งที่ START / NEXT SCENE
รูปทั้ง 3 คือ ORIGINAL IDENTITY MASTER ใช้สำหรับใบหน้า ทรงผม สีผิว และรูปร่างธรรมชาติเท่านั้น เสื้อผ้าในรูปไม่ใช่ชุดประจำตัว

② START
กดครั้งเดียว แอปส่ง Character Master + คำสั่งไป ChatGPT
จากนั้นส่งข้อความใน ChatGPT เพื่อให้สร้าง STORY BIBLE / โครงเรื่อง และสร้างภาพ SCENE 01 ต่อทันที
ไม่ต้อง CONFIRM / QC / REPAIR / LOCK

③ NEXT SCENE
เมื่อได้ภาพฉากปัจจุบันแล้ว กลับ AUTO-MOVIE และกด NEXT SCENE
ระบบจะเลื่อนไปฉากถัดไป แนบ ORIGINAL CHARACTER MASTER เดิม และรักษาความต่อเนื่องให้อัตโนมัติ
ทำซ้ำจนจบ 20 ฉากต่อ EP และต่อเนื่องถึง EP 05

สำคัญ:
• ใช้ ORIGINAL MASTER เดิมเสมอ ห้ามใช้ภาพ AI ที่สร้างภายหลังเป็น Master ใหม่
• ตัวละครรองให้ระบบสร้างและรักษาความต่อเนื่องอัตโนมัติ
• ทุกฉาก 8 วินาที • 16:9 • Flow/Veo 3.1 • Locked-off
                """.trimIndent()
            )
            .setPositiveButton("เข้าใจแล้ว", null)
            .show()
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private fun playTone(tone: Int, durationMs: Int) {
        if (!soundEnabled) return
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35).apply {
                startTone(tone, durationMs)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { release() } catch (_: Exception) {}
                }, durationMs.toLong() + 80L)
            }
        } catch (_: Exception) {
            // เสียงเป็น feedback เสริมเท่านั้น ต้องไม่รบกวน workflow หากอุปกรณ์เล่นเสียงไม่ได้
        }
    }

    private val clockTick = object : Runnable {
        override fun run() {
            updateTimeLabel()
            clockHandler.postDelayed(this, 1000L)
        }
    }

    private fun startClock() {
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    private fun updateTimeLabel() {
        if (!::timeLabel.isInitialized) return

        val now = System.currentTimeMillis()
        val dateText = SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale("th", "TH"))
            .format(Date(now))

        val elapsedMs = when {
            storyStartTimeMs <= 0L -> 0L
            storyEndTimeMs > 0L -> storyEndTimeMs - storyStartTimeMs
            else -> now - storyStartTimeMs
        }.coerceAtLeast(0L)

        val totalSeconds = elapsedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val storyTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        timeLabel.text =
            if (storyStartTimeMs > 0L) {
                "🕐 $dateText   •   ⏱ STORY $storyTime"
            } else {
                "🕐 $dateText   •   ⏱ STORY --:--:--"
            }
    }

    override fun onPause() {
        if (::input.isInitialized) saveWorkState()
        super.onPause()
    }

    override fun onStop() {
        if (::input.isInitialized) saveWorkState()
        super.onStop()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    private fun stepAction(
        step: Int,
        title: String,
        subtitle: String,
        function: () -> Unit
    ): Button {
        return action(title, subtitle, function).also { button ->
            stepButtons[step] = button
        }
    }

    private fun migrateWorkflowStateIfNeeded() {
        val migrationKey = "workflow_gate_v618"
        if (statePrefs.getBoolean(migrationKey, false)) return

        // R6.17 และเก่ากว่าเคยบันทึก activeStep ที่กระโดดข้ามได้
        // รีเซ็ตเฉพาะ workflow งานเก่า 1 ครั้ง เพื่อให้ R6.18 เริ่มทดสอบตามลำดับจริง
        currentEp = 1
        currentScene = 1
        repairCount = 0
        sceneLocked = false
        selectedCategory = null
        activeStep = 1
        storyStartTimeMs = 0L
        storyEndTimeMs = 0L
        input.setText("")
        titlePanel.visibility = android.view.View.GONE

        statePrefs.edit()
            .clear()
            .putBoolean(migrationKey, true)
            .putBoolean("soundEnabled", soundEnabled)
            .apply()

        updateTimeLabel()
        updateUi()
        status.text = "✓ R6.18 พร้อมใช้งาน • เริ่มตามลำดับจากขั้นตอน ①"
    }

    private fun storyInProgress(): Boolean {
        return storyStartTimeMs > 0L && storyEndTimeMs == 0L
    }

    private fun updateNextButton() {
        if (!::nextButton.isInitialized) return

        when {
            currentEp == 5 && currentScene == 20 && sceneLocked -> {
                nextButton.text = "🏁 STORY COMPLETE"
                nextButton.isEnabled = false
                nextButton.alpha = 0.38f
            }
            currentScene == 20 && currentEp < 5 && sceneLocked -> {
                nextButton.text = "→ ส่งต่อ EP ${fmt(currentEp + 1)} • HANDOFF"
                nextButton.isEnabled = activeStep == 7
                nextButton.alpha = if (nextButton.isEnabled) 1f else 0.38f
            }
            else -> {
                nextButton.text = "→ ฉากถัดไป • NEXT SCENE"
                nextButton.isEnabled = sceneLocked && activeStep == 7
                nextButton.alpha = if (nextButton.isEnabled) 1f else 0.38f
            }
        }
    }

    private fun requireStep(required: Int): Boolean {
        if (activeStep == required) return true
        rejectOutOfOrder(required)
        return false
    }

    private fun rejectOutOfOrder(requested: Int) {
        playTone(ToneGenerator.TONE_PROP_NACK, 120)
        status.text = "⛔ ทำตามลำดับก่อน • ตอนนี้อยู่ขั้นตอน $activeStep"
        refreshStepIndicator()
    }

    private fun setActiveStep(step: Int) {
        val nextStep = step.coerceIn(1, 3)
        val changed = nextStep != activeStep
        activeStep = nextStep
        refreshStepIndicator()
        if (changed && lastSoundStep != activeStep) {
            playTone(ToneGenerator.TONE_PROP_BEEP, 65)
            lastSoundStep = activeStep
        }
        if (::input.isInitialized) saveWorkState()
    }

    private fun refreshStepIndicator() {
        stepButtons.forEach { (step, button) ->
            val active = step == activeStep
            button.isEnabled = active
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (active) activeGreen else panel)
            button.setTextColor(if (active) Color.WHITE else ice)
            button.elevation = dp(if (active) 8 else 2).toFloat()
            button.alpha = if (active) 1f else 0.38f
        }
        if (::nextHint.isInitialized) {
            nextHint.text = when (activeStep) {
                1 -> "① เลือกชื่อเรื่อง • NEW STORY"
                2 -> "② START → โครงเรื่อง + ภาพฉาก 01 ในครั้งเดียว"
                3 -> "③ ได้ภาพแล้ว → กด NEXT SCENE เพื่อสร้างฉากต่อไป"
                else -> ""
            }
        }
    }

    private fun action(
        title: String,
        subtitle: String,
        function: () -> Unit
    ): Button {

        return Button(this).apply {

            text = if (title.firstOrNull() in listOf('❶','❷','❸','❹','❺','❻')) {
                android.text.SpannableString(title).apply {
                    setSpan(
                        android.text.style.AbsoluteSizeSpan(22, true),
                        0, 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                title
            }
            contentDescription = "$title — $subtitle"

            textSize = 14f

            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(
                11, 14, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP
            )

            isAllCaps = false

            gravity =
                Gravity.CENTER

            setPadding(
                dp(12),
                dp(3),
                dp(12),
                dp(3)
            )

            minHeight = dp(34)

            setTextColor(ice)
            setTypeface(typeface, Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(panel)
            elevation = dp(2).toFloat()

            setOnClickListener {
                if (!isEnabled) {
                    playTone(ToneGenerator.TONE_PROP_NACK, 120)
                    return@setOnClickListener
                }
                function()
            }
        }
    }

    private fun section(
        textValue: String
    ): TextView {

        return TextView(this).apply {

            text =
                textValue

            textSize =
                12f

            setTextColor(gold)

            setTypeface(
                typeface,
                Typeface.BOLD
            )

            setPadding(
                0,
                dp(2),
                0,
                dp(2)
            )
        }
    }

    private fun full(
        height: Int =
            LinearLayout.LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams {

        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        ).apply {

            setMargins(
                0,
                dp(1),
                0,
                dp(1)
            )
        }
    }

    private fun fmt(
        number: Int
    ): String {

        return number
            .toString()
            .padStart(
                2,
                '0'
            )
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }

    private fun hideKeyboard() {

        val view =
            currentFocus ?: return

        val imm =
            getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        imm.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }
}
