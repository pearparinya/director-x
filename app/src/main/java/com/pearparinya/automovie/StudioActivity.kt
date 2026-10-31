package com.pearparinya.automovie

import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun buildChatShareIntent(text: String, uris: ArrayList<Uri>): Intent =
    Intent(if (uris.size <= 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (uris.isEmpty()) "text/plain" else {
            val extensions = uris.map { it.lastPathSegment?.substringAfterLast('.')?.lowercase() }.distinct()
            when (extensions.singleOrNull()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/*" }
        }
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"คำสั่งจาก DIRECTOR X")
        putExtra(Intent.EXTRA_TITLE,"DIRECTOR X")
        clipData = ClipData.newPlainText("คำสั่ง DIRECTOR X",text).apply {
            uris.forEach { addItem(ClipData.Item(it)) }
        }
        if (uris.isNotEmpty()) {
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM,uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Keep ChatGPT in its own Android task. Without NEW_TASK, some Android
        // launchers place the ChatGPT screen inside DIRECTOR X's Recents card,
        // so selecting DIRECTOR X only reopens ChatGPT.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** Mixed document and image transfer; the receiving AI still controls submission and execution. */
internal fun buildStoryPackageIntent(text: String, uris: ArrayList<Uri>): Intent {
    require(uris.size == 4) { "ต้องมีไฟล์คำสั่งและ MASTER 3 ภาพ" }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type="*/*"
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"DIRECTOR X • เขียนบทและส่งกลับ story.json")
        putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
        clipData=ClipData.newPlainText("คำสั่งวางบท",text).apply { uris.forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Carry the instruction as an actual attachment when Gemini ignores EXTRA_TEXT. */
internal fun buildGeminiPackageIntent(text: String, command: Uri, images: ArrayList<Uri>): Intent {
    require(images.isNotEmpty() && images.size<=9) { "ต้องมี MASTER 1–9 ภาพพร้อมไฟล์คำสั่ง" }
    val files=arrayListOf(command).apply { addAll(images) }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type="*/*"
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"สร้างภาพตามไฟล์ READ_FIRST_IMAGE_COMMAND.txt พร้อม MASTER ที่แนบ")
        putParcelableArrayListExtra(Intent.EXTRA_STREAM,files)
        clipData=ClipData.newPlainText("คำสั่งและ MASTER",text).apply { files.forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal const val GEMINI_IMAGE_REQUEST = "สร้างและแสดงภาพจริง 1 ภาพตามคำสั่งในไฟล์ READ_FIRST_IMAGE_COMMAND.txt ที่แนบ ใช้ภาพ MASTER ที่แนบจับคู่กับตัวละครตามชื่อไฟล์ รักษาข้อความบนปกตามไฟล์คำสั่ง หากเป็นภาพฉากให้ทำตามข้อกำหนดในไฟล์ ไม่ต้องสรุปคำสั่งหรืออธิบายตัวละคร หากสร้างภาพไม่ได้ให้แจ้งสาเหตุ"

internal fun neutralIdentityGuide(source: String, aspect: String): String =
    ImagePromptPolicy.identity(source) + "\n" + ImagePromptPolicy.style(aspect) + " ไม่มีข้อความหรือลายน้ำ หากสร้างไม่ได้ให้แจ้งตามจริง"

/** Whole-story workspace. Legacy preferences and character masters stay untouched. */
class StudioActivity : AppCompatActivity() {
    private val storyUiHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var geminiStatusView: TextView?=null
    private var geminiProgressView: ProgressBar?=null
    private var geminiLiveDialog: TextView?=null
    private val storyPoll=object: Runnable { override fun run() {
        if(!isFinishing) {
            val status=readGeminiStatus()
            geminiStatusView?.text=geminiStatusText(status)
            geminiProgressView?.progress=status?.optInt("percent")?.coerceIn(0,100) ?: 0
            geminiLiveDialog?.text=geminiStatusText(status)+if(status?.optString("state")=="running" && GeminiStoryService.running) "\n\nระบบจะทำต่อเองระหว่างรอ • กดหยุดเมื่อต้องการพักงาน" else ""
            if(plan==null && File(project,"story.json").isFile && !importing) render()
            storyUiHandler.postDelayed(this,1000)
        }
    } }
    private val characterMemory by lazy {
        CharacterMemoryStore(File(filesDir,"character_memory"), JSONObject(assets.open("character_profiles.json").bufferedReader().use { it.readText() }))
    }
    private val detailedImageProfiles get() = prefs.getBoolean("detailed_image_profiles",false)
    private fun promptProfile(id: String, source: File): String =
        if(detailedImageProfiles) characterMemory.brief(id,source) else ""
    private fun memoryId(person: JSONObject): String = FlowCast.id(person) ?: person.optString("master").ifBlank { person.optString("name") }
    private fun memoryBrief(person: JSONObject): String {
        val profile=characterMemory.read(memoryId(person),masterFor(person))
        return ((listOf("face","hair") + BodyReferencePolicy.fields.map { it.first }).map { profile.optString(it) } +
            if(person.has("accessories")) person.optString("accessories") else profile.optString("accessories"))
            .filter { it.isNotBlank() }.joinToString("; ")
    }
    private val base get() = File(filesDir, "studio").apply { mkdirs() }
    private val prefs get() = getSharedPreferences("studio", MODE_PRIVATE)
    private val project get() = File(base, prefs.getString("project", "draft")!!).apply { mkdirs() }
    private val pendingRecovery get() = File(filesDir, "pending_story_import")
    private var plan: JSONObject? = null
    private var importing = false
    private var pendingStoryShare: (() -> Unit)? = null
    private var pendingStorySummary = ""
    private var pendingReceipt: (() -> Unit)? = null
    private var exportEpisode: Int?
        get() = prefs.getInt("export_episode",0).takeIf { it>0 }
        set(value) { prefs.edit().putInt("export_episode",value ?: 0).apply() }
    private var backupMode: Boolean
        get() = prefs.getBoolean("backup_mode",false)
        set(value) { prefs.edit().putBoolean("backup_mode",value).apply() }
    private val updater = StudioUpdater(this)
    private lateinit var root: LinearLayout
    private val navy = Color.rgb(18,29,49)
    private val gold = Color.rgb(232,180,82)
    private val electricBlue = Color.rgb(30,156,255)
    private val cyan = Color.rgb(42,220,255)
    private val ice = Color.rgb(248,246,239)
    private val muted = Color.rgb(164,164,169)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun backdrop(top: Int, bottom: Int, border: Int? = null) = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(top,bottom)).apply {
        cornerRadius = dp(16).toFloat()
        if (border != null) setStroke(dp(1),border)
    }
    private val menuColors = arrayOf(
        intArrayOf(Color.rgb(255,218,112),Color.rgb(255,177,58)),
        intArrayOf(Color.rgb(119,232,250),Color.rgb(63,184,235)),
        intArrayOf(Color.rgb(219,195,255),Color.rgb(176,143,244)),
        intArrayOf(Color.rgb(128,240,202),Color.rgb(63,200,160)),
        intArrayOf(Color.rgb(255,185,209),Color.rgb(245,139,175)))
    private fun menuIcon(index: Int) = listOf("🎭", "🎨", "📚", "🎬", "💡")[index % 5]
    private fun choiceMenu(title: String, choices: List<String>, cancel: String = "✖️ ยกเลิก",
        cancelable: Boolean = true, onCancel: (() -> Unit)? = null, action: (Int) -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(18),dp(16),dp(12))
            background = backdrop(Color.rgb(24,24,27),navy,Color.rgb(74,61,39))
        }
        panel.addView(TextView(this).apply {
            text = title; textSize = 20f; setTextColor(gold); setTypeface(null,Typeface.BOLD)
            setPadding(0,0,0,dp(14))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        panel.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        val dialog = AlertDialog.Builder(this).setView(panel).setCancelable(cancelable).create()
        choices.forEachIndexed { index, value ->
            list.addView(TextView(this).apply {
                text = value; textSize = 16f; setTextColor(navy); setTypeface(null,Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL; minHeight = dp(60)
                setPadding(dp(14),dp(14),dp(14),dp(14))
                background = backdrop(Color.rgb(248,246,239),Color.rgb(226,234,243),Color.rgb(173,187,204))
                layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(10) }
                isClickable = true; isFocusable = true
                val attr = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground,attr,true)
                foreground = getDrawable(attr.resourceId)
                setOnClickListener { dialog.dismiss(); action(index) }
            })
        }
        if (choices.isEmpty()) list.addView(TextView(this).apply {
            text = "📭 ยังไม่มีรายการ"; textSize = 16f; setTextColor(ice); setPadding(0,dp(12),0,dp(12))
        })
        panel.addView(TextView(this).apply {
            text = cancel; textSize = 16f; setTextColor(ice); gravity = Gravity.CENTER
            minHeight = dp(48); background = backdrop(Color.rgb(36,36,40),Color.rgb(28,28,32))
            isFocusable = true; setOnClickListener { dialog.dismiss(); onCancel?.invoke() }
        })
        dialog.setOnCancelListener { onCancel?.invoke() }
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(24),-2)
        panel.post {
            val maximum = (resources.displayMetrics.heightPixels * 0.85f).toInt()
            val available = maximum - (panel.height - scroll.height)
            if (scroll.height > available) scroll.layoutParams = LinearLayout.LayoutParams(-1,available.coerceAtLeast(dp(80)))
        }
    }
    private val legacyCategories = listOf(
        "The Broken Vows | บันทึกรอยร้าวชีวิตคู่",
        "Sweet Revenge | เกมรักซ่อนลวง บทเรียนรสแซ่บ",
        "Family Secrets | ความลับใต้ชายคา...ที่ไม่มีใครกล้าพูด",
        "Twisted Truth | พล็อตหักมุม...ความจริงใต้เตียง",
        "Healing Hearts | ซ่อมรักหลังบ้าน ซีรีส์ฮีลใจ")
    private val categories = ChannelCatalog.mainCategories.map { it.label }
    private var masterSlot: String?
        get() = prefs.getString("master_slot", null)
        set(value) { prefs.edit().putString("master_slot", value).apply() }
    private val masterPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = masterSlot
        if (uri != null && slot in listOf("gawin", "rinlada", "mind")) {
            try {
                val folder = File(filesDir, "character_masters").apply { mkdirs() }
                val temporary = File(folder, "$slot.tmp")
                contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์นี้ไม่ใช่ภาพที่อ่านได้" }
                val previous=File(folder,"$slot.master"); if(previous.exists()) previous.copyTo(File(folder,"$slot.previous"),overwrite=true)
                require(temporary.renameTo(previous)) { "บันทึกภาพไม่ได้" }
                notice("บันทึกภาพแล้ว เปิดเมนูตรวจภาพ MASTER เพื่อเทียบกับต้นฉบับก่อนใช้งาน")
            } catch (e: Exception) { notice("รับภาพไม่สำเร็จ: ${e.message}") }
        }
        masterSlot = null
    }
    private val masterLibrary by lazy { MasterLibrary(File(filesDir,"master_library")) { assets.open(it).use { stream -> stream.readBytes() } } }
    private fun libraryMenu() {
        runCatching {
            val entries=masterLibrary.entries()
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            panel.addView(TextView(this).apply { text=if(entries.isEmpty()) "คลังว่าง • เพิ่มภาพใหม่ผ่านเมนูตัวละคร" else "${entries.size} แผ่น • หนึ่งแผ่นต่อหนึ่งตัวละคร" })
            val search=EditText(this).apply { hint="ค้นหา CHAR_001 หรือชื่อไฟล์"; isSingleLine=true; panel.addView(this) }
            val list=LinearLayout(this).apply { orientation=1 }
            panel.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,dp(420)))
            fun show(query: String) {
                list.removeAllViews()
                entries.filter { (it.id+" "+it.displayName+" "+it.original).contains(query,ignoreCase=true) }.forEach { entry ->
                    list.addView(android.widget.Button(this).apply {
                        text="${entry.id} • ${entry.displayName} • อายุสมมติ ${entry.characterAge} ปี • ${entry.original}"
                        setOnClickListener { libraryEntry(entry) }
                    })
                }
            }
            search.addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { show(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            show("")
            AlertDialog.Builder(this).setTitle("🎭 คลัง MASTER").setView(panel).setNegativeButton("ปิด",null).showDirector()
        }.onFailure { notice("เปิดคลังไม่ได้: ${it.message}") }
    }
    private fun libraryEntry(entry: MasterLibrary.Entry) {
        runCatching {
            val file=masterLibrary.source(entry)
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
            panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(400)); setOnClickListener { fullImage(file,entry.id) } })
            panel.addView(TextView(this).apply { text="${entry.id} • ${entry.displayName} • อายุสมมติ ${entry.characterAge} ปี • ${entry.original}\nภาพต้นฉบับ 3 มุมมอง แตะภาพเพื่อขยาย" })
            AlertDialog.Builder(this).setTitle("${entry.id} • ${entry.displayName}").setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("ใช้กับเรื่องใหม่") { _,_ ->
                    choiceMenu("กำหนดภาพ ${entry.id}",listOf("ตัวละครหลัก: กวิน","ตัวละครหลัก: รินรดา","ตัวละครหลัก: เมย์","เพิ่มตัวละครเสริมพร้อมตั้งชื่อ")) { slot ->
                        if(slot==3) addLibrarySupport(entry) else runCatching {
                            val id=listOf("gawin","rinlada","mind")[slot]
                            masterLibrary.assign(entry,File(filesDir,"character_masters/$id.master"))
                            notice("เลือก ${entry.id} สำหรับเรื่องใหม่แล้ว • เรื่องเดิมใช้ภาพเดิม")
                        }.onFailure { notice(it.message ?: "บันทึกไม่ได้") }
                    }
                }.setNegativeButton("ปิด",null).showDirector()
        }.onFailure { notice(it.message ?: "เปิดภาพไม่ได้") }
    }
    private fun addLibrarySupport(entry: MasterLibrary.Entry) {
        if(readSupportingCast().length()>=8) { notice("เลือกตัวละครเสริมได้ไม่เกิน 8 คนต่อเรื่อง • นำคนเดิมออกก่อน"); return }
        val input=EditText(this).apply { hint="ชื่อตัวละครในเรื่อง"; setText(entry.displayName) }
        AlertDialog.Builder(this).setTitle("เพิ่ม ${entry.id} สำหรับเรื่องใหม่").setView(input).setPositiveButton("เพิ่ม") { _,_ ->
            runCatching {
                val name=input.text.toString().trim()
                val roster=readSupportingCast()
                require(name.isNotBlank() && StoryIdentity.keyFor(name)==null && (0 until roster.length()).none { roster.getJSONObject(it).getString("name")==name }) { "ระบุชื่อไม่ซ้ำกับตัวละครที่มีอยู่" }
                require((0 until roster.length()).none { roster.getJSONObject(it).optString("library_id")==entry.id }) { "เลือก MASTER นี้ไว้แล้ว" }
                val id="support_"+UUID.randomUUID().toString().take(8)
                val person=JSONObject().put("name",name).put("master","${id.uppercase()}_MASTER.png")
                    .put("library_id",entry.id).put("visual_description",MasterLibrary.description(entry.id))
                roster.put(person)
                require(FlowCast.issues(JSONObject().put("supporting_characters",roster)).isEmpty()) { "ข้อมูลตัวละครไม่ถูกต้อง" }
                masterLibrary.assign(entry,File(filesDir,"supporting_masters/$id.master"))
                // Keep the existing array format consumed by legacy projects and UI.
                val target=File(filesDir,"supporting_cast.json")
                val pending=File(filesDir,"supporting_cast.json.pending")
                pending.writeText(roster.toString(2)); check(pending.renameTo(target))
                notice("เพิ่ม $name จาก ${entry.id} แล้ว • ใช้เมื่อสร้างเรื่องใหม่")
            }.onFailure { notice(it.message ?: "เพิ่มไม่ได้") }
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun selectedLeadReferences(): JSONObject {
        val entries=masterLibrary.entries()
        val selected=JSONObject()
        FlowProductionPack.names.keys.forEach { id ->
            val file=File(filesDir,"character_masters/$id.master")
            if(file.isFile) {
                val hash=MasterLibrary.digest(file.readBytes())
                entries.firstOrNull { it.sha256==hash }?.let { selected.put(id,JSONObject().put("library_id",it.id).put("visual_description",MasterLibrary.description(it.id))) }
            }
        }
        return selected
    }

    private fun characterMasters() {
        val names=listOf("gawin" to "กวิน", "rinlada" to "รินรดา", "mind" to "เมย์")
        choiceMenu("🎭 MASTER และความจำตัวละคร",names.map { (id,name) -> val file=File(filesDir,"character_masters/$id.master"); "$name • ${if(characterMemory.read(id,file).optLong("reference_reviewed_at")>0) "ตรวจภาพแล้ว" else "ยังไม่ได้ตรวจภาพ"}" } + listOf("🎭 คลัง MASTER")) { i ->
            if (i == names.size) { libraryMenu() } else {
                val (id,name)=names[i]; val file=File(filesDir,"character_masters/$id.master")
                choiceMenu(name,listOf("ดู MASTER","เปลี่ยนภาพ MASTER","ข้อมูลใบหน้า ผม และรูปร่าง","เตรียม MASTER มาตรฐาน","ตรวจภาพ MASTER")) { action -> when(action) {
                    0 -> if(file.exists()) fullImage(file,name) else notice("ยังไม่มี MASTER")
                    1 -> { masterSlot=id; masterPicker.launch(arrayOf("image/*")) }
                    2 -> editCharacterMemory(id,name,file)
                    3 -> sendProductionMaster(id,name,file)
                    4 -> reviewMaster(id,name,file)
                } }
            }
        }
    }
    private fun sendProductionMaster(id: String, name: String, file: File) {
        if(!file.exists()) { notice("เพิ่ม MASTER ก่อน"); return }
        val source=masterExportName(file,false) ?: run { notice("อ่านภาพ MASTER ไม่ได้"); return }
        val command=ImagePromptPolicy.productionMaster(name,source,promptProfile(id,file))
        showFlowCode("คำสั่งเตรียม MASTER ใน Flow",command,"${id.uppercase()}_MASTER_COMMAND.txt")
    }
    private fun reviewMaster(id: String, name: String, file: File) {
        if(!file.exists()) { notice("ยังไม่มี MASTER"); return }
        val profile=characterMemory.read(id,file)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(320)) })
        panel.addView(TextView(this).apply { text="ตรวจเทียบต้นฉบับ: ใบหน้าและผมตรงรายคน • รูปร่างไม่เปลี่ยนโดยไม่ตั้งใจ • มือปกติ • ชุดพื้นฐานชัดเจน • แสงและภาพคมชัด\nการยืนยันนี้เป็นผลตรวจของผู้ใช้ ไม่ใช่ผลรับรองจากระบบสร้างภาพ" })
        AlertDialog.Builder(this).setTitle("ตรวจ MASTER • $name").setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("ตรวจแล้ว ใช้ภาพนี้") { _,_ -> profile.put("reference_reviewed_at",System.currentTimeMillis()); characterMemory.save(id,file,profile); notice("บันทึกผลตรวจของภาพนี้แล้ว") }
            .setNeutralButton("เลือกภาพใหม่") { _,_ -> if(file.parentFile?.name=="character_masters") { masterSlot=id; masterPicker.launch(arrayOf("image/*")) } else { castTarget=name to file; castPicker.launch(arrayOf("image/*")) } }
            .setNegativeButton("ปิด",null).showDirector()
    }
    private fun editCharacterMemory(id: String, name: String, file: File) {
        if (!file.exists()) { notice("เพิ่มภาพ MASTER ก่อนบันทึกโปรไฟล์"); return }
        val profile=characterMemory.read(id,file)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        panel.addView(TextView(this).apply { text="ข้อมูลนี้ผูกกับภาพ MASTER นี้เท่านั้น • บันทึกเฉพาะสิ่งที่เห็น ชุดและฉากกำหนดในบท" })
        panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(240)) })
        val fields=listOf("face" to "ใบหน้าและดวงตา", "hair" to "ผมและหนวดเครา") + BodyReferencePolicy.fields + ("accessories" to "เครื่องประดับอ้างอิง (เปลี่ยนตามบทได้)")
        val inputs=fields.map { (key,label) -> key to EditText(this).apply { hint=label; setText(profile.optString(key)); panel.addView(this) } }
        AlertDialog.Builder(this).setTitle("ความจำตัวละคร • $name").setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("บันทึก") { _,_ -> inputs.forEach { (key,input) -> profile.put(key,input.text.toString().trim()) }; characterMemory.save(id,file,profile); notice("บันทึกข้อมูลอ้างอิงของ $name แล้ว") }
            .setNegativeButton("ยกเลิก",null).showDirector()
    }
    private val otherGenres = listOf("ดราม่าชีวิต", "ครอบครัว", "โรแมนติก", "ตลก", "สืบสวน", "ระทึกขวัญ", "สยองขวัญ", "แฟนตาซี", "แอ็กชัน", "อบอุ่นหัวใจ")
    private val smartDirector = "ระบบกำกับละครอัจฉริยะ"
    private val genreLabels = listOf(
        "🎭 ดราม่าชีวิต\nต่อสู้ชีวิตและการเริ่มต้นใหม่",
        "🏡 ครอบครัว\nความรักและความผูกพันในบ้าน",
        "💕 โรแมนติก\nเรื่องรักและความสัมพันธ์",
        "😂 ตลก\nเรื่องสนุก ชวนยิ้มและหัวเราะ",
        "🔎 สืบสวน\nตามเบาะแส เปิดเผยความจริง",
        "😱 ระทึกขวัญ\nลุ้นทุกฉาก ปริศนาและอันตราย",
        "👻 สยองขวัญ\nเรื่องลี้ลับและความน่ากลัว",
        "✨ แฟนตาซี\nโลกจินตนาการและพลังวิเศษ",
        "🔥 แอ็กชัน\nภารกิจ การไล่ล่าและการต่อสู้",
        "🌷 อบอุ่นหัวใจ\nความหวัง การช่วยเหลือและกำลังใจ")
    private val originalChannel = ChannelCatalog.channel
    private val planPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) background {
            val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val candidate = JSONObject(text.removePrefix("\uFEFF"))
            validate(candidate)
            val id = UUID.randomUUID().toString()
            val folder = File(base, id).apply { mkdirs() }
            File(folder, "story.json").writeText(candidate.toString(2))
            prefs.edit().putString("project", id).commit()
        }
    }
    private val packagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) receivePackage(uris)
    }
    private fun receivePackage(selected: List<Uri>, confirmed: Boolean = false) {
        importing = true
        val dialog = AlertDialog.Builder(this).setMessage("กำลังตรวจบทและภาพ…").setCancelable(false).showDirector()
        Thread {
            try {
                File(cacheDir,"story_import").deleteRecursively()
                val staging = File(cacheDir, "story_import/${UUID.randomUUID()}").apply { mkdirs() }
                val files = mutableListOf<Uri>()
                val failedZipEntries = mutableListOf<String>()
                for (uri in selected) {
                    if (displayName(uri).endsWith(".zip", true) || contentResolver.getType(uri) in listOf("application/zip", "application/x-zip-compressed")) {
                        val archive = File(staging, "archive-${files.size}.zip")
                        contentResolver.openInputStream(uri)!!.use { input -> archive.outputStream().use { input.copyTo(it) } }
                        require(archive.length() > 4 && archive.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }) { "ไฟล์ที่เลือกไม่ใช่ ZIP ที่สมบูรณ์" }
                        val unpacked = StoryFiles.unpackFile(archive, File(staging, "unpacked-${files.size}"))
                        files.addAll(unpacked.files.map { Uri.fromFile(it) })
                        failedZipEntries.addAll(unpacked.failedEntries)
                    } else files.add(uri)
                }
                // A broken large ZIP may end before story.json. Keep its valid images,
                // then automatically merge them when the separately downloaded story.json arrives.
                val selectedHasStory = files.any { displayName(it).equals("story.json", true) }
                if (selectedHasStory && pendingRecovery.isDirectory) {
                    files.addAll(pendingRecovery.listFiles()?.filter { it.isFile && it.name != "recovery.txt" }?.map { Uri.fromFile(it) }.orEmpty())
                }
                val scripts = files.filter { displayName(it).equals("story.json", true) }
                require(scripts.size <= 1) { "เลือก story.json ของเรื่องเดียวเท่านั้น" }
                val videos = files.filter { displayName(it).endsWith(".mp4",true) || contentResolver.getType(it)=="video/mp4" }
                val pictures = files.filter { displayName(it).substringAfterLast('.').lowercase() in listOf("png", "jpg", "jpeg", "webp") || contentResolver.getType(it)?.startsWith("image/") == true }
                val masters = pictures.filter { Regex("^CHARACTER_\\d+\\.", RegexOption.IGNORE_CASE).containsMatchIn(displayName(it)) }
                val covers = pictures.filter { StoryFiles.coverName(displayName(it)) != null }
                val scenes = pictures.filter { !StoryFiles.isMaster(displayName(it)) } - covers.toSet()
                require(scripts.isNotEmpty() || pictures.isNotEmpty() || videos.isNotEmpty()) { "ไม่พบ story.json หรือภาพในไฟล์ที่เลือก กรุณาเลือก ZIP ชุดงาน หรือ JSON/PNG/JPG" }
                val candidate = scripts.firstOrNull()?.let { contentResolver.openInputStream(it)!!.bufferedReader().use { reader -> JSONObject(reader.readText().removePrefix("\uFEFF")) } }
                if (candidate == null && plan == null && pictures.isNotEmpty()) {
                    pendingRecovery.deleteRecursively(); pendingRecovery.mkdirs()
                    pictures.forEachIndexed { index, uri ->
                        val safeName = displayName(uri).ifBlank { "recovered-$index.img" }
                        contentResolver.openInputStream(uri)!!.use { input -> File(pendingRecovery,safeName).outputStream().use { input.copyTo(it) } }
                    }
                    File(pendingRecovery,"recovery.txt").writeText("กู้ภาพ ${pictures.size} ไฟล์\n" + failedZipEntries.joinToString("\n"))
                    runOnUiThread {
                        importing=false; dialog.dismiss(); render()
                        notice("กู้ภาพที่สมบูรณ์ไว้แล้ว ${pictures.size} ไฟล์ • ZIP ขาด story.json\n\nนำเข้า story.json ผ่านเมนูนำเข้าบทอีกครั้ง แอปจะรวมภาพที่กู้ไว้ให้อัตโนมัติ ไม่ต้องเลือก ZIP ซ้ำ")
                    }
                    return@Thread
                }
                val destinationStory = candidate ?: plan ?: error("ยังไม่มีบท กรุณานำเข้า story.json ก่อน")
                val checkedVideos = videos.map { uri ->
                    val number=StoryFiles.sceneNumber(displayName(uri)) ?: error("วิดีโอต้องชื่อ EP01_SC001.mp4: ${displayName(uri)}")
                    val scene=rows(destinationStory).firstOrNull { it.getInt("ep")==number.first && it.getInt("scene")==number.second } ?: error("วิดีโอไม่ตรงกับบท: ${displayName(uri)}")
                    val temp=File(staging,"checked_videos/${key(scene)}.mp4").apply { parentFile!!.mkdirs() }
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val reader=android.media.MediaMetadataRetriever()
                    try { reader.setDataSource(temp.path); require(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it>0 }==true) { "วิดีโออ่านไม่ได้" } } finally { reader.release() }
                    key(scene) to temp
                }
                require(checkedVideos.map { it.first }.distinct().size==videos.size) { "มีวิดีโอฉากซ้ำ" }
                val checkedCovers = covers.map { uri ->
                    val name = StoryFiles.coverName(displayName(uri))!!
                    require(name in coverNames(destinationStory)) { "ปก $name ไม่ตรงกับ EP ในเรื่อง" }
                    val temp = File(staging,"checked/$name.img").apply { parentFile!!.mkdirs() }
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                    val ratio = if (name.endsWith("16x9")) 16.0 / 9 else 9.0 / 16
                    require(bounds.outHeight > 0 && kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight - ratio) < 0.04) { "$name ต้องมีสัดส่วน ${if (name.endsWith("16x9")) "16:9" else "9:16"}" }
                    name to temp
                }
                require(checkedCovers.map { it.first }.distinct().size == covers.size) { "มีไฟล์ปกซ้ำ กรุณาเลือกหนึ่งภาพต่อสัดส่วนต่อ EP" }
                if (candidate != null) validate(candidate)
                if (!confirmed) {
                    runOnUiThread {
                        importing=false; dialog.dismiss()
                        showPackagePreview(destinationStory, files, failedZipEntries) { receivePackage(selected,true) }
                    }
                    return@Thread
                }
                if (candidate != null) {
                    validate(candidate)
                    // Reimporting the same story must retain images already received.
                    val sameStory = plan?.let { old -> old.optString("title") == candidate.optString("title") && old.optString("category") == candidate.optString("category") && rows(old).map { it.toString() } == rows(candidate).map { it.toString() } } == true
                    val folder = if (sameStory) project else File(base, UUID.randomUUID().toString()).apply { mkdirs() }
                    importOriginalReferences(pictures,folder,candidate)
                    importNewMasters(masters, folder, candidate)
                    if (candidate.optString("character_mode","original") == "original") {
                        listOf("gawin","rinlada","mind").forEach { id ->
                            val target=File(folder,"original_masters/$id.master")
                            val source=File(filesDir,"character_masters/$id.master")
                            if(!target.exists() && source.exists()) { target.parentFile!!.mkdirs(); source.copyTo(target) }
                        }
                    }
                    val storyFile = File(folder,"story.json")
                    storyFile.writeText(candidate.toString(2))
                    require(storyFile.isFile && storyFile.length() > 0) { "บันทึก story.json ไม่สำเร็จ" }
                    require(prefs.edit().putString("project",folder.name).commit()) { "ตั้งค่าโครงการปัจจุบันไม่สำเร็จ" }
                    val persisted = JSONObject(File(project,"story.json").readText())
                    validate(persisted)
                    plan = persisted
                    File(project,"package_imported").writeText(System.currentTimeMillis().toString())
                } else {
                    require(plan != null) { "ยังไม่มีบท กรุณานำเข้า story.json หรือ ZIP ที่มีบทก่อน" }
                    importOriginalReferences(pictures,project,plan!!)
                    importNewMasters(masters)
                }
                checkedVideos.forEach { (id,temp) ->
                    val target=File(project,"videos/$id.mp4").apply { parentFile!!.mkdirs() }
                    if(target.exists()) archiveVersion(target,project,id,"video")
                    temp.copyTo(target,overwrite=true)
                }
                checkedCovers.forEach { (name, temp) -> coverFile(name).apply { parentFile!!.mkdirs() }.let { if(it.exists()) archiveVersion(it,project,name,"cover"); temp.copyTo(it,overwrite=true) } }
                runOnUiThread {
                    importing = false; dialog.dismiss(); render()
                    if (candidate != null && plan == null) return@runOnUiThread notice("รับไฟล์แล้วแต่ยังเปิดโครงการไม่ได้ กรุณานำเข้า ZIP อีกครั้ง")
                    if (scenes.isNotEmpty()) importImages(scenes)
                    else if (videos.isNotEmpty()) showImportReceipt()
                    else if (covers.isNotEmpty()) { pendingRecovery.deleteRecursively(); showImportReceipt() }
                    else notice(if (failedZipEntries.isNotEmpty()) "รับส่วนที่สมบูรณ์แล้ว • ข้ามไฟล์ที่อ่านไม่ได้ ${failedZipEntries.size} ไฟล์ กดตรวจงานที่ขาดเพื่อเพิ่มเฉพาะไฟล์" else if (candidate != null) "รับ story.json แล้ว • นำเข้า ZIP ภาพและปกของแต่ละ EP ผ่านเมนู 2 ต่อได้ ไม่ต้องเลือกบทซ้ำ" else "รับภาพ MASTER แล้ว เพิ่มภาพฉากในขั้นตอน 2 ได้เลย")
                }
            } catch (e: Exception) { runOnUiThread { importing = false; dialog.dismiss(); render(); notice("นำเข้าบทและภาพไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private val imagesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }
    private val zipPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { val backup = backupMode; backupMode = false; background { if (backup) contentResolver.openOutputStream(uri)!!.use { StudioArchive.write(project,it) } else export(uri) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adoptGeminiProject(intent)
        val masterError = runCatching { installCharacterMasters() }.exceptionOrNull()
        render()
        if (masterError != null) notice("ติดตั้ง MASTER ตัวละครไม่สำเร็จ กรุณาเปิดแอปอีกครั้ง")
    }
    private fun adoptGeminiProject(incoming: Intent?) {
        val id=incoming?.getStringExtra("gemini_project") ?: return
        if(Regex("[a-zA-Z0-9-]+").matches(id) && File(base,"$id/gemini_job/spec.json").isFile) prefs.edit().putString("project",id).apply()
    }
    override fun onNewIntent(incoming: Intent) {
        super.onNewIntent(incoming); setIntent(incoming); adoptGeminiProject(incoming); render()
    }
    private fun installCharacterMasters() {
        CharacterLibraryReset.install(filesDir)
    }
    override fun onResume() {
        super.onResume()
        pendingStoryShare=null
        pendingReceipt=null
        val receiptPath=prefs.getString("gemini_receipt_pending",null)
        if(receiptPath!=null) {
            prefs.edit().remove("gemini_receipt_pending").apply()
            val receiver=prefs.getString("image_receipt_app","Gemini") ?: "Gemini"
            AlertDialog.Builder(this).setTitle("ผลการรับไฟล์ใน $receiver")
                .setMessage("$receiver รับไฟล์คำสั่ง TXT และรูป MASTER ครบหรือไม่? การเปิดแอปสำเร็จยังไม่ยืนยันว่ารับไฟล์ครบ")
                .setPositiveButton("รับครบแล้ว") { _,_ -> recordGeminiReceipt(receiptPath,"user_confirmed_received") }
                .setNegativeButton("ไฟล์ไม่ครบ") { _,_ -> recordGeminiReceipt(receiptPath,"user_reported_missing"); notice("บันทึกปัญหาไว้แล้ว • ยังไม่ถือว่าส่งสำเร็จ กรุณาส่งชุดเดิมอีกครั้งจากรหัสปกหรือซีน") }
                .setNeutralButton("ยังไม่ตรวจ") { _,_ -> recordGeminiReceipt(receiptPath,"unverified") }.showDirector()
        }
        storyUiHandler.removeCallbacks(storyPoll)
        storyUiHandler.post(storyPoll)
        if(!importing && !GeminiStoryService.running) updater.checkAutomatically()
    }
    private fun recordGeminiReceipt(path: String, status: String) {
        val file=File(path)
        if(!file.canonicalPath.startsWith(base.canonicalPath+"/")) return
        runCatching { val value=JSONObject(file.readText()); value.put("receipt_status",status).put("receipt_at",System.currentTimeMillis()); file.writeText(value.toString(2)) }
    }
    override fun onPause() {
        storyUiHandler.removeCallbacks(storyPoll)
        super.onPause()
    }
    private fun rows(p: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val eps = p.getJSONArray("episodes")
        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            val scenes = ep.getJSONArray("scenes")
            for (j in 0 until scenes.length()) result.add(scenes.getJSONObject(j).put("ep", ep.getInt("ep")))
        }
        return result
    }
    private fun key(s: JSONObject) = "EP%02d_SC%03d".format(s.getInt("ep"), s.getInt("scene"))
    private fun image(s: JSONObject) = File(project, "images/${key(s)}.img")
    private fun validate(p: JSONObject) {
        ChannelCatalog.updateLegacyChannel(p)
        require(p.getString("title").isNotBlank()) { "ไม่มีชื่อเรื่อง" }
        val mode = p.optString("character_mode", "original")
        if(mode=="original") require(FlowCast.issues(p).isEmpty()) { FlowCast.issues(p).joinToString("\n") }
        require(mode in listOf("original", "new")) { "รูปแบบตัวละครไม่ถูกต้อง" }
        require(p.getString("category") in (if (mode == "new") otherGenres else ChannelCatalog.acceptedCategories + legacyCategories)) { "หมวดหมู่ไม่ตรงกับรูปแบบช่อง" }
        if (mode == "new") require(p.optString("channel").isNotBlank()) { "กรุณาระบุชื่อช่องในบท" }
        val eps = p.getJSONArray("episodes")
        require(eps.length() > 0) { "ไม่มี EP" }
        if (p.optInt("schema_version", 1) >= 2) {
            require(p.optString("channel").isNotBlank()) { "บทขาดชื่อช่อง" }
            require(p.optString("tagline").isNotBlank()) { "บทขาดคำโปรยเรื่อง" }
            require(p.getInt("episode_count") == eps.length()) { "จำนวน EP ไม่ตรงกับบท" }
            require(p.getInt("scene_count") == (0 until eps.length()).sumOf { eps.getJSONObject(it).getJSONArray("scenes").length() }) { "จำนวนซีนรวมไม่ตรงกับบท" }
        }
        for (i in 0 until eps.length()) {
            val ep = eps.getJSONObject(i)
            require(ep.getInt("ep") == i + 1 || p.optString("export_scope") == "episode" && eps.length() == 1 && ep.getInt("ep") == p.optInt("source_episode")) { "EP ต้องเรียงจาก 1" }
            val scenes = ep.getJSONArray("scenes")
            require(scenes.length() > 0) { "EP ไม่มีฉาก" }
            if (p.optInt("schema_version", 1) >= 2) {
                require(ep.optString("title").isNotBlank()) { "EP ${i + 1} ขาดชื่อตอน" }
                require(ep.optString("tagline").isNotBlank()) { "EP ${i + 1} ขาดคำโปรย" }
                require(ep.getInt("scene_count") == scenes.length()) { "จำนวนซีน EP ${i + 1} ไม่ตรงกับบท" }
            }
            for (j in 0 until scenes.length()) {
                val s = scenes.getJSONObject(j)
                require(s.getInt("scene") == j + 1) { "ฉากต้องเรียงจาก 1 ภายในแต่ละ EP" }
                if (p.optInt("schema_version", 1) >= 3) {
                    require(s.optString("location").isNotBlank() && s.optString("continuity_group").isNotBlank()) { "ฉากขาดสถานที่หรือรหัสเหตุการณ์ต่อเนื่อง" }
                    val cast = s.getJSONArray("characters")
                    require(cast.length() > 0) { "ฉากขาดรายการตัวละคร" }
                    val names = mutableSetOf<String>()
                    for (c in 0 until cast.length()) {
                        val person = cast.getJSONObject(c)
                        require(person.optString("name").isNotBlank() && names.add(person.getString("name"))) { "ชื่อหรือรายการตัวละครไม่ถูกต้อง" }
                        require(person.optString("outfit").isNotBlank()) { "ฉากขาดชุดของ ${person.optString("name")}" }
                        val master = person.optString("master")
                        require(if (mode == "original") FlowCast.matches(p,person) else Regex("^CHARACTER_[0-9]+[.]png$").matches(master)) { "MASTER ไม่ตรงกับตัวละคร ${person.optString("name")}" }
                    }
                }
                for (field in listOf("action", "image_prompt", "flow_prompt")) require(s.getString(field).isNotBlank()) { "ฉากขาด $field" }
            }
        }
    }
    private fun render() {
        plan = File(project, "story.json").takeIf { it.exists() }?.let { ChannelCatalog.updateLegacyChannel(JSONObject(it.readText())) }
        if (plan?.optString("character_mode","original") == "original") {
            listOf("gawin","rinlada","mind").forEach { id ->
                val target=File(project,"original_masters/$id.master"); val source=File(filesDir,"character_masters/$id.master")
                if(!target.exists() && source.exists()) { target.parentFile!!.mkdirs(); source.copyTo(target) }
            }
        }
        window.statusBarColor = navy
        window.navigationBarColor = navy
        ViewCompat.getWindowInsetsController(window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val scroll = ScrollView(this).apply { background = backdrop(Color.rgb(9,18,36),Color.rgb(35,18,55)); isFillViewport = true; clipToPadding = false }
        root = LinearLayout(this).apply { orientation = 1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        scroll.addView(root)
        val host = LinearLayout(this).apply { orientation = 1; setBackgroundColor(navy) }
        host.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        host.addView(TextView(this).apply {
            text = "Veo 3.1 Lite • ${plan?.optInt("duration_seconds",8) ?: 8} วินาที • ${plan?.optString("aspect_ratio","9:16") ?: "9:16"}\nพัฒนาโดย : นายปริญญา จันทร์จักษุ"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(muted)
            setPadding(dp(10),dp(6),dp(10),dp(6))
            background = backdrop(navy,navy)
        })
        setContentView(host)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(host)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = backdrop(Color.rgb(12,24,45), Color.rgb(31,18,45), Color.rgb(139,99,43)); elevation = dp(6).toFloat()
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.director_mark)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(88), dp(88)).apply { marginEnd = dp(12) }
            foreground = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(2),gold); cornerRadius=dp(18).toFloat() }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply { setColor(Color.rgb(15,25,43)); cornerRadius=dp(18).toFloat() }
            contentDescription = "รูปผู้กำกับ DIRECTOR X"
        })
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fun brandLine(value: String, maximum: Int, minimum: Int, color: Int, bold: Boolean, spacing: Int) {
            heading.addView(TextView(this).apply {
                text = value
                gravity = Gravity.START
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                setSingleLine(true)
                textSize = maximum.toFloat()
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(spacing), 0, dp(spacing))
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, minimum, maximum, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            })
        }
        brandLine("DIRECTOR X ${packageManager.getPackageInfo(packageName, 0).versionName}", 20, 14, gold, true, 1)
        brandLine("ผู้กำกับอัจฉริยะ", 16, 12, ice, true, 0)
        header.addView(heading)
        root.addView(header)
        
        flowHome()
    }
    private fun splitActions(left: String, right: String, leftAction: () -> Unit, rightAction: () -> Unit, colors: List<IntArray> = listOf(menuColors[1], menuColors[2])) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(3); bottomMargin = dp(3) }
        }
        listOf(left to leftAction, right to rightAction).forEachIndexed { index, item ->
            row.addView(Button(this).apply {
                text = item.first; isAllCaps = false; textSize = 12f
                setTextColor(gold); setTypeface(null,Typeface.BOLD); gravity = Gravity.CENTER
                minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
                setPadding(dp(4),dp(5),dp(4),dp(5))
                backgroundTintList = null
                background = backdrop(Color.rgb(25,25,28),Color.rgb(19,19,22),Color.rgb(63,55,39))
                layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { if (index == 0) marginEnd = dp(3) else marginStart = dp(3) }
                setOnClickListener { if (!importing) item.second() }
            })
        }
        root.addView(row)
    }
    private fun testImageMenu() {
        choiceMenu("🖼️ ทดลองสร้างรูป 1 ภาพ", listOf("🎭 ตัวละครเดิม • เลือกคนที่ต้องการ", "✨ ตัวละครสมมุติ • ใช้รูปผมหรือสร้างคนใหม่")) { choice ->
            if (choice == 0) choiceMenu("🎭 เลือกตัวละครในภาพ", listOf("👨 กวิน • คนเดียว", "👩 รินรดา • คนเดียว", "👩 เมย์ • คนเดียว", "👥 ทั้ง 3 คนในภาพเดียว")) { index ->
                val keys = listOf("gawin", "rinlada", "mind")
                val selected = if (index == 3) keys else listOf(keys[index])
                choiceMenu("🖼️ เลือกวิธีทดสอบ", listOf("🎯 คงตัวละครและชุดเดิม • สถานที่อัตโนมัติ", "👗 เปลี่ยนชุดและฉาก") + if (selected.size == 1) listOf("🧵 เปลี่ยนชุดอย่างเดียว • คงท่าและฉาก") else emptyList()) { mode ->
                    if(mode==0) sendTestImage(selected,preserveMaster=true) else choiceMenu("กิจกรรมสำหรับทดสอบ",listOf("ประชุม","อยู่บ้าน","เข้านอน","ท่องเที่ยว")) { activity ->
                        sendTestImage(selected,preserveMaster=false,wardrobeOnly=mode==2,testActivity=listOf("meeting","home","sleep","casual")[activity])
                    }
                }
            }
            else choiceMenu("✨ ตัวละครสมมุติ", listOf("👨 ใช้รูปผม • เปลี่ยนชุดและฉากอัตโนมัติ", "✨ สร้างคนใหม่")) { mode ->
                if (mode == 0) personalTestImageMenu()
                else sendFictionalCharacterTest()
            }
        }
    }
    private fun sendFictionalCharacterTest() {
        val references = listOf("gawin" to "GAWIN_MASTER", "rinlada" to "RINLADA_MASTER", "mind" to "MIND_MASTER")
        val available = references.filter { File(filesDir,"character_masters/${it.first}.master").exists() }
        if (available.size != references.size) return notice("ภาพ MASTER ทั้ง 3 คนยังไม่ครบ กรุณาเพิ่มต้นฉบับตัวละครก่อนทดสอบ")
        val last = prefs.getInt("fictional_reference_last",-1)
        val candidates = available.indices.filter { it != last }
        val index = candidates.random()
        prefs.edit().putInt("fictional_reference_last",index).apply()
        val (key,label) = available[index]
        val filename = masterExportName(File(filesDir,"character_masters/$key.master"),false) ?: return notice("อ่านภาพต้นแบบไม่ได้")
        val location = nextTestLocation()
        val command = ImagePromptPolicy.newCharacter("$filename ($label)",location)
        shareText(command,useNewMasters=false,selectedMasterKeys=listOf(key))
    }
    private val personalTestPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val temporary = File(filesDir, "personal_test_reference.tmp")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                } ?: error("เปิดภาพไม่ได้")
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์นี้ไม่ใช่ภาพที่อ่านได้" }
                require(temporary.renameTo(File(filesDir, "personal_test_reference.master"))) { "บันทึกภาพไม่ได้" }
                sendPersonalTestImage()
            } catch (e: Exception) {
                temporary.delete()
                notice("รับรูปผมไม่สำเร็จ: ${e.message}")
            }
        }
    }
    private fun personalTestImageMenu() {
        val reference = File(filesDir, "personal_test_reference.master")
        if (!reference.exists()) {
            try {
                assets.open("test_references/PARINYA_REFERENCE.png").use { input ->
                    reference.outputStream().use { input.copyTo(it) }
                }
            } catch (e: Exception) {
                reference.delete()
                notice("เตรียมรูปเริ่มต้นไม่สำเร็จ: ${e.message}")
                return
            }
            sendPersonalTestImage()
            return
        }
        choiceMenu("👨 รูปผมสำหรับทดสอบ", listOf("🖼️ ใช้รูปที่บันทึกไว้ • สร้างภาพทดสอบ", "📷 เปลี่ยนรูปผม")) { choice ->
            if (choice == 0) sendPersonalTestImage()
            else personalTestPicker.launch(arrayOf("image/*"))
        }
    }
    private fun sendPersonalTestImage() {
        try {
            val reference = File(filesDir, "personal_test_reference.master")
            if (!reference.exists()) {
                personalTestPicker.launch(arrayOf("image/*"))
                return
            }
            val outfits = listOf("เสื้อโปโลสีกรมท่าพอดีตัวกับกางเกงขายาวสีเบจ", "เสื้อเชิ้ตสีเทาอ่อนพับแขนกับกางเกงขายาวสีดำ", "เสื้อยืดสีขาวกับเสื้อคลุมสีน้ำตาลและกางเกงขายาวสีครีม")
            val last = prefs.getInt("personal_test_outfit_last", -1)
            val index = outfits.indices.filter { it != last }.random()
            prefs.edit().putInt("personal_test_outfit_last", index).apply()
            val location = nextTestLocation()
            val command = ImagePromptPolicy.scene(
                "ปริญญา → PARINYA_REFERENCE | ชุด: ${outfits[index]}", location,
                "กำลังจัดผักสดลงชามบนโต๊ะเตรียมอาหาร สีหน้าเป็นมิตร",
                source="ภาพ PARINYA_REFERENCE ที่แนบ", framing="เห็นศีรษะถึงต้นขาและมือที่กำลังทำงาน")
            shareText(command, useNewMasters = false, includeMasters = false,
                referenceImages = listOf("PARINYA_REFERENCE" to reference))
        } catch (e: Exception) {
            notice("เตรียมภาพอ้างอิงไม่สำเร็จ: ${e.message}")
        }
    }
    private fun nextTestLocation(): String {
        val places = listOf(
            "ครัวบ้านสไตล์ญี่ปุ่น ตู้ไม้สีอ่อน หน้าต่างชมสวนหิน แสงเช้า",
            "ระเบียงบ้านริมสวน โต๊ะเตรียมอาหารไม้และเตาพกพา มีต้นไม้และแสงธรรมชาติ",
            "ครัวร้านกาแฟเล็ก ผนังอิฐ ชั้นวางถ้วยและเคาน์เตอร์เตรียมอาหาร",
            "ห้องเรียนทำอาหาร โต๊ะสเตนเลส อุปกรณ์เรียบร้อย หน้าต่างรับแสงกลางวัน",
            "ลานทำอาหารในสวนชนบท ซุ้มไม้ โต๊ะเตรียมอาหาร มีแปลงผักด้านหลัง",
            "ครัวบ้านพักริมทะเล ตู้สีขาว หน้าต่างมองเห็นทะเล แสงบ่ายนุ่ม",
            "ครัวกระท่อมบนภูเขา เคาน์เตอร์ไม้ หน้าต่างมองเห็นต้นสน แสงธรรมชาติ",
            "ครัวบนดาดฟ้าอาคาร โต๊ะเตรียมอาหาร เตาพกพา มีสวนกระถางและวิวเมือง"
        )
        val last = prefs.getInt("test_location_last", -1)
        val used = prefs.getStringSet("test_location_used", emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.filter { it in places.indices }.toSet()
        val available = places.indices.filter { it !in used }
        val round = if (available.isEmpty()) emptySet<Int>() else used
        val choices = (if (available.isEmpty()) places.indices.toList() else available).filter { it != last }
        val index = choices.random()
        prefs.edit().putInt("test_location_last", index)
            .putStringSet("test_location_used", (round + index).map { it.toString() }.toSet()).apply()
        return places[index]
    }
    private fun sendTestImage(selectedKeys: List<String>, preserveMaster: Boolean, wardrobeOnly: Boolean = false, testActivity: String = "home") {
        val cast = listOf("gawin" to "กวิน", "rinlada" to "รินรดา", "mind" to "เมย์")
        val selected = cast.filter { it.first in selectedKeys }
        require(selected.isNotEmpty())
        if (selected.any { !File(filesDir,"character_masters/${it.first}.master").exists() }) {
            AlertDialog.Builder(this).setTitle("เพิ่มภาพ MASTER ก่อนทดสอบ")
                .setMessage("ต้องมีต้นฉบับของ ${selected.joinToString { it.second }} เพื่ออ้างอิงเอกลักษณ์และใบหน้า")
                .setPositiveButton("เพิ่มภาพตัวละคร") { _,_ -> characterMasters() }.setNegativeButton("ยกเลิก",null).showDirector()
            return
        }
        val scene = JSONObject().put("activity",testActivity).put("story_day","TEST_DAY01").put("wardrobe_event","TEST_$testActivity")
        val characters = JSONArray()
        val actions = mutableListOf<String>()
        val details = mutableListOf<String>()
        val references = mutableListOf<Pair<String,File>>()
        for ((key, name) in selected) {
            if (StoryIdentity.keyFor(name) != key) { notice("จับคู่ตัวละครกับ MASTER ไม่ได้"); return }
            val source = File(filesDir,"character_masters/$key.master")
            val masterName = masterExportName(source, false)
            if (masterName == null || !StoryIdentity.matchesMaster(name, masterName)) {
                notice("อ่านภาพ MASTER ของ $name ไม่ได้ กรุณาเพิ่มภาพต้นฉบับใหม่"); return
            }
            val outfit = if (preserveMaster) "คงเครื่องแต่งกายจาก MASTER ของคนนี้" else WardrobeTimeline.resolve(WardrobeBeat(key,"TEST_DAY01","TEST_$testActivity",testActivity,"",""),emptyList())
            val action = if (preserveMaster) "$name คงท่าทาง สีหน้า สายตา และกิจกรรมตาม MASTER ของตนเอง" else when(testActivity) {
                "meeting" -> "$name ยืนถือแฟ้มเอกสารข้างโต๊ะประชุม สีหน้าตั้งใจ"
                "sleep" -> "$name นั่งบนขอบเตียงเตรียมเข้านอน สีหน้าผ่อนคลาย"
                "home" -> "$name นั่งพักบนโซฟา ถือแก้วน้ำ"
                else -> "$name นั่งพักที่โต๊ะร้านอาหารริมทะเล"
            }
            characters.put(JSONObject().put("name", name).put("master", masterName).put("outfit", outfit))
            actions.add(action)
            details.add("$name → $masterName | ${promptProfile(key,source)} | ชุด: $outfit")
            references.add(masterName.substringBeforeLast('.') to source)
        }
        scene.put("characters", characters)
        val location = if(preserveMaster) nextTestLocation() else when(testActivity) {
            "meeting" -> "ห้องประชุมสำนักงานทันสมัยตอนเช้า"
            "sleep" -> "ห้องนอนตอนกลางคืน แสงโคมไฟหัวเตียง"
            "home" -> "ห้องนั่งเล่นตอนบ่าย"
            else -> "ร้านอาหารริมทะเลตอนบ่าย"
        }
        val command = when {
            wardrobeOnly -> ImagePromptPolicy.edit("ภาพ MASTER", "เครื่องแต่งกายเป็นชุดต่อไปนี้ โดยให้ทรงชุดพอดีกับรูปร่างเดิม\n${details.joinToString("\n")}", cast=selected.joinToString { it.second })
            preserveMaster && selected.size == 1 -> ImagePromptPolicy.edit("ภาพ MASTER", "ฉากหลังเป็น $location", cast=details.joinToString("\n"))
            else -> ImagePromptPolicy.scene(details.joinToString("\n"), location,
                if (preserveMaster) "จัดแต่ละคนในภาพเดียว ให้ทำกิจกรรมตาม MASTER รายคน" else actions.joinToString("; "), lighting=if(testActivity=="sleep") "แสงนุ่มจากโคมไฟในเวลากลางคืน" else "แสงธรรมชาติตามเวลาในฉาก",changeWardrobe=!preserveMaster)
        }
        shareText(command,includeMasters=false,referenceImages=references)
    }
    private fun stepCard(number: Int, icon: String, title: String, detail: String, enabled: Boolean = true, completed: Boolean = false, currentStep: Int, statusText: String? = null, action: () -> Unit) {
        val active = enabled && !completed && number == currentStep
        val status = statusText ?: if (completed) "เสร็จแล้ว" else if (enabled) "พร้อมใช้งาน" else "รอข้อมูลขั้นตอนก่อนหน้า"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1,-2)
        }
        row.addView(object : android.view.View(this) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val x = width / 2f; val y = height / 2f; val radius = dp(14).toFloat()
                paint.strokeWidth = dp(if (number <= currentStep) 4 else 2).toFloat()
                if (number > 1) { paint.color=if(number<=currentStep) electricBlue else Color.rgb(74,87,108); canvas.drawLine(x,0f,x,y-radius,paint) }
                if (number < 4) { paint.color=if(number<currentStep) electricBlue else Color.rgb(74,87,108); canvas.drawLine(x,y+radius,x,height.toFloat(),paint) }
                if (active) { paint.color=Color.argb(70,42,220,255); canvas.drawCircle(x,y,dp(18).toFloat(),paint) }
                paint.color = if (completed || active) electricBlue else Color.rgb(85,102,125)
                canvas.drawCircle(x,y,radius,paint)
                paint.color = if (active || completed) Color.WHITE else muted
                paint.textSize = dp(12).toFloat(); paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.typeface = Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
                canvas.drawText(if (completed) "✓" else number.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)
            }
        }, LinearLayout.LayoutParams(dp(34),-1).apply { marginEnd = dp(10) })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16),dp(15),dp(16),dp(15))
            background = backdrop(if (active) Color.rgb(18,51,88) else Color.rgb(24,31,47), if (active) Color.rgb(28,23,67) else Color.rgb(17,23,37), if (active) cyan else if (completed) Color.rgb(57,112,157) else Color.rgb(67,76,94))
            layoutParams = LinearLayout.LayoutParams(0,-2,1f).apply { topMargin = dp(6); bottomMargin = dp(6) }
            minimumHeight = dp(108)
            isEnabled = enabled; isClickable = enabled; isFocusable = enabled
            val attribute = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground,attribute,true)
            foreground = getDrawable(attribute.resourceId)
            contentDescription = "ขั้นตอน $number $title $status $detail"
            setOnClickListener { if (enabled && !importing) action() else if (importing) Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        }
        card.addView(TextView(this).apply {
            text = "ขั้นตอน $number  •  $status"; textSize = 10f
            setTextColor(if(completed || active) cyan else muted)
            setTypeface(null,Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "$title  ${if (completed) "✓" else if (enabled) "›" else "🔒"}"; textSize = 16f
            setTypeface(null,Typeface.BOLD); setTextColor(if(enabled || completed) ice else muted)
            setPadding(0,dp(7),0,0)
        })
        card.addView(TextView(this).apply {
            text = detail; textSize = 12f; setTextColor(if(enabled || completed) Color.rgb(185,198,218) else Color.rgb(112,120,137)); setPadding(0,dp(6),0,0)
        })
        row.addView(card)
        root.addView(row)
    }
    private fun toolsMenu() {
        choiceMenu("⚙️ ตัวเลือก", listOf("🎭 ภาพต้นฉบับตัวละคร", "🔎 ดูภาพและบทในเรื่องนี้", "📚 เลือกเรื่องที่บันทึก", "🎬 ส่งคำสั่งภาพทั้งเรื่อง", "💡 วิธีใช้งาน", "📝 คำสั่งภาพ: ${if(detailedImageProfiles) "พร้อมข้อมูลละเอียด" else "กระชับตามต้นแบบ"}")) { index ->
            when (index) {
                0 -> characterMasters()
                1 -> review()
                2 -> {
                    val folders = base.listFiles()?.filter { File(it,"story.json").exists() } ?: emptyList()
                    choiceMenu("📚 เรื่องของคุณ", folders.map { "📖 " + JSONObject(File(it,"story.json").readText()).getString("title") }) { i -> prefs.edit().putString("project",folders[i].name).apply(); render() }
                }
                5 -> { prefs.edit().putBoolean("detailed_image_profiles",!detailedImageProfiles).apply(); notice(if(detailedImageProfiles) "เพิ่มข้อมูลโปรไฟล์ในคำสั่งภาพแล้ว" else "ใช้คำสั่งภาพกระชับตามต้นแบบแล้ว") }
                3 -> wholeStoryCommands()
                4 -> notice("1. สร้างเรื่อง: ตรวจ MASTER เลือกช่องและแนว ส่งคำสั่งไป ChatGPT แล้วส่งข้อความใน ChatGPT เพื่อเริ่มสร้างบทและภาพ\n\n2. รับบทและภาพ: ดาวน์โหลดและนำเข้า story.json ก่อน จากนั้นนำเข้า ZIP ภาพและปกของแต่ละ EP ผ่านเมนู 2 เพิ่มต่อทีละ EP ได้ ไม่ต้องเลือกบทซ้ำ ตรวจงานที่ขาดก่อนทำวิดีโอ\n\n3. ทำวิดีโอ: ใช้ภาพฉากและคำสั่งใน Flow / Veo สร้างคลิปเอง ดาวน์โหลด MP4 แล้วนำกลับเข้าฉากที่ตรงกันในแอป จัดลำดับและรวม EP พร้อมปกเปิด 3 วินาที\n\n4. ดาวน์โหลดหนัง: เมื่อรวม EP สำเร็จ บันทึกหรือแชร์ MP4 ชื่อเรื่อง + เลข EP + ชื่อตอน ส่วน ZIP ใช้สำรองงาน\n\nแอปเปิดบริการภายนอกและจัดไฟล์ ผู้ใช้ต้องส่งข้อความใน ChatGPT และอัปโหลดภาพใน Flow เอง")
            }
        }
    }

    private fun label(value: String, size: Int, bold: Boolean = false) {
        root.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(if (bold) gold else muted)
            setPadding(dp(4),dp(4),dp(4),dp(4)); if (bold) setTypeface(null, Typeface.BOLD)
        })
    }
    private fun button(value: String, primary: Boolean = true, action: () -> Unit) {
        val colors = when {
            !primary -> if (value.contains("Update")) menuColors[3] else if (value.contains("ปก")) menuColors[1] else menuColors[4]
            value.startsWith("①") -> intArrayOf(Color.rgb(255,204,86),Color.rgb(255,174,38))
            value.startsWith("②") -> intArrayOf(Color.rgb(42,153,246),Color.rgb(27,101,211))
            value.startsWith("③") -> intArrayOf(Color.rgb(164,96,246),Color.rgb(111,65,209))
            value.startsWith("④") -> intArrayOf(Color.rgb(40,220,164),Color.rgb(10,183,133))
            value.startsWith("⑤") -> intArrayOf(Color.rgb(35,205,216),Color.rgb(14,163,182))
            else -> intArrayOf(Color.rgb(255,203,81),Color.rgb(255,166,39))
        }
        val darkText = !value.startsWith("②") && !value.startsWith("③")
        root.addView(Button(this).apply {
            text = value; isAllCaps = false; textSize = if (primary) 16f else 14f
            setTypeface(null,if (primary) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (primary) navy else gold)
            backgroundTintList = null
            background = if (primary) backdrop(gold,Color.rgb(208,151,54)) else backdrop(Color.rgb(25,25,28),Color.rgb(19,19,22),Color.rgb(63,55,39))
            elevation = dp(if (primary) 3 else 0).toFloat()
            minHeight = dp(if (primary) 46 else 38); minimumHeight = minHeight
            layoutParams = LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(4),0,dp(4)) }
            setPadding(dp(10),dp(6),dp(10),dp(6))
            setOnClickListener { if (!importing) action() else Toast.makeText(this@StudioActivity,"กำลังนำเข้าภาพ กรุณารอสักครู่",Toast.LENGTH_SHORT).show() }
        })
    }
    private fun showPackagePreview(story: JSONObject, files: List<Uri>, warnings: List<String>, confirm: () -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(12))
            background = backdrop(navy,Color.rgb(35,18,55),gold)
        }
        fun text(parent: LinearLayout, value: String, color: Int = ice, size: Float = 13f): TextView = TextView(this).apply {
            this.text=value; textSize=size; setTextColor(color); setPadding(0,dp(5),0,dp(5)); parent.addView(this)
        }
        text(panel,"ตรวจชุดไฟล์ก่อนนำเข้า",gold,20f)
        text(panel,story.optString("title"),ice,16f)
        val names=files.map { displayName(it) }
        val sceneCount=names.count { StoryFiles.sceneNumber(it)!=null && !it.endsWith(".mp4",true) }
        val coverCount=names.count { StoryFiles.coverName(it)!=null }
        text(panel,"เลือกครั้งนี้ • บท ${names.count { it.equals("story.json",true) }} • ภาพ $sceneCount • ปก $coverCount",cyan)
        text(panel,"ตามบททั้งหมด • ${story.getJSONArray("episodes").length()} EP • ${rows(story).size} ฉาก • ${coverNames(story).size} ปก",muted)
        val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(this).apply { isVerticalScrollBarEnabled=true; isScrollbarFadingEnabled=false; addView(list) }
        panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val episodes=story.getJSONArray("episodes")
        for(i in 0 until episodes.length()) {
            val ep=episodes.getJSONObject(i); val number=ep.getInt("ep")
            val selected=names.filter { StoryFiles.sceneNumber(it)?.first==number || StoryFiles.coverName(it)?.startsWith("EP%02d_".format(number))==true }
            val card=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)); background=backdrop(Color.rgb(21,42,65),Color.rgb(30,24,54),Color.rgb(65,94,126)); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) } }
            val heading=text(card,"EP %02d • %s  ▾".format(number,ep.optString("title")),gold,15f)
            text(card,"เลือก ${selected.size} ไฟล์ • บท ${ep.getJSONArray("scenes").length()} ฉาก")
            val detail=text(card,selected.joinToString("\n").ifBlank { "ยังไม่ได้เลือกไฟล์ของ EP นี้" })
            detail.visibility=android.view.View.GONE
            heading.isFocusable=true; heading.setOnClickListener { detail.visibility=if(detail.visibility==android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE }
            list.addView(card)
        }
        val other=names.filter { StoryFiles.sceneNumber(it)==null && StoryFiles.coverName(it)==null }
        if(other.isNotEmpty()) text(list,other.joinToString("\n"),muted)
        if(warnings.isNotEmpty()) text(list,"ข้อควรตรวจ\n"+warnings.joinToString("\n"),gold)
        text(list,"ภาพฉากซ้ำจะถามก่อนแทน • ปกที่ซ้ำเก็บเวอร์ชันเดิม\nหลังบันทึกจะตรวจจำนวนที่ยังขาด",muted)
        val dialog=AlertDialog.Builder(this).setView(panel).create()
        val actions=LinearLayout(this).apply { orientation=0 }
        listOf("ยกเลิก","ยืนยันนำเข้า").forEachIndexed { index,label ->
            actions.addView(Button(this).apply {
                text=label; isAllCaps=false; setTextColor(if(index==1) navy else ice); backgroundTintList=null
                background=if(index==1) backdrop(gold,Color.rgb(214,154,54)) else backdrop(navy,navy,Color.rgb(95,110,137))
                layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),dp(8),dp(3),0) }
                setOnClickListener { isEnabled=false; dialog.dismiss(); if(index==1) confirm() }
            })
        }
        panel.addView(actions)
        dialog.show(); dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(24),(resources.displayMetrics.heightPixels*0.82).toInt())
    }
    private fun showImportReceipt(batchSummary: String = "") {
        val story=plan ?: return
        val issues=FlowProductionPack.issues(story)
        val text=batchSummary+"\nเรื่อง: ${story.optString("title")}\n${story.getJSONArray("episodes").length()} EP • ${rows(story).size} ซีน\n"+
            if(issues.isEmpty()) "บทพร้อมส่งออกคำสั่ง Flow • ไม่ต้องมีภาพฉากก่อน" else "ตรวจข้อมูลก่อนส่งออก:\n"+issues.take(12).joinToString("\n")
        AlertDialog.Builder(this).setTitle("นำเข้าไฟล์แล้ว").setMessage(text)
            .setPositiveButton("กลับหน้าหลัก") { _,_ -> render() }.showDirector()
    }
    private fun notice(text: String) { AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage(text).setPositiveButton("ตกลง", null).showDirector() }
    private fun background(block: () -> Unit) {
        importing = true
        val dialog = AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage("กำลังจัดไฟล์…").setCancelable(false).showDirector()
        Thread {
            try { block(); runOnUiThread { importing = false; dialog.dismiss(); render(); notice("เสร็จเรียบร้อย") } }
            catch (e: Exception) { File(filesDir,"last_error.txt").writeText("${java.util.Date()}\n${e.stackTraceToString()}"); runOnUiThread { importing = false; dialog.dismiss(); notice("ดำเนินการไม่สำเร็จ: ${e.message}") } }
        }.start()
    }
    private fun masterExportName(source: File, useNewMasters: Boolean): String? {
        if (!source.isFile) return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
        val name = if (useNewMasters) source.nameWithoutExtension else source.nameWithoutExtension + "_MASTER"
        return "${name.uppercase()}.$extension"
    }
    private fun shareImageCommand(command: String, references: List<Pair<String,File>>) {
        val preferred=prefs.getString("image_provider","ChatGPT") ?: "ChatGPT"
        val providers=if(preferred=="Gemini") listOf("Gemini","ChatGPT") else listOf("ChatGPT","Gemini")
        choiceMenu("สร้างภาพด้วยแอปไหน",providers.mapIndexed { i,name -> name+if(i==0) " • ตัวเลือกที่จำไว้" else "" }) { selected ->
            val provider=providers[selected]
            prefs.edit().putString("image_provider",provider).apply()
            shareText(command,includeMasters=false,referenceImages=references,destinationLabel=provider,imagePackage=true)
        }
    }
    private fun shareText(inputText: String, lockCreation: Boolean = false, useNewMasters: Boolean = plan?.optString("character_mode") == "new", includeMasters: Boolean = true, selectedMasterKeys: List<String>? = null, referenceImages: List<Pair<String, File>> = emptyList(), destinationLabel: String = "ChatGPT", imagePackage: Boolean = false) {
        val text = if (lockCreation && !inputText.contains("ค่าตั้งต้นผู้ใช้ (ใช้แทนค่าตัวอย่าง)")) inputText + "\nค่าตั้งต้นผู้ใช้ (ใช้แทนค่าตัวอย่าง): EP=${prefs.getString("plan_episodes", "").orEmpty().ifBlank { "AI ประเมิน" }} ฉาก/EP=${prefs.getString("plan_scenes", "").orEmpty().ifBlank { "AI ประเมิน" }} เวลา=${prefs.getString("plan_duration", "8")} วินาที สัดส่วน=${"9:16"} กำหนด duration_seconds และ aspect_ratio ใน story.json และคำสั่งทุกฉากตามนี้" else inputText
        val uris = arrayListOf<Uri>()
        val attachments = JSONArray()
        val transferId = UUID.randomUUID().toString()
        val sources = if (!includeMasters) emptyList<File>() else if (useNewMasters) File(project, "masters").listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") && !it.name.endsWith(".pending") } ?: emptyList()
            else (selectedMasterKeys ?: listOf("gawin", "rinlada", "mind")).map { File(project, "original_masters/$it.master").takeIf { f -> f.exists() } ?: File(filesDir, "character_masters/$it.master") }
        if (includeMasters && (sources.isEmpty() || sources.any { !it.exists() })) {
            notice("ภาพ MASTER ยังไม่ครบสำหรับคำสั่งนี้ กรุณาเพิ่มต้นฉบับตัวละครก่อนส่ง เพื่อไม่สร้างตัวละครผิดคน")
            return
        }
        for (source in sources) {
            if (source.exists()) {
                val name = masterExportName(source, useNewMasters)
                if (name == null) { notice("อ่านภาพ MASTER ไม่ได้ กรุณาเลือกภาพตัวละครใหม่"); return }
                val out = File(cacheDir,"character_master_exports/$transferId/$name")
                out.parentFile!!.mkdirs(); source.copyTo(out, overwrite = true)
                attachments.put(JSONObject().put("name",out.name).put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(out.readBytes()).joinToString("") { "%02x".format(it) }).put("bytes",out.length()))
                uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",out))
            }
        }
        for ((name, source) in referenceImages) {
            if (!source.exists()) { notice("ไม่พบภาพอ้างอิง $name กรุณานำเข้าภาพฉากก่อน"); return }
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(source.path, options)
            if (options.outWidth <= 0) { notice("อ่านภาพอ้างอิง $name ไม่ได้"); return }
            val extension = when (options.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
            val out = File(cacheDir, "scene_reference_exports/$transferId/$name.$extension")
            out.parentFile!!.mkdirs(); source.copyTo(out, overwrite = true)
            attachments.put(JSONObject().put("name",out.name).put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(out.readBytes()).joinToString("") { "%02x".format(it) }).put("bytes",out.length()))
                uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",out))
        }
        // ChatGPT Android builds do not all read the same share field. Keep
        // the command in both EXTRA_TEXT and the first ClipData item, then
        // append every granted image URI to that same ClipData payload.
        val filePackage=(destinationLabel=="Gemini" || imagePackage) && uris.isNotEmpty()
        val intent = if(filePackage) {
            val command=File(cacheDir,"scene_reference_exports/$transferId/READ_FIRST_IMAGE_COMMAND.txt")
            command.parentFile!!.mkdirs()
            command.writeText("สร้างภาพหนึ่งภาพตามคำสั่งด้านล่าง ใช้ภาพ MASTER ที่แนบจับคู่ตามชื่อไฟล์ ไม่สรุปคำสั่ง ไม่สร้างวิดีโอ\n\n"+text,Charsets.UTF_8)
            val commandUri=FileProvider.getUriForFile(this,"$packageName.fileprovider",command)
            val shareText=if(destinationLabel=="Gemini" && imagePackage) GEMINI_IMAGE_REQUEST else text
            val result=buildGeminiPackageIntent(shareText,commandUri,ArrayList(uris))
            uris.add(0,commandUri)
            attachments.put(JSONObject().put("name",command.name).put("bytes",command.length()).put("sha256",MasterLibrary.digest(command.readBytes())))
            result
        } else buildChatShareIntent(text,uris)
        if(!filePackage) (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("คำสั่ง DIRECTOR X", text))
        val destination = project
        val sceneId = prefs.getString("last_scene_${destination.name}", "")!!
        val commandFile = File(destination, if (lockCreation) "last_story_command.txt" else "last_command.txt")
        commandFile.writeText(text)
        File(destination,"last_payload.json").writeText(JSONObject().put("text",text).put("creation",lockCreation)
            .put("images",JSONArray(uris.map { it.toString() })).put("attachments",attachments).toString())
        val imageList = (0 until attachments.length()).joinToString("\n") { i -> attachments.getJSONObject(i).let { "${it.optString("name")} • ${it.optLong("bytes")} bytes • SHA ${it.optString("sha256").take(12)}" } }
        pendingStorySummary = "คำสั่ง ${text.length} ตัวอักษร • ภาพ ${uris.count { contentResolver.getType(it)?.startsWith("image/")==true }} ภาพ" + if(filePackage) " • ไฟล์คำสั่ง 1 ไฟล์" else ""
        fun launchShare() {
            if(filePackage && destinationLabel=="Gemini" && imagePackage) {
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("คำสั่งสร้างภาพ Gemini",GEMINI_IMAGE_REQUEST))
            }
            val request = JSONObject().put("id",transferId).put("scene",sceneId).put("created_at",System.currentTimeMillis())
                .put("text",text).put("attachments",attachments).put("status","opened_external_app")
            File(destination,"image_requests/$transferId.json").apply { parentFile!!.mkdirs(); writeText(request.toString(2)) }
            pendingReceipt = {
                if (lockCreation) File(destination,"command_sent").writeText("confirmed")
                else File(destination,"receipts/${UUID.randomUUID()}.txt").apply { parentFile!!.mkdirs() }
                    .writeText("$sceneId\n${System.currentTimeMillis()}\n$text")
            }
            var opened = false
            for (target in if(destinationLabel=="Gemini") listOf("com.google.android.apps.bard") else if(destinationLabel=="ChatGPT") listOf("com.openai.chatgpt", "com.openai.chatgpt.beta") else emptyList()) {
                try {
                    uris.forEach { grantUriPermission(target,it,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    startActivity(Intent(intent).setPackage(target))
                    opened = true; break
                } catch (_: android.content.ActivityNotFoundException) { }
                  catch (_: SecurityException) { }
            }
            if (!opened) {
                try {
                    startActivity(Intent.createChooser(intent,if(filePackage) "เลือก $destinationLabel • แนบไฟล์คำสั่งพร้อมรูป" else "เลือก $destinationLabel • ตรวจรูปและวางคำสั่งก่อนส่ง").apply {
                        clipData = intent.clipData; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                    opened = true
                } catch (e: Exception) { pendingReceipt = null; notice("เปิดหน้ารับแชร์ไม่ได้: ${e.message}\n"+if(filePackage) "กลับมาส่งชุดคำสั่งและรูปใหม่ได้" else "คำสั่งคัดลอกไว้แล้ว") }
            }
            if (opened) {
                pendingStoryShare = { launchShare() }
                if(filePackage) prefs.edit().putString("image_receipt_app",destinationLabel).putString("gemini_receipt_pending",File(destination,"image_requests/$transferId.json").absolutePath).apply()
            }
        }
        val preview = LinearLayout(this).apply { orientation = 1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        preview.addView(TextView(this).apply {
            this.text = if(filePackage && destinationLabel=="Gemini" && imagePackage) "$pendingStorySummary\n$imageList\nเมื่อเปิด Gemini ตรวจไฟล์คำสั่งและภาพ MASTER ให้ครบ หากช่องข้อความว่าง ให้แตะค้างแล้วเลือก วาง เพื่อใส่คำสั่งสร้างภาพที่แอปคัดลอกให้ จากนั้นกดส่ง\n\n$GEMINI_IMAGE_REQUEST" else if(filePackage) "$pendingStorySummary\n$imageList\nแนบไฟล์ READ_FIRST_IMAGE_COMMAND.txt พร้อม MASTER ไปในชุดเดียวกัน ตรวจว่า $destinationLabel แสดงไฟล์คำสั่งและรูปครบก่อนกดส่ง หากช่องข้อความว่างให้ระบุคำสั่งก่อนส่ง" else "$pendingStorySummary\n$imageList\nตรวจภาพและข้อความใน $destinationLabel ก่อนกดส่ง คำสั่งคัดลอกไว้ในคลิปบอร์ดแล้ว"
            textSize = 13f
        })
        uris.filter { contentResolver.getType(it)?.startsWith("image/")==true }.forEach { uri -> preview.addView(ImageView(this).apply {
            setImageBitmap(previewBitmap(uri)); adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(-1,dp(120)).apply { bottomMargin = dp(8) }
            contentDescription = uri.lastPathSegment
        }) }
        preview.addView(TextView(this).apply { this.text = text; textSize = 14f; setTextIsSelectable(true) })
        AlertDialog.Builder(this).setTitle("ตรวจคำสั่งและภาพก่อนส่ง")
            .setView(ScrollView(this).apply { addView(preview) })
            .setPositiveButton("ส่งและเปิด $destinationLabel") { _,_ -> launchShare() }
            .setNeutralButton("คัดลอก") { _,_ -> copyCommand(text) }
            .setNegativeButton("ยกเลิก",null).showDirector()
    }

    private fun storyVisualGuide(mode: String): String {
        val aspect = plan?.optString("aspect_ratio","9:16") ?: "9:16"
        return neutralIdentityGuide(if (mode == "new") "CHARACTER MASTER ของเรื่องนี้" else "ภาพ MASTER ที่แนบ",aspect)
    }
    private fun sceneWardrobe(scene: JSONObject, person: JSONObject): String {
        val character=memoryId(person)
        val history=mutableListOf<WardrobeEntry>()
        val scenes=plan?.let { rows(it) }.orEmpty()
        for (item in scenes) {
            val people=item.optJSONArray("characters") ?: JSONArray()
            for(i in 0 until people.length()) {
                val actor=people.getJSONObject(i)
                if(memoryId(actor)!=character) continue
                val activity=WardrobeTimeline.activity(item.optString("activity").ifBlank { item.optString("action") })
                val beat=WardrobeBeat(character,item.optString("story_day"),actor.optString("wardrobe_event").ifBlank { item.optString("wardrobe_event") },
                    activity,item.optString("continuity_group"),actor.optString("outfit"),actor.optBoolean("wardrobe_change",item.optBoolean("wardrobe_change",false)))
                val outfit=WardrobeTimeline.resolve(beat,history)
                if(item === scene || (item.optInt("ep")==scene.optInt("ep") && item.optInt("scene")==scene.optInt("scene"))) return outfit
                history.add(WardrobeEntry(beat,outfit))
            }
        }
        return person.optString("outfit").ifBlank { WardrobeTimeline.resolve(WardrobeBeat(character,scene.optString("story_day"),scene.optString("wardrobe_event"),WardrobeTimeline.activity(scene.optString("activity").ifBlank { scene.optString("action") }),scene.optString("continuity_group"),""),history) }
    }
    private fun sceneCastDescription(scene: JSONObject): String {
        val cast = scene.optJSONArray("characters") ?: return ""
        return (0 until cast.length()).joinToString("\n") { i ->
            val person = cast.getJSONObject(i)
            val master=masterFor(person)
            val exported=masterExportName(master,person.optString("master").startsWith("CHARACTER",true)) ?: person.optString("master")
            listOf("${person.optString("name")} → $exported",(if(detailedImageProfiles) memoryBrief(person) else ""),
                "ชุด: ${sceneWardrobe(scene,person)}",PerformanceDirection.text(person).takeIf { it.isNotBlank() }?.let { "การแสดง: $it" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" | ") }
    }
    private fun storySceneCommand(p: JSONObject, scene: JSONObject): String {
        val setting=listOf(scene.optString("location"),scene.optString("story_day"),scene.optString("story_time"),scene.optString("activity"),scene.optString("props")).filter { it.isNotBlank() }.joinToString(" • ")
        val details=sceneCastDescription(scene)
        val people=scene.optJSONArray("characters") ?: JSONArray()
        val corrections=(0 until people.length()).mapNotNull { i ->
            val person=people.getJSONObject(i)
            val review=scene.optJSONObject("body_reviews")?.optJSONObject(memoryId(person))
            val status=bodyReviewStatus(scene,person)
            if(review!=null && status.startsWith("ต้องแก้") && review.optString("note").isNotBlank())
                "${person.optString("name")}: ${review.optString("note")} • แก้ให้กลับมาตรง MASTER โดยไม่เพิ่มสัดส่วนเอง" else null
        }.joinToString("; ")
        val action=scene.optString("action").ifBlank { scene.optString("image_prompt") }
        val continuity=listOf(scene.optString("start_state"),
            "ภาพก่อนหน้าใช้ดูสถานที่และสิ่งของ ชุดยึดฉากปัจจุบัน ตัวบุคคลยึด MASTER รายคน",corrections).filter { it.isNotBlank() }.joinToString(" • ")
        return ImagePromptPolicy.scene(details,setting,action,p.optString("aspect_ratio","9:16"),
            if(p.optString("character_mode","original")=="new") "CHARACTER MASTER ที่แนบ" else "ภาพ MASTER ที่แนบ",
            scene.optString("framing"),scene.optString("lighting"),continuity,"${key(scene)}.png")
    }

    private fun sendSceneImage(p: JSONObject, scene: JSONObject) {
        val cast = scene.optJSONArray("characters")
        val keys = if (cast != null) (0 until cast.length()).mapNotNull { FlowCast.id(cast.getJSONObject(it)) }
            else StoryIdentity.keysFromPrompt(scene.optString("image_prompt"))
        val original = p.optString("character_mode", "original") == "original"
        if (original && keys.isEmpty()) { notice("ฉากนี้ยังจับคู่ MASTER ไม่ได้ กรุณาอัปเดตบทให้มีชื่อตัวละครใน image_prompt หรือ characters"); return }
        val scenes = rows(p)
        val index = scenes.indexOfFirst { key(it) == key(scene) }
        val group = scene.optString("continuity_group")
        val previous = if (group.isBlank()) null else scenes.take(index).lastOrNull {
            it.optString("continuity_group") == group && image(it).exists()
        }
        val references = previous?.let { listOf(key(it) to image(it)) } ?: emptyList()
        val masterRefs = if(cast != null) (0 until cast.length()).map { cast.getJSONObject(it) }.map { person ->
            val source=masterFor(person)
            val exported=masterExportName(source,!original) ?: run { notice("ขาด MASTER ของ ${person.optString("name")}"); return }
            exported.substringBeforeLast('.') to source
        } else keys.map { id -> val source=File(project,"original_masters/$id.master").takeIf { it.exists() } ?: File(filesDir,"character_masters/$id.master"); "${id.uppercase()}_MASTER" to source }
        rememberScene(scene)
        shareImageCommand(storySceneCommand(p,scene),masterRefs+references)
    }
    private fun wholeStoryCommands() {
        val p = plan ?: return notice("นำเข้าแผนเรื่องก่อนครับ")
        val episodes = p.getJSONArray("episodes")
        val commands = (0 until episodes.length()).joinToString("\n\n") { i ->
            val ep = episodes.getJSONObject(i)
            rows(p).filter { it.getInt("ep") == ep.getInt("ep") }.joinToString("\n\n") { s -> storySceneCommand(p,s) } + "\n\n" + coverCommand(p,ep)
        }
        shareText("สร้างและแสดงภาพจริงตามรายการของเรื่อง ${p.getString("title")} เรียงครบทุก EP หนึ่งฉากหนึ่งภาพ เมื่อสร้างภาพฉากสุดท้ายของแต่ละ EP แล้วสร้างปกตามคำสั่งปกแยกต่างหาก หากติดข้อจำกัดให้แจ้งฉากล่าสุดเพื่อทำต่อ\n\n$commands")
    }
    private fun coverNames(p: JSONObject): List<String> {
        val eps = p.getJSONArray("episodes")
        return (0 until eps.length()).flatMap { i -> listOf("9x16","16x9").map { ratio -> "EP%02d_COVER_%s".format(eps.getJSONObject(i).getInt("ep"), ratio) } }
    }
    private fun coverFile(name: String, folder: File = project) = File(folder, "covers/$name.img")
    private fun coverCommand(p: JSONObject, ep: JSONObject): String {
        val number = ep.getInt("ep")
        val title = ep.optString("title")
        val scenes=ep.getJSONArray("scenes")
        val people=linkedMapOf<String,JSONObject>()
        for(i in 0 until scenes.length()) {
            val cast=scenes.getJSONObject(i).getJSONArray("characters")
            for(j in 0 until cast.length()) { val person=cast.getJSONObject(j); people.putIfAbsent(person.getString("name"),person) }
        }
        val refs=people.values.joinToString("\n") { person ->
            val master=masterFor(person)
            "${person.getString("name")} → ${masterExportName(master,false) ?: person.optString("master")} • ${person.optString("visual_description")} • Outfit: ${person.optString("outfit")}"
        }
        return listOf("9x16","16x9").joinToString("\n\n") { ratio ->
            FlowVisualDirection.cover(ratio,refs,p.optString("channel",originalChannel),p.getString("title"),number,title,
                ep.optString("tagline").ifBlank { p.optString("tagline") },ep.optString("cover_incident").ifBlank { scenes.getJSONObject(0).optString("action") })
        }
    }
    private fun sendCoverCommands() {
        val p = plan ?: return notice("นำเข้าบทและภาพฉากจาก ZIP ก่อนสร้างปก")
        val eps = p.getJSONArray("episodes")
        choiceMenu("🎨 สร้าง / ปรับปกจากภาพฉาก", listOf("สร้างปกทุก EP") + (0 until eps.length()).map { i ->
            val ep = eps.getJSONObject(i)
            "EP ${ep.getInt("ep")} • ${ep.optString("title")}" }) { index ->
            if (index == 0) {
                val references = (0 until eps.length()).map { i ->
                    rows(p).lastOrNull { it.getInt("ep") == eps.getJSONObject(i).getInt("ep") && image(it).exists() }
                }
                if (references.any { it == null }) notice("ยังมี EP ที่ไม่มีภาพฉาก กรุณานำเข้า ZIP หรือภาพฉากก่อน")
                else sendReferencedCovers(p, references.filterNotNull())
            } else {
                val ep = eps.getJSONObject(index - 1)
                val available = rows(p).filter { it.getInt("ep") == ep.getInt("ep") && image(it).exists() }
                if (available.isEmpty()) notice("EP นี้ยังไม่มีภาพฉาก กรุณานำเข้าภาพก่อน")
                else choiceMenu("เลือกภาพฉากต้นแบบของปก", available.map { "${key(it)} • ${it.optString("action")}" }) { selected ->
                    sendReferencedCovers(p, listOf(available[selected]))
                }
            }
        }
    }
    private fun sendReferencedCovers(p: JSONObject, references: List<JSONObject>) {
        val edits = EditText(this).apply { hint = "สิ่งที่อยากปรับ เช่น สี ตัวอักษร ตำแหน่งข้อความ (เว้นว่างได้)" }
        AlertDialog.Builder(this).setTitle("ปกจากภาพฉากที่นำเข้า")
            .setMessage("แนบภาพฉาก ${references.size} ภาพ ใช้ตัวละคร ชุด และสถานที่จากภาพเหล่านี้ สร้างปก 9:16")
            .setView(edits).setPositiveButton("ส่งไป ChatGPT") { _, _ ->
                val eps = p.getJSONArray("episodes")
                val commands = references.joinToString("\n\n") { scene ->
                    val ep = (0 until eps.length()).map { eps.getJSONObject(it) }.first { it.getInt("ep") == scene.getInt("ep") }
                    val number = ep.getInt("ep")
                    "สร้างปกแนวตั้ง 9:16 EP $number จากภาพ ${key(scene)} ที่แนบ หนึ่งภาพ ชื่อไฟล์ EP%02d_COVER_9x16.png\n".format(number)+
                        ImagePromptPolicy.coverReference("ภาพฉากที่แนบ")+"\nเหตุการณ์: ${scene.optString("action")}\n"+
                        CoverTypography.prompt(p.optString("channel",originalChannel),p.getString("title"),number,ep.optString("title"))
                }
                shareText("ใช้ภาพฉากที่แนบจากชุดงานที่นำเข้าเป็นต้นแบบปก แก้จากภาพเดิม โดยคงชุดและสถานที่ของภาพนั้น\nสิ่งที่ผู้ใช้ขอปรับ: ${edits.text.toString().ifBlank { "ออกแบบปกให้สวยงาม อ่านชัด" }}\n$commands\nแสดงปกจริงแยกไฟล์ และรวมเฉพาะปกที่สร้างใน ZIP สำหรับนำเข้ากลับ DIRECTOR X หากสร้างไม่ได้ให้แจ้งตามจริง", includeMasters = false, referenceImages = references.map { key(it) to image(it) })
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private var starterFlowPack = false
    private val flowPackPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri!=null) {
            val current=if(starterFlowPack) null else plan?.let { JSONObject(it.toString()) }
            val sources=FlowCast.definitions(current).associate { person ->
                val id=FlowCast.id(person)!!
                id to
                if(current==null) File(filesDir,"character_masters/$id.master")
                else File(project,"original_masters/$id.master").takeIf { it.exists() } ?: File(filesDir,"character_masters/$id.master")
            }
            val destination=project
            val storyRevision=if(current!=null) File(destination,"story.json").takeIf { it.isFile }?.let { EpisodeMedia.digest(it) } else null
            val memory=JSONObject()
            sources.forEach { (id,file) -> memory.put(id,characterMemory.read(id,file)) }
            background {
                contentResolver.openOutputStream(uri)!!.use { output ->
                    if(current!=null) SimpleProductionCommands.write(output,current)
                    else FlowProductionPack.write(output,null,sources,memory,categories.first(),originalChannel)
                }
                if(storyRevision!=null) File(destination,"flow_pack_exported.sha256").writeText(storyRevision)
            }
        }
    }
    private val flowCodePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if(uri!=null) runCatching {
            val text=prefs.getString("pending_flow_code","").orEmpty()
            require(text.isNotBlank()) { "ไม่มีคำสั่งให้บันทึก" }
            contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.onSuccess { notice("บันทึกไฟล์คำสั่งแล้ว") }.onFailure { notice("บันทึกไม่สำเร็จ: ${it.message}") }
    }
    private fun sendStoryPackage(code: String) {
        runCatching {
            val destination=File(cacheDir,"character_master_exports/${UUID.randomUUID()}").apply { mkdirs() }
            val uris=arrayListOf<Uri>()
            val command=File(destination,"FLOW_STORY_COMMAND.txt").apply { writeText(code,Charsets.UTF_8) }
            uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",command))
            FlowProductionPack.names.keys.forEach { id ->
                val source=File(project,"original_masters/$id.master").takeIf { it.isFile } ?: File(filesDir,"character_masters/$id.master")
                val name=masterExportName(source,false) ?: error("อ่าน MASTER ของ ${FlowProductionPack.names[id]} ไม่ได้")
                val image=File(destination,name); source.copyTo(image)
                uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",image))
            }
            val intro="อ่าน FLOW_STORY_COMMAND.txt และ MASTER ทั้ง 3 ภาพที่แนบ แล้วเริ่มเขียนบทตามคำสั่ง สร้าง story.json และ ZIP บทให้ดาวน์โหลดเพื่อนำกลับเข้า DIRECTOR X ไม่สร้างภาพหรือวิดีโอในขั้นตอนนี้\n\n$code"
            val intent=buildStoryPackageIntent(intro,uris)
            val chooser=Intent.createChooser(intent,"เลือกแอป AI สำหรับเขียนบท").apply {
                clipData=intent.clipData; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        }.onFailure { notice("ส่งชุดวางบทไม่สำเร็จ: ${it.message}\nใช้ดูคำสั่งหรือบันทึกไฟล์เพื่อลองใหม่ได้") }
    }
    private fun commandCardsMenu() {
        val story=plan ?: return notice("สร้างหรือนำเข้าบทก่อนครับ")
        if(story.optString("aspect_ratio")!="9:16") { landscapeCopyMenu(); return }
        val eps=story.getJSONArray("episodes")
        val options=(0 until eps.length()).map { "EP.${eps.getJSONObject(it).getInt("ep")} • ${eps.getJSONObject(it).optString("title")} • 2 ไฟล์" }+
            listOf("ตรวจคำสั่ง / แก้เฉพาะจุด","ดาวน์โหลด ZIP • ตอนละ 2 ไฟล์")
        choiceMenu("คำสั่งภาพ + Flow • แยกตาม EP",options) { index ->
            when {
                index==eps.length() -> productionCheckMenu(story)
                index>eps.length() -> {
                    val problems=ProductionCommandCheck.issues(story)
                    if(problems.isNotEmpty()) productionCheckMenu(story)
                    else { starterFlowPack=false; flowPackPicker.launch(StoryFiles.zipName(story.optString("title"))) }
                }
                else -> {
                    val number=eps.getJSONObject(index).getInt("ep")
                    choiceMenu("EP.$number • คำสั่งพร้อมใช้",listOf("1. สร้างภาพ ChatGPT / Gemini • แนบ MASTER ให้","2. สร้างวิดีโอใน Flow • ใช้ภาพฉาก","ไฟล์คำสั่ง / แก้ไข / ทำต่อ")) { kind ->
                        if(kind<2) productionTaskDeck(story,number,kind==0)
                        else productionTaskType(story,number)

                    }
                }
            }
        }
    }
    private fun productionCheckMenu(story: JSONObject) {
        val problems=ProductionCommandCheck.issues(story)
        val text=if(problems.isEmpty()) "ผ่านการตรวจโครงสร้าง ตัวละคร ผู้พูด ตำแหน่ง ชุดรายวัน และข้อขัดแย้งเสียงที่ระบบตรวจได้ • ยังต้องตรวจภาพและวิดีโอจริง" else problems.joinToString("\n\n")
        AlertDialog.Builder(this).setTitle("ตรวจคำสั่ง • ${problems.size} จุด").setMessage(text)
            .setPositiveButton("แก้ซีนที่เลือก") { _,_ ->
                val scenes=rows(story)
                choiceMenu("แก้เฉพาะซีน • ไม่เขียนเรื่องใหม่",scenes.map { key(it)+" • "+it.optString("action").take(50) }) { editScene(scenes[it]) }
            }.setNeutralButton("แก้ข้อความปก") { _,_ -> editProductionCover(story) }.setNegativeButton("ปิด",null).showDirector()
    }
    private fun editProductionCover(story: JSONObject) {
        val eps=story.getJSONArray("episodes")
        choiceMenu("แก้ข้อความปก",listOf("ปกรวม • ชื่อช่อง / ชื่อเรื่อง / คำโปรย")+(0 until eps.length()).map { "EP.${eps.getJSONObject(it).getInt("ep")} • ชื่อตอน" }) { index ->
            val target=if(index==0) story else eps.getJSONObject(index-1)
            val fields=if(index==0) listOf("channel" to "ชื่อช่อง","title" to "ชื่อเรื่อง","tagline" to "คำโปรย") else listOf("title" to "ชื่อตอน")
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            val inputs=fields.map { (key,label) -> key to EditText(this).apply { hint=label; setText(target.optString(key)); panel.addView(this) } }
            val dialog=AlertDialog.Builder(this).setTitle("แก้ข้อความปก • ไม่เปลี่ยนซีน").setView(panel).setPositiveButton("บันทึก",null).setNegativeButton("ปิด",null).showDirector()
            dialog.getButton(-1).setOnClickListener {
                if(inputs.any { it.second.text.isBlank() }) notice("กรอกข้อความปกให้ครบ")
                else { inputs.forEach { (key,input) -> target.put(key,input.text.toString().trim()) }; saveStory(); dialog.dismiss(); render(); notice("บันทึกข้อความปกแล้ว • ส่งออกไฟล์ใหม่ได้ ไม่ต้องเขียนบทใหม่") }
            }
        }
    }
    private fun productionTaskType(story: JSONObject,number: Int) {
        choiceMenu("EP.$number • เปิดหรือแก้เฉพาะรายการ",listOf("รูปและปก ChatGPT / Gemini","วิดีโอ Flow")) { productionTaskDeck(story,number,it==0) }
    }
    private fun productionTaskDeck(story: JSONObject,number: Int,images: Boolean) {
        val tasks=runCatching { if(images) SimpleProductionCommands.imageTasks(story,number) else SimpleProductionCommands.videoTasks(story,number) }.getOrElse {
            notice(it.message ?: "ข้อมูลคำสั่งไม่ครบ"); productionCheckMenu(story); return
        }
        val preference="production_cursor_${project.name}_${number}_$images"
        val previous=prefs.getString(preference,"")
        val start=tasks.indexOfFirst { it.id==previous }.coerceAtLeast(0)
        val options=listOf("ทำต่อจากล่าสุดที่เปิด • ${tasks[start].id}")+tasks.map { "${it.id} • ${it.title}" }
        choiceMenu("EP.$number • เลือกรหัสรูป/ซีน",options) { selected ->
            val index=if(selected==0) start else selected-1; val task=tasks[index]
            prefs.edit().putString(preference,task.id).apply()
            val next=tasks.getOrNull(index+1)?.id ?: "จบรายการของ EP นี้"
            val code=SimpleProductionCommands.block(task,index,images)+"\n\nล่าสุดที่เปิด: ${task.id} | ถัดไป: $next\nสถานะนี้บันทึกการเปิดคำสั่ง ไม่ได้ยืนยันว่าภาพหรือคลิปสร้างสำเร็จแล้ว"
            val problems=ProductionCommandCheck.issues(story)
            val dialog=showFlowCode("${task.id} • ถัดไป $next",code,"${task.id}_${if(images) "IMAGE" else "FLOW"}.txt")
            if(problems.isNotEmpty()) dialog.getButton(-1).setOnClickListener { notice("คำสั่งยังมีจุดต้องแก้ • เปิด ตรวจคำสั่ง / แก้เฉพาะจุด ก่อนคัดลอก") }
            dialog.getButton(-1).text=if(images) "เลือกแอปสร้างภาพ + MASTER" else "ส่ง Flow + ภาพฉาก"
            dialog.getButton(-1).setOnClickListener {
                if(problems.isNotEmpty()) { productionCheckMenu(story); return@setOnClickListener }
                if(images) {
                    val refs=task.references.map { person -> person.getString("master").substringBeforeLast('.') to masterFor(person) }
                    if(refs.isEmpty() || refs.any { !it.second.isFile }) { notice("ขาด MASTER ของตัวละครในรายการนี้"); return@setOnClickListener }
                    shareImageCommand(task.command,refs)
                } else {
                    val scene=rows(story).first { key(it)==task.id }
                    if(!image(scene).isFile) { notice("สร้างภาพซีนนี้ใน ChatGPT หรือ Gemini แล้วนำเข้าภาพผ่านขั้นตอน 4 ก่อนส่ง Flow"); return@setOnClickListener }
                    if(!sceneFrameApproved(scene) || FlowScenePrompt.issues(flowSpec(scene)).isNotEmpty()) { compareScene(scene); return@setOnClickListener }
                    shareText(flowCommand(scene),includeMasters=false,referenceImages=listOf(task.id to image(scene)),destinationLabel="Flow")
                }
            }
            dialog.getButton(-3).text="แก้เฉพาะรายการนี้"
            dialog.getButton(-3).setOnClickListener {
                dialog.dismiss()
                val scene=rows(story).firstOrNull { key(it)==task.id }
                choiceMenu("แก้ ${task.id}",if(scene==null) listOf("แก้การจัดภาพปก","แก้ข้อความบนปก") else listOf("แก้ทิศทาง${if(images) "ภาพ" else "วิดีโอ"}เฉพาะจุด","แก้บท / การกระทำ / ชุด / ตำแหน่ง / เสียง")) { action ->
                    if(action==1) { if(scene==null) editProductionCover(story) else editScene(scene) }
                    else {
                        val target=scene ?: if(task.id.startsWith("STORY_")) story else story.getJSONArray("episodes").let { eps -> (0 until eps.length()).map { eps.getJSONObject(it) }.first { it.getInt("ep")==number } }
                        val field=if(scene==null) "production_note_${task.id}" else if(images) "production_image_note" else "production_video_note"
                        val input=EditText(this).apply { hint="ระบุส่วนที่ต้องแก้ โดยคงตัวละคร ชุดและบทเดิม"; setText(target.optString(field)) }
                        AlertDialog.Builder(this).setTitle("แก้เฉพาะ ${task.id}").setView(input).setPositiveButton("บันทึก") { _,_ ->
                            target.put(field,input.text.toString().trim())
                            if(scene!=null) { scene.put("user_review","needs_revision"); scene.remove("flow_image_revision") }
                            saveStory(); render(); notice("บันทึกแล้ว • รายการอื่นและชุดประจำวันคงเดิม • เปิดรหัสเดิมหรือส่งออกใหม่ได้")
                        }.setNegativeButton("ปิด",null).showDirector()
                    }
                }
            }
        }
    }
    private fun showCommandCards(cards: List<SceneCommandCards.Card>, number: Int, kind: Int) {
        require(cards.isNotEmpty())
        var index=0
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val picker=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,cards.map { it.title }); panel.addView(this) }
        panel.addView(TextView(this).apply { text=if(kind==0) "แนบ MASTER ตามรายชื่อ • สร้างทีละภาพใน Gemini" else "แม่แบบ Flow • แนบภาพซีนที่ตรวจแล้ว ตรวจผู้พูดและตำแหน่งก่อนใช้ • เสียงสร้างจากคำสั่งใน Flow"; textSize=12f })
        val code=TextView(this).apply { textSize=13f; setTextIsSelectable(true); setPadding(dp(8),dp(8),dp(8),dp(8)) }
        panel.addView(ScrollView(this).apply { addView(code) },LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*0.38).toInt()))
        val nav=LinearLayout(this).apply { orientation=0; panel.addView(this) }
        fun show(n: Int) { index=n.coerceIn(cards.indices); code.text=cards[index].command; if(picker.selectedItemPosition!=index) picker.setSelection(index) }
        listOf("ก่อนหน้า" to -1,"ถัดไป" to 1).forEach { (label,step) -> nav.addView(android.widget.Button(this).apply { text=label; setOnClickListener { show(index+step) } },LinearLayout.LayoutParams(0,-2,1f)) }
        picker.onItemSelectedListener=object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?,view: android.view.View?,position: Int,id: Long) { show(position) }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        val dialog=AlertDialog.Builder(this).setTitle("EP.$number • ${if(kind==0) "คำสั่งภาพ" else "คำสั่งวิดีโอ"}").setView(panel)
            .setPositiveButton("คัดลอกชุดนี้",null).setNeutralButton("บันทึกทั้ง EP") { _,_ -> prefs.edit().putString("pending_flow_code",SceneCommandCards.join(cards)).apply(); flowCodePicker.launch("EP%02d_%s.txt".format(number,if(kind==0) "IMAGES" else "VIDEOS")) }
            .setNegativeButton("ปิด",null).showDirector()
        dialog.getButton(-1).setOnClickListener { copyCommand(cards[index].command) }
        show(0)
    }
    private fun showFlowCode(title: String, code: String, filename: String): AlertDialog {
        val text=TextView(this).apply {
            this.text=code; textSize=13f; typeface=Typeface.MONOSPACE; setTextIsSelectable(true)
            setPadding(dp(16),dp(12),dp(16),dp(12)); setTextColor(ice); setBackgroundColor(navy)
        }
        // Bound only the scroll area, leaving dialog title and action buttons visible.
        val scroll=ScrollView(this).apply { addView(text) }
        val frame=FrameLayout(this).apply {
            addView(scroll,FrameLayout.LayoutParams(-1,-1))
            layoutParams=android.view.ViewGroup.LayoutParams(-1,(resources.displayMetrics.heightPixels*0.48f).toInt())
        }
        val builder=AlertDialog.Builder(this).setTitle(title)
            .setMessage(if(filename.endsWith("_IMAGE.txt")) "คำสั่งภาพรายการเดียวสำหรับ Gemini • แนบ MASTER ที่ตรงกับตัวละคร • หัวข้อและชื่อไฟล์ไม่ใช่ข้อความให้วาดลงซีน" else if(filename.matches(Regex("EP[0-9]+_IMAGES[.]txt"))) "แนบไฟล์นี้พร้อม MASTER ใน Gemini • มีสารบัญและรหัสภาพ • next = ถัดไป / แก้ภาพ <รหัส> = แก้เฉพาะจุด • ตรวจข้อความปกในภาพจริง" else if(filename.matches(Regex("EP[0-9]+_FLOW[.]txt"))) "รวมทุกซีนของ EP นี้ • คัดลอกทีละบล็อกไป Flow พร้อมภาพ PNG รหัสเดียวกัน • บทพูดและเสียงอยู่ในบล็อกเดียวแล้ว" else if(filename.contains("GEMINI_NEXT")) "คัดลอกทั้งหมดครั้งเดียวไป Gemini พร้อม MASTER ที่ระบุ • สร้างทีละภาพแล้วพิมพ์ next • ถ้าข้อความยาวเกินช่องแชต ให้บันทึกไฟล์แนบพร้อม MASTER แล้วสั่งให้อ่านไฟล์และเริ่มคิว • ต้องตรวจผลจริง ภาพไม่ได้สร้างในแอป" else if(filename.startsWith("AD_")) "ภาพและปก: คัดลอกไป Gemini พร้อมภาพอ้างอิง • XML: คัดลอกคำสั่งทีละฉากไป Flow หลังตรวจภาพ • แอปไม่ได้สร้างสื่อในขั้นตอนนี้" else if(filename=="FLOW_STORY_COMMAND.txt") "ส่งคำสั่ง + MASTER 3 ภาพให้ AI เขียนบท → รับ story.json → นำเข้าขั้นตอน 2 • แอปปลายทางอาจให้กดส่งอีกครั้ง" else "ไฟล์คำสั่งสำหรับใช้งานใน Flow • ยังไม่ได้สร้างภาพหรือวิดีโอ")
            .setView(frame)
            .setPositiveButton(if(filename=="FLOW_STORY_COMMAND.txt") "ส่งให้ AI" else if(filename.contains("GEMINI_NEXT")) "คัดลอกทั้งหมด" else "คัดลอก") { _,_ ->
                if(filename=="FLOW_STORY_COMMAND.txt") sendStoryPackage(code) else copyCommand(code)
            }
            .setNeutralButton("บันทึกไฟล์") { _,_ -> prefs.edit().putString("pending_flow_code",code).apply(); flowCodePicker.launch(filename) }
            .setNegativeButton("ปิด",null)
        val dialog=builder.showDirector()
        frame.layoutParams=frame.layoutParams.apply { height=(resources.displayMetrics.heightPixels*0.48f).toInt() }
        frame.requestLayout()
        return dialog
    }
    private fun flowHome() {
        label("เลือกคลิปสั้น / คลิปยาว • เลือกเวลาต่อ EP • ซีนละ 8 วินาที • 9:16",13)
        val current=plan
        val storyFile=File(project,"story.json")
        val issues=current?.let { FlowProductionPack.issues(it) }.orEmpty()
        val prepared=current!=null || File(project,"flow_story_command.txt").isFile
        val exported=current!=null && storyFile.isFile && File(project,"flow_pack_exported.sha256").takeIf { it.isFile }?.readText()==EpisodeMedia.digest(storyFile)
        val episodes=current?.getJSONArray("episodes")
        val sceneCount=current?.let { rows(it).size } ?: 0
        val clipCount=current?.let { rows(it).count { scene -> video(scene).isFile && video(scene).length()>0 } } ?: 0
        val finished=episodes!=null && episodes.length()>0 && (0 until episodes.length()).all { i ->
            val ep=episodes.getJSONObject(i).getInt("ep")
            latestMovie(ep)?.let { job -> runCatching {
                val specFile=File(job,"spec.json")
                val spec=JSONObject(specFile.readText())
                val scenes=orderedMovieScenes(episodes.getJSONObject(i))
                val inputs=listOf(coverFile("EP%02d_COVER_9x16".format(ep)))+scenes.map { video(it) }
                val saved=spec.getJSONArray("clips")
                saved.length()==scenes.size && (0 until saved.length()).all { saved.getString(it)==video(scenes[it]).path } &&
                    spec.optString("cover")==inputs.first().path && inputs.all { it.isFile && it.lastModified()<=specFile.lastModified() }
            }.getOrDefault(false) } ?: false
        }
        val active=when { current==null -> if(prepared) 2 else 1; !exported -> 3; else -> 4 }
        label(if(current==null) "ยังไม่มีบท • เริ่มขั้นตอน 1 หรือนำเข้าบทที่มีแล้ว" else "${current.optString("title")} • ${episodes!!.length()} EP • คลิป $clipCount/$sceneCount",14,true)
        if(File(project,"gemini_job/spec.json").isFile) {
            geminiStatusView=TextView(this).apply { text=geminiStatusText(readGeminiStatus()); setTextColor(ice); textSize=14f; setPadding(dp(8),dp(8),dp(8),dp(8)) }
            root.addView(geminiStatusView)
            geminiProgressView=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=100; progress=readGeminiStatus()?.optInt("percent")?.coerceIn(0,100) ?: 0
                progressTintList=ColorStateList.valueOf(Color.rgb(0,210,240))
                contentDescription="ความคืบหน้าบทที่บันทึกแล้ว"
            }
            root.addView(geminiProgressView,LinearLayout.LayoutParams(-1,dp(10)).apply { setMargins(dp(8),0,dp(8),dp(8)) })
            button("🤖 งานเขียนบท • ดูสถานะ / ทำต่อ",false) { geminiJobMenu() }
        } else { geminiStatusView=null; geminiProgressView=null }
        stepCard(1,"📝","วางบทและเขียนคำสั่ง","เลือกแนวเรื่อง → AI เขียนบทและรับกลับในแอป → ส่งออก Flow • ตั้งค่า API Key ครั้งแรก",completed=prepared,currentStep=active,
            statusText=if(prepared) "เตรียมคำสั่ง / มีบทแล้ว" else "พร้อมเริ่มวางบท") { flowPlanning() }
        stepCard(2,"📥","นำเข้าบท / ZIP","นำเข้า story.json หรือ ZIP บท • ไม่ต้องรอภาพฉาก • เพิ่มไฟล์ภาพและคลิปภายหลังได้",completed=current!=null,currentStep=active,
            statusText=if(current!=null) "นำเข้าบทแล้ว • เพิ่มไฟล์ได้" else "รอนำเข้า story.json / ZIP") { packagePicker.launch(arrayOf("*/*")) }
        stepCard(3,"📦","คำสั่งสร้างรูป + Flow • ตอนละ 2 ไฟล์",if(current==null) "สร้างหรือนำเข้าบทก่อน แล้วใช้คำสั่งเพียง 2 ไฟล์" else if(issues.isNotEmpty()) "บทต้องแก้ ${issues.size} จุด • แตะดูรายการก่อนส่งออก" else "เลือก EP → ไฟล์ภาพ + ไฟล์ Flow • มีสารบัญ รหัสซีน และตรวจคำสั่งก่อนส่งออก • MASTER เก็บในแอป",
            enabled=current!=null,completed=exported,currentStep=active,statusText=when { current==null -> "รอนำเข้าบท"; issues.isNotEmpty() -> "ต้องตรวจแก้บท"; exported -> "ส่งออกบทฉบับนี้แล้ว"; else -> "พร้อมส่งออกคำสั่ง" }) {
            if(issues.isNotEmpty()) notice(issues.take(30).joinToString("\n")) else commandCardsMenu()
        }
        stepCard(4,"🎥","รับ MP4 / รวม EP / ดาวน์โหลด",if(current==null) "ต้องมีบทเพื่อจับคู่คลิปกับซีน • รับคลิปจาก Flow แล้วรวมและดาวน์โหลด MP4" else "สร้างภาพฉาก → เทียบ MASTER / ยืนยัน → สร้างวิดีโอจากภาพนั้น → รับคลิป $clipCount/$sceneCount และรวม EP",
            enabled=current!=null,completed=finished,currentStep=active,statusText=when { current==null -> "รอบทสำหรับจับคู่คลิป"; finished -> "รวม EP ครบแล้ว"; clipCount<sceneCount -> "รับคลิป / ทำต่อได้"; else -> "คลิปครบ • พร้อมรวม EP" }) {
            choiceMenu("งานวิดีโอและ EP",listOf("EP / ซีน • ตรวจภาพก่อนสร้างคลิป","รวม EP / ดาวน์โหลด MP4")) { if(it==0) episodesMenu() else moviesMenu() }
        }
        if(current!=null && current.optString("aspect_ratio","16:9")!="9:16") button("↔ สร้างสำเนาเรื่องแนวตั้ง 9:16",false) { landscapeCopyMenu() }
        button("📋 คำสั่งภาพ / วิดีโอ • ปกและซีน",false) { commandCardsMenu() }
        label("เครื่องมือเสริม",15,true)
        button("⚙️ AutoDirector v45 • ภาพ Gemini / วิดีโอ Flow",false) { autoDirectorMenu() }
        button("🧪 ทดสอบสร้างวิดีโอ • เลือกตัวละคร",false) { flowTestMenu() }
        button("🎭 MASTER และข้อมูลตัวละคร",false) { choiceMenu("ตัวละคร",listOf("ตัวละครหลัก 3 คน","ตัวละครเสริมสำหรับเรื่องใหม่","ตัวละครของเรื่องที่เปิด","คลัง MASTER 35")) { when(it) { 0 -> characterMasters(); 1 -> supportingCastMenu(); 2 -> castMenu(); else -> libraryMenu() } } }
        button("⬇ ดาวน์โหลดแม่บทเริ่มงาน + MASTER",false) { starterFlowPack=true; flowPackPicker.launch("DIRECTOR_FLOW_STARTER.zip") }
        splitActions("📚 เรื่องที่บันทึก","⚙️ ตั้งค่า",{ storiesMenu() },{ flowSettings() })
    }
    private fun flowSettings() {
        choiceMenu("ตั้งค่า DIRECTOR X",listOf("อัปเดตแอป","ดาวน์โหลดแม่บท + MASTER","สำรองเรื่องทั้งหมด","กู้คืนชุดสำรอง","เริ่มเรื่องใหม่","Groq / Gemini API • ตั้งค่าการเขียนบท")) { i -> when(i) {
            0 -> updater.check()
            1 -> { starterFlowPack=true; flowPackPicker.launch("DIRECTOR_FLOW_STARTER.zip") }
            2 -> if(plan==null) notice("ยังไม่มีเรื่องให้สำรอง") else { backupMode=true; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title")+"_สำรอง")) }
            3 -> restorePicker.launch(arrayOf("application/zip"))
            4 -> AlertDialog.Builder(this).setTitle("เริ่มเรื่องใหม่")
                .setMessage("เรื่องปัจจุบันยังอยู่ในรายการเรื่องที่บันทึก")
                .setPositiveButton("เริ่มใหม่") { _,_ -> prefs.edit().putString("project",UUID.randomUUID().toString()).apply(); render() }
                .setNegativeButton("ยกเลิก",null).showDirector()
            5 -> geminiSettings()
        } }
    }
    private fun readGeminiStatus(): JSONObject? = runCatching { GeminiStoryFiles.read(File(project,"gemini_job/status.json")) }.getOrNull()
    private fun geminiStatusText(status: JSONObject?): String {
        if(status==null) return "${StoryProvider.label(this)} • ยังไม่เริ่มงาน"
        return StoryStatusText.format(status,GeminiStoryService.running && GeminiStoryService.projectId==project.name)
    }
    private fun geminiSettings(afterSave: (() -> Unit)?=null) {
        val choices=StoryProvider.entries.map { it.label+if(!StoryProvider.hybrid(this) && it==StoryProvider.selected(this)) " • เลือกอยู่" else "" }+
            listOf("Groq + Gemini อัตโนมัติ"+if(StoryProvider.hybrid(this)) " • เลือกอยู่" else "","ทดสอบการเชื่อมต่อ API ทั้งสองตัว")
        choiceMenu("เลือก API เขียนบท",choices) { index ->
            when(index) {
                0,1 -> storyProviderSettings(StoryProvider.entries[index],afterSave)
                2 -> {
                    val missing=StoryProvider.entries.filterNot { GeminiSettings(this,it).hasKey() }
                    if(missing.isNotEmpty()) notice("ยังขาดคีย์: "+missing.joinToString { it.label }+" • เลือกบริการในตั้งค่า API แล้วบันทึกคีย์ก่อน")
                    else {
                        StoryProvider.enableHybrid(this)
                        afterSave?.invoke() ?: notice("เลือก Groq + Gemini แล้ว • Gemini วางโครง / Groq เขียนซีน • ใช้ข้อมูลเรื่องและบัญชีชุดร่วมกัน • สลับเมื่อโควตาเต็มหรือบริการขัดข้อง • โควตาและค่าบริการตามบัญชีแต่ละตัว")
                    }
                }
                3 -> testStoryApis(StoryProvider.entries.toList())
            }
        }
    }
    private fun testStoryApis(providers: List<StoryProvider>) {
        if(GeminiStoryService.running) { notice("กรุณารอหรือหยุดงานเขียนบทก่อนทดสอบ API"); return }
        val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
        val active=java.util.concurrent.atomic.AtomicReference<StoryTextClient?>(null)
        val view=TextView(this).apply { setPadding(dp(20),dp(16),dp(20),dp(16)); text="กำลังตรวจคีย์และโมเดลที่บันทึกไว้…\nส่งคำขอทดสอบสั้นจริงไปแต่ละบริการ ใช้โควตาของบัญชีนั้น ไม่ส่งบทหรือรูป"; setTextIsSelectable(true) }
        val dialog=AlertDialog.Builder(this).setTitle("ทดสอบ API • ไม่สลับบริการแทนผลทดสอบ")
            .setView(view).setNegativeButton("ปิด / หยุด",null).showDirector()
        dialog.setOnDismissListener { stopped.set(true); active.get()?.cancel() }
        Thread {
            val results=mutableListOf<String>()
            for(target in providers) {
                if(stopped.get()) break
                val result=try {
                    val settings=GeminiSettings(this,target)
                    if(!settings.hasKey()) "${target.label}: ยังไม่มีคีย์ / อ่านคีย์ไม่ได้"
                    else {
                        val client: StoryTextClient=if(target==StoryProvider.GROQ) GroqClient(settings.key(),settings.model(),{stopped.get()},automaticRetry=false)
                            else GeminiClient(settings.key(),settings.model(),{stopped.get()},automaticRetry=false)
                        active.set(client)
                        if(stopped.get()) throw java.util.concurrent.CancellationException()
                        StoryApiProbe.check(client)
                        "${target.label}: ผ่าน • ${settings.model()} • รับคำตอบ JSON สำเร็จ"
                    }
                } catch(e: Exception) { "${target.label}: "+StoryApiProbe.error(e) }
                finally { active.set(null) }
                results.add(result)
                val text=results.joinToString("\n\n")+"\n\nผลทดสอบ ณ เวลานี้ ไม่รับประกันโควตาระหว่างเขียนเรื่องยาว"+
                    if(results.size<providers.size) "\nกำลังทดสอบบริการถัดไป…" else ""
                runOnUiThread { if(!stopped.get() && !isFinishing && !isDestroyed) view.text=text }
            }
        }.apply { name="director-api-probe"; start() }
    }
    private fun storyProviderSettings(provider: StoryProvider,afterSave: (() -> Unit)?=null) {
        val settings=GeminiSettings(this,provider)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        panel.addView(TextView(this).apply { text="${provider.label} • ใส่คีย์บนเครื่องนี้ คีย์แต่ละบริการเก็บแยกและเข้ารหัส ไม่ส่งออกในสำรองข้อมูล • ส่งเฉพาะข้อความบท/ลักษณะตัวละคร ไม่ส่งรูป MASTER • แผนฟรีมีโควตา ค่าบริการและเงื่อนไขข้อมูลขึ้นกับบัญชีของคุณ ไม่มีการเปิดแผนเสียเงินเอง • โหมด Groq + Gemini จะสลับระหว่างสองบริการตามคีย์และโมเดลที่คุณตั้งไว้" })
        val key=EditText(this).apply { hint=if(settings.hasKey()) "มีคีย์แล้ว • ว่างเพื่อใช้คีย์เดิม" else "${provider.label} API Key"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(true); panel.addView(this) }
        val model=EditText(this).apply { hint="ชื่อโมเดล"; setSingleLine(true); setText(settings.model()); panel.addView(this) }
        if(provider==StoryProvider.GROQ) panel.addView(TextView(this).apply { text="โมเดลที่รองรับ: "+GroqClient.MODELS.joinToString(" / ")+" • ครั้งละ 2 ซีน • เว้นคำขออย่างน้อย 65 วินาทีเพื่อลดการชนโควตาฟรี • หยุด / ทำต่อได้" })
        panel.addView(Button(this).apply { text="สร้างคีย์ ${provider.label}"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(provider.keyUrl))) } })
        panel.addView(Button(this).apply { text="บันทึกคีย์และทดสอบ ${provider.label}"; setOnClickListener {
            runCatching { settings.save(key.text.toString().trim().ifBlank { settings.key() },model.text.toString().trim()) }
                .onSuccess { key.setText(""); key.hint="มีคีย์แล้ว • ว่างเพื่อใช้คีย์เดิม"; testStoryApis(listOf(provider)) }
                .onFailure { notice("ตรวจคีย์และชื่อโมเดลก่อนทดสอบ") }
        } })
        val dialog=AlertDialog.Builder(this).setTitle("${provider.label} • ตั้งค่า API")
            .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("บันทึกและเลือกใช้",null)
            .setNeutralButton("ลบคีย์") { _,_ -> settings.clear(); notice("ลบคีย์ ${provider.label} จากเครื่องแล้ว") }.setNegativeButton("ปิด",null).showDirector()
        dialog.getButton(-1).setOnClickListener {
            runCatching { settings.save(key.text.toString().trim().ifBlank { settings.key() },model.text.toString().trim()); StoryProvider.select(this,provider) }
                .onSuccess { dialog.dismiss(); afterSave?.invoke() ?: notice("เลือก ${provider.label} แล้ว • งานเดิมกดทำต่อ / เรื่องใหม่เข้าขั้นตอน 1 • ยังไม่ได้ทดสอบเรียก API") }
                .onFailure { Toast.makeText(this,it.message ?: "บันทึกไม่ได้",Toast.LENGTH_LONG).show() }
        }
    }
    private fun startGeminiJob(folder: File) {
        if(GeminiStoryService.running) { notice("มีงานเขียนบทกำลังทำงานอยู่ กรุณารอหรือหยุดงานเดิมก่อน"); return }
        runCatching {
            val intent=Intent(this,GeminiStoryService::class.java).putExtra("project",folder.name)
            androidx.core.content.ContextCompat.startForegroundService(this,intent)
        }.onFailure { notice("เริ่มงานไม่ได้ กรุณาเปิดแอปค้างไว้แล้วลองใหม่") }
    }
    private fun geminiJobMenu() {
        val state=readGeminiStatus()?.optString("state")
        if(state=="complete" && plan!=null) { if(plan!!.optString("content_profile")==AutoDirectorContent.profile) autoDirectorMenu() else episodesMenu(); return }
        if(GeminiStoryService.running) {
            val live=TextView(this).apply { setPadding(dp(20),dp(16),dp(20),dp(16)); text=geminiStatusText(readGeminiStatus()); setTextIsSelectable(true) }
            geminiLiveDialog=live
            val dialog=AlertDialog.Builder(this).setTitle("สถานะงาน • อัปเดตทุกวินาที")
                .setView(live)
                .setPositiveButton("ดูต่อ",null).setNeutralButton("หยุดงาน") { _,_ -> startService(Intent(this,GeminiStoryService::class.java).setAction(GeminiStoryService.CANCEL)) }
                .setNegativeButton("ปิด",null).showDirector()
            dialog.setOnDismissListener { if(geminiLiveDialog===live) geminiLiveDialog=null }
        } else {
            AlertDialog.Builder(this).setTitle("เขียนบทต่อจากที่บันทึก")
                .setMessage(geminiStatusText(readGeminiStatus())+"\nจะใช้ ${StoryProvider.label(this@StudioActivity)} และโมเดลที่ตั้งค่าปัจจุบัน เขียนเฉพาะช่วงที่ยังไม่สำเร็จ")
                .setPositiveButton("ทำต่อ") { _,_ ->
                    if(!StoryProvider.ready(this)) geminiSettings { geminiJobMenu() }
                    else runCatching {
                        val specFile=File(project,"gemini_job/spec.json"); val spec=StoryProvider.configure(this,GeminiStoryFiles.read(specFile))
                        GeminiStoryFiles.write(specFile,spec); startGeminiJob(project)
                    }.onFailure { notice("อ่านงานเดิมไม่ได้ กรุณาเริ่มเรื่องใหม่") }
                }.setNeutralButton("ตั้งค่า API") { _,_ -> geminiSettings() }.setNegativeButton("ปิด",null).showDirector()
        }
    }
    private fun landscapeCopyMenu() {
        val source=plan ?: return notice("ยังไม่มีบทให้ทำสำเนา")
        AlertDialog.Builder(this).setTitle("สร้างสำเนาแนวตั้ง 9:16")
            .setMessage("เก็บเรื่องเดิมไว้ แล้วทำสำเนาบทกับ MASTER เพื่อสร้างปก ภาพ และวิดีโอใหม่เป็นแนวตั้ง 1080×1920 • ไม่ยืดหรือครอปภาพ/คลิปเก่าอัตโนมัติ ต้องตรวจภาพใหม่ก่อนใช้ Flow")
            .setPositiveButton("สร้างสำเนา") { _,_ ->
                val original=project
                val folder=File(base,UUID.randomUUID().toString())
                runCatching {
                    folder.mkdirs()
                    listOf("original_masters","masters").forEach { name -> val refs=File(original,name); if(refs.isDirectory) check(refs.copyRecursively(File(folder,name))) }
                    val copy=LandscapeProduction.copyPlan(source).put("portrait_copy_of",original.name)
                    GeminiStoryFiles.write(File(folder,"story.json"),copy)
                    prefs.edit().putString("project",folder.name).apply(); render()
                    notice("สร้างสำเนาแนวตั้งแล้ว • สร้างปกและภาพใหม่ จากนั้นนำเข้ามาตรวจและสร้างวิดีโอ 9:16")
                }.onFailure { folder.deleteRecursively(); notice(it.message ?: "สร้างสำเนาไม่ได้") }
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun autoDirectorMenu() {
        choiceMenu("AutoDirector v45",listOf("สร้างเรื่องใหม่ • เลือกหมวดหลัก / AI คิดพล็อต","Phase 1 • ดูโครงเรื่องที่เปิด","Phase 2–4 • ทำงานทีละ EP","ส่งออก ZIP • ตอนละ 2 ไฟล์","คำสั่งภาพ / Flow • เลือก EP")) { action ->
            if(action==0) { flowPlanning(true); return@choiceMenu }
            val story=plan
            if(story==null) { if(File(project,"gemini_job/spec.json").isFile) geminiJobMenu() else notice("ยังไม่มีบท กรุณาสร้างเรื่องใหม่หรือนำเข้าบทก่อน"); return@choiceMenu }
            if(story.optString("content_profile")!=AutoDirectorContent.profile) {
                notice("เรื่องนี้ใช้รูปแบบเดิม เลือกสร้างเรื่องใหม่ใน AutoDirector v45 เพื่อเลือกเวลาคลิป เสื้อผ้า 4 ส่วน และไฟล์ Gemini / Flow XML"); return@choiceMenu
            }
            if(story.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return@choiceMenu }
            val wardrobeIssues=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(story))
            if(action>=2 && wardrobeIssues.isNotEmpty()) { notice(wardrobeIssues.take(12).joinToString("\n")); return@choiceMenu }
            val revision=EpisodeMedia.digest(File(project,"story.json"))
            val phaseKey="autodirector_${project.name}_$revision"
            when(action) {
                1 -> {
                    showFlowCode("Phase 1 • โครงเรื่อง",AutoDirectorContent.outline(story),"AD_01_OUTLINE.txt")
                    prefs.edit().putBoolean(phaseKey+"_outline",true).apply()
                }
                2 -> {
                    val eps=story.getJSONArray("episodes")
                    choiceMenu("เลือก EP",(0 until eps.length()).map { i -> "EP.${i+1} ${eps.getJSONObject(i).optString("title")}" }) { index ->
                        val number=eps.getJSONObject(index).getInt("ep"); val key=phaseKey+"_ep$number"
                        choiceMenu("EP.$number • เลือกทำทีละขั้น",listOf("ภาพ EP.$number • ปกและซีน","บทพูด EP.$number","วิดีโอ EP.$number • ทีละซีนพร้อมเสียง","XML EP.$number • ขั้นสูง")) { stage ->
                            val reached=prefs.getInt(key,0)
                            val code=when(stage) {
                                0,2 -> ""
                                1 -> AutoDirectorContent.dialogue(story,number)
                                else -> AutoDirectorContent.flowXml(story,number)
                            }
                            val file=when(stage) { 0 -> "AD_EP%02d_IMAGES_GEMINI.txt"; 1 -> "AD_EP%02d_DIALOGUE.txt"; else -> "AD_EP%02d_FLOW.xml" }.format(number)
                            when(stage) {
                                0 -> productionTaskDeck(story,number,true)
                                2 -> productionTaskDeck(story,number,false)
                                else -> showFlowCode("Phase ${if(stage==3) 4 else 3} • EP.$number",code,file)
                            }
                            prefs.edit().putInt(key,maxOf(reached,minOf(stage+1,3))).apply()
                        }
                    }
                }
                4 -> commandCardsMenu()
                3 -> {
                    val issues=FlowProductionPack.issues(story)
                    if(issues.isNotEmpty()) notice(issues.take(30).joinToString("\n")) else { starterFlowPack=false; flowPackPicker.launch(StoryFiles.zipName(story.optString("title"))) }
                }
            }
        }
    }
    private fun chooseChannelTopic(done: (Int) -> Unit) {
        choiceMenu("เลือกหมวดหลัก • ให้ AI คิดเรื่องใหม่",ChannelCatalog.mainCategories.map { it.menuLabel },action=done)
    }
    private fun chooseClip(done: (String,Int) -> Unit) {
        choiceMenu("เลือกรูปแบบ • ทุกแบบแนวตั้ง 9:16",listOf("คลิปสั้น","คลิปยาว")) { format ->
            val times=if(format==0) ClipPlan.shortSeconds else ClipPlan.longSeconds
            choiceMenu("เวลาเนื้อหาต่อ EP • ไม่รวมปกเปิด 3 วินาที",times.map { ClipPlan.label(it)+(if(it==if(format==0) 60 else 300) " • แนะนำเริ่มต้น" else "") }) { index -> done(if(format==0) "short" else "long",times[index]) }
        }
    }
    private fun flowPlanning(autoDirector: Boolean = false) {
        if(GeminiStoryService.running) { geminiJobMenu(); return }
        val provider=StoryProvider.selected(this)
        val settings=GeminiSettings(this,provider)
        if(!StoryProvider.ready(this)) { geminiSettings { flowPlanning(autoDirector) }; return }
        val configure: (Int) -> Unit = { index ->
            chooseClip { format, seconds ->
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            panel.addView(TextView(this).apply { text="หมวด: ${categories[index]}\nAI เลือกนักแสดงจากคลัง MASTER และตั้งชื่ออัตโนมัติตามบท แล้วเขียนเรื่องต่อครบทุก EP"; textSize=14f })
            val premise=EditText(this).apply { hint="แนวคิดเรื่อง (ว่าง = คิดเรื่องใหม่)"; maxLines=4; panel.addView(this) }
            val episodes=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("1 EP • จบในตอน","ให้ AI เลือก 4–7 EP","3 EP","4 EP","5 EP","6 EP","7 EP")); panel.addView(this) }
            panel.addView(TextView(this).apply { text="${if(format=="short") "คลิปสั้น" else "คลิปยาว"} • ${ClipPlan.label(seconds)} ต่อ EP • ไม่รวมปกเปิด 3 วินาที • สร้างเป็นเรื่องใหม่ เก็บเรื่องเดิมไว้ • ${StoryProvider.label(this@StudioActivity)} • ใช้โมเดลที่ตั้งไว้ • เขียนเฉพาะบทและคำสั่ง ส่งวิดีโอไปสร้างใน Flow ของคุณภายหลัง" })
            AlertDialog.Builder(this).setTitle(if(autoDirector) "AutoDirector v45 • ${categories[index]}" else "สร้างบทด้วย ${StoryProvider.label(this@StudioActivity)}")
                .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("สร้างบทในแอป") { _,_ ->
                    runCatching {
                        val catalog=JSONObject(assets.open("master_library/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
                        require(catalog.length()>0) { "คลังตัวละครว่าง กรุณาเพิ่มตัวละครชุดใหม่ก่อนใช้คัดนักแสดงอัตโนมัติ" }
                        val folder=File(base,UUID.randomUUID().toString()).apply { mkdirs() }
                        for(n in 0 until catalog.length()) {
                            val row=catalog.getJSONObject(n); val target=File(folder,"master_candidates/"+row.getString("file")); target.parentFile!!.mkdirs()
                            assets.open("master_library/"+row.getString("file")).use { input -> target.outputStream().use { input.copyTo(it) } }
                        }
                        val count=when(episodes.selectedItemPosition) { 0 -> 1; 1 -> 0; else -> episodes.selectedItemPosition+1 }
                        GeminiStoryFiles.write(File(folder,"gemini_job/spec.json"),StoryProvider.configure(this,ClipPlan.apply(JSONObject(),format,seconds).put("category",categories[index]).put("channel",originalChannel)
                            .put("content_profile",if(autoDirector) AutoDirectorContent.profile else "").put("autodirector_version",if(autoDirector) AutoDirectorContent.version else 0).put("performance_version",1).put("audio_version",1).put("command_version",1).put("outline_review_required",false).put("creative_seed",UUID.randomUUID().toString()).put("auto_cast",true).put("casting_version",2).put("recent_casts",AutoCast.recentCasts(base,folder)).put("master_catalog",catalog).put("supporting_characters",JSONArray()).put("lead_references",JSONObject()).put("premise",premise.text.toString()).put("episodes",count)))
                        prefs.edit().putString("project",folder.name).apply(); render(); startGeminiJob(folder)
                    }.onFailure { notice(it.message ?: "เริ่มงานไม่ได้") }
                }.setNeutralButton(if(autoDirector) "กลับเมนู AutoDirector" else "ใช้แชร์ไฟล์แทน") { _,_ -> if(autoDirector) autoDirectorMenu() else flowPlanningShare() }.setNegativeButton("ยกเลิก",null).showDirector()
        }
        }
        chooseChannelTopic(configure)
    }
    private fun flowPlanningShare() {
        chooseChannelTopic { index ->
            chooseClip { format, seconds ->
            val premise=EditText(this).apply { hint="แนวคิดเรื่อง (เว้นว่างให้คิดใหม่)" }
            fun prepare(): String {
                val code=FlowProductionPack.masterCommand(categories[index],originalChannel)+"\n"+ChannelCatalog.direction(categories[index])+"\nUSER DURATION OVERRIDE: ${ClipPlan.scenes(seconds)} scenes per EP, 8 seconds each, ${ClipPlan.time(ClipPlan.scenes(seconds)*8)} content per EP. Supersedes default 24 scenes and 180-second minimum above. Set clip_plan_version=1, clip_format=$format, target_seconds=$seconds, scenes_per_episode=${ClipPlan.scenes(seconds)} in story.json.\nแนวคิด: "+premise.text.toString().ifBlank { "คิดเรื่องใหม่อัตโนมัติ" }
                File(project,"flow_story_command.txt").writeText(code)
                render()
                return code
            }
            AlertDialog.Builder(this).setTitle("ส่งข้อมูลให้ AI วางบท")
                .setMessage("แอปแนบคำสั่งและ MASTER 3 ภาพให้เอง เลือกแอป AI ที่รับไฟล์ แล้วตรวจไฟล์แนบและกดส่งในแอปนั้น ผลลัพธ์ที่ต้องรับกลับ: story.json / ZIP บท เพื่อนำเข้าขั้นตอน 2 • ยังไม่ใช่การเรียก API อัตโนมัติ")
                .setView(premise).setPositiveButton("ส่งคำสั่ง + MASTER") { _,_ -> sendStoryPackage(prepare()) }
                .setNeutralButton("ดูคำสั่งก่อน") { _,_ -> showFlowCode("คำสั่งวางบท",prepare(),"FLOW_STORY_COMMAND.txt") }
                .setNegativeButton("ยกเลิก",null).showDirector()
        }
    }
    }
    private fun flowTestMenu() {
        val ids=FlowProductionPack.names.keys.toList(); val checked=BooleanArray(ids.size)
        val dialog=AlertDialog.Builder(this).setTitle("เลือกตัวละครทดสอบวิดีโอ")
            .setMultiChoiceItems(ids.map { FlowProductionPack.names.getValue(it) }.toTypedArray(),checked) { _,i,value -> checked[i]=value }
            .setPositiveButton("ตั้งค่าฉาก",null).setNegativeButton("ยกเลิก",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val selected=ids.filterIndexed { i,_ -> checked[i] }
            if(selected.isEmpty()) Toast.makeText(this,"เลือกอย่างน้อย 1 คน",Toast.LENGTH_SHORT).show()
            else { dialog.dismiss(); flowTestSetup(selected) }
        } }; dialog.show()
    }
    private fun flowTestSetup(ids: List<String>) {
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        panel.addView(TextView(this).apply { text="แนบภาพตัวละครที่เลือกตามรหัสอ้างอิงด้านบนคำสั่ง ไม่ต้องเปลี่ยนชื่อไฟล์หรือกรอกนามสกุล" })
        val activity=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("ลำลอง / อยู่บ้าน","ประชุม / ทำงาน","ชุดนอน")) }
        panel.addView(activity)
        val location=EditText(this).apply { hint="สถานที่และเวลา"; setText("ห้องนั่งเล่นตอนบ่าย แสงธรรมชาติ"); panel.addView(this) }
        val action=EditText(this).apply { hint="ท่าทาง / การกระทำ"; setText("ยืนข้างโต๊ะ ถือแก้วน้ำอย่างผ่อนคลาย"); panel.addView(this) }
        val outfit=EditText(this).apply { hint="ชุดที่ต้องการ (ว่าง = เลือกตามกิจกรรม)"; panel.addView(this) }
        AlertDialog.Builder(this).setTitle("ทดสอบสร้างวิดีโอ • ${ids.joinToString { FlowProductionPack.names.getValue(it) }}")
            .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("สร้างคำสั่ง") { _,_ ->
                val files=ids.associateWith { id -> File(filesDir,"character_masters/$id.master") }
                runCatching {
                    require(files.values.all { it.isFile && it.length()>0 }) { "ขาดภาพ MASTER ของตัวละครที่เลือก" }
                    FlowTestPrompt.build(ids,listOf("home","meeting","sleep")[activity.selectedItemPosition],location.text.toString(),action.text.toString(),outfit.text.toString()) }
                    .onSuccess { code ->
                        showFlowCode("คำสั่งทดสอบสร้างวิดีโอ • แนบ MASTER ที่เลือก",code,"FLOW_VIDEO_TEST_${ids.joinToString("_")}.txt")
                    }
                    .onFailure { notice(it.message ?: "ตรวจข้อมูลทดสอบ") }
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun flowSceneMenu(s: JSONObject) {
        if(plan?.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return }
        choiceMenu("${key(s)} • ${if(sceneFrameApproved(s)) "ภาพผ่านแล้ว" else "รอตรวจภาพ"}",listOf("1. สร้างภาพฉากจาก MASTER","3. สร้างวิดีโอจากภาพที่ผ่าน","แก้บท / ชุด / สถานที่","นำเข้าภาพจาก Gemini / Flow","นำเข้าคลิป MP4","2. เทียบ MASTER / ยืนยันภาพ","ดูคลิป","เวอร์ชันไฟล์")) { i -> when(i) {
            0 -> if(plan?.optString("content_profile")==AutoDirectorContent.profile) {
                val issues=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(plan!!))
                if(issues.isNotEmpty()) notice(issues.take(12).joinToString("\n")) else showFlowCode("IMAGE PROMPT • Gemini",AutoDirectorContent.sceneImage(plan!!,s),"AD_${key(s)}_IMAGE_GEMINI.txt")
            }
                else showFlowCode("IMAGE PROMPT",FlowReferencePrompt.scene(s,s.optInt("ep",1),"image"),"${key(s)}_IMAGE.txt")
            1 -> reviewedVideoCommand(s)
            2 -> editScene(s)
            3 -> { mediaSlot=key(s); mediaKind="image"; mediaProject=project.name; sceneMediaPicker.launch(arrayOf("image/*")) }
            4 -> { mediaSlot=key(s); mediaKind="video"; mediaProject=project.name; sceneMediaPicker.launch(arrayOf("video/mp4")) }
            5 -> compareScene(s)
            6 -> playVideo(s)
            7 -> versionsMenu(s)
        } }
    }

    private fun planning() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
            background = backdrop(navy, Color.rgb(27,24,57))
        }
        panel.addView(TextView(this).apply {
            text = "🎬 เลือกช่องสร้างหนัง"; textSize = 20f
            setTextColor(gold); setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14))
        })
        val dialog = AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setView(panel).create()
        fun option(mainChannel: Boolean, title: String, colors: IntArray, action: () -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = backdrop(colors[0], colors[1], Color.argb(150,255,255,255))
                clipToOutline = true; elevation = dp(5).toFloat()
                layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(14) }
                isClickable = true; isFocusable = true
                contentDescription = title.replace("\n", " ")
                setOnClickListener { dialog.dismiss(); action() }
            }
            card.addView(ImageView(this).apply {
                setImageResource(R.drawable.studio_movie)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                contentDescription = "สตูดิโอ • เพิ่มตัวละครใหม่ของคุณ"
            })
            card.addView(TextView(this).apply {
                text = title; textSize = 18f; setTextColor(navy)
                setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                setPadding(dp(10), dp(12), dp(10), dp(14))
                layoutParams = LinearLayout.LayoutParams(-1,-2)
                setSingleLine(false); maxLines = 2
                setAutoSizeTextTypeUniformWithConfiguration(14, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            })
            panel.addView(card)
        }
        panel.addView(Button(this).apply {
            text = "🎭 ตรวจ / เปลี่ยน MASTER ก่อนสร้างเรื่อง"; isAllCaps = false
            setOnClickListener { dialog.dismiss(); characterMasters() }
        })
        panel.addView(TextView(this).apply {
            text = "ตรวจภาพต้นฉบับก่อนส่ง • เลือกช่องด้านล่างเมื่อพร้อม"; textSize = 12f
            setTextColor(ice); setPadding(0,dp(6),0,dp(10))
        })
        option(true, "🎬 ร้อยฉาก\nซีรีส์สั้นหลากชีวิต", intArrayOf(Color.rgb(255,204,86), Color.rgb(255,174,38))) {
            chooseGenre(originalChannel, "original")
        }
        option(false, "🎬 ระบบกำกับละครอัจฉริยะ\nสร้างละครครบทุกแนว", intArrayOf(Color.rgb(73,218,242), Color.rgb(32,169,228))) {
            chooseGenre(prefs.getString("other_channel", "")?.takeIf { it.isNotBlank() } ?: smartDirector, "new")
        }
        panel.addView(TextView(this).apply {
            text = "ยกเลิก"; textSize = 14f; setTextColor(muted)
            gravity = Gravity.CENTER; minHeight = dp(48)
            setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(resources.displayMetrics.widthPixels - dp(24), -2)
    }
    private fun chooseGenre(channel: String, mode: String) {
        if(mode=="original") { flowPlanning(); return }
        val choices = if (mode == "original") categories else otherGenres
        val labels = if (mode == "original") choices.mapIndexed { index, category -> "${menuIcon(index)} $category" } else genreLabels
        val title = if (mode == "original") "🎬 เลือกหมวดหมู่\n$channel" else "🎬 $smartDirector\nเลือกแนวละคร • 10 แนว"
        choiceMenu(title, labels) { index ->
            planningForCategory(choices[index],channel,mode)
        }
    }
    private fun planningForCategory(category: String, channel: String, mode: String) {
        val idea = EditText(this).apply { hint = "แนวคิดเรื่อง (เว้นว่างให้คิดอัตโนมัติ)" }
        AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setTitle(category).setMessage("สร้างเรื่องจนจบครบทุก EP\nเว้นแนวคิดว่างได้ ระบบจะคิดเรื่องในหมวดที่เลือก").setView(idea).setPositiveButton("ส่งคำสั่ง") { _, _ ->
            shareText("""วางบทหนัง/ละครให้ช่อง $channel จนจบครบทุก EP ตามแนวที่เลือก
แนวคิด: ${idea.text.toString().ifBlank { "คิดเรื่องใหม่อัตโนมัติ" }}
หมวดหมู่ที่ผู้ใช้เลือก: $category
ข้อมูลอ้างอิงตัวละครจาก MASTER ปัจจุบัน (ชุดให้กำหนดตามวันเวลาและกิจกรรม):
${if(mode=="original") listOf("gawin" to "กวิน","rinlada" to "รินรดา","mind" to "เมย์").joinToString("\n") { (id,name) -> val source=File(filesDir,"character_masters/$id.master"); "$name → ${masterExportName(source,false) ?: "ขาด MASTER"}${promptProfile(id,source).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}" } else "ออกแบบตัวละครใหม่แยกรายคน ไม่ใช้ใบหน้าของโปรไฟล์เดิม"}
ก่อนเริ่มสร้างภาพ ต้องแสดงข้อมูลโครงการในแชตให้ครบ ห้ามข้าม: ชื่อช่อง "$channel", ชื่อหมวด "$category", ชื่อเรื่องภาษาไทย, คำโปรยเรื่องสั้น 1–2 ประโยค, จำนวน EP ทั้งหมด, เหตุผลที่เลือกจำนวน EP หนึ่งประโยค และจำนวนซีนรวม
ตามด้วยตารางทุก EP: เลข EP | ชื่อตอนภาษาไทย | คำโปรยตอนสั้น | จำนวนซีน | เวลารวม (จำนวนซีน × 8 วินาที) ต้องระบุจำนวนจริง ไม่ใช้คำว่าแล้วแต่เนื้อหาแทนตัวเลข
ชื่อช่องและหมวดใช้ค่าจากแอปตรงตามนี้ ไม่เปลี่ยนชื่อช่องเอง ตั้งชื่อเรื่องและชื่อทุก EP ใหม่ให้เข้ากับเนื้อหา ${if (mode == "original") "รูปแบบร้อยฉาก ซีรีส์สั้นหลากชีวิต มีจุดเปิดเรื่อง ปมและจุดจบชัดเจนแต่ละ EP พร้อมเส้นเรื่องต่อเนื่อง" else "รูปแบบหนัง/ละครหลายแนว ให้โทนเรื่อง จังหวะ ความสัมพันธ์ และบทสรุปตรงกับแนวที่เลือก ถ้าเรื่องจบในตอนเดียวใช้ 1 EP ถ้าต้องต่อเนื่องแบ่งหลาย EP อย่างมีเหตุผล"}
วางบทครบทุก EP ก่อนสร้างภาพ เก็บข้อมูลสรุปเดียวกันใน story.json และตรวจตัวเลขให้ตรงกับจำนวนสมาชิก episodes และ scenes จริง ห้ามส่ง JSON ตัวอย่างที่มีแค่ฉากเดียวแทนบทเต็ม
ตั้งชื่อเรื่องเป็นภาษาไทยเท่านั้น ใส่ชื่อภาษาไทยในค่า title ของ story.json และตั้งชื่อไฟล์ ZIP เป็นชื่อเรื่องภาษาไทย.zip ห้ามใช้ชื่อหมวดภาษาอังกฤษเป็นชื่อเรื่องหรือชื่อ ZIP
สร้างชื่อเรื่องและพล็อตให้ตรงหมวดนี้ ใช้ค่า category ใน story.json เป็น "$category" ตรงทุกตัวอักษร
เกณฑ์ Auto EP: ห้ามใช้ 3 EP เป็นค่าเริ่มต้นซ้ำโดยไม่มีเหตุผล เรื่องสั้นปมเดียวใช้ 1–2 EP เรื่องระดับกลางมีจุดพลิกใช้ 3–4 EP เรื่องซับซ้อนหลายความสัมพันธ์หรือหลายปมใช้ 5–6 EP เลือกเท่าที่จำเป็นเพื่อเล่าจบ ไม่ยืดเรื่อง และอธิบายเหตุผลหนึ่งประโยคก่อนตาราง หากค่าตั้งต้นผู้ใช้กำหนดตัวเลข EP ให้ใช้ตัวเลขนั้นแทนเกณฑ์ Auto บันทึกเหตุผลเดียวกันใน story.json ด้วยฟิลด์ episode_reason
ประเมินจำนวน EP และฉากตามเนื้อหาเอง สำหรับ Flow / Veo 3.1 Lite ทุกฉาก 8 วินาที แนวตั้ง 9:16 Locked-off static camera ${if (mode == "original") "ตัวละครหลัก กวิน รินรดา เมย์ อ้างอิง MASTER ของแต่ละคนเพื่อรักษาเอกลักษณ์ ใบหน้า ทรงผม ช่วงวัย และบุคลิกตรงรายคน" else "สร้างชื่อ ใบหน้า และทรงผมใหม่สำหรับตัวละครของเรื่องนี้ ก่อนสร้างแต่ละ CHARACTER MASTER ให้เลือก GAWIN_MASTER, RINLADA_MASTER หรือ MIND_MASTER หนึ่งภาพเป็น design_reference ด้านสไตล์ภาพ ความสมจริง และการจัดเฟรม กระจายการเลือกให้เหมาะกับตัวละครใหม่ บันทึกชื่อ design_reference ไว้ใน bible แล้วสร้าง CHARACTER_01.png, CHARACTER_02.png ตามจำนวนที่ต้องใช้ ใช้ CHARACTER MASTER ใหม่เดิมอย่างต่อเนื่องทุกฉาก"}\n${storyVisualGuide(mode)}\nใช้วิธีเดียวกับการทดสอบสร้างภาพ: MASTER รายคนเป็นต้นฉบับของตัวละคร ชุด สถานที่และการกระทำเป็นข้อมูลใหม่ตามบท เขียน image_prompt พร้อมใช้ของแต่ละฉากอย่างกระชับ: ตัวละครและ MASTER รายคน → ชุดรายคน → สถานที่และสิ่งของ → การกระทำ → สีหน้าและสายตา ระบุชนิด สี และทรงชุดให้เหมาะกับบทบาท รวมทั้งสถานที่จริงในแต่ละฉากเพื่อสร้างซ้ำได้ ชุดเปลี่ยนตามวันหรือเหตุการณ์ และคงเดิมในเหตุการณ์ต่อเนื่อง ใช้คำสั่งสั้นแบบ ตัวละครอ้างอิง → ชุด → สถานที่ → การกระทำ → รูปแบบภาพ ระบุลักษณะใบหน้า ผมและเครื่องประดับเฉพาะที่เห็นจาก MASTER ไม่แต่งรายละเอียดที่ไม่ทราบ ไม่อ้างว่าล็อกความเหมือนเป็นเปอร์เซ็นต์ เลือกระยะภาพและแสงตามกิจกรรม ไม่กำหนดเอวขึ้นไปทุกฉาก ใช้คำอธิบายเชิงบวกสั้น ๆ เหตุการณ์ทุกฉากมาจากบทของเรื่องนี้ จบปมครบทุกเรื่อง ผู้พูดแต่ละฉากชัดเจน แยก speaker และ narration ผู้บรรยายชายเป็นเสียงนอกภาพ ตัวละครไม่ขยับปากตามเสียงบรรยาย
แผนความต่อเนื่องของตัวละคร: เก็บชื่อ → ไฟล์ MASTER ตายตัวใน bible: กวิน → GAWIN_MASTER, รินรดา → RINLADA_MASTER, เมย์ → MIND_MASTER ใช้นามสกุล .png/.jpg/.webp ตามไฟล์ภาพที่แนบ โดยชื่อฐานจับคู่ตรงคน สำหรับช่องตัวละครใหม่ใช้ชื่อใหม่ → CHARACTER_01.png ตามที่สร้าง แต่ละฉากมี characters เฉพาะคนที่ปรากฏ พร้อม name, master, outfit ระบุ location และ continuity_group เหตุการณ์เดียวกันใช้รหัสกลุ่มเดียวกัน ชุดและรายละเอียดสถานที่เหมือนเดิม เมื่อเปลี่ยนวันหรือเหตุการณ์จึงเปลี่ยนกลุ่ม
ระบบตัวละครกับชุด: MASTER ใช้ใบหน้า ผม สีผิวและรูปร่างรายคน เสื้อผ้าใน MASTER เป็นเพียงชุดในภาพอ้างอิง ทุกฉากต้องมี story_day, story_time, activity และ wardrobe_event เมื่อประชุมให้ใช้ชุดประชุม กลับบ้านเปลี่ยนชุดอยู่บ้าน เข้านอนเปลี่ยนชุดนอน เปลี่ยนวันให้เลือกชุดใหม่ตามบท (ยกเว้นชุดทำงานที่ต้องเหมือนเดิม) เหตุการณ์ต่อเนื่องในวันเดียวกันและกิจกรรมเดียวกันให้คงชุดเดิม เปลี่ยนวันหรือเปลี่ยนกิจกรรมให้ขึ้น wardrobe_event ใหม่ กำหนด outfit รายคนและ wardrobe_change=true เมื่อเปลี่ยนชุดจริง ชุดที่ผู้ใช้ระบุมีลำดับสูงสุด แยกเสียงผู้พูดออกจากรูปร่างและเสื้อผ้า
การต่อฉาก: ระบุ start_state และ end_state ทุกฉาก ให้สถานะต้นฉากต่อจากท้ายฉากก่อน รวมผู้ถือและตำแหน่งอุปกรณ์ การเข้าออกของตัวละคร วันเวลาและสถานที่ ห้ามย้ายซอง เอกสาร หรือคนโดยไม่มีจังหวะส่งต่อ ข้าม EP ต่อเหตุการณ์เดิมให้ใช้สถานะเดิม ปิดคำถามที่เปิดไว้ เช่น เหตุผลการโอนเงิน ก่อนจบเรื่อง
การกำหนดคนพูด: characters ทุกคนต้องมี visual_description (ทรงผมและลักษณะเด่น) และ screen_position ตามองค์ประกอบภาพที่วางแผน ชื่ออย่างเดียวไม่พอ หลังเห็นภาพจริงต้องตรวจตำแหน่งอีกครั้ง ไม่เดาซ้ายขวา เขียน flow_prompt เป็น CHARACTER MAP ชื่อ + ลักษณะ + ชุด + ตำแหน่ง แล้วระบุผู้พูดเพียงหนึ่งคนกับบทพูดหนึ่งครั้ง ผู้ฟังทุกคนปิดปากและแสดงปฏิกิริยาเงียบ แยกฉากบทพูดจากเสียงบรรยาย ผู้บรรยายเป็นเสียงนอกภาพและไม่มีใครขยับปากตาม ระยะเวลาต้องพอกับบท หากยาวให้ย่อหรือแยกฉาก ไม่ให้แย่งกันพูด
คำสั่งภาพแต่ละฉากใช้ต้นแบบสั้น: สร้างภาพฉากละครจากไฟล์อ้างอิงที่แนบ คงใบหน้า ทรงผม สีผิว และสัดส่วนรูปร่างโดยรวมตาม MASTER ไม่เพิ่มหรือลดเอง ${ImagePromptPolicy.referenceAppeal} ใช้ชุดที่ระบุในฉาก วันและเหตุการณ์ต่อเนื่องใช้ชุดเดิม เปลี่ยนเมื่อวันเวลา กิจกรรม หรือบทกำหนด แนวตั้งตาม aspect_ratio แล้วเติมชื่อและ MASTER ตรงรายคน ชุด สถานที่ การกระทำ และความต่อเนื่องของฉาก ไม่เพิ่มรายการกำกับสรีระยาวซ้ำทุกฉาก ไม่สร้างภาพทดสอบเพิ่มเป็นขั้นบังคับ ผู้ใช้เลือกทดสอบรายคนได้เมื่อจำเป็น\nก่อนใช้เครื่องมือสร้างภาพแต่ละฉาก อ้างอิง MASTER ต้นฉบับตรงรายคนอีกครั้ง ใช้ภาพฉากก่อนหน้าในกลุ่มเดียวกันเป็นตัวช่วยเรื่องสถานที่และสิ่งของ ชุดยึดข้อมูลฉากปัจจุบันเป็นหลักและใช้ภาพก่อนหน้ารักษารายละเอียดเมื่อเป็นชุดเดียวกัน โดยใบหน้าและรูปร่างยึด MASTER เป็นหลัก ตรวจชื่อและชุดของแต่ละคนก่อนส่งสร้าง ไม่ใช้ภาพที่คลาดเคลื่อนแทน MASTER หากขาดต้นฉบับให้แจ้งก่อนสร้าง
ส่งไฟล์ story.json เป็น JSON ไม่มี markdown โครงสร้าง:
{"schema_version":3,"channel":${JSONObject.quote(channel)},"character_mode":"$mode","title":"ชื่อเรื่อง","category":${JSONObject.quote(category)},"tagline":"คำโปรยเรื่อง","episode_count":1,"scene_count":1,"bible":"ความสัมพันธ์และพล็อต","episodes":[{"ep":1,"title":"ชื่อตอนภาษาไทย","tagline":"คำโปรยตอน","scene_count":1,"scenes":[{"scene":1,"characters":[{"name":"ชื่อตัวละครที่อยู่ในฉาก","master":"ชื่อไฟล์ MASTER ตรงรายคน","outfit":"ชุดตามวันเวลาและกิจกรรม ระบุชนิดและสี","wardrobe_change":false,"accessories":"เครื่องประดับตามบท หรือว่างเมื่อไม่ใส่","visual_description":"ทรงผมและลักษณะเด่นจาก MASTER รายคน","screen_position":"ตำแหน่งที่วางแผนในภาพ ต้องยืนยันกับภาพจริงก่อนใช้ Flow"}],"story_day":"DAY01","activity":"meeting / work / home / sleep / exercise / casual / formal","wardrobe_event":"DAY01_MEETING","story_time":"เวลาในเรื่อง","location":"สถานที่และสิ่งของหลัก","framing":"ระยะภาพที่เห็นกิจกรรมครบ เช่น ศีรษะถึงเข่าหรือเต็มตัวตามบท","lighting":"แสงตามเวลาและสถานที่","continuity_group":"DAY01_LOCATION01_EVENT01","start_state":"สถานะต่อจากท้ายฉากก่อน รวมตำแหน่งคนและสิ่งของ","end_state":"สถานะที่ส่งต่อฉากถัดไป","flow_action":"One visible action in English","action":"เหตุการณ์","dialogue":"บทพูดภาษาไทย","speaker":"ชื่อผู้พูดหรือว่างเมื่อไม่มีบทพูด","narration":"เสียงบรรยายชายหรือว่าง","image_prompt":"ตัวละครและ MASTER ตรงรายคน คงใบหน้า ผม สีผิวและรูปร่างตามภาพอ้างอิง; ชุดตามฉากปัจจุบันระบุชนิดและสี; สถานที่และสิ่งของ; การกระทำ สีหน้าและสายตาตามบท; ภาพละครไทยสมจริง 9:16 เห็นการกระทำครบ ไม่มีข้อความ","flow_prompt":"Flow / Veo 3.1 Lite: English prompt + Thai speech, 8 seconds, 9:16, locked-off static camera"}]}]}
การส่งชุดงานสุดท้าย: ส่ง story.json เป็นไฟล์แยก และส่งภาพฉากกับปกเป็น ZIP แยกราย EP ใช้ MASTER เป็นภาพอ้างอิงระหว่างสร้าง แต่ไม่ใส่ไฟล์ภาพ MASTER หรือโฟลเดอร์ MASTER/characters ลงใน ZIP คำสั่ง Flow รวมหนึ่งไฟล์ต่อ EP: EP01_FLOW.txt, EP02_FLOW.txt ตามจำนวนตอนจริง แต่ละไฟล์มีชื่อตอนและทุกซีนเรียงเลข แต่ละซีนแสดงเลขฉาก เหตุการณ์ บทพูด และคำสั่ง Flow แยกเป็นบล็อกสำหรับคัดลอกทีละซีน ไม่แยกเป็นไฟล์ Flow รายซีน คำสั่ง flow_prompt ใน story.json ยังต้องครบทุกซีนตามเดิม
ตัวเลข 1 ในตัวอย่างเป็นเพียงรูปแบบ ต้องแทนด้วยจำนวนจริงของบทเต็ม เพิ่ม EP และฉากเท่าที่เรื่องต้องใช้ เรียงเลขจาก 1 ไม่กำหนดจำนวนตายตัว หลังวางบททั้งเรื่อง ต้องส่ง story.json เป็นไฟล์ดาวน์โหลดแยกทันที ก่อนเริ่มสร้างภาพ ห้ามรอใส่ story.json ไว้ท้าย ZIP เท่านั้น จากนั้นใช้เครื่องมือสร้างภาพและแสดงภาพจริงในแชตแยกหนึ่งฉากหนึ่งไฟล์ตามลำดับครบทุก EP จนจบ ตั้งชื่อ EP01_SC001.png ตามเลข EP และฉาก ไม่ทำภาพรวมหลายฉาก ${if (mode == "original") "ใช้ MASTER ที่แนบให้ตรงรายคน" else "ใช้ CHARACTER MASTER ใหม่ที่สร้างสำหรับเรื่องนี้ตรงรายคน"} พร้อมคำสั่งสำหรับ Flow / Veo 3.1 Lite ทุกฉาก ผู้ใช้ต้องการบทและภาพพร้อมนำไปทำวิดีโอ
เพื่อป้องกันไฟล์ขนาดใหญ่ถูกตัด ให้ส่ง ZIP แยกหนึ่งไฟล์ต่อ EP ชื่อ EP01_IMAGES.zip, EP02_IMAGES.zip ตามจริง แต่ละ ZIP ใส่เฉพาะภาพฉากและปกของ EP นั้น หลังสร้าง ZIP แต่ละ EP ให้เปิดตรวจว่าอ่านได้ ตรวจจำนวนภาพฉากและปก แล้วรายงานจำนวนที่ตรวจจริง ห้ามแจ้งว่าครบจากแผนโดยไม่ตรวจไฟล์ ห้ามรวมภาพทุก EP เป็น ZIP ก้อนใหญ่ไฟล์เดียว
หลังสร้างฉากสุดท้ายของแต่ละ EP ให้สร้างปกแนวตั้งหนึ่งไฟล์ทันที: EP01_COVER_9x16.png ขนาด 9:16 เปลี่ยนเลขตาม EP จัดองค์ประกอบแต่ละสัดส่วนใหม่ ใช้ตัวละครจาก MASTER รายคน สื่อเหตุการณ์เด่นของตอนนั้น ข้อความภาษาไทย 3 บรรทัดจัดกึ่งกลางในพื้นที่กลางภาพ เว้นขอบซ้ายขวาอย่างน้อย 10% และขอบบนล่าง 20%: บรรทัด 1 ชื่อช่อง (เล็กกว่า), บรรทัด 2 ชื่อเรื่อง (ใหญ่ที่สุด), บรรทัด 3 EP 01 • ชื่อตอน (ขนาดรอง) ปรับขนาดอักษรให้ครบทั้งบรรทัด อ่านชัดบนมือถือ ไม่ทับใบหน้า ชื่อเรื่องและชื่อตอนต้องตรง story.json รวมปกไว้ใน ZIP ราย EP ที่ตรงกับเลขตอนด้วย""", lockCreation = true, includeMasters=false, referenceImages=listOf("gawin","rinlada","mind").map { id -> val file=File(filesDir,"character_masters/$id.master"); "${id.uppercase()}_MASTER" to file })
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun sceneCommands() {
        val p = plan ?: return notice("นำเข้าแผนเรื่องก่อนครับ")
        val scenes = rows(p)
        choiceMenu("🎬 เลือกฉากเพื่อส่งคำสั่งภาพ", scenes.map { "🎞️ " + key(it) }) { index ->
            val s = scenes[index]
            sendSceneImage(p,s)
        }
    }
    @Deprecated("Android legacy back callback")
    override fun onBackPressed() { if (!importing) super.onBackPressed() }
    private fun displayName(uri: Uri): String = if (uri.scheme == "file") File(uri.path!!).name else contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null,null,null)?.use { if (it.moveToFirst()) it.getString(0) else "" } ?: ""
    private fun importOriginalReferences(pictures: List<Uri>, folder: File, story: JSONObject) {
        if(story.optString("character_mode","original")!="original") return
        FlowCast.definitions(story).forEach { person ->
            val id=FlowCast.id(person) ?: return@forEach
            val source=pictures.firstOrNull { displayName(it).substringBeforeLast('.').equals("${id.uppercase()}_MASTER",true) } ?: return@forEach
            val target=File(folder,"original_masters/$id.master"); target.parentFile!!.mkdirs()
            val temp=File(target.parentFile,target.name+".pending")
            try {
                contentResolver.openInputStream(source)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                require(masterExportName(temp,false)!=null) { "อ่านภาพอ้างอิงของ ${person.getString("name")} ไม่ได้" }
                check(temp.renameTo(target)) { "บันทึกภาพอ้างอิงไม่ได้" }
            } finally { temp.delete() }
        }
    }
    private fun importNewMasters(uris: List<Uri>, folder: File = project, story: JSONObject? = plan) {
        if (uris.isEmpty()) return
        require(story?.optString("character_mode") == "new") { "ภาพ CHARACTER ใช้กับช่องอื่นเท่านั้น" }
        val dir = File(folder, "masters").apply { mkdirs() }
        for (uri in uris) {
            val index = Regex("^CHARACTER_(\\d+)\\.", RegexOption.IGNORE_CASE).find(displayName(uri))!!.groupValues[1]
            val temporary = File(dir, "CHARACTER_$index.tmp")
            contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temporary.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "ไฟล์ MASTER ไม่ใช่ภาพ" }
            val target=File(dir,"CHARACTER_$index.png")
            if(target.exists()) archiveVersion(target,folder,"CHARACTER_$index","master")
            require(temporary.renameTo(target)) { "บันทึก MASTER ไม่สำเร็จ" }
        }
    }
    private fun importImages(uris: List<Uri>, confirmedOrder: Boolean = false) {
        val scenes = rows(plan!!)
        val assignments = linkedMapOf<Uri,JSONObject>()
        val unknown = mutableListOf<Uri>()
        for (uri in uris) {
            val match = StoryFiles.sceneNumber(displayName(uri))
            val found = match?.let { m -> scenes.find { it.getInt("ep") == m.first && it.getInt("scene") == m.second } }
            if (match != null && found == null) return notice("${displayName(uri)} ไม่ตรงกับฉากในเรื่องนี้ กรุณาเลือกภาพของเรื่องปัจจุบัน")
            if (found == null) unknown.add(uri) else assignments[uri] = found
        }
        if (assignments.values.map { key(it) }.distinct().size != assignments.size) return notice("มีไฟล์ชื่อฉากซ้ำในชุดที่เลือก กรุณาเลือกหนึ่งภาพต่อฉากครับ")
        val occupied = assignments.values.map { key(it) }.toSet()
        val available = scenes.filter { key(it) !in occupied && !image(it).exists() }
        if (unknown.size > available.size) {
            val named = uris.filter { StoryFiles.sceneNumber(displayName(it)) != null }
            val skipped = unknown.joinToString("\n") { displayName(it) }
            if (named.isEmpty()) return notice("ยังจับคู่ภาพไม่ได้ กรุณาใช้ชื่อ EP01_SC001.png หรือโฟลเดอร์ EP01/SC001.png\nไฟล์ที่ต้องตรวจ:\n$skipped")
            AlertDialog.Builder(this).setTitle("พบภาพอื่นในชุดงาน")
                .setMessage("นำเข้าภาพที่ตรงเลขฉากได้ ${named.size} ไฟล์ ส่วน ${unknown.size} ไฟล์ต่อไปนี้ยังจับคู่ไม่ได้ จะเว้นไว้เพื่อไม่ใส่ผิดฉาก:\n$skipped")
                .setPositiveButton("นำเข้าภาพที่จับคู่ได้") { _, _ -> importImages(named) }
                .setNegativeButton("ยกเลิก", null).showDirector()
            return
        }
        unknown.forEachIndexed { i, uri -> assignments[uri] = available[i] }
        if (unknown.isNotEmpty() && !confirmedOrder) {
            val list = uris.joinToString("\n") { "${displayName(it)} → ${key(assignments.getValue(it))}" }
            val content = TextView(this).apply { text = "บางภาพไม่มีเลขฉาก แอปเสนอเรียงตามลำดับไฟล์ที่เลือก กรุณาตรวจให้ตรงก่อนนำเข้า\n\n$list"; setPadding(dp(16),dp(12),dp(16),dp(12)) }
            AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setTitle("ตรวจลำดับภาพครั้งเดียว").setView(ScrollView(this).apply { addView(content) }).setPositiveButton("ลำดับถูกต้อง") { _,_ -> importImages(uris,true) }.setNegativeButton("ยกเลิก",null).showDirector()
            return
        }
        val importFolder = project
        importing = true
        val remaining = uris.toMutableList()
        var savedCount=0; var skippedCount=0
        val failures=mutableListOf<String>()
        val progressDialog=AlertDialog.Builder(this).setTitle("กำลังบันทึกภาพ").setMessage("0/${uris.size} ไฟล์").setCancelable(false).showDirector()
        fun next() {
            if (remaining.isEmpty()) {
                importing = false; pendingRecovery.deleteRecursively(); render()
                progressDialog.dismiss()
                showImportReceipt("ครั้งนี้ • บันทึกภาพ $savedCount • ข้าม $skippedCount • ล้มเหลว ${failures.size}" + if(failures.isEmpty()) "" else "\n"+failures.joinToString("\n"))
                return
            }
            progressDialog.setMessage("กำลังบันทึก ${uris.size-remaining.size+1}/${uris.size} ไฟล์")
            val uri = remaining.removeAt(0)
            val name = displayName(uri)
            val match = StoryFiles.sceneNumber(name)
            val found = match?.let { m -> scenes.find { it.getInt("ep") == m.first && it.getInt("scene") == m.second } }
            fun save(s: JSONObject) {
                Thread {
                    try {
                        val target = File(importFolder,"images/${key(s)}.img"); target.parentFile!!.mkdirs()
                        val tmp = File(target.parentFile,"${target.name}.tmp")
                        contentResolver.openInputStream(uri)!!.use { src -> tmp.outputStream().use { src.copyTo(it) } }
                        require(tmp.length() > 0) { "ไฟล์ภาพว่าง" }
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(tmp.path,bounds)
                        require(bounds.outWidth > 0) { "อ่านภาพไม่ได้" }
                        if (target.exists()) archiveVersion(target, importFolder, key(s), "image")
                        check(tmp.renameTo(target)) { "บันทึกภาพไม่ได้" }
                        runOnUiThread { savedCount++; next() }
                    } catch (e: Exception) { runOnUiThread { failures.add("$name: ${e.message}"); next() } }
                }.start()
            }
            fun assign(s: JSONObject) {
                if (image(s).exists()) AlertDialog.Builder(this).setIcon(R.drawable.studio_movie).setMessage("${key(s)} มีภาพแล้ว แทนด้วย $name หรือไม่?").setPositiveButton("แทนภาพ") { _,_ -> save(s) }.setNegativeButton("ข้าม") { _,_ -> skippedCount++; next() }.setCancelable(false).showDirector()
                else save(s)
            }
            if (assignments[uri] != null) assign(assignments.getValue(uri))
            else choiceMenu("🧩 จับคู่ภาพ: $name", scenes.map { "🎞️ " + key(it) + if (image(it).exists()) " ✓" else " • ขาดภาพ" }, cancel = "⏭️ ข้าม", cancelable = false, onCancel = { next() }) { i -> assign(scenes[i]) }
        }
        next()
    }
    private fun previewBitmap(uri: Uri): android.graphics.Bitmap? {
        return runCatching {
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,bounds) }
            val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=1 }
            while(bounds.outWidth/options.inSampleSize>1200 || bounds.outHeight/options.inSampleSize>1600) options.inSampleSize *= 2
            contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
        }.getOrNull()
    }
    private fun copyCommand(text: String) {
        runCatching {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("DIRECTOR X",text))
        }.onSuccess { notice("คัดลอกคำสั่งแล้ว • นำไปวางในเครื่องมือที่ต้องการ") }
            .onFailure { notice("คัดลอกไม่ได้หรือข้อความยาวเกินคลิปบอร์ด • เลือกบันทึกไฟล์แล้วแนบใน Gemini หรือเลือกเฉพาะ EP") }
    }
    private fun saveStory() {
        val p = plan ?: return
        val target = File(project,"story.json")
        if (target.exists()) target.copyTo(File(project,"story.previous.json"),overwrite=true)
        val tmp = File(project,"story.pending")
        tmp.writeText(p.toString(2)); check(tmp.renameTo(target)) { "บันทึกบทไม่ได้" }
    }
    private fun rememberScene(s: JSONObject) {
        prefs.edit().putString("last_scene_${project.name}",key(s)).apply()
        File(project,"last_scene.txt").writeText(key(s))
    }
    private fun video(s: JSONObject) = File(project,"videos/${key(s)}.mp4")
    private fun archiveVersion(source: File, folder: File, id: String, kind: String) {
        val dst = File(folder,"versions/$id/$kind/${System.currentTimeMillis()}_${UUID.randomUUID()}.${source.extension}")
        dst.parentFile!!.mkdirs(); source.copyTo(dst)
    }
    private fun resumeScene() {
        val p = plan ?: return notice("สร้างหรือนำเข้าบทก่อนครับ")
        val scenes = rows(p)
        val last = prefs.getString("last_scene_${project.name}",null) ?: File(project,"last_scene.txt").takeIf { it.exists() }?.readText().orEmpty()
        sceneMenu(scenes.firstOrNull { key(it) == last } ?: scenes.firstOrNull { !image(it).exists() } ?: scenes.first())
    }
    private fun episodesMenu() {
        val p = plan ?: return notice("ยังไม่มีบท • เริ่มเรื่องใหม่หรือรับ story.json")
        val episodes = p.getJSONArray("episodes")
        choiceMenu("🎞️ ${p.optString("title")}",(0 until episodes.length()).map { i ->
            val ep = episodes.getJSONObject(i); val scenes = rows(p).filter { it.getInt("ep") == ep.getInt("ep") }
            "EP ${ep.getInt("ep")} • ${ep.optString("title")}\nภาพ ${scenes.count { image(it).exists() }}/${scenes.size} • วิดีโอ ${scenes.count { video(it).exists() }}/${scenes.size}"
        }) { index ->
            val ep = episodes.getJSONObject(index).getInt("ep")
            val scenes = rows(p).filter { it.getInt("ep") == ep }
            choiceMenu("EP $ep • เลือกฉาก",scenes.map { "${key(it)} • ${if (image(it).exists()) "มีภาพ" else "ขาดภาพ"} • ${if (video(it).exists()) "มีวิดีโอ" else "รอวิดีโอ"}" } + "📦 ส่งออก ZIP คำสั่งทั้งเรื่อง") { i ->
                if (i == scenes.size) { val issues=FlowProductionPack.issues(p); if(issues.isNotEmpty()) notice(issues.take(30).joinToString("\n")) else { starterFlowPack=false; flowPackPicker.launch(StoryFiles.zipName(plan?.optString("title").orEmpty())) } }
                else sceneMenu(scenes[i])
            }
        }
    }
    private fun flowSpec(s: JSONObject): FlowSceneSpec {
        val cast = s.optJSONArray("characters") ?: JSONArray()
        return FlowSceneSpec((0 until cast.length()).map { i ->
            val person = cast.getJSONObject(i)
            FlowPerson(person.optString("name"), person.optString("visual_description"), person.optString("screen_position"), sceneWardrobe(s,person),PerformanceDirection.text(person))
        }, s.optString("speaker"), s.optString("dialogue"), s.optString("narration"),
            s.optString("flow_action").ifBlank { s.optString("action") }, s.optString("location"),
            s.optString("start_state"), s.optString("end_state"),
            plan?.optInt("duration_seconds",8) ?: 8, plan?.optString("aspect_ratio","9:16") ?: "9:16",SoundDesign.notes(s),ProductionCommandCheck.offscreen(s))
    }
    private fun flowMappingCurrent(s: JSONObject): Boolean = image(s).exists() &&
        s.optLong("flow_image_revision",-1L) == image(s).lastModified()
    private fun flowCommand(s: JSONObject): String {
        if (!sceneFrameApproved(s) || FlowScenePrompt.issues(flowSpec(s)).isNotEmpty()) return "ยังไม่พร้อมส่ง Flow • เปิดภาพ ${key(s)} และยืนยันลักษณะ/ตำแหน่งผู้พูดในเมนูกำหนดตัวละครก่อน ไม่เดาตำแหน่งจากข้อความบท"
        return FlowScenePrompt.build(flowSpec(s))
    }
    private fun landscapeSceneImage(file: File): Boolean {
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeFile(file.path,bounds)
        return bounds.outHeight>0 && kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight-9.0/16)<0.04
    }
    private fun sceneFrameApproved(s: JSONObject): Boolean = landscapeSceneImage(image(s)) && SceneFrameReview.current(s,image(s),FlowReferencePrompt.people(s).map { masterFor(it) },flowSpec(s).toString())
    private fun reviewedVideoCommand(s: JSONObject, copy: Boolean=false) {
        if(plan?.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return }
        if(plan?.optString("content_profile")==AutoDirectorContent.profile) {
            val issues=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(plan!!))
            if(issues.isNotEmpty()) { notice(issues.take(12).joinToString("\n")); return }
        }
        if(!image(s).isFile) { notice("สร้างภาพฉากใน Gemini / Flow แล้วนำเข้าภาพก่อน จากนั้นเทียบ MASTER และยืนยันภาพ"); return }
        if(!sceneFrameApproved(s) || FlowScenePrompt.issues(flowSpec(s)).isNotEmpty()) { compareScene(s); return }
        val code="แนบภาพฉากที่ยืนยันแล้วของ ${key(s)} เป็น START FRAME\nไม่ใช้ภาพ MASTER แทนเฟรมเริ่มต้น และไม่สร้างหน้าตา/ชุดใหม่\n"+
            FlowScenePrompt.build(if(plan?.optString("content_profile")==AutoDirectorContent.profile) AutoDirectorContent.externalSpec(plan!!,flowSpec(s)) else flowSpec(s))+"\n"+FlowVisualDirection.color+"\nPreserve the approved first-frame faces and body proportions throughout all frames. No body or face morphing.\n"+FlowVisualDirection.referenceReview
        if(copy) copyCommand(code) else showFlowCode("วิดีโอจากภาพที่ยืนยันแล้ว",code,"${key(s)}_VIDEO.txt")
    }
    private fun copyFlowCommand(s: JSONObject) { reviewedVideoCommand(s,true) }
    private fun mapFlowCharacters(s: JSONObject) {
        if (!image(s).exists()) { notice("นำเข้าภาพฉากนี้ก่อนกำหนดตัวละคร"); return }
        if(!landscapeSceneImage(image(s))) { notice("ภาพฉากต้องเป็นแนวตั้ง 9:16 ก่อนยืนยัน • สร้างภาพใหม่ใน Gemini แล้วนำเข้าอีกครั้ง"); return }
        val cast = s.optJSONArray("characters") ?: JSONArray()
        if (cast.length() == 0) { notice("บทฉากนี้ขาดรายการตัวละคร กรุณาแก้บทก่อนยืนยันภาพ"); return }
        val panel = LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        panel.addView(TextView(this).apply { text="เทียบภาพจริง: ระบุทรงผม/ลักษณะเด่น และตำแหน่งของแต่ละคน ไม่เดาจากชื่อ หากบทพูดกับเสียงบรรยายมีพร้อมกันให้แก้บทแยกก่อน" })
        panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(image(s)))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(280)) })
        val descriptions=(0 until cast.length()).map { i ->
            val person=cast.getJSONObject(i)
            panel.addView(TextView(this).apply { text="${person.optString("name")} • ชุด: ${person.optString("outfit")}" })
            val appearance=EditText(this).apply { hint="ลักษณะจากภาพ เช่น ผู้หญิงผมสั้นประบ่า"; setText(person.optString("visual_description")); panel.addView(this) }
            val position=EditText(this).apply { hint="ตำแหน่งจากภาพ เช่น ซ้าย / กลาง / ขวา"; setText(person.optString("screen_position")); panel.addView(this) }
            appearance to position
        }
        val checks=listOf("ตรวจใบหน้า ผม และตัวตนตรง MASTER รายคนแล้ว","ตรวจรูปร่าง หน้าอก เอว สะโพก และแขนตามต้นฉบับแล้ว","ตรวจชุด สีภาพ มือ และอุปกรณ์แล้ว","ตรวจตำแหน่งและผู้พูดในภาพจริงแล้ว").map { label -> CheckBox(this).apply { text=label; panel.addView(this) } }
        val speaker=EditText(this).apply { hint="ผู้พูดหนึ่งคน (ชื่อในรายการ) หรือว่างเมื่อไม่มีบทพูด"; setText(s.optString("speaker")); panel.addView(this) }
        val dialog=AlertDialog.Builder(this).setTitle("ยืนยันภาพฉาก • ${key(s)}")
            .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("ยืนยันภาพและเปิดคำสั่ง",null).setNegativeButton("ยกเลิก",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(checks.any { !it.isChecked }) { notice("ตรวจภาพและทำเครื่องหมายให้ครบก่อนยืนยัน"); return@setOnClickListener }
            val candidate=JSONObject(s.toString())
            val people=candidate.getJSONArray("characters")
            descriptions.forEachIndexed { i, fields -> people.getJSONObject(i).put("visual_description",fields.first.text.toString().trim()).put("screen_position",fields.second.text.toString().trim()) }
            candidate.put("speaker",speaker.text.toString().trim())
            val issues=FlowScenePrompt.issues(flowSpec(candidate))
            if (issues.isNotEmpty()) notice(issues.joinToString("\n")) else {
                s.put("characters",people).put("speaker",candidate.optString("speaker")).put("flow_image_revision",image(s).lastModified())
                val approved=runCatching { SceneFrameReview.approve(s,image(s),FlowReferencePrompt.people(s).map { masterFor(it) },flowSpec(s).toString()) }
                if(approved.isFailure) { notice(approved.exceptionOrNull()?.message ?: "ยืนยันภาพไม่ได้"); return@setOnClickListener }
                saveStory(); dialog.dismiss(); reviewedVideoCommand(s)
            }
        } }
        dialog.show()
    }
    private fun sceneMenu(s: JSONObject) {
        rememberScene(s)
        flowSceneMenu(s)
    }
    private fun imageRequestResult(scene: JSONObject) {
        val requests=File(project,"image_requests").listFiles()?.filter { it.extension=="json" }?.mapNotNull { file ->
            runCatching { file to JSONObject(file.readText()) }.getOrNull()
        }?.filter { it.second.optString("scene")==key(scene) } ?: emptyList()
        val latest=requests.maxByOrNull { it.second.optLong("created_at") } ?: return notice("ยังไม่มีคำสั่งที่ส่งจากรุ่นนี้สำหรับฉากนี้")
        val (file,request)=latest
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        panel.addView(TextView(this).apply { text="คำสั่งที่เปิดส่งล่าสุด\n${request.optJSONArray("attachments")}\n${request.optString("text")}\nผลที่ผู้ใช้บันทึก: ${request.optString("result","ยังไม่ได้บันทึก")}\n${request.optString("provider_message")}"; setTextIsSelectable(true) })
        val message=EditText(this).apply { hint="วางข้อความตอบกลับหรือข้อผิดพลาดจริงจาก ChatGPT"; setText(request.optString("provider_message")); minLines=3 }
        panel.addView(message)
        fun record(result: String) {
            request.put("result",result).put("provider_message",message.text.toString()).put("reported_by","user").put("reported_at",System.currentTimeMillis())
            file.writeText(request.toString(2)); notice("บันทึกผลแล้ว • บท ชุด และ MASTER ยังคงเดิม")
        }
        AlertDialog.Builder(this).setTitle("ผลจาก ChatGPT • ${key(scene)}")
            .setMessage("แอปไม่สามารถอ่านผลจากหน้าแชร์อัตโนมัติ กรุณาบันทึกตามที่เห็น ไม่ระบุสาเหตุแทนระบบ")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("สร้างได้") { _,_ -> record("created_reported") }
            .setNeutralButton("สร้างไม่ได้") { _,_ -> record("failed_reported") }
            .setNegativeButton("ปิด",null).showDirector()
    }
    private fun bodyReviewStatus(s: JSONObject, person: JSONObject): String {
        val file=image(s); val master=masterFor(person)
        if(!file.exists() || !master.exists()) return "ยังไม่มีภาพครบสำหรับตรวจ"
        val record=s.optJSONObject("body_reviews")?.optJSONObject(memoryId(person)) ?: return "ยังไม่ตรวจรูปร่าง"
        val stamp=BodyReviewStamp(record.optString("scene_hash"),record.optString("master_hash"),record.optString("outfit"))
        return if(stamp.matches(EpisodeMedia.digest(file),EpisodeMedia.digest(master),sceneWardrobe(s,person)))
            "${record.optString("result")} • ${record.optString("note")}" else "ผลตรวจเก่า • ภาพ MASTER หรือชุดเปลี่ยน ต้องตรวจใหม่"
    }
    private fun sceneBodyCheck(s: JSONObject, review: Boolean) {
        val cast=s.optJSONArray("characters") ?: return notice("ฉากนี้ยังไม่มีข้อมูลตัวละคร")
        if(cast.length()==0) return notice("ฉากนี้ยังไม่มีข้อมูลตัวละคร")
        choiceMenu(if(review) "ตรวจรูปร่างเทียบ MASTER" else "ทดสอบรายคนก่อนสร้างฉาก",(0 until cast.length()).map { cast.getJSONObject(it).optString("name") }) { i ->
            val person=cast.getJSONObject(i); val master=masterFor(person); val name=person.optString("name")
            if(!master.exists()) { notice("ขาด MASTER ของ $name"); return@choiceMenu }
            if(!review) {
                val exported=masterExportName(master,person.optString("master").startsWith("CHARACTER",true)) ?: return@choiceMenu
                val command=BodyReferencePolicy.singleCheck(name,exported,if(detailedImageProfiles) memoryBrief(person) else "",sceneWardrobe(s,person),plan?.optString("aspect_ratio","9:16") ?: "9:16")
                shareText(command,includeMasters=false,referenceImages=listOf(exported.substringBeforeLast('.') to master))
            } else {
                if(!image(s).exists()) { notice("นำเข้าภาพฉากก่อนตรวจ"); return@choiceMenu }
                val sceneHash=EpisodeMedia.digest(image(s)); val masterHash=EpisodeMedia.digest(master); val outfit=sceneWardrobe(s,person)
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
                listOf("MASTER • $name" to master, "ภาพฉาก • ${key(s)}" to image(s)).forEach { (label,file) ->
                    panel.addView(TextView(this).apply { text=label })
                    panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(320)); setOnClickListener { fullImage(file,label) } })
                }
                panel.addView(TextView(this).apply { text="เทียบไหล่ ทรวงอก–เอว สะโพก ความยาวลำตัวและแขนขาที่เห็น โดยคำนึงถึงชุด ท่าทางและมุมกล้อง ไม่ประเมินจากขนาดในเฟรมอย่างเดียว\n${bodyReviewStatus(s,person)}" })
                val note=EditText(this).apply { hint="จุดที่คลาดเคลื่อนหรือมองไม่เห็น เช่น เอวเปลี่ยน / แขนบังลำตัว"; panel.addView(this) }
                fun save(result: String) {
                    if(EpisodeMedia.digest(image(s))!=sceneHash || EpisodeMedia.digest(master)!=masterHash || sceneWardrobe(s,person)!=outfit) { notice("ข้อมูลเปลี่ยน กรุณาเปิดตรวจใหม่"); return }
                    val records=s.optJSONObject("body_reviews") ?: JSONObject()
                    records.put(memoryId(person),JSONObject().put("scene_hash",sceneHash).put("master_hash",masterHash).put("outfit",outfit).put("result",result).put("note",note.text.toString().trim()).put("reviewed_at",System.currentTimeMillis()))
                    s.put("body_reviews",records)
                    if(result!="ผู้ใช้ตรวจว่าตรง") { s.put("user_review","needs_revision"); s.remove("flow_image_revision") }
                    saveStory(); notice("บันทึกผลตรวจของ $name แล้ว")
                }
                AlertDialog.Builder(this).setTitle("รูปร่าง • $name").setView(ScrollView(this).apply { addView(panel) })
                    .setPositiveButton("ตรงตามอ้างอิง") { _,_ -> save("ผู้ใช้ตรวจว่าตรง") }
                    .setNegativeButton("ต้องแก้") { _,_ -> save("ต้องแก้") }
                    .setNeutralButton("มองไม่ชัด") { _,_ -> save("ยังประเมินไม่ได้") }.showDirector()
            }
        }
    }
    private fun compareScene(s: JSONObject) {
        val panel = LinearLayout(this).apply { orientation = 1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        fun picture(title: String, file: File) {
            panel.addView(TextView(this).apply { text = title; textSize = 15f; setTypeface(null,Typeface.BOLD) })
            if (file.exists()) panel.addView(ImageView(this).apply {
                setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(-1,dp(280))
                setOnClickListener { fullImage(file,title) }
            }) else panel.addView(TextView(this).apply { text = "ยังไม่มีไฟล์" })
        }
        val cast = s.optJSONArray("characters") ?: JSONArray()
        for (i in 0 until cast.length()) {
            val person = cast.getJSONObject(i); picture("MASTER • ${person.optString("name")} • ${bodyReviewStatus(s,person)}",masterFor(person))
        }
        picture("ผลลัพธ์ • ${key(s)} • ${s.optString("user_review","รอตรวจ")}",image(s))
        panel.addView(TextView(this).apply { text = "ตรวจใบหน้า ผม รูปร่าง ชุด และมือเทียบ MASTER • ให้ผู้ใช้ประเมินจากภาพจริง\n${sceneCastDescription(s)}\n${s.optString("action")}\n${s.optString("dialogue")}" })
        AlertDialog.Builder(this).setTitle("เปรียบเทียบต้นฉบับ").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("ตรวจแล้ว / ยืนยันภาพ") { _,_ -> mapFlowCharacters(s) }.setNegativeButton("ปิด",null).showDirector()
    }
    private fun fullImage(file: File, title: String) {
        val imageView = ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(imageView).setPositiveButton("ปิด",null).create()
        dialog.show(); dialog.window?.setLayout(-1,-1)
    }
    private fun editScene(s: JSONObject) {
        val draftFile=File(project,"drafts/${key(s)}.json")
        val draft=runCatching { if(draftFile.exists()) JSONObject(draftFile.readText()) else s }.getOrDefault(s)
        val previousGroup=s.optString("continuity_group")
        val fields = listOf("action" to "เหตุการณ์", "dialogue" to "บทพูด", "speaker" to "ผู้พูด", "narration" to "เสียงบรรยาย", "location" to "สถานที่", "continuity_group" to "กลุ่มความต่อเนื่อง", "props" to "อุปกรณ์", "story_time" to "เวลาในเรื่อง", "story_day" to "วันในเรื่อง เช่น DAY01", "activity" to "กิจกรรม เช่น meeting / home / sleep", "wardrobe_event" to "ช่วงชุด เช่น DAY01_MEETING", "image_prompt" to "คำสั่งภาพ", "framing" to "ระยะภาพ (ว่าง = ตามการกระทำ)", "lighting" to "แสงตามฉาก", "flow_prompt" to "VIDEO PROMPT (แก้ให้ตรงกับบทและชุดปัจจุบัน)", "flow_action" to "การกระทำสำหรับวิดีโอ (English)", "start_state" to "สถานะต้นฉาก", "end_state" to "สถานะท้ายฉาก")
        val panel = LinearLayout(this).apply { orientation = 1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        val inputs = fields.map { (key,label) -> key to EditText(this).apply { hint = label; setText(draft.optString(key,s.optString(key))); panel.addView(this) } }
        val cast = s.optJSONArray("characters") ?: JSONArray()
        val outfits = (0 until cast.length()).map { i -> i to EditText(this).apply {
            hint = "ชุด ${cast.getJSONObject(i).optString("name")}"; setText(draft.optJSONArray("characters")?.optJSONObject(i)?.optString("outfit") ?: cast.getJSONObject(i).optString("outfit")); panel.addView(this)
        } }
        val positions=(0 until cast.length()).map { i -> i to EditText(this).apply {
            hint="ตำแหน่ง ${cast.getJSONObject(i).optString("name")} เช่น left / center / right"
            setText(draft.optJSONArray("characters")?.optJSONObject(i)?.optString("screen_position") ?: cast.getJSONObject(i).optString("screen_position")); panel.addView(this)
        } }
        val soundInputs=listOf("ambience","sfx","music").map { field -> field to EditText(this).apply {
            hint="เสียง $field (ระบุ none เมื่อไม่มี)"; setText(draft.optJSONObject("sound_design")?.optString(field).orEmpty()); panel.addView(this)
        } }
        fun persistDraft() {
            val copy=JSONObject(s.toString())
            inputs.forEach { (key,input) -> copy.put(key,input.text.toString()) }
            val people=copy.optJSONArray("characters")
            outfits.forEach { (i,input) -> people?.optJSONObject(i)?.put("outfit",input.text.toString()) }
            positions.forEach { (i,input) -> people?.optJSONObject(i)?.put("screen_position",input.text.toString()) }
            if(soundInputs.any { it.second.text.isNotBlank() }) copy.put("sound_design",JSONObject().apply { soundInputs.forEach { (field,input) -> put(field,input.text.toString().ifBlank { "none" }) } })
            draftFile.parentFile!!.mkdirs(); draftFile.writeText(copy.toString(2))
        }
        val watcher=object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) { persistDraft() }
            override fun afterTextChanged(text: android.text.Editable?) {}
        }
        (inputs.map { it.second }+outfits.map { it.second }+positions.map { it.second }+soundInputs.map { it.second }).forEach { it.addTextChangedListener(watcher) }
        AlertDialog.Builder(this).setTitle("แก้บท ${key(s)} • เก็บร่างอัตโนมัติ")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("บันทึก") { _,_ ->
                if(inputs.any { it.first in listOf("action","image_prompt","flow_prompt") && it.second.text.isBlank() }) { notice("เหตุการณ์ คำสั่งภาพ และคำสั่ง Flow ต้องไม่ว่าง"); return@setPositiveButton }
                inputs.forEach { (key,input) -> s.put(key,input.text.toString().trim()) }
                outfits.forEach { (i,input) -> cast.getJSONObject(i).put("outfit",input.text.toString().trim()) }
                positions.forEach { (i,input) -> cast.getJSONObject(i).put("screen_position",input.text.toString().trim()) }
                if(soundInputs.any { it.second.text.isNotBlank() }) s.put("sound_design",JSONObject().apply { soundInputs.forEach { (field,input) -> put(field,input.text.toString().trim().ifBlank { "none" }) } })
                val group = s.optString("continuity_group")
                val affected = rows(plan!!).filter { key(it) == key(s) || (group.isNotBlank() && it.optString("continuity_group") == group || previousGroup.isNotBlank() && it.optString("continuity_group") == previousGroup) }
                affected.forEach { it.put("user_review","needs_revision"); it.remove("flow_image_revision") }; saveStory(); draftFile.delete(); render()
                notice("บันทึกแล้ว • ตรวจความต่อเนื่อง ${affected.joinToString { key(it) }}\nเก็บภาพและวิดีโอเดิมไว้แล้ว ต้องตรวจคำสั่ง Flow ให้ตรงบทที่แก้")
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun masterFor(person: JSONObject): File {
        if (person.optString("master").startsWith("CHARACTER",true)) {
            val requested=person.optString("master"); val exact=File(project,"masters/$requested")
            return if(exact.exists()) exact else File(project,"masters").listFiles()?.firstOrNull { it.nameWithoutExtension.equals(requested.substringBeforeLast('.'),true) } ?: exact
        }
        val id = FlowCast.id(person) ?: return File(project,"missing.master")
        return File(project,"original_masters/$id.master").takeIf { it.exists() } ?: File(filesDir,"character_masters/$id.master")
    }
    private fun repairScene(s: JSONObject) {
        if (!image(s).exists()) return notice("ต้องนำเข้าภาพฉากก่อนแก้ภาพ")
        val input = EditText(this).apply { hint = "ส่วนที่ต้องแก้ เช่น ชุดใหม่ / ฉากหลัง / แสงและสี" }
        AlertDialog.Builder(this).setTitle("แก้เฉพาะส่วน ${key(s)}").setView(input).setPositiveButton("ตรวจและส่ง") { _,_ ->
            val change = input.text.toString().trim()
            if (change.isBlank()) { notice("ระบุส่วนที่ต้องแก้ก่อน"); return@setPositiveButton }
            val cast = s.optJSONArray("characters") ?: JSONArray()
            val refs = (0 until cast.length()).map { cast.getJSONObject(it) }.map { (masterExportName(masterFor(it),it.optString("master").startsWith("CHARACTER",true)) ?: it.optString("master")).substringBeforeLast('.') to masterFor(it) } + (key(s) to image(s))
            shareText(ImagePromptPolicy.edit("ภาพฉาก ${key(s)}",change,plan?.optString("aspect_ratio","9:16") ?: "9:16",
                sceneCastDescription(s),"${key(s)}.png"),includeMasters=false,referenceImages=refs)
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private var mediaSlot: String?
        get() = prefs.getString("media_slot",null)
        set(value) { prefs.edit().putString("media_slot",value).apply() }
    private var mediaKind: String
        get() = prefs.getString("media_kind","image")!!
        set(value) { prefs.edit().putString("media_kind",value).apply() }
    private var mediaProject: String
        get() = prefs.getString("media_project",project.name)!!
        set(value) { prefs.edit().putString("media_project",value).apply() }
    private val sceneMediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = mediaSlot
        if (uri != null && id != null) {
            val folder = File(base,mediaProject); val kind = mediaKind
            val target = File(folder,if(kind == "video") "videos/$id.mp4" else "images/$id.img")
            fun accept() { background {
                target.parentFile!!.mkdirs(); val temp = File(target.parentFile,target.name+".pending")
                try {
                    contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    require(temp.length() > 0) { "ไฟล์ว่าง" }
                    if (kind == "image") {
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        android.graphics.BitmapFactory.decodeFile(temp.path,bounds); require(bounds.outWidth > 0) { "อ่านภาพไม่ได้" }
                    } else {
                        val retriever = android.media.MediaMetadataRetriever()
                        try { retriever.setDataSource(temp.path); require(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { it > 0 } == true) { "อ่านวิดีโอไม่ได้" } } finally { retriever.release() }
                    }
                    if (target.exists()) archiveVersion(target,folder,id,kind)
                    check(temp.renameTo(target)) { "บันทึกไม่ได้" }
                    val storyFile = File(folder,"story.json")
                    val story = JSONObject(storyFile.readText()); rows(story).firstOrNull { key(it) == id }?.put("user_review","pending")
                    storyFile.writeText(story.toString(2))
                } finally { temp.delete() }
            } }
            AlertDialog.Builder(this).setTitle("ตรวจไฟล์ก่อนนำเข้า").setMessage("${displayName(uri)} → $id\nชนิด: $kind\n${if(target.exists()) "ไฟล์เดิมจะเก็บเป็นเวอร์ชันก่อนแทน" else "เพิ่มไฟล์ใหม่"}")
                .setPositiveButton("นำเข้า") { _,_ -> accept() }.setNegativeButton("ยกเลิก",null).showDirector()
        }
        mediaSlot = null
    }
    private fun playVideo(s: JSONObject) {
        val file = video(s); if (!file.exists()) return notice("ยังไม่มีวิดีโอฉากนี้ • สร้างใน Flow แล้วนำเข้ากลับ")
        val view = VideoView(this).apply {
            setVideoPath(file.path); val controls = MediaController(this@StudioActivity); controls.setAnchorView(this); setMediaController(controls)
            setOnPreparedListener { start() }; setOnErrorListener { _,_,_ -> notice("เล่นไฟล์นี้ไม่ได้ กรุณานำเข้าวิดีโอ MP4 ที่รองรับ"); true }
        }
        val dialog = AlertDialog.Builder(this).setTitle(key(s)).setView(view).setPositiveButton("ปิด",null).create()
        dialog.setOnDismissListener { view.stopPlayback() }; dialog.show(); dialog.window?.setLayout(-1,dp(560))
    }
    private fun versionsMenu(s: JSONObject) {
        choiceMenu("เวอร์ชัน ${key(s)}",listOf("ภาพ", "วิดีโอ")) { choice ->
            val kind = if(choice == 0) "image" else "video"
            val files = File(project,"versions/${key(s)}/$kind").listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
            choiceMenu("เลือกเวอร์ชัน $kind",files.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }) { i ->
                val source = files[i]; val target = if(kind == "image") image(s) else video(s)
                AlertDialog.Builder(this).setMessage("ใช้เวอร์ชันนี้? เวอร์ชันปัจจุบันจะเก็บไว้")
                    .setPositiveButton("ใช้เวอร์ชันนี้") { _,_ ->
                        if(target.exists()) archiveVersion(target,project,key(s),kind)
                        target.parentFile!!.mkdirs(); source.copyTo(target,overwrite=true); s.put("user_review","pending"); saveStory(); render()
                    }.setNegativeButton("ยกเลิก",null).showDirector()
            }
        }
    }
    private fun auditStory() {
        val p = plan ?: return notice("ยังไม่มีบท")
        val report = rows(p).map { s ->
            val cast = s.optJSONArray("characters") ?: JSONArray()
            val absent = (0 until cast.length()).map { cast.getJSONObject(it) }.filter { !masterFor(it).exists() }.map { it.optString("name") }
            "${key(s)} • ภาพ ${if(image(s).exists()) "✓" else "ขาด"} • วิดีโอ ${if(video(s).exists()) "✓" else "ขาด"} • ตรวจรับ ${s.optString("user_review","รอตรวจ")}\n" +
                (if(absent.isEmpty()) "" else "ขาด MASTER: ${absent.joinToString()}\n") +
                (if(s.optString("dialogue").length > 55) "ตรวจบทพูด: อาจยาวเกิน 8 วินาที\n" else "")
        }.joinToString("\n")
        val text = TextView(this).apply { this.text = report; setTextIsSelectable(true); setPadding(dp(16),dp(12),dp(16),dp(12)) }
        AlertDialog.Builder(this).setTitle("ตรวจงาน • ${p.optString("title")}").setView(ScrollView(this).apply { addView(text) }).setPositiveButton("ปิด",null).setNeutralButton("คัดลอก") { _,_ -> copyCommand(report) }.showDirector()
    }
    private fun storiesMenu() {
        val folders = base.listFiles()?.filter { File(it,"story.json").exists() || File(it,"gemini_job/spec.json").exists() }?.sortedByDescending { maxOf(File(it,"story.json").lastModified(),File(it,"gemini_job/status.json").lastModified()) }.orEmpty()
        choiceMenu("📚 เรื่องของฉัน",folders.map { f ->
            runCatching { JSONObject(File(f,"story.json").readText()).optString("title") }.getOrElse { runCatching { GeminiStoryFiles.read(File(f,"gemini_job/outline.json")).getString("title")+" • ยังเขียนไม่จบ" }.getOrDefault("งานเขียนบทที่ยังไม่จบ") } + if(File(f,"archived").exists()) " • เก็บแล้ว" else ""
        }) { i ->
            prefs.edit().putString("project",folders[i].name).apply(); render()
            if(plan==null) { geminiJobMenu(); return@choiceMenu }
            choiceMenu("จัดการเรื่อง",listOf("▶ ทำต่อ", "✏️ เปลี่ยนชื่อ", "📑 ทำสำเนา", "📁 เก็บ / เปิดใช้งาน", "🗑️ ลบเรื่อง")) { action -> when(action) {
                0 -> resumeScene()
                1 -> { val input = EditText(this).apply { setText(plan?.optString("title")) }
                    AlertDialog.Builder(this).setTitle("ชื่อเรื่อง").setView(input).setPositiveButton("บันทึก") { _,_ ->
                        if(input.text.isNotBlank()) { plan?.put("title",input.text.toString().trim()); saveStory(); render() }
                    }.setNegativeButton("ยกเลิก",null).showDirector() }
                2 -> background { val source = project; val target = File(base,UUID.randomUUID().toString()); check(source.copyRecursively(target))
                    val p = JSONObject(File(target,"story.json").readText()); p.put("title",p.optString("title")+" (สำเนา)"); File(target,"story.json").writeText(p.toString(2)); prefs.edit().putString("project",target.name).commit() }
                3 -> { val file = File(project,"archived"); if(file.exists()) file.delete() else file.writeText("archived"); render() }
                4 -> { val folder = project; AlertDialog.Builder(this).setMessage("ลบเรื่องและไฟล์ทั้งหมด? สำรอง ZIP ก่อนลบ")
                    .setPositiveButton("ลบเรื่อง") { _,_ -> folder.deleteRecursively(); prefs.edit().putString("project",UUID.randomUUID().toString()).apply(); render() }
                    .setNegativeButton("ยกเลิก",null).showDirector() }
            } }
        }
    }
    private fun settingsMenu() {
        choiceMenu("⚙️ ตั้งค่าและดูแลงาน",listOf("🔄 อัปเดตแอป", "📦 ส่งออกงานทั้งหมด / สำรอง", "📥 กู้คืนชุดสำรอง", "🎭 ตัวเลือกเดิม / MASTER", "💾 พื้นที่และข้อผิดพลาด", "↶ กู้คืนบทก่อนแก้ล่าสุด", "🎛️ ตั้งค่าการสร้างเรื่อง", "🛟 สำรองทั้งเรื่อง รวม MASTER และเวอร์ชัน", "↗ ส่งคำสั่งล่าสุดซ้ำ")) { i -> when(i) {
            0 -> updater.check()
            1 -> { if(plan == null) notice("ยังไม่มีบท") else { exportEpisode=null; backupMode=false; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title"))) } }
            2 -> restorePicker.launch(arrayOf("application/zip"))
            3 -> toolsMenu()
            4 -> { val used = base.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val message = "DIRECTOR X ${packageManager.getPackageInfo(packageName,0).versionName}\nงานทั้งหมด ${used/1024/1024} MB\nพื้นที่ว่าง ${filesDir.usableSpace/1024/1024} MB\nเรื่อง ${plan?.optString("title").orEmpty()}\n${File(filesDir,"last_error.txt").takeIf { it.exists() }?.readText().orEmpty()}"
                AlertDialog.Builder(this).setMessage(message).setPositiveButton("ปิด",null).setNeutralButton("คัดลอก") { _,_ -> copyCommand(message) }.showDirector() }
            5 -> { val previous=File(project,"story.previous.json"); if(!previous.exists()) notice("ยังไม่มีบทก่อนแก้") else AlertDialog.Builder(this).setMessage("กู้คืนบทก่อนแก้ล่าสุด? ไฟล์ภาพยังอยู่")
                .setPositiveButton("กู้คืน") { _,_ -> val old=previous.readText(); saveStory(); File(project,"story.json").writeText(old); render() }.setNegativeButton("ยกเลิก",null).showDirector() }
            6 -> planningSettings()
            8 -> retryLastCommand()
            7 -> { if(plan == null) notice("ยังไม่มีบท") else { backupMode=true; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title")+"_สำรอง")) } }
        } }
    }
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) AlertDialog.Builder(this).setTitle("กู้คืนชุดสำรอง")
            .setMessage("${displayName(uri)}\nจะกู้คืนเป็นเรื่องใหม่ ไม่เขียนทับเรื่องเดิม รวม MASTER ภาพ วิดีโอ และเวอร์ชันที่มีในชุดสำรอง")
            .setPositiveButton("กู้คืน") { _,_ -> background {
                val staging=File(cacheDir,"restore_${UUID.randomUUID()}")
                try {
                    contentResolver.openInputStream(uri)!!.use { StudioArchive.read(it,staging) }
                    val p=JSONObject(File(staging,"story.json").readText()); validate(p)
                    val folder=File(base,UUID.randomUUID().toString()); check(staging.copyRecursively(folder))
                    prefs.edit().putString("project",folder.name).commit()
                } finally { staging.deleteRecursively() }
            } }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun retryLastCommand() {
        val file=File(project,"last_payload.json")
        if(!file.exists()) return notice("ยังไม่มีคำสั่งที่เตรียมส่ง")
        runCatching {
            val payload=JSONObject(file.readText()); val images=payload.getJSONArray("images")
            val references=(0 until images.length()).map { i ->
                val uri=Uri.parse(images.getString(i))
                // Resolve only the app's own export roots, never an external path.
                require(uri.authority=="$packageName.fileprovider") { "แหล่งภาพส่งซ้ำไม่ถูกต้อง" }
                val relative=uri.path?.trimStart('/') ?: error("ไม่มีเส้นทางภาพ")
                require(relative.startsWith("character_master_exports/") || relative.startsWith("scene_reference_exports/")) { "ภาพไม่ได้อยู่ในพื้นที่ส่งออก" }
                val source=File(cacheDir,relative)
                require(source.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator) && source.isFile) { "ภาพส่งซ้ำหมดอายุ กรุณาส่งจากเมนูฉากอีกครั้ง" }
                source.nameWithoutExtension to source
            }
            shareText(payload.getString("text"),lockCreation=payload.optBoolean("creation"),includeMasters=false,referenceImages=references)
        }.onFailure { notice(it.message ?: "ส่งซ้ำไม่ได้") }
    }
    private fun planningSettings() {
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        val fields=listOf("episodes" to "จำนวน EP (ว่าง = AI ประเมิน)", "scenes" to "ฉากต่อ EP (ว่าง = AI ประเมิน)", "duration" to "วินาทีต่อฉาก", "ratio" to "สัดส่วนภาพ 9:16 เท่านั้น")
        val inputs=fields.map { (id,label) -> id to EditText(this).apply { hint=label; setText(if(id=="ratio") "9:16" else prefs.getString("plan_$id",if(id=="duration") "8" else "")); if(id=="ratio") isEnabled=false; panel.addView(this) } }
        AlertDialog.Builder(this).setTitle("ค่าตั้งต้นสำหรับเรื่องใหม่").setView(panel).setPositiveButton("บันทึก") { _,_ ->
            val values=inputs.associate { it.first to it.second.text.toString().trim() }
            if(values["ratio"] !in listOf("9:16") || values["duration"]?.toIntOrNull()?.let { it in 1..60 } != true || listOf("episodes","scenes").any { values[it]!!.isNotBlank() && values[it]?.toIntOrNull()?.let { n -> n in 1..100 } != true }) notice("ตรวจจำนวน 1–100 ระยะเวลา 1–60 และสัดส่วน 9:16")
            else { val edit=prefs.edit(); values.forEach { (k,v) -> edit.putString("plan_$k",v) }; edit.apply(); notice("บันทึกค่าตั้งต้นแล้ว ใช้กับคำสั่งสร้างเรื่องใหม่") }
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private var castTarget: Pair<String,File>?
        get() { val name=prefs.getString("cast_target_name",null) ?: return null; val path=prefs.getString("cast_target_path",null) ?: return null; return name to File(path) }
        set(value) { prefs.edit().putString("cast_target_name",value?.first).putString("cast_target_path",value?.second?.path).apply() }
    private val castPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target=castTarget; castTarget=null
        if(uri!=null && target!=null) background {
            val file=target.second; file.parentFile!!.mkdirs(); val temp=File(file.parentFile,file.name+".pending")
            try {
                contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }; android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                require(bounds.outWidth>0) { "อ่าน MASTER ไม่ได้" }
                if(file.exists()) archiveVersion(file,project,target.first,"master")
                check(temp.renameTo(file))
                val affected=rows(plan!!).filter { scene -> scene.optJSONArray("characters")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("name")==target.first } } == true }
                affected.forEach { it.put("user_review","needs_revision"); it.remove("flow_image_revision") }; saveStory()
            } finally { temp.delete() }
        }
    }
    private fun readSupportingCast(): JSONArray = runCatching { JSONArray(File(filesDir,"supporting_cast.json").readText()) }.getOrDefault(JSONArray())
    private val supportImagePicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending=prefs.getString("pending_support",null); prefs.edit().remove("pending_support").apply()
        if(uri!=null && pending!=null) runCatching {
            val person=JSONObject(pending); val id=FlowCast.id(person)!!
            val file=File(filesDir,"supporting_masters/$id.master"); file.parentFile!!.mkdirs()
            val temp=File(file.parentFile,file.name+".pending")
            try {
                contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { input.copyTo(it) } }
                require(masterExportName(temp,false)!=null) { "อ่านภาพตัวละครเสริมไม่ได้" }
                check(temp.renameTo(file))
                val list=readSupportingCast(); list.put(person)
                require(FlowCast.issues(JSONObject().put("supporting_characters",list)).isEmpty()) { "ชื่อซ้ำหรือข้อมูลตัวละครเสริมไม่ถูกต้อง" }
                File(filesDir,"supporting_cast.json").writeText(list.toString(2))
            } finally { temp.delete() }
        }.onSuccess { notice("บันทึกตัวละครเสริมแล้ว • ใช้กับเรื่องใหม่ที่สร้างต่อจากนี้") }.onFailure { notice(it.message ?: "บันทึกไม่ได้") }
    }
    private fun supportingCastMenu() {
        val roster=readSupportingCast()
        choiceMenu("ตัวละครเสริม • ใช้กับเรื่องใหม่",(0 until roster.length()).map { roster.getJSONObject(it).getString("name") }+"เพิ่มตัวละครเสริม") { index ->
            if(index==roster.length()) {
                if(roster.length()>=8) { notice("รองรับตัวละครเสริมไม่เกิน 8 คน"); return@choiceMenu }
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
                val name=EditText(this).apply { hint="ชื่อตัวละครเสริม"; panel.addView(this) }
                val description=EditText(this).apply { hint="ผู้ใหญ่ • บทบาท / ใบหน้า / ผม / รูปร่างที่เห็นจากภาพ"; panel.addView(this) }
                AlertDialog.Builder(this).setTitle("เพิ่มตัวละครเสริมพร้อมภาพต้นฉบับ").setView(panel).setPositiveButton("เลือกภาพ") { _,_ ->
                    val value=name.text.toString().trim()
                    if(value.isBlank() || description.text.isBlank() || StoryIdentity.keyFor(value)!=null || (0 until roster.length()).any { roster.getJSONObject(it).getString("name")==value }) notice("ระบุชื่อไม่ซ้ำและคำบรรยายจากภาพ")
                    else {
                        val id=UUID.randomUUID().toString().take(8).uppercase()
                        val person=JSONObject().put("name",value).put("master","SUPPORT_${id}_MASTER.png").put("visual_description","adult fictional character; "+description.text.toString())
                        prefs.edit().putString("pending_support",person.toString()).apply(); supportImagePicker.launch(arrayOf("image/*"))
                    }
                }.setNegativeButton("ยกเลิก",null).showDirector()
            } else {
                val person=roster.getJSONObject(index); val file=File(filesDir,"supporting_masters/${FlowCast.id(person)}.master")
                choiceMenu(person.getString("name"),listOf("ดูภาพต้นฉบับ","นำออกจากเรื่องใหม่")) { action ->
                    if(action==0) fullImage(file,person.getString("name")) else {
                        val remaining=JSONArray(); for(i in 0 until roster.length()) if(i!=index) remaining.put(roster.getJSONObject(i))
                        File(filesDir,"supporting_cast.json").writeText(remaining.toString(2)); notice("นำออกจากรายการเรื่องใหม่แล้ว เรื่องเดิมยังเก็บภาพของตัวเอง")
                    }
                }
            }
        }
    }
    private fun castMenu() {
        val p=plan ?: return characterMasters()
        val people=rows(p).flatMap { scene -> scene.optJSONArray("characters")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty() }.distinctBy { it.optString("name") }
        choiceMenu("🎭 ตัวละครเรื่องนี้",people.map { "${it.optString("name")} • ${FlowCast.supporting(p).firstOrNull { c -> c.optString("name")==it.optString("name") }?.optString("library_id").orEmpty()} • ${if(masterFor(it).exists()) "มี MASTER" else "ขาด MASTER"}" }) { i ->
            val person=people[i]; val name=person.optString("name")
            choiceMenu(name,listOf("🖼️ ดู MASTER", "📷 เปลี่ยน MASTER รายเรื่อง", "✏️ บทบาทตัวละคร", "🧠 ข้อมูลใบหน้า ผม และรูปร่าง", "เตรียม MASTER มาตรฐาน", "ตรวจภาพ MASTER")) { action -> when(action) {
                0 -> { val file=masterFor(person); if(file.exists()) fullImage(file,name) else notice("ยังไม่มี MASTER") }
                1 -> { val id=FlowCast.id(person)
                    val file=if(p.optString("character_mode","original")=="new") masterFor(person) else if(id!=null) File(project,"original_masters/$id.master") else return@choiceMenu
                    AlertDialog.Builder(this).setMessage("เปลี่ยน MASTER ของ $name เฉพาะเรื่องนี้ • ฉากที่ใช้คนนี้จะถูกระบุว่าต้องตรวจใหม่")
                        .setPositiveButton("เลือกรูป") { _,_ -> castTarget=name to file; castPicker.launch(arrayOf("image/*")) }.setNegativeButton("ยกเลิก",null).showDirector() }
                2 -> { val input=EditText(this).apply { hint="บทบาทและลักษณะตัวละคร"; setText(p.optJSONObject("cast_roles")?.optString(name)) }
                    AlertDialog.Builder(this).setTitle(name).setView(input).setPositiveButton("บันทึก") { _,_ -> val roles=p.optJSONObject("cast_roles") ?: JSONObject(); roles.put(name,input.text.toString()); p.put("cast_roles",roles); saveStory() }.setNegativeButton("ยกเลิก",null).showDirector() }
                3 -> editCharacterMemory(memoryId(person),name,masterFor(person))
                4 -> sendProductionMaster(memoryId(person),name,masterFor(person))
                5 -> reviewMaster(memoryId(person),name,masterFor(person))
            } }
        }
    }

    private fun moviesMenu() {
        val p=plan ?: return notice("นำเข้าบทและคลิปก่อนครับ")
        val eps=p.getJSONArray("episodes")
        choiceMenu("🎬 เลือก EP เพื่อทำวิดีโอ",(0 until eps.length()).map { i ->
            val ep=eps.getJSONObject(i)
            val scenes=rows(p).filter { it.getInt("ep")==ep.getInt("ep") }
            val count=scenes.count { video(it).isFile && video(it).length()>0 }
            "EP ${ep.getInt("ep")} • ${ep.optString("title")}\nคลิป $count/${scenes.size} • " + if(count==scenes.size && scenes.isNotEmpty()) "พร้อมตรวจและรวม EP" else "นำเข้า MP4 รายฉากก่อนรวม"
        }) { i -> movieEpisode(eps.getJSONObject(i)) }
    }
    private fun movieEpisode(ep: JSONObject) {
        choiceMenu("EP ${ep.getInt("ep")} • ${ep.optString("title")}",listOf("🎬 ตรวจคลิปและรวม EP", "⏳ ดูความคืบหน้า / ยกเลิก", "▶ ดูหนังที่รวมแล้ว", "⬇ ดาวน์โหลด MP4", "↗ แชร์ MP4", "🗂️ หนังเวอร์ชันก่อนหน้า", "🎞️ จัดการคลิปฉาก", "↕ จัดลำดับคลิป")) { i -> when(i) {
            0 -> prepareMovie(ep)
            1 -> movieProgress(ep.getInt("ep"))
            2 -> latestMovie(ep.getInt("ep"))?.let { playMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            3 -> latestMovie(ep.getInt("ep"))?.let { downloadMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            4 -> latestMovie(ep.getInt("ep"))?.let { shareMovie(it) } ?: notice("ยังไม่มีหนังที่รวมสำเร็จ")
            5 -> { val jobs=movieJobs(ep.getInt("ep")).filter { File(it,"output.mp4").exists() && readMovieState(it).optString("state")=="complete" }
                choiceMenu("เลือกเวอร์ชัน",jobs.map { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.lastModified())) }) { n ->
                    choiceMenu("หนังเวอร์ชันนี้",listOf("ดูหนัง", "ดาวน์โหลด MP4", "แชร์")) { action -> when(action) { 0 -> playMovie(jobs[n]); 1 -> downloadMovie(jobs[n]); 2 -> shareMovie(jobs[n]) } }
                }
            }
            6 -> episodesMenu()
            7 -> reorderMovie(ep)
        } }
    }
    private fun orderedMovieScenes(ep: JSONObject): List<JSONObject> {
        val scenes=rows(plan!!).filter { it.getInt("ep")==ep.getInt("ep") }.sortedBy { it.getInt("scene") }
        val order=ep.optJSONArray("render_order") ?: return scenes
        val keys=(0 until order.length()).map { order.getString(it) }
        require(keys.size==scenes.size && keys.toSet()==scenes.map { key(it) }.toSet()) { "ลำดับคลิปไม่ตรงบท กรุณาจัดลำดับใหม่หรือคืนลำดับเลขฉาก" }
        return keys.map { id -> scenes.first { key(it)==id } }
    }
    private fun reorderMovie(ep: JSONObject) {
        val scenes=rows(plan!!).filter { it.getInt("ep")==ep.getInt("ep") }.sortedBy { it.getInt("scene") }
        val ordered=runCatching { orderedMovieScenes(ep) }.getOrDefault(scenes)
        val ids=ordered.map { key(it) }.toMutableList()
        choiceMenu("จัดลำดับ • เลือกคลิปเพื่อเลื่อน",ids.mapIndexed { i,id -> "${i+1}. $id" }+"คืนลำดับเลขฉาก") { index ->
            if(index==ids.size) { ep.remove("render_order"); saveStory(); reorderMovie(ep) }
            else choiceMenu(ids[index],listOf("เลื่อนขึ้น", "เลื่อนลง")) { action ->
                val destination=if(action==0) index-1 else index+1
                if(destination !in ids.indices) notice("อยู่ปลายลำดับแล้ว") else {
                    val item=ids.removeAt(index); ids.add(destination,item); ep.put("render_order",JSONArray(ids)); saveStory(); reorderMovie(ep)
                }
            }
        }
    }
    private fun movieJobs(ep: Int) = File(project,"movies/EP%02d".format(ep)).listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }.orEmpty()
    private fun readMovieState(job: File): JSONObject = runCatching { JSONObject(File(job,"status.json").readText()) }.getOrDefault(JSONObject())
    private fun latestMovie(ep: Int) = movieJobs(ep).firstOrNull { readMovieState(it).optString("state")=="complete" && File(it,"output.mp4").isFile && runCatching { JSONObject(File(it,"result.json").readText()).let { result -> result.optInt("width")==EpisodeRules.WIDTH && result.optInt("height")==EpisodeRules.HEIGHT } }.getOrDefault(false) }
    private fun movieFingerprint(ep: Int): String {
        val episodes=plan!!.getJSONArray("episodes")
        val episode=(0 until episodes.length()).map { episodes.getJSONObject(it) }.first { it.getInt("ep")==ep }
        val scenes=orderedMovieScenes(episode)
        val cover=coverFile("EP%02d_COVER_9x16".format(ep))
        return "9:16|1080x1920|"+scenes.joinToString(",") { key(it) }+"|"+(listOf(cover)+scenes.map { video(it) }).joinToString("|") { if(it.exists()) EpisodeMedia.digest(it) else "missing" }
    }
    private fun prepareMovie(ep: JSONObject) {
        if(plan?.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return }
        if(EpisodeRenderService.running) return movieProgress(ep.getInt("ep"))
        val number=ep.getInt("ep"); val scenes=runCatching { orderedMovieScenes(ep) }.getOrElse { notice(it.message ?: "ตรวจลำดับคลิป"); return }
        val missing=scenes.filter { !video(it).exists() }
        if(missing.isNotEmpty()) return notice("ยังขาดคลิป ${missing.joinToString { key(it) }}\nนำเข้าให้ครบก่อนรวม")
        val cover=coverFile("EP%02d_COVER_9x16".format(number))
        if(!cover.exists()) return notice("ยังขาดปกแนวตั้ง EP $number • นำเข้าปกก่อนรวม")
        val dialog=AlertDialog.Builder(this).setMessage("กำลังตรวจขนาด เสียง และไฟล์คลิป…").setCancelable(false).showDirector()
        val folder=project; val p=plan!!
        Thread {
            try {
                val infos=scenes.map { EpisodeMedia.inspect(video(it)) }
                val duration=3000L+infos.sumOf { it.durationMs }
                val rate=kotlin.math.round(infos.first().fps).toInt().coerceIn(1,120)
                val bitrate=maxOf(24_000_000,infos.maxOf { it.bitrate }).coerceAtMost(80_000_000)
                val needed=EpisodeRules.estimateBytes(duration,bitrate,infos.sumOf { it.file.length() })
                require(folder.usableSpace>needed) { "พื้นที่ไม่พอ ต้องมีว่างประมาณ ${needed/1024/1024} MB" }
                val fingerprint=movieFingerprint(number)
                val report="ปกเปิด 3 วินาที → ${scenes.size} ฉาก\nผลลัพธ์ 1080 × 1920 • $rate fps • ${duration/1000} วินาที\nพื้นที่ประมาณ ${needed/1024/1024} MB\n\n"+infos.mapIndexed { i,v -> "${key(scenes[i])}: ${v.displayWidth} × ${v.displayHeight}, ${v.fps} fps, ${v.durationMs/1000.0} วินาที, ${v.videoMime}, เสียง ${v.audioMime ?: "ไม่มี"}" }.joinToString("\n")
                runOnUiThread {
                    dialog.dismiss()
                    val view=TextView(this).apply { text=report; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                    AlertDialog.Builder(this).setTitle("ตรวจลำดับและคุณภาพก่อนรวม").setView(ScrollView(this).apply { addView(view) })
                        .setPositiveButton("เลือกรูปแบบรวม") { _,_ ->
                            choiceMenu("วิธีรวม • เก็บต้นฉบับทุกคลิป",listOf("คุณภาพสูง • ปรับไฟล์ให้เข้ากัน", "ไม่เข้ารหัสคลิปใหม่ • ต้องตรงทุกค่า")) { mode ->
                                fun start(crop: Boolean) {
                                    if(EpisodeRenderService.running) { notice("มีงานรวมกำลังทำอยู่"); return }
                                    val job=File(folder,"movies/EP%02d/%d_%s".format(number,System.currentTimeMillis(),UUID.randomUUID())).apply { mkdirs() }
                                    val spec=JSONObject().put("aspect_ratio","9:16").put("width",EpisodeRules.WIDTH).put("height",EpisodeRules.HEIGHT).put("ep",number).put("title",p.optString("title")).put("filename",EpisodeRules.filename(p.optString("title"),number,ep.optString("title")))
                                        .put("cover",cover.path).put("clips",JSONArray(infos.map { it.file.path })).put("fps",rate).put("bitrate",bitrate).put("duration_ms",duration).put("fingerprint",fingerprint).put("input_hashes",JSONArray((listOf(cover)+infos.map { it.file }).map { EpisodeMedia.digest(it) })).put("lossless",mode==1).put("crop",crop).put("has_audio",infos.any { it.audioMime!=null })
                                    File(job,"spec.json").writeText(spec.toString(2))
                                    File(job,"status.json").writeText(JSONObject().put("state","queued").put("message","กำลังเริ่มรวม").toString())
                                    try { androidx.core.content.ContextCompat.startForegroundService(this,Intent(this,EpisodeRenderService::class.java).putExtra("job",job.path)); movieProgress(number) }
                                    catch(e: Exception) { File(job,"status.json").writeText(JSONObject().put("state","failed").put("message",e.message).toString()); notice("เริ่มรวมไม่ได้: ${e.message}") }
                                }
                                if(mode==1 && infos.any { it.width!=EpisodeRules.WIDTH || it.height!=EpisodeRules.HEIGHT || it.rotation!=0 || kotlin.math.abs(it.fps-infos.first().fps)>0.1f }) { notice("ขนาด/เฟรมเรตต่างกัน ต้องเลือกปรับไฟล์คุณภาพสูง"); return@choiceMenu }
                                val message=if(mode==0) "ปกและคลิปจะเข้ารหัสใหม่เป็น H.264 คุณภาพสูง ${bitrate/1_000_000} Mbps เสียง AAC รักษาเสียงเดิม ไม่มีดนตรีเพิ่ม คุณภาพอาจเปลี่ยนเล็กน้อย\nไฟล์ขนาดเล็กกว่าจะถูกขยาย แต่ไม่เพิ่มรายละเอียดจริง" else "สร้างปกเปิดก่อน แล้วตรวจ codec/config และเสียง ถ้าไม่ตรงจะหยุดและแจ้ง ไม่แปลงคลิปเอง"
                                AlertDialog.Builder(this).setTitle("ยืนยันวิธีรวม").setMessage(message).setPositiveButton("ดำเนินการ") { _,_ ->
                                    if(mode==0 && infos.any { it.width.toDouble()/it.height !in 1.76..1.79 || it.rotation!=0 }) choiceMenu("จัดภาพต่างสัดส่วน",listOf("เว้นขอบ • เห็นภาพครบ", "ครอปกึ่งกลาง • เต็มจอ อาจตัดขอบ")) { layout -> movieLayoutPreview(infos.first { it.width.toDouble()/it.height !in 1.76..1.79 || it.rotation!=0 }.file,layout==1) { start(layout==1) } }
                                    else start(false)
                                }.setNegativeButton("ยกเลิก",null).showDirector()
                            }
                        }.setNegativeButton("ยกเลิก",null).showDirector()
                }
            } catch(e: Exception) { runOnUiThread { dialog.dismiss(); notice("ตรวจคลิปไม่ผ่าน: ${e.message}") } }
        }.start()
    }
    private fun movieLayoutPreview(file: File, crop: Boolean, proceed: () -> Unit) {
        val reader=android.media.MediaMetadataRetriever()
        val bitmap=runCatching {
            reader.setDataSource(file.path)
            if(android.os.Build.VERSION.SDK_INT>=27) reader.getScaledFrameAtTime(0,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,960,540) else reader.getFrameAtTime(0)
        }.getOrNull()
        reader.release()
        val view=ImageView(this).apply {
            setImageBitmap(bitmap); setBackgroundColor(Color.BLACK)
            scaleType=if(crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            layoutParams=LinearLayout.LayoutParams(dp(288),dp(162))
        }
        val panel=LinearLayout(this).apply { orientation=1; gravity=Gravity.CENTER; addView(view) }
        val dialog=AlertDialog.Builder(this).setTitle(if(crop) "ตัวอย่างครอปกึ่งกลาง 9:16" else "ตัวอย่างเว้นขอบ 9:16").setView(panel)
            .setPositiveButton("ใช้รูปแบบนี้") { _,_ -> proceed() }.setNegativeButton("ยกเลิก",null).create()
        dialog.setOnDismissListener { view.setImageDrawable(null); bitmap?.recycle() }; dialog.show()
    }
    private fun movieProgress(ep: Int) {
        val job=movieJobs(ep).firstOrNull() ?: return notice("ยังไม่มีงานรวม EP นี้")
        val text=TextView(this).apply { setPadding(dp(16),dp(16),dp(16),dp(16)); textSize=16f }
        val dialog=AlertDialog.Builder(this).setTitle("รวมหนัง EP $ep").setView(text).setPositiveButton("ปิดหน้าต่าง",null).setNeutralButton("ยกเลิกงาน") { _,_ ->
            if(EpisodeRenderService.running) startService(Intent(this,EpisodeRenderService::class.java).setAction(EpisodeRenderService.CANCEL).putExtra("job",job.path))
        }.create()
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        var ticks=0
        val update=object : Runnable { override fun run() {
            if(!dialog.isShowing) return
            val status=readMovieState(job); val state=status.optString("state")
            val interrupted=state in listOf("preparing","rendering","queued") && !EpisodeRenderService.running && ++ticks>5
            text.text=if(interrupted) "งานถูกหยุดก่อนเสร็จ กรุณารวมใหม่ • ต้นฉบับยังอยู่" else "${status.optString("message")}\n${status.optInt("progress")}%\n${if(state=="complete") "กลับเมนู EP เพื่อดูหนังหรือดาวน์โหลด MP4" else "สลับแอปได้ งานยังทำต่อผ่านบริการเบื้องหน้า"}"
            handler.postDelayed(this,1000)
        } }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }; dialog.show(); handler.post(update)
    }
    private fun checkMovieChanges(job: File, proceed: () -> Unit) {
        val ep=JSONObject(File(job,"spec.json").readText()).getInt("ep")
        val old=JSONObject(File(job,"result.json").readText()).optString("fingerprint")
        val dialog=AlertDialog.Builder(this).setMessage("ตรวจว่าคลิปและปกเปลี่ยนหรือไม่…").setCancelable(false).showDirector()
        Thread { val changed=runCatching { movieFingerprint(ep)!=old }.getOrDefault(true); runOnUiThread {
            dialog.dismiss()
            if(changed) AlertDialog.Builder(this).setMessage("คลิปหรือปกเปลี่ยนหลังรวม หนังนี้เป็นเวอร์ชันเดิม ต้องรวมใหม่เพื่อใช้การแก้ล่าสุด")
                .setPositiveButton("ใช้เวอร์ชันเดิม") { _,_ -> proceed() }.setNegativeButton("ยกเลิก",null).showDirector() else proceed()
        } }.start()
    }
    private fun playMovie(job: File) {
        val file=File(job,"output.mp4"); val view=VideoView(this).apply { setVideoPath(file.path); val controls=MediaController(this@StudioActivity); controls.setAnchorView(this); setMediaController(controls); setOnPreparedListener { start() }; setOnErrorListener { _,_,_ -> notice("เล่นหนังไฟล์นี้ไม่ได้"); true } }
        val dialog=AlertDialog.Builder(this).setTitle("หนัง EP").setView(view).setPositiveButton("ปิด",null).create(); dialog.setOnDismissListener { view.stopPlayback() }; dialog.show(); dialog.window?.setLayout(-1,dp(560))
    }
    private val movieDownloadPicker=registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val path=prefs.getString("movie_download_job",null)
        if(uri!=null && path!=null) {
            val dialog=AlertDialog.Builder(this).setMessage("กำลังบันทึก MP4 ลงตำแหน่งที่เลือก…").setCancelable(false).showDirector()
            Thread {
                try {
                    val job=File(path); val file=File(job,"output.mp4"); require(file.isFile) { "ไม่พบ MP4" }
                    contentResolver.openOutputStream(uri,"wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
                    val size=contentResolver.openFileDescriptor(uri,"r")?.use { it.statSize }
                    require(size==null || size<0 || size==file.length()) { "บันทึกไฟล์ไม่ครบ" }
                    File(job,"downloaded.json").writeText(JSONObject().put("uri",uri.toString()).put("time",System.currentTimeMillis()).toString())
                    runOnUiThread { dialog.dismiss(); notice("ดาวน์โหลด MP4 สำเร็จ • ${displayName(uri)}") }
                } catch(e: Exception) { runOnUiThread { dialog.dismiss(); notice("ดาวน์โหลดไม่สำเร็จ: ${e.message} • ไฟล์ที่ตำแหน่งปลายทางอาจยังไม่ครบ กรุณาบันทึกใหม่") } }
            }.start()
        }
        prefs.edit().remove("movie_download_job").apply()
    }
    private fun downloadMovie(job: File) = checkMovieChanges(job) {
        val filename=JSONObject(File(job,"result.json").readText()).getString("filename")
        prefs.edit().putString("movie_download_job",job.path).apply(); movieDownloadPicker.launch(filename)
    }
    private fun shareMovie(job: File) = checkMovieChanges(job) {
        val file=File(job,"output.mp4")
        val name=JSONObject(File(job,"result.json").readText()).getString("filename")
        val export=File(cacheDir,"movie_exports/${UUID.randomUUID()}/$name").apply { parentFile!!.mkdirs() }
        background { file.copyTo(export); runOnUiThread {
            val uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",export)
            val intent=Intent(Intent.ACTION_SEND).apply { type="video/mp4"; putExtra(Intent.EXTRA_STREAM,uri); clipData=ClipData.newUri(contentResolver,"หนัง EP",uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching { startActivity(Intent.createChooser(intent,"แชร์หนัง EP")) }.onFailure { notice("เปิดแชร์ไม่ได้: ${it.message}") }
        } }
    }

    private fun review() { episodesMenu() }
    private fun export(uri: Uri) {
        val p = plan!!
        contentResolver.openOutputStream(uri)!!.use { output -> ZipOutputStream(output).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            val delivered = JSONObject(p.toString())
            if(exportEpisode != null) {
                val selected = JSONArray(); val all = p.getJSONArray("episodes")
                for(i in 0 until all.length()) if(all.getJSONObject(i).getInt("ep")==exportEpisode) selected.put(all.getJSONObject(i))
                delivered.put("export_scope","episode").put("source_episode",exportEpisode).put("episodes",selected).put("episode_count",selected.length()).put("scene_count",rows(delivered).size)
            }
            put("story.json",delivered.toString(2).toByteArray())
            put("MISSING.txt", rows(p).filter { exportEpisode == null || it.getInt("ep") == exportEpisode }.joinToString("\n") { "${key(it)}: ภาพ=${image(it).exists()} วิดีโอ=${video(it).exists()}" }.toByteArray())
            put("README.txt","${p.getString("title")}\n${p.getString("category")}\nนำภาพฉากและคำสั่งใน Flow/EPxx_FLOW.txt ไปใช้ใน Flow • 8 วินาที • 9:16".toByteArray())
            for (name in coverNames(p)) {
                val file = coverFile(name)
                if (!file.exists()) continue
                if (exportEpisode != null && StoryFiles.sceneNumber(name + "_SC001")?.first != exportEpisode && !name.startsWith("EP%02d_".format(exportEpisode))) continue
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val ext = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                put("covers/$name.$ext",file.readBytes())
            }
            val episodes = p.getJSONArray("episodes")
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                val number = ep.getInt("ep")
                if (exportEpisode != null && number != exportEpisode) continue
                val scenes = rows(p).filter { it.getInt("ep") == number }
                val commands = StoryFiles.episodeFlow(number, ep.optString("title"), scenes.map {
                    Triple(key(it), "${it.getString("action")}\n${it.optString("dialogue")}", flowCommand(it))
                })
                put("Flow/EP%02d_FLOW.txt".format(number), commands.replace("8 วินาทีต่อซีน • 9:16", "${p.optInt("duration_seconds",8)} วินาทีต่อซีน • ${p.optString("aspect_ratio","9:16")}").toByteArray())
            }
            for (s in rows(p)) {
                if (exportEpisode != null && s.getInt("ep") != exportEpisode) continue
                val path = "EP%02d/%s".format(s.getInt("ep"),key(s))
                val file = image(s)
                val video = video(s)
                if (video.exists()) { zip.putNextEntry(ZipEntry("videos/${key(s)}.mp4")); video.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                if (!file.exists()) continue
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path,opts)
                val extension = when(opts.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                zip.putNextEntry(ZipEntry("$path.$extension")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()

            }
        } }
    }
}


