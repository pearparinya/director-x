Warning: truncated output (original token count: 72137)
Total output lines: 2795

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
        plan = File(project, "story.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) }
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
        val scroll=ScrollView(this).apply { isVerticalScrollBarEna…32137 tokens truncated…).setPositiveButton("แทนภาพ") { _,_ -> save(s) }.setNegativeButton("ข้าม") { _,_ -> skippedCount++; next() }.setCancelable(false).showDirector()
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


