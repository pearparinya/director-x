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
internal fun buildGeminiPackageIntent(text: String, command: Uri, images: ArrayList<Uri>, imagesFirst: Boolean = false): Intent {
    require(images.isNotEmpty() && images.size<=9) { "ต้องมี MASTER 1–9 ภาพพร้อมไฟล์คำสั่ง" }
    val files=if(imagesFirst) ArrayList(images).apply { add(command) } else arrayListOf(command).apply { addAll(images) }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type="*/*"
        putExtra(Intent.EXTRA_TEXT,text)
        putExtra(Intent.EXTRA_SUBJECT,"สร้างภาพตามไฟล์ READ_FIRST_IMAGE_COMMAND.txt พร้อม MASTER ที่แนบ")
        putParcelableArrayListExtra(Intent.EXTRA_STREAM,files)
        clipData=ClipData.newPlainText("คำสั่งและ MASTER",text).apply { files.forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal const val GEMINI_IMAGE_REQUEST = "สร้างและแสดงภาพจริง 1 ภาพตามรายละเอียดต่อไปนี้ ใช้ภาพตัวละครที่แนบตามลำดับ จับคู่ภาพแรกกับตัวละครแรก โดยไม่อิงชื่อไฟล์ รักษาข้อความบนปกตามคำสั่ง หากเป็นภาพฉากให้ทำตามข้อกำหนดของฉาก ไม่ต้องสรุปคำสั่ง ไม่ต้องเขียนโค้ด XML หากสร้างภาพไม่ได้ให้แจ้งสาเหตุ"

internal fun geminiImageMessage(command: String, twoCovers: Boolean = false): String = (if(twoCovers) GEMINI_IMAGE_REQUEST.replace("1 ภาพ","2 ภาพแยกไฟล์") else GEMINI_IMAGE_REQUEST)+"\n\n"+command

internal fun imageProviderOptions(preferred: String): List<String> = listOf("ChatGPT", "Gemini").let { options ->
    if (preferred in options) listOf(preferred) + options.filter { it != preferred } else options
}

internal fun buildGeminiImageShareIntent(text: String, images: ArrayList<Uri>, imagesFirst: Boolean = false): Intent {
    require(images.isNotEmpty() && images.size<=9) { "ต้องมีภาพอ้างอิง 1–9 ภาพ" }
    return Intent(if(images.size==1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type="image/*"
        putExtra(Intent.EXTRA_TEXT,text)
        if(images.size==1) putExtra(Intent.EXTRA_STREAM,images[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,images)
        clipData=if(imagesFirst) ClipData("ภาพอ้างอิงและคำสั่ง", arrayOf("image/*", "text/plain"), ClipData.Item(images[0])).apply { images.drop(1).forEach { addItem(ClipData.Item(it)) }; addItem(ClipData.Item(text)) } else ClipData("คำสั่งและภาพอ้างอิง", arrayOf("image/*", "text/plain"), ClipData.Item(text, null, null, images[0])).apply { images.drop(1).forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal fun neutralIdentityGuide(source: String, aspect: String): String =
    ImagePromptPolicy.identity(source) + "\n" + ImagePromptPolicy.style(aspect) + " ไม่มีข้อความหรือลายน้ำ หากสร้างไม่ได้ให้แจ้งตามจริง"

/** Whole-story workspace. Legacy preferences and character masters stay untouched. */
class StudioActivity : AppCompatActivity() {
    private var wardrobePreviewResult: ((Uri) -> Unit)?=null
    private val wardrobePreviewPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback=wardrobePreviewResult
        wardrobePreviewResult=null
        if(uri!=null) callback?.invoke(uri)
    }

    private val storyUiHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var geminiStatusView: TextView?=null
    private var geminiProgressView: ProgressBar?=null
    private var geminiLiveDialog: TextView?=null
    private val storyPoll=object: Runnable { override fun run() {
        if(!isFinishing) {
            val status=readGeminiStatus()
            geminiStatusView?.text=geminiStatusText(status)
            geminiProgressView?.progress=status?.optInt("percent")?.coerceIn(0,100) ?: 0
            geminiLiveDialog?.text=geminiStatusText(status,true)+if(status?.optString("state")=="running" && GeminiStoryService.running) "\n\nระบบจะทำต่อเองระหว่างรอ • กดหยุดเมื่อต้องการพักงาน" else ""
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
    private var activeProductionDialog: AlertDialog? = null
    private var pendingStoryShare: (() -> Unit)? = null
    private var pendingStorySummary = ""
    private var pendingReceipt: (() -> Unit)? = null
    private var exportEpisode: Int?
        get() = prefs.getInt("export_episode",0).takeIf { it>0 }
        set(value) { prefs.edit().putInt("export_episode",value ?: 0).apply() }
    private var backupMode: Boolean
        get() = prefs.getBoolean("backup_mode",false)
        set(value) { prefs.edit().putBoolean("backup_mode",value).apply() }
    private lateinit var updater: StudioUpdater
    private lateinit var root: LinearLayout
    private val navy = Color.rgb(12,12,15)
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
            val start=title.indexOf("สถานะ:")
            val decorated=android.text.SpannableString(title)
            if(start>=0) decorated.setSpan(
                android.text.style.ForegroundColorSpan(if(title.contains("สถานะ: ยืนยันชุดในบทแล้ว")) Color.rgb(129,225,167) else Color.rgb(255,196,104)),
                start,title.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = decorated; textSize = 20f; setTextColor(gold); setTypeface(null,Typeface.BOLD)
            setPadding(0,0,0,dp(14))
        })
        if(choices.size>1) panel.addView(TextView(this).apply {
            text = "เลือกหนึ่งรายการ • หมายเลขใช้ระบุตัวเลือก"
            textSize = 12f; setTextColor(Color.rgb(187,200,221)); setPadding(0,0,0,dp(10))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        panel.addView(scroll,LinearLayout.LayoutParams(-1,-2))
        val dialog = AlertDialog.Builder(this).setView(panel).setCancelable(cancelable).create()
        choices.forEachIndexed { index, value ->
            val palette=when(MenuChoiceStyle.role(value)) {
                MenuChoiceStyle.AI -> intArrayOf(Color.rgb(70,41,112),Color.rgb(40,27,74),Color.rgb(176,137,225))
                MenuChoiceStyle.EDIT -> intArrayOf(Color.rgb(98,67,35),Color.rgb(61,42,31),Color.rgb(222,174,94))
                else -> intArrayOf(Color.rgb(25,66,103),Color.rgb(18,40,72),Color.rgb(91,175,220))
            }
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
                minimumHeight=dp(68); setPadding(dp(12),dp(10),dp(12),dp(10))
                background=backdrop(palette[0],palette[1],palette[2])
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
                isClickable=true; isFocusable=true
                contentDescription="ตัวเลือก ${index+1}: $value"
                val attr=android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground,attr,true)
                foreground=getDrawable(attr.resourceId)
                setOnClickListener { dialog.dismiss(); action(index) }
            }
            row.addView(TextView(this).apply {
                text=(index+1).toString(); textSize=18f; setTextColor(Color.WHITE)
                setTypeface(null,Typeface.BOLD); gravity=Gravity.CENTER
                background=backdrop(palette[2],palette[0])
                importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(dp(38),dp(38)).apply { marginEnd=dp(12) })
            if((title=="ตั้งค่าคีย์ API • ใช้อัตโนมัติ" && index<StoryProvider.activeProviders.size) || title=="หมวดช่อง • นายเปีย บ้านคลองขุด") {
                val parts=value.split("\n", limit=2)
                val labels=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                labels.addView(TextView(this).apply { text=parts[0]; textSize=if(title.startsWith("หมวดช่อง")) 14f else 16f; setTextColor(Color.WHITE); setTypeface(null,Typeface.BOLD); maxLines=1
                    if(title.startsWith("หมวดช่อง")) androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,11,14,1,android.util.TypedValue.COMPLEX_UNIT_SP) })
                labels.addView(TextView(this).apply {
                    text=parts.getOrElse(1) { "" }; setTextColor(Color.WHITE); maxLines=1
                    androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,8,12,1,android.util.TypedValue.COMPLEX_UNIT_SP)
                },LinearLayout.LayoutParams(-1,-2))
                row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
            } else row.addView(TextView(this).apply {
                text=value; textSize=16f; setTextColor(Color.WHITE); setTypeface(null,Typeface.BOLD)
                gravity=Gravity.CENTER_VERTICAL
                importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(0,-2,1f))
            list.addView(row)
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
    private val categories = ChannelCatalog.allCategories.map { it.label }
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
    // Body references are not actor identities. New actors are registered only after image review.
    private val bodyTemplateFiles=listOf("body_01.png")
    private val actorCatalogFile get() = File(filesDir,"actor_catalog_v2.json")
    private fun actorCatalog(): JSONArray = if(!actorCatalogFile.exists()) JSONArray() else JSONObject(actorCatalogFile.readText()).getJSONArray("records")
    private fun fantasyCatalog(): JSONArray = JSONObject(assets.open("fantasy_masters/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
    // Include the bundled legacy references and user-created actors without changing either catalog.
    private fun castingCatalog(): JSONArray {
        val legacy=JSONObject(assets.open("master_library/catalog.json").bufferedReader().use { it.readText() }).getJSONArray("records")
        val custom=actorCatalog()
        val result=JSONArray()
        val ids=mutableSetOf<String>()
        for (rows in listOf(legacy,custom,fantasyCatalog())) for (i in 0 until rows.length()) {
            val row=JSONObject(rows.getJSONObject(i).toString())
            require(ids.add(row.getString("id"))) { "รหัส MASTER ซ้ำในคลัง: ${row.getString("id")}" }
            result.put(row)
        }
        return result
    }
    private val piaActorId = "CHAR_1999999999"
    private val piaIdentityFile get() = File(filesDir,"pia_identity_master.image")
    private val piaIdentityPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) runCatching {
            val bytes=contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("เปิดภาพไม่ได้")
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0) { "ไฟล์ไม่ใช่ภาพที่อ่านได้" }
            val pending=File(filesDir,"pia_identity_master.pending")
            try { pending.writeBytes(bytes); check(pending.renameTo(piaIdentityFile)) { "บันทึก MASTER ไม่สำเร็จ" } } finally { pending.delete() }
            notice("บันทึก MASTER นายเปียแล้ว • เปิดหน้าสร้างเรื่องใหม่เพื่อตรวจภาพและเลือกกรอบใบหน้าอีกครั้ง")
        }.onFailure { notice("บันทึก MASTER ไม่สำเร็จ: ${it.message}") }
    }
    private fun piaCatalog(): JSONArray {
        val migrationPrefs=getSharedPreferences("pia_master_updates", MODE_PRIVATE)
        if(!migrationPrefs.getBoolean("identity_431",false)) {
            val bytes=assets.open("pia_identity_431.png").use { it.readBytes() }
            PiaMasterUpdate.install(piaIdentityFile,bytes)
            check(migrationPrefs.edit().putBoolean("identity_431",true).commit()) { "บันทึกสถานะ MASTER ไม่สำเร็จ" }
        }
        val catalog=castingCatalog()
        require((0 until catalog.length()).none { catalog.getJSONObject(it).optString("id")==piaActorId }) { "รหัสตัวละครนายเปียซ้ำ" }
        val bytes=if(piaIdentityFile.isFile) piaIdentityFile.readBytes() else assets.open("pia_outfits/01.png").use { it.readBytes() }
        val row=JSONObject().put("id",piaActorId).put("display_name","นายเปีย บ้านคลองขุด")
            .put("file","PIA_PRESENTER.png").put("sha256",MasterLibrary.digest(bytes))
            .put("character_age",35).put("age_basis","fictional_casting_placeholder_not_real_age")
            .put("character_gender","man").put("casting_description","Use the attached original MASTER for this presenter identity.")
        val result=JSONArray().put(row)
        for(i in 0 until catalog.length()) result.put(catalog.getJSONObject(i))
        return result
    }
    private fun installPiaDefaults() {
        for(slot in 1..13) {
            val target=presenterOutfitFile(piaActorId,slot)
            if(!target.exists()) {
                target.parentFile!!.mkdirs()
                val tmp=File(target.parentFile,"$slot.default.pending")
                try {
                    assets.open(if(slot==13) "pia_outfits/13.webp" else "pia_outfits/%02d.png".format(slot)).use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
                    check(tmp.renameTo(target)) { "ติดตั้งภาพชุดนายเปียไม่สำเร็จ" }
                } finally { tmp.delete() }
            }
        }
    }
    private fun castingSource(row: JSONObject): File {
        if(row.optString("source_type")=="fantasy_bundle") {
            val filename=row.getString("file")
            require(filename.matches(Regex("FANTASY_[0-9]{2}[.]png"))) { "ชื่อไฟล์ MASTER ไม่ถูกต้อง" }
            val bytes=assets.open("fantasy_masters/$filename").use { it.readBytes() }
            require(MasterLibrary.digest(bytes)==row.getString("sha256")) { "ภาพ MASTER ไม่ตรงกับทะเบียน" }
            val target=File(filesDir,"fantasy_masters/$filename")
            if(!target.isFile || MasterLibrary.digest(target.readBytes())!=row.getString("sha256")) {
                target.parentFile!!.mkdirs()
                val temp=File(target.parentFile,target.name+".pending")
                try { temp.writeBytes(bytes); check(temp.renameTo(target)) { "บันทึก MASTER ไม่สำเร็จ" } } finally { temp.delete() }
            }
            return target
        }
        if(row.getString("id")==piaActorId) {
            if(piaIdentityFile.isFile) {
                require(MasterLibrary.digest(piaIdentityFile.readBytes())==row.getString("sha256")) { "MASTER นายเปียเปลี่ยนระหว่างเลือก กรุณาเปิดหน้าสร้างเรื่องใหม่" }
                return piaIdentityFile
            }
            val bytes=assets.open("pia_outfits/01.png").use { it.readBytes() }
            val target=File(filesDir,"pia_presenter_master.png")
            if(!target.isFile || MasterLibrary.digest(target.readBytes())!=MasterLibrary.digest(bytes)) target.writeBytes(bytes)
            return target
        }
        val entry=masterLibrary.entries().firstOrNull { it.id==row.getString("id") }
        return if(entry!=null) masterLibrary.source(entry) else File(filesDir,"actor_master_v2/"+row.getString("file"))
    }
    private fun bodyTemplateFile(index: Int): File {
        val target=File(cacheDir,"body_template_sources/${bodyTemplateFiles[index]}")
        target.parentFile!!.mkdirs()
        assets.open("body_templates/"+bodyTemplateFiles[index]).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }
    private val actorImagePicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending=prefs.getString("new_actor_pending",null)
        if(uri!=null && pending!=null) runCatching {
            val person=JSONObject(pending)
            val input=contentResolver.openInputStream(uri) ?: error("เปิดภาพไม่ได้")
            val bytes=input.use { it.readBytes() }
            val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0) { "ไฟล์ไม่ใช่ภาพ" }
            val extension=when(bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/png" -> "png"; else -> error("รองรับภาพ PNG, JPG หรือ WebP") }
            val records=actorCatalog()
            require((0 until records.length()).none { records.getJSONObject(it).getString("display_name")==person.getString("display_name") }) { "ชื่อนี้มีในคลังแล้ว" }
            val fileName=person.getString("id") + "." + extension
            val dir=File(filesDir,"actor_master_v2").apply { mkdirs() }
            val target=File(dir,fileName)
            require(!target.exists()) { "รหัสตัวละครซ้ำ กรุณาเริ่มใหม่" }
            val temp=File(dir,fileName+".pending")
            try {
                temp.writeBytes(bytes)
                person.put("file",fileName).put("original_filename",fileName).put("sha256",MasterLibrary.digest(bytes)).put("age_basis","user_assigned")
                records.put(person)
                check(temp.renameTo(target)) { "บันทึกภาพไม่ได้" }
                try {
                    val catalogTemp=File(filesDir,"actor_catalog_v2.pending")
                    catalogTemp.writeText(JSONObject().put("records",records).toString(2))
                    check(catalogTemp.renameTo(actorCatalogFile)) { "บันทึกทะเบียนไม่ได้" }
                } catch(e: Exception) { target.delete(); throw e }
            } finally { temp.delete() }
            prefs.edit().remove("new_actor_pending").apply()
        }.onSuccess { notice("บันทึก MASTER ตัวละครใหม่แล้ว • ตรวจภาพและข้อมูลในคลังได้") }.onFailure { notice(it.message ?: "บันทึกตัวละครไม่ได้") }
    }
    // One private outfit image per person and outfit slot. Existing stories keep their own snapshot.
    private fun presenterOutfitFile(actorId: String, slot: Int): File {
        require(actorId.matches(Regex("[A-Za-z0-9_]+")) && slot in 1..13)
        return File(filesDir,"presenter_outfits/$actorId/$slot.image")
    }
    private val presenterOutfitPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val actor=prefs.getString("presenter_outfit_actor", "").orEmpty()
        val slot=prefs.getInt("presenter_outfit_slot",0)
        if(uri!=null) runCatching {
            val input=contentResolver.openInputStream(uri) ?: error("เปิดภาพไม่ได้")
            val bytes=input.use { it.readBytes() }
            require(bytes.size in 1..20_000_000) { "ภาพต้องมีขนาดไม่เกิน 20 MB" }
            val info=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,info)
            require(info.outWidth>0 && info.outHeight>0 && info.outMimeType in listOf("image/jpeg","image/png","image/webp")) { "กรุณาเลือกภาพ PNG, JPG หรือ WebP" }
            val target=presenterOutfitFile(actor,slot); target.parentFile!!.mkdirs()
            val tmp=File(target.parentFile,"$slot.pending")
            try { tmp.writeBytes(bytes); check(tmp.renameTo(target)) { "บันทึกภาพชุดไม่ได้" } } finally { tmp.delete() }
        }.onSuccess { notice("บันทึกภาพชุดกับตัวละครแล้ว • เลือกชุดนี้ตอนสร้างเรื่อง") }.onFailure { notice(it.message ?: "บันทึกภาพไม่ได้") }
        prefs.edit().remove("presenter_outfit_actor").remove("presenter_outfit_slot").apply()
    }
    private fun bodyTemplateMenu() {
        choiceMenu("แม่แบบรูปร่าง",listOf("แม่แบบ 1 • ผู้ชาย")) { index ->
            val file=runCatching { bodyTemplateFile(index) }.getOrElse { notice("เปิดแม่แบบไม่ได้"); return@choiceMenu }
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(240)) })
            val name=EditText(this).apply { hint="ชื่อนักแสดงใหม่ (ชื่อถาวร)"; panel.addView(this) }
            val age=EditText(this).apply { hint="อายุสมมติ 0–1,000 ปี"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; panel.addView(this) }
            val face=EditText(this).apply { hint="ลักษณะใบหน้าและทรงผมใหม่ (ไทยหรืออังกฤษ)"; panel.addView(this) }
            val styling=EditText(this).apply { hint="สีผิวและเสื้อผ้า (ไทยหรืออังกฤษ)"; panel.addView(this) }
            panel.addView(TextView(this).apply { text="1. ส่งคำสั่งพร้อมแม่แบบไปสร้างใน ChatGPT หรือ Gemini\n2. ตรวจภาพจริง แล้วกลับมาเลือก นำเข้าภาพเพื่อบันทึก" })
            val dialog=AlertDialog.Builder(this).setTitle("สร้างตัวละครจากแม่แบบ ${index+1}").setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("ส่งคำสั่งสร้างภาพ",null).setNeutralButton("นำเข้าภาพที่ตรวจแล้ว",null).setNegativeButton("ปิด",null).showDirector()
            fun profile(): JSONObject? {
                val n=name.text.toString().trim(); val years=age.text.toString().toIntOrNull(); val appearance=face.text.toString().trim()
                if(n.isEmpty() || years==null || !CharacterAge.valid(years) || appearance.isEmpty()) { notice("กรอกชื่อ อายุ 0–1,000 ปี และลักษณะใบหน้า/ทรงผม"); return null }
                if((0 until actorCatalog().length()).any { actorCatalog().getJSONObject(it).optString("display_name")==n }) { notice("ชื่อนี้มีในคลังแล้ว"); return null }
                val id="CHAR_"+(System.currentTimeMillis()%2000000000L).toString()
                return JSONObject().put("id",id).put("display_name",n).put("character_age",years).put("character_gender",if(index==0) "man" else "woman").put("casting_description",appearance).put("body_template",index+1)
            }
            dialog.getButton(-1).setOnClickListener {
                val p=profile() ?: return@setOnClickListener
                val gender=p.getString("character_gender")
                val appearance=p.getString("casting_description")
                val style=styling.text.toString().trim().ifBlank { "natural skin tone and elegant opaque clothing" }
                val command=ImagePromptPolicy.threeViewCharacter("a fictional $gender character with assigned age ${p.getInt("character_age")} years, age-appropriate nonsexual portrayal and outfit; for supernatural age preserve the reference appearance, new face and hairstyle: $appearance, $style, body silhouette and proportions based on the attached reference image")
                shareImageCommand(command,listOf("BODY_TEMPLATE_${index+1}" to file),bodyOnly=true)
            }
            dialog.getButton(-3).setOnClickListener {
                val p=profile() ?: return@setOnClickListener
                prefs.edit().putString("new_actor_pending",p.toString()).apply()
                actorImagePicker.launch(arrayOf("image/*"))
            }
        }
    }
    private fun importCharacterMaster() {
        val labels=listOf("มนุษย์", "เด็ก", "เทพ / ผู้ใช้พลัง", "อัศวิน", "สัตว์", "อสูร", "อื่น ๆ", "ปีศาจ", "ผี", "วิญญาณ", "ภูต")
        val types=listOf("human", "child", "deity", "knight", "animal", "creature", "other", "demon", "ghost", "spirit", "fairy")
        choiceMenu("เลือกประเภทตัวละคร", labels) { index ->
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            val name=EditText(this).apply { hint="ชื่อตัวละคร"; panel.addView(this) }
            val age=EditText(this).apply { hint="อายุสมมติ 0–1,000 ปี (0 = ต่ำกว่า 1 ปี)"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; panel.addView(this) }
            val description=EditText(this).apply { hint="ลักษณะหรือรายละเอียดเพิ่มเติม (ไม่บังคับ)"; panel.addView(this) }
            val gender=android.widget.Spinner(this).apply {
                adapter=android.widget.ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("ไม่ระบุเพศ", "ชาย", "หญิง"))
                panel.addView(this)
            }
            panel.addView(TextView(this).apply { text="เลือกภาพ MASTER ต้นฉบับ 1 ภาพที่มี 3 มุม แอปบันทึกภาพโดยไม่สร้างใบหน้าใหม่" })
            val dialog=AlertDialog.Builder(this).setTitle("นำเข้า MASTER • ${labels[index]}")
                .setView(panel).setPositiveButton("เลือกภาพ",null).setNegativeButton("ยกเลิก",null).showDirector()
            dialog.getButton(-1).setOnClickListener {
                val n=name.text.toString().trim(); val years=age.text.toString().toIntOrNull()
                if(n.isEmpty() || years==null || !CharacterAge.valid(years)) { notice("กรอกชื่อและอายุ 0–1,000 ปี"); return@setOnClickListener }
                if(types[index] in listOf("human", "child", "deity", "knight") && gender.selectedItemPosition==0) { notice("กรุณาเลือกเพศตัวละคร เพื่อกำหนดผู้พูดให้ถูกต้อง"); return@setOnClickListener }
                if((0 until actorCatalog().length()).any { actorCatalog().getJSONObject(it).optString("display_name")==n }) { notice("ชื่อนี้มีในคลังแล้ว"); return@setOnClickListener }
                val id="CHAR_"+(System.currentTimeMillis()%2000000000L).toString()
                val p=JSONObject().put("id",id).put("display_name",n).put("character_age",years)
                    .put("character_type",types[index]).put("casting_description",description.text.toString().trim())
                if(gender.selectedItemPosition>0) p.put("character_gender",if(gender.selectedItemPosition==1) "man" else "woman")
                prefs.edit().putString("new_actor_pending",p.toString()).apply()
                dialog.dismiss()
                actorImagePicker.launch(arrayOf("image/*"))
            }
        }
    }
    private fun actorCatalogMenu() {
        runCatching {
            val records=actorCatalog()
            val channel=ChannelCatalog.currentChannel(plan?.optString("channel").orEmpty())
            val fantasy=ChannelFeaturePolicy.hasFantasyTools(channel)
            val pia=channel=="นายเปีย บ้านคลองขุด"
            val visible=(0 until records.length()).filter { i -> !pia || records.getJSONObject(i).optString("character_type") !in setOf("knight","deity","creature","demon","ghost","spirit","fairy") }
            val base=if(fantasy) 4 else 3
            val menus=listOf("📥 นำเข้า MASTER ที่มีอยู่","➕ สร้างตัวละครจากแม่แบบ","📚 ดู MASTER คลังเดิม") + if(fantasy) listOf("✨ ดู MASTER แฟนตาซีและภูตผี") else emptyList()
            chooseNamedCharacterGrid("คลังตัวละคร • ${channel.ifBlank { "ช่องไม่ระบุ" }}",menus.map { it to null }+visible.map { i -> records.getJSONObject(i).let { ("${it.getString("display_name")} • อายุ ${it.getInt("character_age")} ปี") to File(filesDir,"actor_master_v2/"+it.getString("file")) } }) { index ->
                if(index==0) importCharacterMaster() else if(index==1) bodyTemplateMenu() else if(index==2) libraryMenu() else if(fantasy && index==3) {
                    val fantasy=fantasyCatalog()
                    chooseNamedCharacterGrid("MASTER แฟนตาซี",(0 until fantasy.length()).map { i -> val entry=fantasy.getJSONObject(i); entry.getString("display_name") to runCatching { castingSource(entry) }.getOrNull() }) { selected ->
                        val row=fantasy.getJSONObject(selected)
                        runCatching { fullImage(castingSource(row),row.getString("display_name")) }.onFailure { notice(it.message ?: "เปิดภาพไม่ได้") }
                    }
                } else {
                    val actualIndex=visible[index-base]
                    val row=records.getJSONObject(actualIndex)
                    val file=File(filesDir,"actor_master_v2/"+row.getString("file"))
                    choiceMenu(row.getString("display_name"),listOf("ดู MASTER","กำหนดประเภทและเพศ","นำออกจากคลังเรื่องใหม่")) { action ->
                        if(action==0) { if(file.isFile) fullImage(file,row.getString("display_name")) else notice("ไม่พบภาพ MASTER") }
                        else if(action==1) {
                            val types=listOf("human", "animal", "creature", "demon", "ghost", "spirit", "fairy")
                            choiceMenu("เลือกประเภทตัวละคร",listOf("มนุษย์", "สัตว์", "อสูร", "ปีศาจ", "ผี", "วิญญาณ", "ภูต")) { selected ->
                                fun save(gender: String) {
                                    row.put("character_type",types[selected]).put("character_gender",gender)
                                    val temp=File(filesDir,"actor_catalog_v2.pending")
                                    temp.writeText(JSONObject().put("records",records).toString(2))
                                    if(temp.renameTo(actorCatalogFile)) notice("บันทึกประเภทและเพศแล้ว • ใช้กับเรื่องใหม่") else notice("บันทึกคลังไม่ได้")
                                }
                                if(selected==0) choiceMenu("เลือกเพศตัวละคร",listOf("ชาย", "หญิง")) { g -> save(if(g==0) "man" else "woman") } else save("")
                            }
                        }
                        else {
                            val rest=JSONArray(); for(i in 0 until records.length()) if(i!=actualIndex) rest.put(records.getJSONObject(i))
                            val temp=File(filesDir,"actor_catalog_v2.pending")
                            temp.writeText(JSONObject().put("records",rest).toString(2))
                            if(temp.renameTo(actorCatalogFile)) notice("นำออกจากคลังแล้ว • เรื่องที่สร้างไว้ยังใช้ภาพเดิม") else notice("บันทึกคลังไม่ได้")
                        }
                    }
                }
            }
        }.onFailure { notice("อ่านคลังไม่ได้: ${it.message}") }
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
                var row: LinearLayout?=null
                entries.filter { (it.id+" "+it.displayName+" "+it.original).contains(query,ignoreCase=true) }.forEachIndexed { index, entry ->
                    if(index%2==0) { row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; list.addView(row) }
                    val file=runCatching { masterLibrary.source(entry) }.getOrNull()
                    val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)); layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
                    row?.addView(card)
                    val label="${entry.displayName}\n${entry.id}"+(if(file?.isFile==true) "" else "\nไม่พบ MASTER")
                    if(file?.isFile==true) card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(file)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(140)); contentDescription=label; setOnClickListener { libraryEntry(entry) } })
                    card.addView(Button(this).apply { text=label; setOnClickListener { libraryEntry(entry) } })
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
    private fun shortCatalogLabel(legacy: String): String? = runCatching {
        val file=File(filesDir,"short_catalog_ids.json")
        if(file.isFile) JSONObject(file.readText()).optString(legacy).takeIf { it.isNotBlank() } else null
    }.getOrNull()
    private fun libraryEntry(entry: MasterLibrary.Entry) {
        runCatching {
            val file=masterLibrary.source(entry)
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
            panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(400)); setOnClickListener { fullImage(file,entry.id) } })
            panel.addView(TextView(this).apply { text="${shortCatalogLabel(entry.id) ?: entry.id} • ${entry.displayName} • อายุสมมติ ${entry.characterAge} ปี\nภาพต้นฉบับ 3 มุมมอง แตะภาพเพื่อขยาย" })
            AlertDialog.Builder(this).setTitle("${shortCatalogLabel(entry.id) ?: entry.id} • ${entry.displayName}").setView(ScrollView(this).apply { addView(panel) })
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
                    .put("library_id",entry.id).put("character_gender",entry.gender).put("character_type",entry.characterType).put("visual_description",MasterLibrary.description(entry.id))
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

    private fun characterMasters() { unifiedMasterMenu() }

    private fun presetMaster(id: String,name: String) {
        val file=File(filesDir,"character_masters/$id.master")
        choiceMenu(name,listOf("ดู MASTER","เปลี่ยนภาพ MASTER","ข้อมูลใบหน้า ผม และรูปร่าง","เตรียม MASTER มาตรฐาน","ตรวจภาพ MASTER")) { action -> when(action) {
            0 -> if(file.exists()) fullImage(file,name) else notice("ยังไม่มี MASTER")
            1 -> { masterSlot=id; masterPicker.launch(arrayOf("image/*")) }
            2 -> editCharacterMemory(id,name,file)
            3 -> sendProductionMaster(id,name,file)
            4 -> reviewMaster(id,name,file)
        } }
    }
    private data class UnifiedMasterItem(val name: String,val id: String,val category: String,val file: File?,val open: ()->Unit)
    private fun unifiedMasterMenu() {
        runCatching {
            val all=mutableListOf<UnifiedMasterItem>()
            listOf("rinlada" to "รินรดา","mind" to "เมย์").forEach { (id,name) ->
                val file=File(filesDir,"character_masters/$id.master")
                all.add(UnifiedMasterItem(name,id.uppercase()+"_MASTER","มนุษย์",file,{ presetMaster(id,name) }))
            }
            val records=piaCatalog()
            val oldEntries=masterLibrary.entries().associateBy { it.id }
            for(i in 0 until records.length()) {
                val item=records.getJSONObject(i)
                val id=item.getString("id")
                val name=item.optString("display_name",id)
                val type=item.optString("character_type").lowercase()
                val category=when(type) {
                    "human" -> "มนุษย์"
                    "animal" -> "สัตว์"
                    "creature","demon" -> "อสูรและสัตว์ประหลาด"
                    "deity","knight" -> "เทพและผู้พิทักษ์"
                    "ghost","spirit","fairy" -> "ผี วิญญาณ และภูต"
                    else -> if(item.optString("character_gender").isNotBlank() && type.isBlank()) "มนุษย์" else "ยังไม่ระบุประเภท"
                }
                val file=runCatching { castingSource(item) }.getOrNull()
                val old=oldEntries[id]
                all.add(UnifiedMasterItem(name,id,category,file,{
                    if(old!=null) libraryEntry(old)
                    else if(file?.isFile==true) fullImage(file,name+" • "+id)
                    else notice("ไม่พบภาพ MASTER ของ $name • $id")
                }))
            }
            installPiaDefaults()
            val blackReference=presenterOutfitFile(piaActorId,13)
            all.add(UnifiedMasterItem("นายเปีย • ชุดดำ 3 มุมมอง", "PIA_BLACK_OUTFIT", "มนุษย์", blackReference, {
                if(blackReference.isFile) fullImage(blackReference,"นายเปีย • ชุดดำ 3 มุมมอง") else notice("ไม่พบภาพอ้างอิงชุดดำ")
            }))
            propCatalog.forEachIndexed { index,name ->
                val id=propId(index)
                val file=runCatching {
                    val target=File(cacheDir,"prop_masters/$id.png")
                    if(!target.isFile) { target.parentFile!!.mkdirs(); assets.open("prop_masters/$id.png").use { input -> target.outputStream().use { input.copyTo(it) } } }
                    target
                }.getOrNull()
                all.add(UnifiedMasterItem(name,id,"อาวุธและอุปกรณ์",file,{
                    if(file?.isFile==true) fullImage(file,name+" • "+id) else notice("ไม่พบภาพ MASTER ของ $name • $id")
                }))
            }
            // The black outfit is another view of the presenter, not another person.
            fun legacyKey(item: UnifiedMasterItem) = if(item.id=="PIA_BLACK_OUTFIT") piaActorId else item.id
            val shortIds=ShortCatalogIds(File(filesDir,"short_catalog_ids.json")).assign(all.map { legacyKey(it) to it.category })
            val categories=listOf("ทั้งหมด","มนุษย์","สัตว์","อสูรและสัตว์ประหลาด","เทพและผู้พิทักษ์","ผี วิญญาณ และภูต","อาวุธและอุปกรณ์","ยังไม่ระบุประเภท")
            choiceMenu("คลัง MASTER • เลือกหมวด",categories+listOf("➕ เพิ่มตัวละคร","🎭 ตัวละครของเรื่องที่เปิด")) { selected ->
                if(selected==categories.size) { actorCatalogMenu(); return@choiceMenu }
                if(selected==categories.size+1) { if(plan!=null) castMenu() else notice("เปิดเรื่องที่มีบทก่อน"); return@choiceMenu }
                val category=categories[selected]
                val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
                val search=EditText(this).apply { hint="ค้นหาชื่อหรือรหัสสั้น"; isSingleLine=true }
                val count=TextView(this)
                val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                panel.addView(search); panel.addView(count)
                panel.addView(ScrollView(this).apply { addView(grid) },LinearLayout.LayoutParams(-1,dp(420)))
                val dialog=AlertDialog.Builder(this).setTitle("คลัง MASTER • $category").setView(panel).setNegativeButton("ปิด",null).create()
                fun show(query: String) {
                    grid.removeAllViews()
                    val found=all.filter { (category=="ทั้งหมด" || it.category==category) && (it.name+" "+it.id+" "+shortIds[legacyKey(it)].orEmpty()).contains(query,ignoreCase=true) }
                    count.text=if(found.isEmpty()) "ไม่พบรายการ • เพิ่มหรือเลือกหมวดอื่นได้" else "พบ ${found.size} รายการ • แตะเพื่อดู"
                    var row: LinearLayout?=null
                    found.forEachIndexed { index,entry ->
                        if(index%2==0) { row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; grid.addView(row) }
                        val cell=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)) }
                        row?.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
                        val label=shortIds[legacyKey(entry)].orEmpty()+" • "+entry.name+(if(entry.file?.isFile==true) "" else "\nไม่พบ MASTER")
                        if(entry.file?.isFile==true) cell.addView(ImageView(this).apply {
                            setImageBitmap(castingThumbnail(entry.file)); adjustViewBounds=true
                            contentDescription=label; setOnClickListener { dialog.dismiss(); entry.open() }
                        },LinearLayout.LayoutParams(-1,dp(140)))
                        cell.addView(Button(this).apply { text=label; setOnClickListener { dialog.dismiss(); entry.open() } })
                    }
                }
                search.addTextChangedListener(object: android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
                    override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { show(s?.toString().orEmpty()) }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
                show(""); dialog.show()
            }
        }.onFailure { notice("เปิดคลังไม่ได้: ${it.message}") }
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
    private val galleryImagesPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }
    private val imagesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importImages(uris)
    }
    private fun launchSmartImagePicker() {
        AlertDialog.Builder(this).setTitle("เลือกภาพหลายภาพพร้อมกัน")
            .setItems(arrayOf("แกลเลอรี • เลือกหลายภาพ", "ไฟล์ในเครื่อง • เลือกหลายไฟล์")) { _, option ->
                if (option == 0) galleryImagesPicker.launch("image/*")
                else imagesPicker.launch(arrayOf("image/*"))
            }.setNegativeButton("ยกเลิก", null).showDirector()
    }
    private val zipPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { val backup = backupMode; backupMode = false; background { if (backup) contentResolver.openOutputStream(uri)!!.use { StudioArchive.write(project,it,File(filesDir,"short_catalog_ids.json")) } else export(uri) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register the update permission launcher after Activity attachment, before STARTED.
        updater = StudioUpdater(this)
        listOf("body_02.png", "body_03.png").forEach { File(cacheDir, "body_template_sources/$it").delete() }
        adoptGeminiProject(intent)
        receiveProductLink(intent)
        val masterError = runCatching { installCharacterMasters() }.exceptionOrNull()
        render()
        if (masterError != null) notice("ติดตั้ง MASTER ตัวละครไม่สำเร็จ กรุณาเปิดแอปอีกครั้ง")
    }
    private fun adoptGeminiProject(incoming: Intent?) {
        val id=incoming?.getStringExtra("gemini_project") ?: return
        if(Regex("[a-zA-Z0-9-]+").matches(id) && File(base,"$id/gemini_job/spec.json").isFile) prefs.edit().putString("project",id).apply()
    }
    private fun receiveProductLink(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND || incoming.type != "text/plain") return
        val shared = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val link = ProductLinkPolicy.firstUrl(shared)
        if (ProductLinkPolicy.allowed(link)) getSharedPreferences("product_assist", MODE_PRIVATE).edit().putString("shared_link", link).apply()
    }
    override fun onNewIntent(incoming: Intent) {
        super.onNewIntent(incoming); setIntent(incoming); adoptGeminiProject(incoming); receiveProductLink(incoming)
        val shared=if(incoming.action==Intent.ACTION_SEND) ProductLinkPolicy.firstUrl(incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()) else ""
        if(shared.isNotBlank() && ProductLinkPolicy.allowed(shared) && ProductAssist.deliverSharedLink(shared)) return
        render()
        if(shared.isNotBlank() && ProductLinkPolicy.allowed(shared)) notice("ได้รับลิงก์แล้ว • เปิดหน้าข้อมูลสินค้าเพื่อเลือกช่อง Affiliate")
    }
    private fun installCharacterMasters() {
        // Install explicitly supplied defaults only when no user image exists.
        val characterDir=File(filesDir,"character_masters").apply { mkdirs() }
        listOf("rinlada","mind").forEach { id ->
            val target=File(characterDir,"$id.master")
            if(!target.exists()) {
                val temp=File(characterDir,"$id.default.pending")
                try {
                    assets.open("character_defaults/$id.jpg").use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    android.graphics.BitmapFactory.decodeFile(temp.path,bounds)
                    require(bounds.outWidth>0 && bounds.outHeight>0) { "ภาพ MASTER ของ $id อ่านไม่ได้" }
                    if(!target.exists()) check(temp.renameTo(target)) { "บันทึก MASTER ไม่สำเร็จ" }
                } finally { temp.delete() }
            }
        }
        // Preserve existing character libraries and project snapshots during upgrades.
        // Refresh an existing cached copy of Krit's reference after the bundled image changes.
        if(File(filesDir,"master_library/CHAR_141__1000030680.jpg").isFile) {
            masterLibrary.entries().firstOrNull { it.id=="CHAR_141" }?.let { masterLibrary.source(it) }
        }
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
                .setMessage("ตรวจว่ารูปและคำสั่งใน $receiver ครบหรือไม่ การเปิดแอปสำเร็จยังไม่ยืนยันว่ารับครบหรือสร้างภาพแล้ว")
                .setPositiveButton("รับครบแล้ว") { _,_ -> recordGeminiReceipt(receiptPath,"user_confirmed_received") }
                .setNegativeButton("ส่งรูปอีกครั้ง") { _,_ -> recordGeminiReceipt(receiptPath,"user_reported_missing"); retryLastImage() }
                .setNeutralButton("กลับรายการรูป/ซีน") { _,_ ->
                    recordGeminiReceipt(receiptPath,"unverified")
                    if(!returnToImageList()) notice("ยังไม่มีรายการรูป/ซีนที่เปิดไว้ในเรื่องนี้")
                }.showDirector()
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
        val isFantasyHome=plan?.let { ChannelFeaturePolicy.hasFantasyTools(it.optString("channel")) } == true
        val scroll = ScrollView(this).apply { background = backdrop(Color.rgb(8,8,10),if(isFantasyHome) Color.rgb(40,25,25) else Color.rgb(31,24,17)); isFillViewport = true; clipToPadding = false }
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
        host.addView(Button(this).apply {
            text = "🧪 API TEST CENTER • ตรวจสอบแบบไม่ใช้เครดิต"
            setOnClickListener {
                startActivity(Intent(this@StudioActivity, ApiTestCenterActivity::class.java))
            }
        })
        setContentView(host)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(host)
        val hero = ImageView(this).apply {
            setImageResource(R.drawable.cinema_header)
            scaleType=ImageView.ScaleType.FIT_CENTER
            adjustViewBounds=true
            contentDescription="DIRECTOR X • ภาพผู้กำกับในโรงภาพยนตร์"
        }
        root.addView(hero,LinearLayout.LayoutParams(-1,-2))
        val versionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        val installed = packageManager.getPackageInfo(packageName, 0)
        versionRow.addView(TextView(this).apply {
            text = "DIRECTOR X ${installed.versionName} • Build ${installed.longVersionCode}"
            textSize = 11f
            gravity = Gravity.END
            setTextColor(gold)
        })
        val refreshIcon = TextView(this).apply {
            text = "↻"; textSize = 16f; setTextColor(Color.rgb(34, 211, 238)); gravity = Gravity.CENTER
        }
        val updateLabel = TextView(this).apply {
            text = "ตรวจสอบ"; textSize = 11f
            setTextColor(Color.rgb(34, 211, 238)); gravity = Gravity.CENTER_VERTICAL
        }
        val updateButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), 0)
            isClickable = true; isFocusable = true
            contentDescription = "ตรวจสอบอัปเดต DIRECTOR X"
            addView(refreshIcon, LinearLayout.LayoutParams(dp(20), dp(42)))
            addView(updateLabel)
            setOnClickListener { updater.check(automatic = false) }
        }
        versionRow.addView(updateButton, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(5) })
        var spinning: android.animation.ObjectAnimator? = null
        updater.onStatus = { state ->
            if (state == "checking") {
                refreshIcon.text = "↻"; updateLabel.text = "กำลังตรวจสอบ"
                val color = Color.rgb(34, 211, 238)
                refreshIcon.setTextColor(color); updateLabel.setTextColor(color)
                if (spinning == null) spinning = android.animation.ObjectAnimator.ofFloat(refreshIcon, "rotation", 0f, 360f).apply {
                    duration = 1000L; repeatCount = android.animation.ValueAnimator.INFINITE
                }
                spinning?.start()
            } else {
                spinning?.cancel(); refreshIcon.rotation = 0f
                val color = when (state) {
                    "latest" -> Color.rgb(52, 211, 153)
                    "available" -> Color.rgb(251, 191, 36)
                    else -> Color.rgb(253, 186, 116)
                }
                refreshIcon.setTextColor(color); updateLabel.setTextColor(color)
                when (state) {
                    "latest" -> { refreshIcon.text = "✓"; updateLabel.text = "ล่าสุดแล้ว" }
                    "available" -> { refreshIcon.text = "↓"; updateLabel.text = "อัปเดตใหม่" }
                    else -> { refreshIcon.text = "↻"; updateLabel.text = "ลองอีกครั้ง" }
                }
            }
        }
        root.addView(versionRow, LinearLayout.LayoutParams(-1, -2))
        
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
            if (choice == 0) chooseNamedCharacterGrid("🎭 เลือกตัวละครในภาพ", listOf("👨 กวิน • คนเดียว" to File(filesDir,"character_masters/gawin.master"), "👩 รินรดา • คนเดียว" to File(filesDir,"character_masters/rinlada.master"), "👩 เมย์ • คนเดียว" to File(filesDir,"character_masters/mind.master"), "👥 ทั้ง 3 คนในภาพเดียว" to null)) { index ->
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
                if (number < 3) { paint.color=if(number<currentStep) electricBlue else Color.rgb(74,87,108); canvas.drawLine(x,y+radius,x,height.toFloat(),paint) }
                if (active) { paint.color=Color.argb(70,42,220,255); canvas.drawCircle(x,y,dp(18).toFloat(),paint) }
                paint.color = if (completed || active) electricBlue else Color.rgb(85,102,125)
                canvas.drawCircle(x,y,radius,paint)
                paint.color = if (active || completed) Color.WHITE else muted
                paint.textSize = dp(12).toFloat(); paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.typeface = Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
                canvas.drawText(number.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)
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
                4 -> notice("1. สร้างเรื่อง: ตรวจ MASTER เลือกช่องและแนว ส่งคำสั่งไป ChatGPT แล้วส่งข้อความใน ChatGPT เพื่อเริ่มสร้างบทและภาพ\n\n2. รับบทและภาพ: ดาวน์โหลดและนำเข้า story.json ก่อน จากนั้นนำเข้า ZIP ภาพและปกของแต่ละ EP ผ่านเมนู 2 เพิ่มต่อทีละ EP ได้ ไม่ต้องเลือกบทซ้ำ ตรวจงานที่ขาดก่อนทำวิดีโอ\n\n3. ทำวิดีโอ: ใช้ภาพฉากและคำสั่งใน Flow / Veo สร้างคลิปเอง ดาวน์โหลด MP4 แล้วนำไปตัดต่อในโปรแกรมภายนอก\n\nแอปเปิดบริการภายนอกและจัดไฟล์ ผู้ใช้ต้องส่งข้อความใน ChatGPT และอัปโหลดภาพใน Flow เอง")
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
    private fun offerFlowTranslation(story: JSONObject,scene: JSONObject,spec: FlowSceneSpec,camera: String,silent: Boolean) {
        val providers=StoryProvider.eligible(this).filter { it.hasKey(this) }
        val fields=ShortFlowPrompt.untranslatedFields(spec,camera).joinToString(" / ").ifBlank { "คำกำกับฉาก" }
        AlertDialog.Builder(this).setTitle("เตรียมคำสั่ง Flow")
            .setMessage("$fields ยังมีภาษาไทย ต้องแปลเป็นอังกฤษก่อนคัดลอก • บทพูด/บรรยายภาษาไทยคงเดิม\n\nใช้ AI แปลเฉพาะคำกำกับ แล้วตรวจผลก่อนคัดลอก (ใช้โควตา API) หรือเลือกแก้ด้วยตนเอง")
            .setPositiveButton("ให้ AI แปล") { _,_ ->
                if(providers.isEmpty()) { notice("ยังไม่มี API ที่เลือกและตั้งคีย์ไว้ กรุณาตั้งค่า API หรือแก้ซีนด้วยตนเอง"); return@setPositiveButton }
                val projectId=project.name; val snapshot=scene.toString()
                val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
                val active=java.util.concurrent.atomic.AtomicReference<StoryTextClient?>(null)
                val loading=AlertDialog.Builder(this).setMessage("กำลังแปลคำกำกับฉาก • บทพูดคงเดิม").setNegativeButton("หยุด",null).showDirector()
                loading.setOnDismissListener { stopped.set(true); active.get()?.cancel() }
                Thread {
                    var value: Pair<FlowSceneSpec,String>?=null
                    for(provider in providers) {
                        if(stopped.get()) break
                        val answer=runCatching { val client=StoryClientFactory.create(provider,provider.key(this),provider.model(this),{ stopped.get() },automaticRetry=false,context=this@StudioActivity); active.set(client); if(stopped.get()) throw java.util.concurrent.CancellationException(); client.generate(FlowEnglishDirections.prompt(spec),FlowEnglishDirections.schema()) }.getOrNull() ?: continue
                        val translated=runCatching { FlowEnglishDirections.apply(spec,answer) }.getOrNull() ?: continue
                        val command=runCatching { ShortFlowPrompt.build(translated,camera,silent) }.getOrNull() ?: continue
                        value=translated to command; break
                    }
                    val result=value
                    runOnUiThread {
                        if(stopped.get() || isFinishing || isDestroyed) return@runOnUiThread
                        active.set(null); loading.dismiss()
                        if(project.name!=projectId || plan!==story || scene.toString()!=snapshot) { notice("ซีนเปลี่ยนระหว่างแปล กรุณาลองใหม่"); return@runOnUiThread }
                        if(result==null) { notice("ยังแปลคำกำกับไม่ได้ • ตรวจ API หรือกดแก้ซีนด้วยตนเอง • ไม่มีการเปลี่ยนบท"); return@runOnUiThread }
                        val text="ตรวจคำแปลเทียบกับบทก่อนคัดลอก • การแปลนี้ไม่แก้บทที่บันทึก\n\nการกระทำเดิม: ${spec.action}\n\nคำแปล: ${result.first.action}\n\nตำแหน่งและเสื้อผ้า (ตรวจให้ตรงกับภาพ): ${result.first.cast.mapIndexed { i,p -> "Character ${i+1}: ${p.position}, ${ShortFlowPrompt.clothing(p).ifBlank { "ไม่ระบุเสื้อ" }} (เดิม: ${ShortFlowPrompt.clothing(spec.cast[i]).ifBlank { "ไม่ระบุ" }})" }.joinToString("; ")}\n\nเสียงฉาก: ${result.first.sound.ifBlank { "ไม่มี" }}\n\nบทพูด/บรรยายภาษาไทยคงเดิม\n\n${result.second}"
                        AlertDialog.Builder(this).setTitle("ตรวจคำแปลก่อนคัดลอก").setView(ScrollView(this).apply { addView(TextView(context).apply { setPadding(dp(18),dp(12),dp(18),dp(12)); this.text=text; setTextIsSelectable(true) }) })
                            .setPositiveButton("คัดลอกคำสั่ง Flow") { _,_ -> copyCommand(result.second) }.setNegativeButton("ยกเลิก",null).showDirector()
                    }
                }.start()
            }.setNeutralButton("แก้ซีนเอง") { _,_ -> editScene(scene) }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun wardrobeSourceKey(projectName: String, id: String, digest: String, person: JSONObject): String {
        val outfitDigest=MasterLibrary.digest(person.optString("outfit").trim().toByteArray(Charsets.UTF_8))
        return "wardrobe_source_v2_${projectName}_${referenceId(id)}_${digest}_${outfitDigest}"
    }
    private fun previewImageReferences(task: SimpleProductionCommands.Task, references: List<Pair<String,File>>, labels: Map<String,String>) {
        val masterDigests=runCatching { references.take(task.references.size).associate { (id,file) -> id to MasterLibrary.digest(file.readBytes()) } }
            .getOrElse { notice("อ่าน MASTER ไม่ได้ กรุณาตรวจไฟล์ก่อนส่ง"); return }
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        fun stepLabel(number: String, title: String) = TextView(this).apply {
            text="❶❷❸"[number.toInt()-1].toString()+"  "+title
            textSize=18f; setTextColor(Color.rgb(255,193,69)); setTypeface(null,Typeface.BOLD)
            setPadding(dp(12),dp(12),dp(12),dp(6))
        }
        val referenceMode=Spinner(this).apply {
            adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("ภาพครอปใบหน้า + ภาพสามมุมมอง", "ภาพครอปใบหน้าอย่างเดียว", "MASTER + ภาพฉากที่ตรวจผ่าน", "MASTER ต้นฉบับเท่านั้น • สำหรับซีนหลายคน", "MASTER ทุกคน + เลือก FACE DETAIL ตามจำนวนภาพ"))
            setSelection(if(task.references.size>=5) 4 else prefs.getInt("image_reference_mode_${project.name}",0).coerceIn(0,4))
        }
        val imageOrder=Spinner(this).apply {
            adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("ภาพสามมุม → ภาพครอป", "ภาพครอป → ภาพสามมุม"))
            setSelection(prefs.getInt("image_attachment_order",0).coerceIn(0,1))
        }
        val transferOrder=Spinner(this).apply {
            adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("คำสั่ง → ภาพ", "ภาพ → คำสั่ง"))
            setSelection(prefs.getInt("image_transfer_order",0).coerceIn(0,1))
        }
        panel.addView(stepLabel("1","เลือกภาพอ้างอิง")); panel.addView(referenceMode)
        panel.addView(stepLabel("2","เลือกลำดับภาพ")); panel.addView(imageOrder)
        panel.addView(stepLabel("3","เลือกลำดับส่ง")); panel.addView(transferOrder)
        val orderSummary=TextView(this).apply { setPadding(dp(12),dp(10),dp(12),dp(10)); setTextColor(Color.CYAN) }
        panel.addView(orderSummary)
        var refreshCards: () -> Unit = {}
        val faceSelections=linkedMapOf<String,android.widget.CheckBox>()
        fun selectedFaceIds(): Set<String> = faceSelections.filterValues { it.isChecked }.keys
        fun refreshOrder() {
            refreshCards()
            imageOrder.isEnabled=referenceMode.selectedItemPosition==0 || referenceMode.selectedItemPosition==4
            val refs=if(referenceMode.selectedItemPosition==1) "ครอปใบหน้า" else if(referenceMode.selectedItemPosition==2) "MASTER + ภาพฉาก" else if(referenceMode.selectedItemPosition==3) "MASTER ต้นฉบับเท่านั้น" else if(referenceMode.selectedItemPosition==4) "MASTER + FACE DETAIL ที่เลือก" else if(imageOrder.selectedItemPosition==1) "ครอป → สามมุม" else "สามมุม → ครอป"
            orderSummary.text=if(transferOrder.selectedItemPosition==1) "$refs → คำสั่ง" else "คำสั่ง → $refs"
        }
        val orderListener=object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { refreshOrder() }
        }
        referenceMode.onItemSelectedListener=orderListener; imageOrder.onItemSelectedListener=orderListener; transferOrder.onItemSelectedListener=orderListener
        panel.addView(TextView(this).apply { text="ตรวจภาพและชื่อก่อนส่ง • แอปปลายทางอาจจัดลำดับต่างกัน" })
        val piaFaceLock=runCatching { GeminiStoryFiles.read(File(project,"gemini_job/spec.json")).optBoolean("pia_face_lock",false) }.getOrDefault(false) && plan?.optString("channel")=="นายเปีย บ้านคลองขุด"
        val faceOnly=android.widget.CheckBox(this).apply { text="ใช้ใบหน้า MASTER • ชุดตามฉาก"; isChecked=piaFaceLock; panel.addView(this) }
        val masterAppearance=linkedMapOf<String,android.widget.CheckBox>()
        val crops=linkedMapOf<String,Pair<String,File>>()
        val cropDigests=linkedMapOf<String,String>()
        val cropLabels=linkedMapOf<String,String>()
        panel.addView(TextView(this).apply { text="ตรวจชื่อกับภาพ MASTER รายคน • ภาพครอปที่บันทึกไว้จะแนบอัตโนมัติพร้อม MASTER เดิม หากยังไม่มี ให้เลือกกรอบหน้าตรง" })
        val cropStore=FaceReferenceStore(File(filesDir,"master_face_references"))
        task.references.forEachIndexed { index, person ->
            val (id,file)=references[index]
            val position=if(task.id.contains("_COVER_")) when(task.references.size) { 1 -> "center"; 2 -> if(index==0) "left" else "right"; 3 -> listOf("left","center","right")[index]; else -> "ลำดับ ${index+1}" } else person.optString("screen_position")
            val shortId=shortCatalogLabel(person.optString("library_id").ifBlank { referenceId(id) })
            panel.addView(TextView(this).apply { text="${shortId?.let { "$it • " }.orEmpty()}${person.optString("name")} • $position\nชุดที่ระบุในบท: ${person.optString("outfit")}"; setTypeface(null,Typeface.BOLD) })
            val sourceKey=wardrobeSourceKey(project.name,id,masterDigests[id].orEmpty(),person)
            masterAppearance[id]=android.widget.CheckBox(this).apply {
                isChecked=prefs.getBoolean(sourceKey,WardrobeEvidence.useMasterOutfit(person.optString("outfit")))
                fun refreshSourceLabel() { text=if(isChecked) "แหล่งชุด: MASTER ของตัวละครนี้ ✓\nแตะเพื่อเปลี่ยนเป็นชุดตามบท" else "แหล่งชุด: ตามบท ✓\nแตะเพื่อใช้ชุดจาก MASTER" }
                refreshSourceLabel()
                setOnCheckedChangeListener { _,_ -> refreshSourceLabel() }
                panel.addView(this)
            }
            val imageRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            val cropColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            val masterColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            imageRow.addView(cropColumn,LinearLayout.LayoutParams(0,-2,1f)); imageRow.addView(masterColumn,LinearLayout.LayoutParams(0,-2,1f)); panel.addView(imageRow)
            val faceChoice=android.widget.CheckBox(this).apply {
                text="แนบ FACE DETAIL • ${person.optString("name")}"
                isChecked=false
                isEnabled=false
                setOnCheckedChangeListener { _, checked ->
                    // A checked FACE DETAIL must be part of the outgoing payload.
                    // Previously MASTER-only modes silently dropped checked crops.
                    if(checked && referenceMode.selectedItemPosition in 2..3) {
                        referenceMode.setSelection(4)
                    }
                    if(checked && selectedFaceIds().size>(9-task.references.size).coerceAtLeast(0)) {
                        isChecked=false
                        notice("เลือก FACE DETAIL ได้สูงสุด ${(9-task.references.size).coerceAtLeast(0)} คน")
                    }
                    refreshOrder()
                }
                cropColumn.addView(this)
            }
            faceSelections[id]=faceChoice
            val status=TextView(this).apply { text="ยังไม่มี FACE DETAIL • กดปุ่มเลือกใบหน้าด้านล่าง"; cropColumn.addView(this) }
            val cropPreview=ImageView(this).apply { adjustViewBounds=true; visibility=android.view.View.GONE; layoutParams=LinearLayout.LayoutParams(-1,dp(110)); cropColumn.addView(this) }
            val masterStatus=TextView(this).apply { text="MASTER • สามมุมมอง"; masterColumn.addView(this) }
            val previousRefresh=refreshCards
            refreshCards={
                previousRefresh()
                val mode=referenceMode.selectedItemPosition
                val offset=index + (0 until index).count { previous -> (mode==0 || mode==4 && references[previous].first in selectedFaceIds()) && crops.containsKey(references[previous].first) }
                val hasCrop=crops.containsKey(id)
                val cropFirst=imageOrder.selectedItemPosition==1
                status.text=if(!hasCrop) "ยังไม่มี FACE DETAIL • เลือกใบหน้าจาก MASTER" else if(mode==0 || mode==4 && id in selectedFaceIds()) "ครอปใบหน้า • ส่ง ${offset+if(cropFirst) 1 else 2}" else if(mode==1) "ครอปใบหน้า • ส่ง ${offset+1}" else "ครอปใบหน้า • ไม่ได้ส่ง"
                masterStatus.text=if(mode==1) "MASTER • ไม่ได้ส่ง" else "MASTER • ส่ง ${offset+if((mode==0 || mode==4 && id in selectedFaceIds()) && cropFirst && hasCrop) 2 else 1}"
            }
            masterColumn.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(110)); setOnClickListener { fullImage(file,person.optString("name")) } })
            fun showCrop(target: File) {
                val cropId="${referenceId(id)}_FACE_CROP"
                crops[id]=cropId to target
                cropDigests[id]=MasterLibrary.digest(target.readBytes())
                cropLabels[cropId]="ภาพครอปใบหน้า ${person.optString("name")} จาก MASTER $id"
                refreshOrder()
                faceChoice.isEnabled=true
                cropPreview.setImageBitmap(previewBitmap(Uri.fromFile(target)))
                cropPreview.visibility=android.view.View.VISIBLE
                if(selectedFaceIds().size < (9-task.references.size).coerceAtLeast(0)) faceChoice.isChecked=true
                cropPreview.setOnClickListener { fullImage(target,"ตรวจภาพครอป • ${person.optString("name")}") }
            }
            runCatching {
                // Reuse the verified crop, or prepare one from a uniquely detected face.
                // Multi-face three-view sheets stay manual to avoid picking the wrong view.
                cropStore.prepare(file)?.let { showCrop(it) }
            }.onFailure { notice("เตรียมภาพใบหน้าอัตโนมัติไม่ได้: ${person.optString("name")}") }
            cropColumn.addView(Button(this).apply {
                text="✂ เลือกใบหน้าจาก MASTER"
                setOnClickListener {
                    runCatching {
                        val cropView=FaceReferenceCropView(this@StudioActivity,file)
                        val layout=LinearLayout(this@StudioActivity).apply { orientation=1; addView(TextView(context).apply { text="ลากกรอบรอบใบหน้ามุมตรงของ ${person.optString("name")} จากภาพนี้ แล้วตรวจชื่อก่อนบันทึก" }); addView(cropView,LinearLayout.LayoutParams(-1,dp(520))) }
                        val dialog=AlertDialog.Builder(this@StudioActivity).setTitle("เลือกใบหน้า • ${person.optString("name")}").setView(layout).setPositiveButton("บันทึกกรอบ",null).setNegativeButton("ยกเลิก",null).showDirector()
                        dialog.getButton(-1).setOnClickListener {
                            runCatching {
                                val target=cropStore.newFile(file)
                                val pending=File(target.parentFile,target.name+".pending")
                                try { cropView.save(pending); check(pending.renameTo(target)) { "บันทึกภาพครอปไม่ได้" } } finally { pending.delete() }
                                showCrop(target)
                                dialog.dismiss()
                            }.onFailure { notice(it.message ?: "บันทึกภาพครอปไม่ได้") }
                        }
                    }.onFailure { notice(it.message ?: "เปิดภาพ MASTER ไม่ได้") }
                }
            })
        }
        val extraChecks=linkedMapOf<String,android.widget.CheckBox>()
        references.drop(task.references.size).forEach { (id,file) ->
            extraChecks[id]=android.widget.CheckBox(this).apply { text=labels[id] ?: "ภาพอ้างอิงที่ตรวจแล้ว • $id"; isChecked=!id.startsWith("OUTFIT_PIA_SELECTED_") || PiaReferencePolicy.outfitInitiallySelected(task.id); panel.addView(this) }
            panel.addView(ImageView(this).apply { setImageBitmap(previewBitmap(Uri.fromFile(file))); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(180)); setOnClickListener { fullImage(file,labels[id] ?: "ภาพอ้างอิง") } })
        }
        val allowFantasyProps=ChannelFeaturePolicy.hasFantasyTools(plan?.optString("channel").orEmpty())
        val propNames=propCatalog
        val propPreference="prop_master_${project.name}_${task.id}"
        val scene=plan?.let { story -> rows(story).firstOrNull { key(it)==task.id } }
        val rawAssignments=runCatching { JSONObject(File(project,"prop_assignments.json").readText()).optJSONArray(task.id) }.getOrNull()
        val automatic=if(scene==null) null else ScenePropBinding.valid(scene,rawAssignments)
        if(rawAssignments!=null && (automatic?.length() ?: 0)<rawAssignments.length()) panel.addView(TextView(this).apply {
            text="⚠️ ข้อเสนออุปกรณ์บางรายการยังไม่ผูกกับบทปัจจุบันหรือบทถูกแก้ไข • วิเคราะห์และตรวจรายการใหม่ก่อนใช้"
            setTypeface(null,Typeface.BOLD)
        })
        val autoIds=(0 until (automatic?.length() ?: 0)).mapNotNull { automatic?.optJSONObject(it)?.optString("prop_id") }.toSet()
        // Only an explicit cover incident naming the exact catalog item is evidence for the cover.
        val coverEvidence=if(task.id.contains("_COVER_")) plan?.optJSONArray("episodes")?.let { episodes ->
            (0 until episodes.length()).mapNotNull { episodes.optJSONObject(it) }.filter { episode ->
                task.id.startsWith("STORY_") || task.id.startsWith("EP%02d_".format(episode.optInt("ep")))
            }.joinToString(" ") { it.optString("cover_incident") }
        }.orEmpty() else ""
        val coverIds=propCatalog.mapIndexedNotNull { index,name -> if(coverEvidence.contains(name,true)) propId(index) else null }.toSet()
        val saved=if(prefs.contains(propPreference)) prefs.getStringSet(propPreference, emptySet()).orEmpty() else autoIds+coverIds
        val checks=linkedMapOf<String,android.widget.CheckBox>()
        val explicitProps=scene?.optString("props")?.trim().orEmpty()
        val noProps=explicitProps.isBlank() || explicitProps.equals("none",true) || explicitProps=="ไม่มี"
        val visiblePropIds=autoIds+coverIds
        if(!noProps && visiblePropIds.isEmpty()) panel.addView(TextView(this).apply {
            text="⚠️ บทระบุสิ่งของ: $explicitProps • ยังไม่พบ MASTER ที่ผูกกับฉาก ตรวจสิ่งของตามบทก่อนส่ง"
            setTypeface(null,Typeface.BOLD)
        })
        if(visiblePropIds.isNotEmpty()) panel.addView(TextView(this).apply { text="MASTER อุปกรณ์ที่ผูกกับบทฉากนี้ • ตรวจว่าปรากฏในภาพเริ่มต้น"; setTypeface(null,Typeface.BOLD) })
        propNames.forEachIndexed { index,name ->
            val id="PROP_%02d_MASTER".format(index+1)
            if(id !in visiblePropIds) return@forEachIndexed
            val checkbox=android.widget.CheckBox(this).apply { text="$name • $id"; isChecked=id in saved || (!prefs.contains(propPreference) && id in visiblePropIds) }
            checks[id]=checkbox
            panel.addView(checkbox)
            val image=ImageView(this).apply { adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(180)); visibility=if(checkbox.isChecked) android.view.View.VISIBLE else android.view.View.GONE }
            fun propFile(): File {
                val file=File(cacheDir,"prop_masters/$id.png")
                if (!file.isFile) { file.parentFile!!.mkdirs(); assets.open("prop_masters/$id.png").use { input -> file.outputStream().use { input.copyTo(it) } } }
                return file
            }
            fun showProp() { runCatching { val file=propFile(); image.setImageBitmap(previewBitmap(Uri.fromFile(file))); image.setOnClickListener { fullImage(file,name) } }.onFailure { notice("เปิดภาพอุปกรณ์ไม่ได้: $name") } }
            if(checkbox.isChecked) showProp()
            checkbox.setOnCheckedChangeListener { _,checked -> image.visibility=if(checked) android.view.View.VISIBLE else android.view.View.GONE; if(checked) showProp() }
            panel.addView(image)
        }
        // Preflight count follows the scene task's actual character references.
        // Never silently remove characters or change the user's chosen mode.
        val imageBudget=TextView(this).apply {
            textSize=16f
            setTypeface(null,Typeface.BOLD)
            setPadding(dp(12),dp(10),dp(12),dp(10))
            setBackgroundColor(Color.rgb(34,43,59))
        }
        panel.addView(imageBudget,0)
        fun updateImageBudget() {
            val mode=referenceMode.selectedItemPosition
            val masterCount=task.references.size
            val cropCount=if(mode==0 || mode==1 || mode==4) task.references.indices.count { crops.containsKey(references[it].first) && (mode!=4 || references[it].first in selectedFaceIds()) } else 0
            val total=masterCount+cropCount // MASTER images are mandatory even in face-crop-only mode.
            val valid=total<=9 && (mode!=1 || cropCount==masterCount) && masterCount>0 && (mode!=4 || selectedFaceIds().all { crops.containsKey(it) })
            imageBudget.setTextColor(if(valid) Color.rgb(170,232,191) else Color.rgb(255,194,109))
            imageBudget.text="ภาพตัวละคร $total/9 • MASTER ${if(mode==1) 0 else masterCount} • FACE ${cropCount}"+
                (if(!valid) "\\n⚠ ภาพเกิน 9 หรือ FACE DETAIL ที่เลือกยังไม่มีไฟล์ • ยกเลิกตัวเลือกหรือครอปใบหน้า" else "")
        }
        val originalRefresh=refreshCards
        refreshCards={ originalRefresh(); updateImageBudget() }
        updateImageBudget()
        AlertDialog.Builder(this).setTitle("ตรวจ MASTER • ${task.id}").setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("เลือกแอปและส่ง") { _,_ ->
                val stable=runCatching { references.take(task.references.size).all { (id,file) -> file.isFile && MasterLibrary.digest(file.readBytes())==masterDigests[id] } }.getOrDefault(false)
                if(!stable) { notice("ภาพ MASTER เปลี่ยนหรือหายระหว่างตรวจ กรุณาเปิดรายการและตรวจภาพอีกครั้ง"); return@setPositiveButton }
                val sceneMasterIds=references.take(task.references.size).map { referenceId(it.first).lowercase() }
                if(sceneMasterIds.size!=sceneMasterIds.distinct().size) {
                    notice("พบ MASTER ID ซ้ำในซีนนี้ • กรุณาตรวจการผูกตัวละครก่อนส่ง เพื่อป้องกันใบหน้าสลับ")
                    return@setPositiveButton
                }
                val mode=referenceMode.selectedItemPosition
                if(mode in 2..3 && selectedFaceIds().isNotEmpty()) {
                    notice("มี FACE DETAIL ที่เลือกไว้ แต่โหมดนี้ไม่แนบใบหน้า • เปลี่ยนเป็น MASTER + FACE DETAIL ก่อนส่ง")
                    return@setPositiveButton
                }
                if(mode==1 && task.references.indices.any { crops[references[it].first]==null }) { notice("โหมดครอปหน้าอย่างเดียวต้องมีภาพครอปของทุกตัวละคร"); return@setPositiveButton }
                
                if(mode==4 && selectedFaceIds().isEmpty() && task.references.size>=5) {
                    notice("ยังไม่ได้เลือก FACE DETAIL เลย • กรุณากดเลือกใบหน้าจาก MASTER อย่างน้อย 1 คน หรือเปลี่ยนเป็นโหมด MASTER ต้นฉบับเท่านั้น")
                    return@setPositiveButton
                }
                if(mode==4 && selectedFaceIds().any { crops[it]==null }) {
                    notice("ตัวละครที่เลือก FACE DETAIL ยังไม่มีภาพครอป กรุณาเลือกกรอบใบหน้าหรือยกเลิกเครื่องหมาย")
                    return@setPositiveButton
                }
                val selected=crops.toMap()
                val cropsToVerify=if(mode==1) crops else selected
                val cropsStable=runCatching { cropsToVerify.all { (id,crop) -> crop.second.isFile && MasterLibrary.digest(crop.second.readBytes())==cropDigests[id] } }.getOrDefault(false)
                if(!cropsStable) { notice("ภาพครอปใบหน้าเปลี่ยนหรือหาย กรุณาเลือกจาก MASTER อีกครั้ง"); return@setPositiveButton }
                val bindings=(if(mode==0 || mode==1) selected.entries else if(mode==4) selected.entries.filter { it.key in selectedFaceIds() } else emptyList()).joinToString("\n") { (id,crop) -> "CHARACTER ${labels[id].orEmpty()} — FACE CROP ${crop.first} is cropped from MASTER ${referenceId(id)}; both show the same character. Use the crop for facial identity and the original MASTER for verification. Never use this crop for another character." }
                val rawCommand=if(faceOnly.isChecked || mode==1) SimpleProductionCommands.faceOnly(task.command) else task.command
                val modeCommand=rawCommand // Keep three-view MASTER instructions; face crops are supplemental only.
                val baseCommand=modeCommand+(if(bindings.isBlank()) "" else "\n\n$bindings")
                val locked=task.references.mapIndexedNotNull { index,person ->
                    val id=references[index].first
                    if(masterAppearance[id]?.isChecked==true) person.optString("name") to referenceId(id) else null
                }
                val command=runCatching { SimpleProductionCommands.withWardrobeSources(baseCommand,task.references,locked.map { it.second }.toSet()) }.getOrElse {
                    notice(it.message ?: "ตรวจแหล่งชุดไม่สำเร็จ"); return@setPositiveButton
                }
                val sourcePrefs=prefs.edit()
                task.references.forEachIndexed { index,person ->
                    val id=references[index].first
                    sourcePrefs.putBoolean(wardrobeSourceKey(project.name,id,masterDigests[id].orEmpty(),person),masterAppearance[id]?.isChecked==true)
                }
                sourcePrefs.apply()
                val chosen=checks.filterValues { it.isChecked }.keys
                val propRefs=runCatching { chosen.map { id ->
                    val file=File(cacheDir,"prop_masters/$id.png")
                    if(!file.isFile) { file.parentFile!!.mkdirs(); assets.open("prop_masters/$id.png").use { input -> file.outputStream().use { input.copyTo(it) } } }
                    check(file.isFile && file.length()>0) { "ไม่พบภาพ $id" }
                    id to file
                } }.getOrElse { notice(it.message ?: "เปิด MASTER อุปกรณ์ไม่ได้"); return@setPositiveButton }
                if (prefs.contains(propPreference) || chosen != saved) prefs.edit().putStringSet(propPreference,chosen.toSet()).apply()
                val propLabels=propRefs.associate { (id,_) -> id to propNames[id.substring(5,7).toInt()-1] }
                val coverOwners=if(task.id.contains("_COVER_")) task.references.mapNotNull { person -> characterProp(person.optString("master")).takeIf { it.isNotBlank() && it in chosen }?.let { "${person.optString("name")} is associated with $it; show it only if appropriate to this cover." } }.joinToString("\n") else ""
                val owners=(0 until (automatic?.length() ?: 0)).mapNotNull { automatic?.optJSONObject(it) }.filter { it.optString("prop_id") in chosen }.joinToString("\n") { "${it.optString("holder").ifBlank { "Holder unspecified" }} is associated with ${it.optString("prop_id")} in this scene. Start-frame prop state: ${it.optString("start_prop_state").ifBlank { "unspecified; follow the story, do not assume it is held" }}. Follow the start state and action." }
                val propInstruction=if(propRefs.isEmpty()) "" else "\n\nPROP MASTER REFERENCES: Each selected image shows multiple views of ONE object. Match each named object to its own attached MASTER. Preserve its distinctive silhouette, proportions, materials, colors, markings, gems, guard and handle across scenes. Do not invent a replacement or mix different objects. Depict only the selected props appropriate to this scene; follow the planned action and placement. Follow the provided image order and generate directly. Similarity is a target and cannot be guaranteed.\n"+propLabels.entries.joinToString("\n") { "${it.value} | MASTER ID: ${it.key}" }
                val extras=references.drop(task.references.size).filter { extraChecks[it.first]?.isChecked == true }
                val outfitExtras=extras.filter { extra -> extra.first.startsWith("OUTFIT_") && locked.none { (_,id) -> extra.first.endsWith(id,true) } }
                if(mode==2 && outfitExtras.none { it.first.startsWith("OUTFIT_EP") }) { notice("ยังไม่มีภาพฉากเดี่ยวที่ตรวจผ่านและชุดตรงกัน กรุณาเลือกวิธีอื่น"); return@setPositiveButton }
                val outfitGuide=if(outfitExtras.isEmpty()) "" else (if(mode==2) "\n\nREVIEWED SCENE REFERENCE: The checked prior single-character scene was reviewed by the user for this same character and outfit. Use it as a supplemental identity and wardrobe reference; the original MASTER remains the source of identity. " else "")+"\n\nREVIEWED OUTFIT REFERENCES: For the listed characters only, these checked images override generic instructions against copying clothing. Use each labeled frame as a clothing reference for its named character, while the original character MASTER remains the identity reference. Preserve visible garments; follow the written outfit for parts not visible. Do not copy background, pose or objects. "+outfitExtras.joinToString("; ") { "${it.first} = ${labels[it.first]}" }
                val primary=references.take(task.references.size) // Original MASTER is mandatory in every mode.
                val orderedPeople=if(mode==0 || mode==1 || mode==4) primary.flatMap { master ->
                    val crop=if(mode==1) crops[master.first] else if(mode==4 && master.first !in selectedFaceIds()) null else selected[master.first]
                    if(crop==null) listOf(master) else if(imageOrder.selectedItemPosition==1) listOf(crop,master) else listOf(master,crop)
                } else primary
                // Always keep every required character MASTER/face crop. Optional outfit,
                // scene and prop references must not push a share past the nine-image limit.
                // Preserve attachment order and never substitute another character's image.
                val optional=extras.filter { !it.first.startsWith("OUTFIT_") || it in outfitExtras }+propRefs
                if(orderedPeople.size>9) {
                    notice("แนบภาพ ${orderedPeople.size}/9 เกินกำหนด • เลือก FACE DETAIL ไม่เกิน ${9-primary.size} คน โดย MASTER ทุกคนยังแนบครบ")
                    return@setPositiveButton
                }
                if(primary.size>9) { notice("ซีนนี้มี MASTER ${primary.size} คน เกินขีดจำกัด 9 ภาพ ไม่สามารถส่งในครั้งเดียวได้ กรุณาแบ่งซีนตามบทโดยไม่ลบตัวละครจากข้อมูลต้นฉบับ"); return@setPositiveButton }
                val remaining=9-orderedPeople.size
                val includedOptional=optional.take(remaining)
                val omittedOptional=optional.drop(remaining)
                val payload=orderedPeople+includedOptional
                if(omittedOptional.isNotEmpty()) {
                    notice("ส่ง MASTER ตัวละครครบแล้ว • จำกัดภาพอ้างอิงไว้ที่ 9 ภาพ โดยงดภาพเสริม ${omittedOptional.size} ภาพ")
                }
                // Bind every attachment to its actual position after the user's order choice.
                // Do not rely on filenames or a presumed MASTER-first ordering.
                val exactBindings=payload.mapIndexed { index, (id, _) ->
                    val owner=task.references.indices.firstOrNull { i ->
                        val masterId=references[i].first
                        id==masterId || crops[masterId]?.first==id
                    }?.let { i -> task.references[i].optString("name") }
                    "Reference image ${index+1}: ${owner?.let { "$it | " }.orEmpty()}${labels[id] ?: cropLabels[id] ?: propLabels[id] ?: id} | ${if(task.references.indices.any { i -> crops[references[i].first]?.first==id }) "FACE CROP" else "SOURCE REFERENCE"}"
                }.joinToString("\n")
                prefs.edit().putInt("image_reference_mode_${project.name}",mode).putInt("image_attachment_order",imageOrder.selectedItemPosition).putInt("image_transfer_order",transferOrder.selectedItemPosition).apply()
                val includedIds=payload.map { it.first }.toSet()
                val includedOutfitExtras=outfitExtras.filter { it.first in includedIds }
                val safeOutfitGuide=if(includedOutfitExtras.isEmpty()) "" else (if(mode==2) "\n\nREVIEWED SCENE REFERENCE: This prior single-character scene was reviewed for the same character and outfit; original MASTER remains identity source. " else "")+"\n\nREVIEWED OUTFIT REFERENCES: "+includedOutfitExtras.joinToString("; ") { "${it.first} = ${labels[it.first]}" }
                val includedPropRefs=propRefs.filter { it.first in includedIds }
                val safePropInstruction=if(includedPropRefs.isEmpty()) "" else "\n\nPROP MASTER REFERENCES: "+includedPropRefs.joinToString("; ") { "${it.first} = ${propLabels[it.first]}" }+". Use each object's own MASTER; do not swap identities."
                val safeOwners=if(includedPropRefs.isEmpty()) "" else owners
                val safeCoverOwners=if(includedPropRefs.isEmpty()) "" else coverOwners
                shareImageCommand(command+safeOutfitGuide+safePropInstruction+(if(safeOwners.isBlank()) "" else "\n$safeOwners")+(if(safeCoverOwners.isBlank()) "" else "\n$safeCoverOwners")+"\n\nATTACHMENT ORDER (verify in receiving app):\n"+exactBindings,payload,labels+cropLabels.filterKeys { key -> selected.values.any { it.first==key } }+propLabels)
            }
            .setNegativeButton("กลับไปตรวจ",null).showDirector()
    }
    private fun shareImageCommand(command: String, references: List<Pair<String,File>>, referenceLabels: Map<String,String> = emptyMap(), twoCovers: Boolean = false, bodyOnly: Boolean = false) {
        val referenceDigests=runCatching { ReferenceSnapshot.capture(references) }.getOrElse { notice(it.message ?: "ตรวจภาพอ้างอิงไม่ได้"); return }
        val orderedCommand=if(bodyOnly) command else SimpleProductionCommands.orderedReferenceCommand(command,references.map { it.first })
        val preferred=prefs.getString("image_provider","ChatGPT") ?: "ChatGPT"
        val providers=imageProviderOptions(preferred)
        val choices=providers.mapIndexed { i,name -> name+if(i==0) " • ตัวเลือกที่จำไว้" else "" } + "คัดลอกคำสั่ง • แนบ MASTER เอง"
        choiceMenu("สร้างภาพด้วยแอปไหน",choices) { selected ->
            if(selected==providers.size) {
                copyCommand(orderedCommand)
                notice("คัดลอกแล้ว • แนบภาพตัวละครตามลำดับในคำสั่งก่อนสร้าง")
            } else {
                val provider=providers[selected]
                prefs.edit().putString("image_provider",provider).apply()
                shareText(orderedCommand,includeMasters=false,referenceImages=references,destinationLabel=provider,imagePackage=true,referenceLabels=referenceLabels,twoCovers=twoCovers,bodyOnly=bodyOnly,referenceDigests=referenceDigests)
            }
        }
    }
    private fun shareText(inputText: String, lockCreation: Boolean = false, useNewMasters: Boolean = plan?.optString("character_mode") == "new", includeMasters: Boolean = true, selectedMasterKeys: List<String>? = null, referenceImages: List<Pair<String, File>> = emptyList(), destinationLabel: String = "ChatGPT", imagePackage: Boolean = false, referenceLabels: Map<String,String> = emptyMap(), twoCovers: Boolean = false, bodyOnly: Boolean = false, referenceDigests: Map<String,String> = emptyMap()) {
        var text = if (lockCreation && !inputText.contains("ค่าตั้งต้นผู้ใช้ (ใช้แทนค่าตัวอย่าง)")) inputText + "\nค่าตั้งต้นผู้ใช้ (ใช้แทนค่าตัวอย่าง): EP=${prefs.getString("plan_episodes", "").orEmpty().ifBlank { "AI ประเมิน" }} ฉาก/EP=${prefs.getString("plan_scenes", "").orEmpty().ifBlank { "AI ประเมิน" }} เวลา=${prefs.getString("plan_duration", "8")} วินาที สัดส่วน=${"9:16"} กำหนด duration_seconds และ aspect_ratio ใน story.json และคำสั่งทุกฉากตามนี้" else inputText
        if (imagePackage && !bodyOnly && !text.contains(SimpleProductionCommands.faceOnlyMarker) && !text.contains(BodyReferencePolicy.imageIdentityLock)) text += "\n\n" + BodyReferencePolicy.imageIdentityLock
        if (imagePackage && !bodyOnly && !text.contains(ImagePromptPolicy.skinTone)) text += "\n\n" + ImagePromptPolicy.skinTone
        if (imagePackage) for ((name,_) in referenceImages) {
            val id=referenceId(name)
            text=text.replace(Regex("(?i)"+Regex.escape(id)+"[.](png|jpe?g|webp)\\b"),id)
        }
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
            out.parentFile!!.mkdirs()
            val expected=referenceDigests[name]
            if(expected!=null) {
                if(runCatching { ReferenceSnapshot.copyVerified(source,out,expected) }.isFailure) { notice("ภาพอ้างอิง $name เปลี่ยนหรือหายระหว่างเตรียมส่ง กรุณาตรวจใหม่"); return }
            } else source.copyTo(out, overwrite = true)
            attachments.put(JSONObject().put("name",out.name).put("sha256",java.security.MessageDigest.getInstance("SHA-256").digest(out.readBytes()).joinToString("") { "%02x".format(it) }).put("bytes",out.length()))
                uris.add(FileProvider.getUriForFile(this,"$packageName.fileprovider",out))
        }
        // ChatGPT Android builds do not all read the same share field. Keep
        // the command in both EXTRA_TEXT and the first ClipData item, then
        // append every granted image URI to that same ClipData payload.
        val geminiImages=destinationLabel=="Gemini" && imagePackage && uris.isNotEmpty()
        val filePackage=!geminiImages && (destinationLabel=="Gemini" || imagePackage) && uris.isNotEmpty()
        val intent = if(geminiImages) buildGeminiImageShareIntent((if(bodyOnly) "สร้างภาพตัวละครใหม่ตามคำสั่ง ใช้ภาพแนบสำหรับรูปร่างเท่านั้น\n\n"+text else geminiImageMessage(text,twoCovers)),ArrayList(uris),prefs.getInt("image_transfer_order",0)==1) else if(filePackage) {
            val command=File(cacheDir,"scene_reference_exports/$transferId/READ_FIRST_IMAGE_COMMAND.txt")
            command.parentFile!!.mkdirs()
            command.writeText((if(bodyOnly) "สร้างตัวละครใหม่ตามคำสั่ง ภาพแนบใช้อ้างอิงรูปร่างเท่านั้น" else if(twoCovers) "สร้างปก 2 ภาพแยกไฟล์ตามคำสั่งด้านล่าง ใช้ MASTER ที่แนบให้ตรงตัวละคร" else "สร้างภาพหนึ่งภาพตามคำสั่งด้านล่าง ใช้ MASTER ที่แนบให้ตรงตัวละคร")+" ไม่สรุปคำสั่ง ไม่สร้างวิดีโอ\n\n"+text,Charsets.UTF_8)
            val commandUri=FileProvider.getUriForFile(this,"$packageName.fileprovider",command)
            val shareText=if(destinationLabel=="Gemini" && imagePackage) geminiImageMessage(text,twoCovers) else text
            val imagesFirst=imagePackage && prefs.getInt("image_transfer_order",0)==1
            val result=buildGeminiPackageIntent(shareText,commandUri,ArrayList(uris),imagesFirst)
            if(imagesFirst) uris.add(commandUri) else uris.add(0,commandUri)
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
        if(imagePackage) {
            val images=uris.filter { contentResolver.getType(it)?.startsWith("image/")==true }
            File(destination,"last_image_payload.json").writeText(JSONObject().put("text",text).put("images",JSONArray(images.map { it.toString() })).toString())
        }
        val imageList = (0 until attachments.length()).joinToString("\n") { i -> attachments.getJSONObject(i).let { "${it.optString("name")} • ${it.optLong("bytes")} bytes • SHA ${it.optString("sha256").take(12)}" } }
        pendingStorySummary = "คำสั่ง ${text.length} ตัวอักษร • ภาพ ${uris.count { contentResolver.getType(it)?.startsWith("image/")==true }} ภาพ" + if(filePackage) " • ไฟล์คำสั่ง 1 ไฟล์" else ""
        fun launchShare() {
            if(geminiImages) {
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("คำสั่งสร้างภาพ Gemini",geminiImageMessage(text,twoCovers)))
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
                if(filePackage || geminiImages) prefs.edit().putString("image_receipt_app",destinationLabel).putString("gemini_receipt_pending",File(destination,"image_requests/$transferId.json").absolutePath).apply()
            }
        }
        val preview = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(14),dp(14),dp(16))
            setBackgroundColor(Color.rgb(13,18,27))
        }
        // Render essential identity captions on Canvas independently of TextView.
        // This isolates the repeated device-specific blank TextView symptom.
        fun paintedCaption(value: String, sizeSp: Float, color: Int, bold: Boolean = false): android.view.View =
            object : android.view.View(this@StudioActivity) {
                private val captionPaint=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color=color
                    textSize=sizeSp*resources.displayMetrics.scaledDensity
                    typeface=if(bold) Typeface.create(Typeface.DEFAULT,Typeface.BOLD) else Typeface.DEFAULT
                }
                init {
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null)
                    contentDescription=value
                    importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                override fun onDraw(canvas: android.graphics.Canvas) {
                    super.onDraw(canvas)
                    val available=(width-dp(8)).coerceAtLeast(1)
                    val layout=android.text.StaticLayout.Builder.obtain(value,0,value.length,captionPaint,available)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(true)
                        .setMaxLines(2)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END)
                        .build()
                    canvas.save()
                    canvas.translate(dp(4).toFloat(),((height-layout.height)/2).coerceAtLeast(0).toFloat())
                    layout.draw(canvas)
                    canvas.restore()
                }
            }
        val titlePanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(8),dp(12),dp(8))
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(31,42,59))
                cornerRadius=dp(10).toFloat()
                setStroke(dp(1),Color.rgb(124,100,64))
            }
        }
        preview.addView(titlePanel,LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,0,0,dp(8))
        })
        titlePanel.addView(paintedCaption("DIRECTOR X  •  ตรวจภาพก่อนส่ง",16f,Color.WHITE,true),
            LinearLayout.LayoutParams(-1,dp(36)))
        titlePanel.addView(paintedCaption("FACE DETAIL ซ้าย  /  ORIGINAL MASTER ขวา",11f,Color.rgb(231,189,102),true),
            LinearLayout.LayoutParams(-1,dp(26)))
        // Pair face crops (left) with the original MASTER (right) by MASTER ID.
        // Preview layout never changes attachment order, files, or the outgoing intent.
        val imageGrid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        preview.addView(imageGrid)
        data class PreviewRef(val index: Int,val uri: Uri,val label: String,val masterId: String,val face: Boolean)
        val imageRefs=uris.filter { contentResolver.getType(it)?.startsWith("image/")==true }
            .mapIndexed { index,uri ->
                val filename=uri.lastPathSegment?.substringAfterLast('/').orEmpty()
                val label=referenceLabels[referenceId(filename)].orEmpty()
                val face=label.startsWith("ภาพครอปใบหน้า") || referenceId(filename).endsWith("_FACE_CROP",true)
                val masterId=if(face) {
                    label.substringAfter(" จาก MASTER ","").ifBlank { referenceId(filename).removeSuffix("_FACE_CROP") }
                } else referenceId(filename)
                PreviewRef(index,uri,label.ifBlank { filename },masterId,face)
            }
        val used=mutableSetOf<Int>()
        val displayRows=mutableListOf<Pair<PreviewRef?,PreviewRef?>>()
        // Use the MASTER's stable ID rather than adjacency: order may be face-first or master-first.
        imageRefs.filter { !it.face }.forEach { master ->
            if(master.index in used) return@forEach
            val face=imageRefs.firstOrNull { it.face && it.index !in used && it.masterId.equals(master.masterId,true) }
            used.add(master.index)
            if(face!=null) used.add(face.index)
            displayRows.add(face to master)
        }
        imageRefs.filter { it.index !in used }.forEach { ref ->
            used.add(ref.index)
            displayRows.add(if(ref.face) ref to null else null to ref)
        }
        // Cinematic identity cards: a single shared title above each pair prevents
        // labels from being lost inside narrow image columns on smaller devices.
        val ink=Color.rgb(19,24,34)
        val panel=Color.rgb(34,41,54)
        val gold=Color.rgb(231,189,102)
        fun addPreviewCell(row: LinearLayout,ref: PreviewRef?,heading: String) {
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(dp(8),dp(8),dp(8),dp(8))
                background=android.graphics.drawable.GradientDrawable().apply {
                    setColor(ink); cornerRadius=dp(10).toFloat()
                }
            }
            row.addView(card,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply {
                setMargins(dp(3),0,dp(3),0)
            })
            card.addView(paintedCaption(heading,11f,gold,true),
                LinearLayout.LayoutParams(-1,dp(30)))
            if(ref==null) {
                card.addView(TextView(this).apply {
                    text=if(heading.startsWith("FACE DETAIL")) "⚠ ยังไม่ได้แนบภาพใบหน้า\nกลับไปเลือกครอปใบหน้าจาก MASTER ก่อนส่ง" else "ไม่มีภาพอ้างอิง"
                    textSize=12f
                    setTextColor(Color.rgb(255,194,109))
                    setPadding(dp(6),dp(8),dp(6),dp(8))
                })
            } else {
                card.addView(ImageView(this).apply {
                    setImageBitmap(previewBitmap(ref.uri))
                    scaleType=ImageView.ScaleType.FIT_CENTER
                    layoutParams=LinearLayout.LayoutParams(-1,dp(150))
                    contentDescription="${heading} • ${ref.label}"
                    setBackgroundColor(Color.rgb(26,30,39))
                })
                card.addView(TextView(this).apply {
                    text="ภาพที่ ${ref.index+1}"
                    textSize=11f
                    setTextColor(Color.WHITE)
                    setPadding(0,dp(4),0,0)
                })
            }
        }
        displayRows.forEachIndexed { rowIndex,(face,master) ->
            val cropName=face?.label?.takeIf { it.startsWith("ภาพครอปใบหน้า ") }
                ?.removePrefix("ภาพครอปใบหน้า ")?.substringBefore(" จาก MASTER ")?.trim().orEmpty()
            val masterName=master?.label?.trim().orEmpty()
            val personName=cropName.ifBlank {
                masterName.takeIf { it.isNotBlank() && !it.startsWith("MASTER") && !it.startsWith("SUPPORT_") }
                    ?: "ตัวละคร • ${master?.masterId ?: face?.masterId ?: "ไม่ทราบรหัส"}"
            }
            val block=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(dp(8),dp(8),dp(8),dp(10))
                background=android.graphics.drawable.GradientDrawable().apply {
                    setColor(panel); cornerRadius=dp(12).toFloat()
                    setStroke(dp(1),Color.rgb(71,79,95))
                }
            }
            imageGrid.addView(block,LinearLayout.LayoutParams(-1,-2).apply {
                setMargins(0,dp(5),0,dp(5))
            })
            val nameBanner=paintedCaption("${rowIndex+1}.  ${personName}",17f,Color.WHITE,true).apply {
                background=android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.rgb(53,66,85))
                    cornerRadius=dp(7).toFloat()
                }
            }
            block.addView(nameBanner,LinearLayout.LayoutParams(-1,dp(52)).apply {
                setMargins(0,0,0,dp(7))
            })
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            block.addView(row)
            addPreviewCell(row,face,"FACE DETAIL • ใบหน้า")
            addPreviewCell(row,master,"ORIGINAL MASTER")
            block.addView(TextView(this).apply {
                text="MASTER ID  •  ${master?.masterId ?: face?.masterId ?: "ไม่พบ"}"
                textSize=10f
                setTextColor(Color.LTGRAY)
                setPadding(dp(5),dp(6),0,0)
                maxLines=2
            })
        }
        val missingFaceCount=displayRows.count { (face,master) -> master!=null && face==null }
        if(missingFaceCount>0) {
            preview.addView(TextView(this).apply {
                text="⚠ ยังไม่มี FACE DETAIL สำหรับ $missingFaceCount ตัวละคร • ภาพ MASTER ไม่ใช่ภาพครอปใบหน้า หากต้องการแนบ FACE DETAIL ให้กลับไปหน้าจอเลือกภาพและครอปหน้าตรงก่อนส่ง"
                textSize=14f
                setTextColor(Color.rgb(255,194,109))
                setPadding(dp(8),dp(10),dp(8),dp(10))
            },1)
        }
        val unmatchedCrops=imageRefs.filter { it.face && imageRefs.none { master ->
            !master.face && master.masterId.equals(it.masterId,true)
        } }
        if(unmatchedCrops.isNotEmpty()) {
            preview.addView(TextView(this).apply {
                text="⚠ ภาพครอปใบหน้า ${unmatchedCrops.size} ภาพไม่มี MASTER ที่มีรหัสตรงกัน • โปรดตรวจสอบก่อนส่ง"
                textSize=14f
                setTextColor(Color.YELLOW)
            })
        }
        val auditDetails=TextView(this).apply {
            text="รายละเอียดไฟล์ / SHA (แตะเพื่อขยาย)"
            textSize=14f
            setTextColor(Color.CYAN)
            setPadding(0,dp(12),0,dp(8))
        }
        preview.addView(auditDetails)
        val auditText=TextView(this).apply {
            text=imageList
            textSize=12f
            setTextColor(Color.LTGRAY)
            setTextIsSelectable(true)
            visibility=android.view.View.GONE
        }
        preview.addView(auditText)
        auditDetails.setOnClickListener {
            auditText.visibility=if(auditText.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }
        val commandDetails=TextView(this).apply {
            text="คำสั่งสร้างภาพ (แตะเพื่อขยาย)"
            textSize=14f
            setTextColor(Color.CYAN)
            setPadding(0,dp(10),0,dp(8))
        }
        preview.addView(commandDetails)
        val commandText=TextView(this).apply {
            text=text
            textSize=13f
            setTextColor(Color.WHITE)
            setTextIsSelectable(true)
            visibility=android.view.View.GONE
        }
        preview.addView(commandText)
        commandDetails.setOnClickListener {
            commandText.visibility=if(commandText.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }
        AlertDialog.Builder(this).setTitle("DIRECTOR X • ตรวจภาพก่อนส่ง")
            .setView(ScrollView(this).apply {
                isFillViewport=true
                setBackgroundColor(Color.rgb(13,18,27))
                addView(preview)
            })
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
    private fun scenePropContinuity(scene: JSONObject, person: JSONObject): String {
        val character = memoryId(person)
        val scenes = plan?.let { rows(it) }.orEmpty()
        val currentIndex = scenes.indexOfFirst { it === scene || (it.optInt("ep") == scene.optInt("ep") && it.optInt("scene") == scene.optInt("scene")) }
        val storyDay = scene.optString("story_day").trim()
        val group = scene.optString("continuity_group").trim()
        val keys = listOf("backpack", "bag", "props", "accessories", "shoes", "footwear", "shoe_brand", "shoe_model", "shoe_color")
        val previous = if (currentIndex > 0) scenes.take(currentIndex).asReversed().firstNotNullOfOrNull { item ->
            val sameDay = storyDay.isNotBlank() && storyDay == item.optString("story_day").trim()
            val sameGroup = group.isNotBlank() && group == item.optString("continuity_group").trim()
            if (!sameDay && !sameGroup) null else {
                val cast = item.optJSONArray("characters") ?: JSONArray()
                (0 until cast.length()).mapNotNull { cast.optJSONObject(it) }.firstOrNull { memoryId(it) == character }
            }
        } else null
        val locked = keys.mapNotNull { field ->
            val current = person.optString(field).trim()
            val inherited = previous?.optString(field)?.trim().orEmpty()
            val value = if (current.isNotBlank()) current else inherited
            if (value.isBlank()) null else "$field=$value"
        }
        val change = person.optBoolean("prop_change", scene.optBoolean("prop_change", false))
        val details = if (locked.isEmpty()) "No structured prop details recorded; keep the same visible backpack, bag and footwear from the previous scene reference. Match each item’s exact color, silhouette, straps, pockets, shoe upper, sole, laces and any visible logo; do not invent a brand or change the color." else locked.joinToString("; ")
        return if (change) "PROP CHANGE EXPLICITLY ALLOWED: $details. Only change items actually specified by the story."
        else "PROP CONTINUITY LOCK [$character]: $details. Same story day or continuity group means identical carried equipment and accessories. Preserve the previously established backpack color and design, and keep the same shoes: brand/model only when verified in story or approved reference, shoe color, upper, sole, laces, logo placement, wear and wet/muddy condition. Never hallucinate or swap shoe brands. Do not replace, recolor or remove props unless the story explicitly records the action. If a previous scene reference exists, match it."
    }
    private fun characterAccuracyLock(scene: JSONObject, person: JSONObject, masterId: String): String {
        val outfit = sceneWardrobe(scene,person).trim()
        val character = person.optString("name").ifBlank { memoryId(person) }
        val group = scene.optString("continuity_group").trim()
        val continuity = if(group.isNotBlank()) "Group $group: preserve outfit between scenes unless wardrobe_change is true." else "Preserve outfit during the same story day unless explicitly changed."
        return listOf(
            "CHARACTER ACCURACY LOCK [$character]",
            "FACE LOCK: use only $masterId MASTER for this person's facial identity, skin tone, eyes, nose, lips, jaw and hairstyle. No face swapping or blending.",
            "BODY LOCK: preserve established natural body proportions from approved references. Never exaggerate or invent proportions.",
            "WARDROBE LOCK: follow exactly [$outfit], including specified colors, garments, collars, sleeves, fabrics, footwear and accessories. MASTER clothing must not override scene outfit.",
            "CONTINUITY LOCK: $continuity",
            scenePropContinuity(scene, person),
            "SELF-CHECK: compare visible identity to correct MASTER and each visible clothing detail to outfit. Correct only mismatched parts; preserve already-correct details."
        ).joinToString(" | ")
    }
    private fun sceneCastDescription(scene: JSONObject): String {
        val cast = scene.optJSONArray("characters") ?: return ""
        return (0 until cast.length()).joinToString("\n") { i ->
            val person = cast.getJSONObject(i)
            val master=masterFor(person)
            val exported=masterExportName(master,person.optString("master").startsWith("CHARACTER",true)) ?: person.optString("master")
            listOf("${person.optString("name")} → ${referenceId(exported)}",(if(detailedImageProfiles) memoryBrief(person) else ""),
                "ชุด: ${sceneWardrobe(scene,person)}",characterAccuracyLock(scene,person,referenceId(exported)),PerformanceDirection.text(person).takeIf { it.isNotBlank() }?.let { "การแสดง: $it" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" | ") }
    }
    private fun cinematicBeatGuide(p: JSONObject, scene: JSONObject): String {
        val scenes = rows(p)
        val index = scenes.indexOfFirst { key(it) == key(scene) }
        val previous = scenes.getOrNull(index - 1)
        val next = scenes.getOrNull(index + 1)
        val cast = scene.optJSONArray("characters")
        val names = if(cast == null) emptyList() else (0 until cast.length()).mapNotNull { i ->
            cast.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }
        val sceneTypes = listOf("DIALOGUE_EXCHANGE", "PHYSICAL_ACTION", "DISCOVERY", "REACTION", "CONFRONTATION", "INTERRUPTION", "SILENT_TENSION", "REVEAL")
        val explicitType = scene.optString("beat_type").trim()
        val actionText = listOf(scene.optString("action"),scene.optString("activity"),scene.optString("image_prompt")).joinToString(" ").lowercase()
        val type = explicitType.ifBlank {
            when {
                listOf("พบ", "เจอ", "ค้นพบ", "หลักฐาน", "discover", "find").any { it in actionText } -> "DISCOVERY"
                listOf("โกรธ", "ทะเลาะ", "เถียง", "กล่าวหา", "confront").any { it in actionText } -> "CONFRONTATION"
                listOf("ตกใจ", "ชะงัก", "สะดุ้ง", "reaction").any { it in actionText } -> "REACTION"
                listOf("วิ่ง", "เดิน", "หยิบ", "เปิดประตู", "action").any { it in actionText } -> "PHYSICAL_ACTION"
                listOf("เงียบ", "สบตา", "นิ่ง", "silent").any { it in actionText } -> "SILENT_TENSION"
                scene.optString("dialogue").isNotBlank() || scene.optString("dialog").isNotBlank() -> "DIALOGUE_EXCHANGE"
                else -> sceneTypes[(if(index < 0) 0 else index) % sceneTypes.size]
            }
        }
        val speech = scene.optString("dialogue").ifBlank { scene.optString("dialog") }
        val start = scene.optString("start_state").ifBlank { previous?.optString("end_state").orEmpty() }
        val end = scene.optString("end_state")
        val nextHook = next?.optString("start_state").orEmpty()
        val recentModes = scenes.take(if(index < 0) 0 else index).takeLast(3).map { it.optString("beat_type").trim() }.filter { it.isNotBlank() }
        val varietyWarning = if(recentModes.size >= 2 && recentModes.all { it == type })
            "Recent scenes repeatedly use $type. If story allows, vary camera framing, meaningful physical action, listening reaction or silence without altering established events."
        else "Avoid repeating the same static speaking pose or camera framing in adjacent scenes."
        val emotionalCause = scene.optString("emotion").ifBlank { scene.optString("mood") }
        val nextEmotion = next?.optString("emotion")?.ifBlank { next.optString("mood") }.orEmpty()
        val performance = "Emotion now: ${emotionalCause.ifBlank { "derive from current conflict" }}; next beat: ${nextEmotion.ifBlank { "respond to consequence" }}. Show changes through facial reaction, gesture and timing rather than exposition."
        val dialogueEstimate = speech.trim().length
        val dialogueQuality = when {
            dialogueEstimate == 0 -> "No scripted dialogue: preserve purposeful silence, sound and visual action; do not invent speech."
            dialogueEstimate > 85 -> "Dialogue length warning: $dialogueEstimate characters may exceed 8 seconds in Thai. Reduce to the essential exchange while retaining meaning and speaker identity; avoid clipped lines."
            else -> "Dialogue length: $dialogueEstimate characters. Allow natural Thai delivery and reaction pauses within 8 seconds; verify against rendered audio."
        }
        val previousGroup = previous?.optString("continuity_group").orEmpty()
        val currentGroup = scene.optString("continuity_group")
        val continuityAudit = if(currentGroup.isNotBlank() && previousGroup == currentGroup)
            "Same continuity group: preserve physical positions, prop ownership, wardrobe, lighting direction and unresolved actions across the cut."
        else "If time or location changes, establish the transition clearly; never assume an unexplained teleport or prop change."
        val scenePayoff = scene.optString("payoff").ifBlank { scene.optString("reveal") }
        val payoffRule = if(scenePayoff.isNotBlank()) "Required story payoff: $scenePayoff. Show it within the 8-second beat." else "Advance an existing conflict or question; do not invent an unrelated twist."
        val previousDialogue = previous?.optString("dialogue")?.ifBlank { previous.optString("dialog") }.orEmpty().trim()
        val repeatWarning = if(speech.isNotBlank() && previousDialogue.isNotBlank() && speech.trim() == previousDialogue)
            "Repeated dialogue matches the preceding scene exactly. Keep only if intentional; otherwise advance the exchange without inventing unrelated plot."
        else "Avoid repeated solo speeches and identical responses across consecutive scenes."
        val endStateQuality = if(end.isBlank())
            "No explicit end_state supplied: finish on a clear visible character position, prop state and emotional reaction that can become the next scene start."
        else "Required end_state: $end. Preserve it as the handoff into the next clip."
        val interaction = if(names.size > 1)
            "Cast: ${names.joinToString(", ")}. Avoid consecutive solo monologues; use natural question-answer, interruption, reaction, or meaningful silent interaction when supported by the story. Assign only one speaker per utterance."
        else "Single visible character: favor meaningful physical action, discovery, off-screen sound, or silent reaction over repetitive monologue; do not invent extra characters."
        return listOf(
            "DIRECTOR X CINEMATIC STORY ENGINE — EXACTLY 8 SECONDS",
            "Scene dramatic mode: $type (adapt to story logic; do not force a mode that contradicts the written scene).",
            "Timing: 0-2s visual hook or initiating action; 2-5s core exchange or event; 5-7s reaction or consequence; 7-8s meaningful end beat. Adjust timing naturally, never cram too many actions.",
            "Dialogue: $speech. Keep spoken Thai concise enough to fit actual 8-second delivery including pauses. Do not add unscripted speeches. If too long, preserve intent and shorten rather than cutting mid-sentence.",
            "Dialogue quality: $dialogueQuality",
            "Dialogue repetition: $repeatWarning",
            "End-state validation: $endStateQuality",
            "Interaction: $interaction",
            "Continuity audit: $continuityAudit",
            "Payoff: $payoffRule",
            "Continuity: previous scene end -> current start [${start.ifBlank { "follow preceding action naturally" }}]. Current end [${end.ifBlank { "finish with a clear state" }}]. Next scene setup [${nextHook.ifBlank { "carry forward the dramatic consequence" }}]. Maintain props, positions, emotions and knowledge.",
            "Variety: $varietyWarning Alternate speaking, listening, movement, discovery and silence; choose camera movement only when motivated by the action.",
            "Performance: $performance",
            "End beat: create a cause, decision, reveal or emotional question that advances the next scene, without inventing a new plot twist every clip.",
            "Image output remains the FIRST FRAME / START STATE of this scene; never depict the full eight-second sequence in one still."
        ).joinToString("\n")
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
        val continuity=listOf(cinematicBeatGuide(p,scene),scene.optString("start_state"),
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
    private fun savedCoverSelection(story: JSONObject,folder: File): Map<String,Set<String>> {
        val eps=story.optJSONArray("episodes") ?: JSONArray()
        val scopes=listOf("STORY")+(0 until eps.length()).map { "EP%02d".format(eps.getJSONObject(it).getInt("ep")) }
        return scopes.distinct().mapNotNull { scope ->
            val key="cover_cast_${folder.name}_$scope"
            if(prefs.contains(key)) scope to prefs.getStringSet(key,emptySet()).orEmpty().toSet() else null
        }.toMap()
    }
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
            val coverSelection=if(current!=null) savedCoverSelection(current,destination) else emptyMap()
            val wardrobeMasterIds=if(current!=null) {
                val eps=current.getJSONArray("episodes")
                (0 until eps.length()).flatMap { i ->
                    val number=eps.getJSONObject(i).getInt("ep")
                    SimpleProductionCommands.imageTasks(current,number,coverSelection)
                }.associate { task -> task.id to task.references.mapNotNull { person ->
                    val id=referenceId(person.optString("master"))
                    val file=runCatching { masterFor(person) }.getOrNull() ?: return@mapNotNull null
                    val digest=runCatching { MasterLibrary.digest(file.readBytes()) }.getOrNull() ?: return@mapNotNull null
                    if(prefs.getBoolean(wardrobeSourceKey(destination.name,id,digest,person),WardrobeEvidence.useMasterOutfit(person.optString("outfit")))) id else null
                }.toSet() }
            } else emptyMap()
            val storyRevision=if(current!=null) File(destination,"story.json").takeIf { it.isFile }?.let { EpisodeMedia.digest(it) } else null
            val memory=JSONObject()
            sources.forEach { (id,file) -> memory.put(id,characterMemory.read(id,file)) }
            background {
                contentResolver.openOutputStream(uri)!!.use { output ->
                    if(current!=null) SimpleProductionCommands.write(output,current,coverSelection,wardrobeMasterIds)
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
    private val propCatalog = listOf("ดาบเงาจันทรา", "ดาบเวทมนตร์แสงฟ้า", "ดาบอัศวินสุริยัน", "ดาบมังกรเพลิง", "คทาดวงดาวและกาลเวลา", "คทาผู้พิทักษ์แสง", "คทาผลึกเหมันต์", "หอกผู้พิทักษ์นภา", "โล่อัศวินศักดิ์สิทธิ์", "ดาบพลังงานจักรกล", "ธนูผู้พิทักษ์พงไพร", "โล่พลังงานไซเบอร์", "คทาปราบวิญญาณ", "ดาบอาคมโบราณ", "มีดหมอพิธีกรรม", "ถุงมือควบคุมพลัง", "ตะเกียงส่องวิญญาณ", "หนังสือผนึกคำสาป", "กุญแจประตูมิติ", "ลูกประคำและเครื่องรางป้องกัน")
    private fun propId(index: Int) = "PROP_%02d_MASTER".format(index+1)
    private fun characterPropKey(master: String) = "character_prop_${project.name}_${referenceId(master).lowercase()}"
    private fun characterProp(master: String) = prefs.getString(characterPropKey(master), "").orEmpty()
    private fun characterPropMenu(story: JSONObject) {
        val people=rows(story).flatMap { FlowReferencePrompt.people(it) }.distinctBy { referenceId(it.optString("master")).lowercase() }.filter { it.optString("master").isNotBlank() }
        if(people.isEmpty()) return notice("ไม่พบตัวละครที่มี MASTER ในเรื่อง")
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        panel.addView(TextView(this).apply { text="ผูกอุปกรณ์ประจำตัวละคร • บันทึกเฉพาะเรื่องนี้ • ไม่ได้หมายความว่าต้องปรากฏทุกซีน\nกำหนดแล้วให้ใช้เมนู AI วิเคราะห์บทเพื่อตรวจแต่ละซีน" })
        val spinners=people.map { person ->
            val master=referenceId(person.getString("master"))
            panel.addView(TextView(this).apply { text="${person.optString("name")} • $master"; setTypeface(null,Typeface.BOLD) })
            Spinner(this).apply {
                adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("ไม่กำหนด")+propCatalog.mapIndexed { index,name -> "$name • ${propId(index)}" })
                setSelection(propCatalog.indices.indexOfFirst { propId(it)==characterProp(master) }.let { if(it<0) 0 else it+1 })
                panel.addView(this)
            }
        }
        AlertDialog.Builder(this).setTitle("อุปกรณ์ประจำตัวละคร").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("บันทึก") { _,_ ->
            val editor=prefs.edit()
            people.forEachIndexed { index,person -> val selected=spinners[index].selectedItemPosition; editor.putString(characterPropKey(person.getString("master")),if(selected<=0) "" else propId(selected-1)) }
            editor.apply(); notice("บันทึกอุปกรณ์ประจำตัวละครแล้ว • ตรวจซีนด้วยเมนู AI ก่อนสร้างภาพ")
        }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun propReviewStamp(scene: JSONObject): String = ReviewFingerprints.props(scene)+":"+image(scene).let { if(it.isFile) runCatching { MasterLibrary.digest(it.readBytes()) }.getOrDefault("image-unreadable") else "no-image" }
    private fun propReviewStatus(scene: JSONObject): String {
        val pref="props_review_${project.name}_${key(scene)}"
        var saved=prefs.getString(pref,"").orEmpty()
        val current=propReviewStamp(scene)

        return when { saved.isBlank() -> "ยังไม่ได้ตรวจ"; saved==current -> "ผู้ใช้ตรวจบทแล้ว"; else -> "บทเปลี่ยน • ตรวจอีกครั้ง" }
    }
    private fun sameOpenStory(snapshot: String): Boolean {
        val current=plan ?: return false
        // rows adds episode numbers for display; normalize both snapshots before comparison.
        rows(current)
        return StorySnapshot.same(snapshot,current)
    }
    private fun suggestSceneObjects(story: JSONObject) {
        if(GeminiStoryService.running) return notice("กรุณารอหรือหยุดงานเขียนบทก่อนวิเคราะห์สิ่งของ")
        val providers=StoryProvider.eligible(this).filter { it.hasKey(this) }
        if(providers.isEmpty()) return notice("กรุณาตั้งค่า API เขียนบทก่อนใช้ AI")
        val scenes=rows(story)
        if(scenes.isEmpty()) return notice("ยังไม่มีฉากให้วิเคราะห์")
        val projectId=project.name
        val snapshot=story.toString()
        val fantasy=ChannelFeaturePolicy.hasFantasyTools(story.optString("channel"))
        val bindings=if(fantasy) scenes.flatMap { FlowReferencePrompt.people(it) }.distinctBy { it.optString("master") }.mapNotNull { person ->
            characterProp(person.optString("master")).takeIf { it.isNotBlank() }?.let { "${person.optString("name")}: $it" }
        }.joinToString("\n") else ""
        val context=JSONArray(scenes.map { scene -> JSONObject().put("scene_id",key(scene)).put("action",scene.optString("action")).put("start_state",scene.optString("start_state")).put("end_state",scene.optString("end_state")).put("props",scene.optString("props")).put("characters",JSONArray(FlowReferencePrompt.people(scene).map { it.optString("name") })) })
        if(context.toString().length>100000) return notice("บทมีขนาดใหญ่เกินไปสำหรับวิเคราะห์ครั้งเดียว • ตรวจทีละฉากได้")
        notice("กำลังวิเคราะห์สิ่งของจากบท • ไม่มีการเปลี่ยนบทจนกว่าจะเลือกและบันทึก")
        Thread {
            val properties=JSONObject()
            listOf("scene_id","item","holder","details","continuity_id","evidence").forEach { properties.put(it,JSONObject().put("type","string")) }
            val item=JSONObject().put("type","object").put("properties",properties).put("required",JSONArray(listOf("scene_id","item","holder","details","continuity_id","evidence")))
            val schema=JSONObject().put("type","object").put("properties",JSONObject().put("items",JSONObject().put("type","array").put("items",item))).put("required",JSONArray().put("items"))
            val prompt="""Extract objects explicitly present in the supplied scene descriptions. Return items with scene_id, item (Thai when possible), holder (exact visible character name or empty), details (only explicit properties), continuity_id (short stable ID only when the same object is supported by story; otherwise empty), evidence (exact verbatim substring of action, start_state, end_state or props). Distinguish initial objects from later actions; do not claim any image has been checked. If a generic tool is unspecified, call it เครื่องมือไม่ระบุชนิด and do not invent a type. Do not invent props, holders, colors, links or events. Fantasy character bindings are hints, not evidence of appearance. Return JSON only.
FANTASY CHARACTER BINDINGS:
$bindings
SCENES:
$context"""
            var answer: JSONObject?=null
            for(provider in providers) {
                answer=runCatching { StoryClientFactory.create(provider,provider.key(this),provider.model(this),{ false },automaticRetry=false,context=this@StudioActivity).generate(prompt,schema) }.getOrNull()?.takeIf { it.optJSONArray("items")!=null }
                if(answer!=null) break
            }
            val result=answer
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                if(result==null) { notice("AI วิเคราะห์ไม่ได้ กรุณาตรวจ API แล้วลองใหม่ • บทเดิมไม่เปลี่ยน"); return@runOnUiThread }
                if(project.name!=projectId || !sameOpenStory(snapshot)) { notice("เรื่องเปลี่ยนระหว่างวิเคราะห์ กรุณาเริ่มใหม่"); return@runOnUiThread }
                val allowed=scenes.associateBy { key(it) }
                val proposals=PropSuggestionReview.valid(result,allowed)
                if(proposals.isEmpty()) { notice("ไม่พบข้อเสนอที่มีหลักฐานตรงกับบท • ตรวจหรือเพิ่มเองรายฉากได้"); return@runOnUiThread }
                // valid() has already checked that evidence is a verbatim substring of this scene.
                // Accept evidence from action/end_state/props as well as start_state.
                // Do not persist inferred color, brand, model or continuity without literal evidence.
                val confirmed=proposals.map { row ->
                    val evidence=row.optString("evidence")
                    val safe=JSONObject(row.toString())
                    if(!evidence.contains(row.optString("details"),ignoreCase=true) || row.optString("details").isBlank())
                        safe.put("details","")
                    // Continuity is a cross-scene claim; do not approve it from one scene's text.
                    safe.put("continuity_id","")
                    safe
                }
                val needsReview=proposals.filter { row ->
                    row.optString("continuity_id").isNotBlank() ||
                        (row.optString("details").isNotBlank() && !row.optString("evidence").contains(row.optString("details"),ignoreCase=true))
                }
                fun thaiItem(v: String): String = when(v.trim().lowercase()) {
                    "megaphone" -> "โทรโข่ง"
                    "topo map", "topographic map" -> "แผนที่ภูมิประเทศ"
                    "backpack" -> "กระเป๋าเป้"
                    "radio" -> "วิทยุสื่อสาร"
                    "communication devices" -> "อุปกรณ์สื่อสาร"
                    else -> v.ifBlank { "ยังไม่ระบุชื่อสิ่งของ" }
                }
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                panel.addView(TextView(this).apply {
                    text="AI วิเคราะห์จากบทแล้ว • กดยืนยันครั้งเดียวเพื่อบันทึกเฉพาะรายการที่มีหลักฐานตรงบท\n" +
                        "พร้อมบันทึก ${confirmed.size} รายการ • รายละเอียด/กลุ่มที่ยังไม่ยืนยัน ${needsReview.size} รายการ\n" +
                        "ยังไม่ได้ตรวจภาพจริง และไม่ถือว่าอนุมัติ MASTER หรือภาพ"
                    setPadding(0,0,0,dp(12))
                })
                proposals.forEach { row ->
                    val safe=true
                    val id=row.optString("scene_id")
                    val holder=row.optString("holder").ifBlank { "ยังไม่ระบุผู้ใช้" }
                    val item=thaiItem(row.optString("item"))
                    panel.addView(TextView(this).apply {
                        text="✓ พร้อมบันทึกชื่อสิ่งของจากบท"+
                            "\n$id • $item\nผู้ใช้: $holder\n" +
                            "รายละเอียด AI เสนอ (ยังไม่ยืนยัน): "+row.optString("details").ifBlank { "ยังไม่ระบุ" }+
                            "\nกลุ่มต่อเนื่อง AI เสนอ (ยังไม่ยืนยัน): "+row.optString("continuity_id").ifBlank { "ยังไม่ระบุ" }
                        setPadding(dp(12),dp(12),dp(12),dp(12))
                    })
                }
                val feedback=TextView(this).apply { setPadding(0,dp(8),0,dp(8)) }
                panel.addView(feedback)
                val dialog=AlertDialog.Builder(this).setTitle("✨ ตรวจสิ่งของที่ AI เสนอ • ${proposals.size} รายการ")
                    .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("ยืนยันและบันทึก ${confirmed.size} รายการ",null).setNegativeButton("ยกเลิก",null).showDirector()
                dialog.getButton(-1).setOnClickListener {
                    if(project.name!=projectId || !sameOpenStory(snapshot)) { notice("เรื่องเปลี่ยนระหว่างตรวจ กรุณาเริ่มใหม่"); return@setOnClickListener }
                    val selected=confirmed
                    if(selected.isEmpty()) { feedback.text="ไม่มีรายการที่มีหลักฐานตรงบทเพียงพอ กรุณาตรวจรายละเอียดก่อนบันทึก"; return@setOnClickListener }
                    runCatching {
                        val previous=plan ?: error("ไม่พบเรื่อง")
                        val candidate=JSONObject(previous.toString())
                        val targets=rows(candidate).associateBy { key(it) }
                        selected.groupBy { it.optString("scene_id") }.forEach { (id,items) -> PropSuggestionReview.apply(targets.getValue(id),items) }
                        plan=candidate
                        try { saveStory() } catch(e: Exception) { plan=previous; throw e }
                    }.onSuccess {
                        prefs.edit().putLong("flow_props_saved_at_${project.name}",System.currentTimeMillis()).apply()
                        dialog.dismiss()
                        render()
                        notice("บันทึกสิ่งของจากบท ${selected.size} รายการแล้ว • เปิดคำสั่ง Flow")
                        commandCardsMenu()
                    }.onFailure { notice(it.message ?: "บันทึกไม่ได้ • บทเดิมไม่เปลี่ยน") }
                }
            }
        }.start()
    }
    private fun reviewSceneProps(story: JSONObject) {
        val scenes=rows(story)
        if(scenes.isEmpty()) return notice("ยังไม่มีฉากให้ตรวจ")
        val options=scenes.map { scene ->
            val props=scene.optString("props").trim().ifBlank { "ยังไม่ระบุสิ่งของในบท" }
            "${key(scene)} • ${propReviewStatus(scene)}\nสิ่งของ: ${props.take(100)}"
        }
        choiceMenu("ขั้นตอน 2 • ตรวจสิ่งของในบท\nเลือกฉากหรือตัวช่วย AI",listOf("✨ ให้ AI เสนอสิ่งของและความต่อเนื่อง • ตรวจและเลือก")+options) { selected ->
            if(selected==0) { suggestSceneObjects(story); return@choiceMenu }
            val scene=scenes[selected-1]
            val cast=FlowReferencePrompt.people(scene).joinToString("、") { it.optString("name") }.ifBlank { "ยังไม่ระบุตัวละคร" }
            val start=scene.optString("start_state").ifBlank { "ยังไม่ระบุ" }
            val editor=EditText(this).apply {
                hint="ระบุสิ่งของและผู้ใช้ เช่น กวิน: จอบ; เพ็ญศรี: ถังน้ำ"
                setText(scene.optString("props")); minLines=3
            }
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
            panel.addView(TextView(this).apply { text="ตัวละคร: $cast\nต้นฉากจากบท: $start\n\nระบุชื่อสิ่งของและผู้ใช้ตามบท หากไม่ชัดเจนให้ระบุว่ายังไม่ทราบ\n\n📷 ยังไม่ได้ตรวจภาพจริง • ตรวจว่าสิ่งของอยู่ในภาพเริ่มต้นด้วยตนเอง\nข้อมูลนี้ใช้ประกอบคำสั่งภาพ ไม่เปลี่ยนภาพที่สร้างไว้แล้ว • หากเปลี่ยนการกระทำ ให้แก้รายละเอียดฉากและคำสั่ง Flow ด้วย" })
            panel.addView(editor)
            val dialog=AlertDialog.Builder(this).setTitle("${key(scene)} • ตรวจสิ่งของในบท").setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("บันทึกและยืนยันตรวจบท",null).setNeutralButton("แก้รายละเอียดฉาก",null).setNegativeButton("กลับ",null).showDirector()
            dialog.getButton(-3).setOnClickListener { dialog.dismiss(); editScene(scene) }
            dialog.getButton(-1).setOnClickListener {
                if(plan!==story) { notice("เรื่องเปลี่ยนแล้ว กรุณาเปิดตรวจใหม่"); return@setOnClickListener }
                val old=scene.optString("props")
                scene.put("props",editor.text.toString().trim())
                runCatching { saveStory() }.onSuccess {
                    val statusSaved=prefs.edit().putString("props_review_${project.name}_${key(scene)}",propReviewStamp(scene)).putLong("flow_props_saved_at_${project.name}",System.currentTimeMillis()).commit()
                    val imageNote=if(image(scene).isFile) "ฉาก ${key(scene)} มีภาพบันทึกไว้ • ตรวจภาพเดิมและคำสั่ง Flow อีกครั้ง" else "ฉาก ${key(scene)} ยังไม่พบภาพที่บันทึกในแอป"
                    dialog.dismiss(); render()
                    showPropsNext(if(statusSaved) "บันทึกและยืนยันตรวจบท ${key(scene)} แล้ว\n$imageNote" else "บันทึกบทแล้ว แต่บันทึกสถานะตรวจไม่สำเร็จ กรุณาตรวจอีกครั้ง")
                }.onFailure { scene.put("props",old); notice(it.message ?: "บันทึกไม่ได้") }
            }
        }
    }
    // Round 1: generate all episode Flow commands in one action; never claim video was rendered.
    private fun autoFlowEpisode(story: JSONObject, number: Int) {
        val tasks=runCatching { SimpleProductionCommands.videoTasks(story,number) }.getOrElse {
            notice("ยังสร้างคำสั่ง Flow ไม่ได้: ${it.message ?: "ตรวจบทอีกครั้ง"}")
            return
        }
        if(tasks.isEmpty()) { notice("EP.$number ยังไม่มีฉากสำหรับคำสั่ง Flow"); return }
        val issues=ProductionCommandCheck.issues(story)
        if(issues.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("ต้องตรวจบทก่อนสร้างคำสั่ง Flow")
                .setMessage("พบข้อควรแก้ ${issues.size} รายการ\n"+issues.take(8).joinToString("\n"))
                .setPositiveButton("เปิดรายการตรวจ") { _,_ -> productionCheckMenu(story) }
                .setNegativeButton("กลับ",null).showDirector()
            return
        }
        val content=tasks.mapIndexed { index,task ->
            "ฉาก ${index+1}/${tasks.size} • ${task.id}\n"+
                SimpleProductionCommands.block(task,index,false)
        }.joinToString("\n\n--------------------\n\n")
        val file=File(project,"EP%02d_FLOW_AUTO.txt".format(number))
        val saved=runCatching {
            val temp=File(project,file.name+".tmp")
            temp.writeText(content,Charsets.UTF_8)
            if(!temp.renameTo(file)) {
                temp.delete()
                error("ไม่สามารถบันทึกไฟล์คำสั่ง Flow ได้")
            }
            prefs.edit().putInt("auto_flow_count_${project.name}_$number",tasks.size)
                .putLong("auto_flow_saved_at_${project.name}_$number",System.currentTimeMillis()).commit()
        }.getOrElse { notice("บันทึกคำสั่ง Flow ไม่สำเร็จ: ${it.message}"); return }
        if(!saved) { notice("บันทึกสถานะคำสั่ง Flow ไม่สำเร็จ"); return }
        val stamp=java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale("th","TH")).format(java.util.Date())
        val dialog=showFlowCode("คำสั่ง Flow EP.$number • ${tasks.size} ฉาก",
            "สร้างคำสั่ง Flow แล้ว ${tasks.size}/${tasks.size} ฉาก\nบันทึก: $stamp\nไฟล์: ${file.name}\nสถานะ: พร้อมตรวจและคัดลอก (ยังไม่ได้สร้างวิดีโอ)\n\n$content",
            file.name)
        dialog.getButton(-2)?.text="ปิด"
    }

    // Phase 1: one-tap batch generation; existing episode files are never overwritten.
    private fun autoFlowAllEpisodes(story: JSONObject, autoContinue: Boolean = false) {
        val issues=ProductionCommandCheck.issues(story)
        if(issues.isNotEmpty()) {
            AlertDialog.Builder(this).setTitle("ตรวจบทก่อนสร้าง Flow ทั้งเรื่อง")
                .setMessage("พบข้อควรแก้ ${issues.size} รายการ")
                .setPositiveButton("เปิดรายการตรวจ") { _,_ -> productionCheckMenu(story) }
                .setNegativeButton("ปิด",null).showDirector()
            return
        }
        val eps=story.optJSONArray("episodes") ?: return notice("ยังไม่มีตอน")
        var created=0
        var skipped=0
        val failures=mutableListOf<String>()
        for(i in 0 until eps.length()) {
            val ep=eps.optJSONObject(i)?.optInt("ep",0) ?: continue
            if(ep<=0) continue
            val file=File(project,"EP%02d_FLOW_AUTO.txt".format(ep))
            if(file.isFile) { skipped++; continue }
            runCatching {
                val tasks=SimpleProductionCommands.videoTasks(story,ep)
                require(tasks.isNotEmpty()) { "ไม่มีฉาก" }
                val content=tasks.mapIndexed { index,task ->
                    "ฉาก ${index+1}/${tasks.size} • ${task.id}\n"+
                        SimpleProductionCommands.block(task,index,false)
                }.joinToString("\n\n--------------------\n\n")
                val temp=File(project,file.name+".tmp")
                try {
                    temp.writeText(content,Charsets.UTF_8)
                    check(temp.renameTo(file)) { "บันทึกไฟล์ไม่สำเร็จ" }
                } finally { if(temp.exists()) temp.delete() }
                prefs.edit().putInt("auto_flow_count_${project.name}_$ep",tasks.size)
                    .putLong("auto_flow_saved_at_${project.name}_$ep",System.currentTimeMillis()).commit()
                created++
            }.onFailure { failures.add("EP.$ep • ${it.message ?: "สร้างไม่สำเร็จ"}") }
        }
        val summary="สร้างใหม่ $created ตอน • ข้ามไฟล์เดิม $skipped ตอน • ผิดพลาด ${failures.size} ตอน"+
            if(failures.isEmpty()) "" else "\n\n"+failures.take(8).joinToString("\n")
        if (autoContinue && failures.isEmpty()) {
            // Resume directly at the first unfinished image batch. This does not call an image API.
            imageBatchFivePlan(story)
        } else {
            AlertDialog.Builder(this).setTitle("Flow ทั้งเรื่อง • ผลการทำงาน")
                .setMessage(summary+"\n\nไฟล์คำสั่งพร้อมตรวจและคัดลอก ยังไม่ได้ส่งไปสร้างวิดีโอ")
                .setPositiveButton("ทำงานต่อ") { _,_ ->
                    if (failures.isEmpty()) imageBatchFivePlan(story) else commandCardsMenu()
                }
                .setNegativeButton("ปิด",null).showDirector()
        }
    }

    // Planning only: ChatGPT Plus does not provide an in-app image-generation API.
    private fun verifiedFlowFile(episode: Int): Boolean {
        val file = File(project, "EP%02d_FLOW_AUTO.txt".format(episode))
        return file.isFile && file.length() > 0L &&
            runCatching { file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readLine() != null } }
                .getOrDefault(false)
    }

    private fun verifiedSceneImage(episode: Int, scene: Int): Boolean {
        val file = File(project, "images/EP%02d_SC%03d.img".format(episode, scene))
        if (!file.isFile || file.length() <= 0L) return false
        return runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.path, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)
    }

    private fun imageBatchFivePlan(story: JSONObject) {
        val eps=story.optJSONArray("episodes") ?: return notice("ยังไม่มีตอน")
        val lines=mutableListOf<String>()
        var totalScenes=0
        var totalPending=0
        var totalBatches=0
        for(i in 0 until eps.length()) {
            val episode=eps.optJSONObject(i) ?: continue
            val number=episode.optInt("ep",i+1)
            val scenes=episode.optJSONArray("scenes")
            val sceneIds=(0 until (scenes?.length() ?: 0)).mapNotNull { index ->
                scenes?.optJSONObject(index)?.optInt("scene",0)?.takeIf { it>0 }
            }
            if(sceneIds.size != (scenes?.length() ?: 0) || sceneIds.distinct().size != sceneIds.size) {
                lines.add("EP.$number • หมายเลขซีนไม่ถูกต้องหรือซ้ำ กรุณาตรวจบท")
                continue
            }
            val verified=sceneIds.filter { verifiedSceneImage(number, it) }.toSet()
            val snapshot=ProductionStateEngine.evaluate(
                sceneIds, ProductionCommandCheck.issues(story).isEmpty(), verified,
                verifiedFlowFile(number)
            )
            val pending=snapshot.missingSceneIds
            totalScenes+=sceneIds.size
            totalPending+=pending.size
            val batches=pending.chunked(5)
            totalBatches+=batches.size
            val ranges=batches.mapIndexed { index, group ->
                "ชุด ${index+1}: ${group.joinToString(", ")}"
            }.joinToString("\n")
            lines.add("EP.$number • ตรวจไฟล์ภาพจริงผ่าน ${snapshot.verifiedImages}/${sceneIds.size} • ค้าง ${pending.size} ซีน • ${batches.size} ชุด"+
                if(ranges.isBlank()) " (ไม่มีงานค้าง)" else "\n$ranges")
        }
        val completed = totalScenes - totalPending
        AlertDialog.Builder(this).setTitle("DIRECTOR X • คิวภาพ")
            .setMessage("เสร็จ $completed/$totalScenes ฉาก • เหลือ $totalPending • $totalBatches ชุด\n\n"+
                lines.joinToString("\n")+
                "\n\nระบบยังไม่สร้างภาพผ่าน AI API")
            .setPositiveButton("นำเข้าภาพฉาก") { _,_ ->
                if(plan == null) notice("กรุณาเปิดโครงการก่อนนำเข้าภาพ")
                else launchSmartImagePicker()
            }
            .setNeutralButton("เปิดคำสั่ง Flow") { _,_ -> commandCardsMenu() }
            .setNegativeButton("ปิด",null).showDirector()
    }

    private fun phaseOneWorkflowStatus(story: JSONObject) {
        val eps=story.optJSONArray("episodes")
        val total=eps?.length() ?: 0
        val saved=(0 until total).count { index ->
            val ep=eps?.optJSONObject(index)?.optInt("ep",0) ?: 0
            ep>0 && verifiedFlowFile(ep)
        }
        var imageTotal=0
        var imageCompleted=0
        var invalidSceneLists=0
        for(index in 0 until total) {
            val ep=eps?.optJSONObject(index) ?: continue
            val number=ep.optInt("ep",index+1)
            val scenes=ep.optJSONArray("scenes")
            val ids=(0 until (scenes?.length() ?: 0)).mapNotNull { i ->
                scenes?.optJSONObject(i)?.optInt("scene",0)?.takeIf { it>0 }
            }
            if(ids.size != (scenes?.length() ?: 0) || ids.distinct().size != ids.size) {
                invalidSceneLists++
                continue
            }
            imageTotal+=ids.size
            imageCompleted+=ids.count { verifiedSceneImage(number,it) }
        }
        val issues=ProductionCommandCheck.issues(story)
        val next=when {
            issues.isNotEmpty() || invalidSceneLists>0 -> "ตรวจและแก้บทก่อนดำเนินการ"
            total==0 -> "สร้างหรือนำเข้าบทที่มีตอน"
            saved<total -> "สร้างชุดคำสั่ง Flow ที่ยังขาด • ระบบข้ามไฟล์เดิม"
            imageCompleted<imageTotal -> "ตรวจคิวภาพ 5 ซีน และดำเนินการกับผู้ให้บริการภาพ"
            else -> "เปิดคำสั่ง Flow ที่บันทึกไว้เพื่อตรวจและนำไปใช้"
        }
        AlertDialog.Builder(this).setTitle("DIRECTOR X • สถานะงานรวม")
            .setMessage("บท: ${if(total>0) "มี $total ตอน" else "ยังไม่มีตอน"}\n"+
                "คำสั่ง Flow: $saved/$total ตอน\n"+
                "ภาพที่ตรวจสอบไฟล์จริง: $imageCompleted/$imageTotal ซีน\n"+
                "ตอนที่หมายเลขซีนผิดพลาด: $invalidSceneLists\n"+
                "ข้อควรแก้ในบท: ${issues.size} รายการ\n\n"+
                "ขั้นตอนถัดไป: $next\n\n"+
                "หมายเหตุ: ตรวจสอบไฟล์ภาพที่เปิดอ่านได้จริงแล้ว • ระบบยังไม่สร้างวิดีโอ")
            .setPositiveButton("▶ ทำงานต่อทันที") { _,_ ->
                if(issues.isNotEmpty() || invalidSceneLists>0) productionCheckMenu(story)
                else if(saved<total) autoFlowAllEpisodes(story, autoContinue = true)
                else imageBatchFivePlan(story)
            }
            .setNegativeButton("ปิด",null).showDirector()
    }

    // Fast path: one tap chooses the next safe existing workflow without a status dialog.
    // Persist only the last requested project. Every resume re-checks source artifacts.
    private fun rememberProductionProject() {
        prefs.edit().putString("production_resume_project", project.name).apply()
    }

    private fun continueFlowOneTap(story: JSONObject) {
        rememberProductionProject()
        val episodes = story.optJSONArray("episodes") ?: return notice("ยังไม่มีตอนในบท")
        if (episodes.length() == 0) return notice("ยังไม่มีตอนในบท")
        if (ProductionCommandCheck.issues(story).isNotEmpty()) {
            productionCheckMenu(story)
            return
        }
        for (index in 0 until episodes.length()) {
            val episode = episodes.optJSONObject(index) ?: return productionCheckMenu(story)
            val number = episode.optInt("ep", 0)
            val scenes = episode.optJSONArray("scenes") ?: return productionCheckMenu(story)
            val ids = (0 until scenes.length()).mapNotNull { i ->
                scenes.optJSONObject(i)?.optInt("scene", 0)?.takeIf { it > 0 }
            }
            if (number <= 0 || ids.size != scenes.length() || ids.distinct().size != ids.size) {
                productionCheckMenu(story)
                return
            }
            if (!verifiedFlowFile(number)) {
                autoFlowAllEpisodes(story, autoContinue = true)
                return
            }
        }
        // Re-evaluate artifacts on every resume; saved flags alone never imply completion.
        val incompleteImages = (0 until episodes.length()).any { index ->
            val episode = episodes.getJSONObject(index)
            val ep = episode.optInt("ep", 0)
            val scenes = episode.optJSONArray("scenes")
            (0 until (scenes?.length() ?: 0)).any { i ->
                val scene = scenes?.optJSONObject(i)?.optInt("scene", 0) ?: 0
                scene <= 0 || !verifiedSceneImage(ep, scene)
            }
        }
        if (incompleteImages) {
            imageBatchFivePlan(story)
        } else {
            notice("ตรวจสอบภาพครบทุกฉากแล้ว • ยังไม่มีระบบยืนยันการสร้างวิดีโออัตโนมัติ")
        }
    }

    private fun commandCardsMenu() {
        val story=plan ?: return notice("สร้างหรือนำเข้าบทก่อนครับ")
        if(story.optString("aspect_ratio")!="9:16") { landscapeCopyMenu(); return }
        val eps=story.getJSONArray("episodes")
        val channel=ChannelCatalog.currentChannel(story.optString("channel"))
        val storyPassed = eps.length() > 0 && ProductionCommandCheck.issues(story).isEmpty()
        val savedFlowCount = (0 until eps.length()).count { index ->
            val number = eps.getJSONObject(index).optInt("ep", 0)
            number > 0 && verifiedFlowFile(number)
        }
        val flowPassed = storyPassed && savedFlowCount == eps.length()
        val imageSceneCount = (0 until eps.length()).sumOf { index ->
            eps.getJSONObject(index).optJSONArray("scenes")?.length() ?: 0
        }
        val verifiedImageCount = (0 until eps.length()).sumOf { index ->
            val ep = eps.getJSONObject(index)
            val number = ep.optInt("ep", 0)
            val scenes = ep.optJSONArray("scenes")
            (0 until (scenes?.length() ?: 0)).count { i ->
                val id = scenes?.optJSONObject(i)?.optInt("scene", 0) ?: 0
                number > 0 && id > 0 && verifiedSceneImage(number, id)
            }
        }
        val imageStatus = when {
            !storyPassed -> "🔒 รอตรวจบท"
            imageSceneCount > 0 && verifiedImageCount == imageSceneCount -> "✅ COMPLETED"
            verifiedImageCount > 0 -> "▶ IN PROGRESS"
            else -> "⏳ PENDING"
        }

        val lastProject = prefs.getString("production_resume_project", null)
        val resumeLabel = if(lastProject == project.name) "▶ ทำงานต่อจากครั้งก่อน • ตรวจไฟล์จริงใหม่"
            else "▶ ทำงานต่ออัตโนมัติ • เลือกงานที่ยังค้าง"
        val options=listOf(
            resumeLabel,
            "${if (storyPassed) "✅ ผ่านแล้ว" else "▶ ต้องตรวจ"} • ตรวจบทและสถานะ",
            "$imageStatus • ภาพฉาก $verifiedImageCount/$imageSceneCount",
            "${if (flowPassed) "✅ ผ่านแล้ว" else if (storyPassed) "⏳ รอสร้าง" else "🔒 รอตรวจบท"} • คำสั่ง Flow $savedFlowCount/${eps.length()} ตอน"
        )+(0 until eps.length()).map { i ->
            val ep=eps.getJSONObject(i).getInt("ep")
            val count=if(File(project,"EP%02d_FLOW_AUTO.txt".format(ep)).isFile) prefs.getInt("auto_flow_count_${project.name}_$ep",0) else 0
            val at=if(File(project,"EP%02d_FLOW_AUTO.txt".format(ep)).isFile) prefs.getLong("auto_flow_saved_at_${project.name}_$ep",0L) else 0L
            val time=if(at>0L) java.text.SimpleDateFormat("dd/MM HH:mm",java.util.Locale("th","TH")).format(java.util.Date(at)) else "ยังไม่สร้าง"
            "🎬 EP.$ep • ${eps.getJSONObject(i).optString("title")}\nคำสั่ง Flow: ${if(count>0) "$count ฉาก • $time" else time}"
        }
        choiceMenu("DIRECTOR X • ศูนย์งาน Flow\nช่อง: ${channel.ifBlank { "ช่องไม่ระบุ" }}\nเรื่อง: ${story.optString("title").ifBlank { "ไม่ระบุชื่อ" }}\nเลือกทำงานต่อได้ทันที",
            options) { index ->
            if(index==0) { continueFlowOneTap(story); return@choiceMenu }
            if(index==1) { phaseOneWorkflowStatus(story); return@choiceMenu }
            if(index==2) { if(storyPassed) imageBatchFivePlan(story) else productionCheckMenu(story); return@choiceMenu }
            if(index==3) { if(storyPassed && !flowPassed) autoFlowAllEpisodes(story) else if(!storyPassed) productionCheckMenu(story) else notice("คำสั่ง Flow ที่บันทึกไว้ผ่านการตรวจสอบแล้ว • ไม่สร้างซ้ำ"); return@choiceMenu }
            val ep=eps.getJSONObject(index-4).getInt("ep")
            val file=File(project,"EP%02d_FLOW_AUTO.txt".format(ep))
            if(!file.isFile) {
                autoFlowEpisode(story,ep)
            } else {
                choiceMenu("คำสั่ง Flow EP.$ep",listOf("เปิดคำสั่งที่บันทึกไว้ • ไม่สร้างซ้ำ","สร้างคำสั่งใหม่ทั้งหมด • แทนที่ไฟล์เดิม")) { action ->
                    if(action==0) {
                        runCatching { file.readText(Charsets.UTF_8) }
                            .onSuccess { content -> showFlowCode("คำสั่ง Flow EP.$ep • บันทึกไว้แล้ว",content,file.name) }
                            .onFailure { notice("เปิดคำสั่งที่บันทึกไว้ไม่ได้: ${it.message}") }
                    } else {
                        AlertDialog.Builder(this).setTitle("สร้างคำสั่ง Flow ใหม่?")
                            .setMessage("คำสั่งเดิมของ EP.$ep จะถูกแทนที่ หลังสร้างคำสั่งใหม่สำเร็จ")
                            .setPositiveButton("สร้างใหม่") { _,_ -> autoFlowEpisode(story,ep) }
                            .setNegativeButton("ยกเลิก",null).showDirector()
                    }
                }
            }
        }
    }

    private fun suggestStoryWardrobe(story: JSONObject, mode: Int) {
        val useVision=mode!=2
        val useOpenAi=mode==1
        val visionName=if(useOpenAi) "OpenAI" else "Gemini"
        if(GeminiStoryService.running) return notice("กรุณารอหรือหยุดงานเขียนบทก่อนปรับชุด")
        val eligible=StoryProvider.eligible(this).filter { it.hasKey(this) }
        val providers=if(useVision) eligible.filter { it==StoryProvider.GEMINI } else eligible
        if(useOpenAi && StoryProvider.freeOnly(this)) return notice("โหมดฟรีเปิดอยู่ • ปิดโหมดฟรีก่อนใช้ OpenAI API ซึ่งอาจมีค่าบริการ")
        if(useOpenAi && !ExtraApiSettings(this,ExtraApiProvider.OPENAI).hasKey()) return notice("ตั้งค่า OpenAI API Key ในเมนู API เพิ่มเติมก่อน • ChatGPT Plus ไม่ใช่ API Key")
        if(!useOpenAi && providers.isEmpty()) return notice(if(useVision) "ต้องเปิดใช้ Gemini และตั้งค่า API Key ก่อนวิเคราะห์ภาพ MASTER • ไม่มีการส่งภาพ" else "ตั้งค่า API เขียนบทก่อนให้ AI เสนอชุด")
        val scenes=rows(story)
        val projectId=project.name
        val snapshot=story.toString()
        val groups=linkedMapOf<Pair<String,String>,JSONObject>()
        scenes.forEach { scene ->
            val day=scene.optString("story_day").trim()
            if(day.isNotBlank()) FlowReferencePrompt.people(scene).forEach { person ->
                val id=referenceId(person.optString("master")); if(id.isNotBlank()) groups.putIfAbsent(id.lowercase() to day.lowercase(),person)
            }
        }
        if(groups.isEmpty()) return notice("ไม่พบตัวละครหรือ story_day ที่ใช้วางชุด")
        val references=if(useVision) groups.values.distinctBy { referenceId(it.optString("master")).lowercase() }.map { person ->
            referenceId(person.optString("master")) to runCatching { masterFor(person) }.getOrNull()
        } else emptyList()
        if(useVision && (references.size>8 || references.any { it.second?.isFile!=true })) return notice("ต้องมี MASTER จริงครบทุกตัวและไม่เกิน 8 ภาพ • กรุณาตรวจคลังหรือเลือกวิเคราะห์จากข้อความ")
        val summary=JSONArray(scenes.map { scene -> JSONObject().put("id",key(scene)).put("story_day",scene.optString("story_day")).put("location",scene.optString("location")).put("action",scene.optString("action")).put("start_state",scene.optString("start_state")).put("characters",JSONArray(FlowReferencePrompt.people(scene).map { p -> JSONObject().put("name",p.optString("name")).put("master",referenceId(p.optString("master"))).put("role",p.optString("role")).put("character_type",p.optString("character_type")).put("outfit",p.optString("outfit")) })) })
        if(summary.toString().length>100000) return notice("บทมีขนาดใหญ่เกินไป กรุณาแก้ชุดรายซีนก่อน")
        notice(if(useVision) "กำลังส่งภาพ MASTER ไป $visionName เพื่อช่วยเสนอชุด • ตรวจผลก่อนบันทึก" else "กำลังให้ AI เสนอชุดจากข้อความบท • ไม่มีการส่งภาพ MASTER")
        Thread {
            val item=JSONObject().put("type","object").put("properties",JSONObject().put("master",JSONObject().put("type","string")).put("story_day",JSONObject().put("type","string")).put("outfit",JSONObject().put("type","string")).put("summary_th",JSONObject().put("type","string"))).put("required",JSONArray().put("master").put("story_day").put("outfit"))
            val schema=JSONObject().put("type","object").put("properties",JSONObject().put("assignments",JSONObject().put("type","array").put("items",item))).put("required",JSONArray().put("assignments"))
            val prompt="""Review the story and propose outfits for visible characters using their actual role, setting and activity. ${WardrobeContext.planning}
Return JSON assignments. Each item must contain exact master ID and exact story_day from the input and a complete outfit with Top: ...; Bottom: ...; Shoes: ...; Accessories: ... . Do not add characters, roles, events, or props. Keep one exact outfit for each character and story_day. For Top, Bottom and Shoes include shade=color #RRGGBB, and for Top include sleeves=long-sleeve, short-sleeve, three-quarter-sleeve, cap-sleeve, sleeveless, strapless or one-sleeve. ${if(useVision) "Each attached image is labeled by MASTER ID; use only the matching image to describe visible armor, clothing and anatomy. Image observations are suggestions requiring human review; the story can specify a different outfit." else "The input describes the story in text only; you cannot see the MASTER image."} Never infer whether clothing, armor or accessories are absent from character type, and never assume an unknown all-none outfit means a deliberate choice. Do not erase known armor. If the story does not supply enough detail to propose an outfit, omit that assignment for user review. Preserve nonhuman anatomy and explicitly described equipment. Existing presenter-selected outfit should remain unless the user decides otherwise. Also provide summary_th, a short faithful Thai description of the proposed outfit, with no invented details; say no clothing where appropriate. Return only items you can match confidently. Story: $summary"""
            var answer: JSONObject?=null
            var failure=""
            if(useOpenAi) {
                val config=ExtraApiSettings(this,ExtraApiProvider.OPENAI)
                answer=runCatching { OpenAiVisionClient(config.key(),config.model()).generate(prompt,schema,references.map { it.first to requireNotNull(it.second) }) }
                    .onFailure { failure=it.message.orEmpty() }.getOrNull()?.takeIf { it.optJSONArray("assignments")!=null }
            }
            for(provider in if(useOpenAi) emptyList() else providers) {
                answer=runCatching {
                    if(useVision) {
                        val slot=GeminiKeyPool.next(this)
                        runOnUiThread { Toast.makeText(this,"วิเคราะห์ภาพด้วย Gemini คีย์ช่อง ${slot.first}",Toast.LENGTH_SHORT).show() }
                        GeminiClient(slot.second,provider.model(this),{ false },automaticRetry=false)
                            .generateWithImages(prompt,schema,references.map { it.first to requireNotNull(it.second) })
                    }
                    else StoryClientFactory.create(provider,provider.key(this),provider.model(this),{ false },automaticRetry=false,context=this@StudioActivity).generate(prompt,schema)
                }.onFailure { failure=it.message.orEmpty() }.getOrNull()?.takeIf { it.optJSONArray("assignments")!=null }
                if(answer!=null) break
            }
            val result=answer
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                if(result==null) { notice((if(useVision) "$visionName วิเคราะห์ภาพ MASTER ไม่สำเร็จ" else "AI ยังเสนอชุดไม่ได้")+" • "+failure.ifBlank { "ตรวจ API แล้วลองใหม่" }+" • ไม่มีการเปลี่ยนบท"); return@runOnUiThread }
                if(project.name!=projectId || !sameOpenStory(snapshot)) { notice("เรื่องเปลี่ยนระหว่างวิเคราะห์ กรุณาเริ่มใหม่"); return@runOnUiThread }
                val a=result.optJSONArray("assignments") ?: JSONArray()
                val proposals=(0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { row ->
                    val id=referenceId(row.optString("master")).lowercase() to row.optString("story_day").trim().lowercase()
                    id in groups && runCatching { AutoDirectorWardrobe.parts(row.optString("outfit")) }.isSuccess
                }.distinctBy { referenceId(it.optString("master")).lowercase() to it.optString("story_day").trim().lowercase() }
                if(proposals.isEmpty()) { notice("AI ไม่ได้เสนอชุดที่ตรวจรูปแบบได้ • ไม่มีการเปลี่ยนบท"); return@runOnUiThread }
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                val reviewScroll=ScrollView(this).apply { addView(panel) }
                val proposedKeys=proposals.map { referenceId(it.optString("master")).lowercase() to it.optString("story_day").trim().lowercase() }.toSet()
                val missing=groups.filterKeys { it !in proposedKeys }.values.map { it.optString("name").ifBlank { "ไม่ระบุชื่อ" } }.distinct()
                if(missing.isNotEmpty()) panel.addView(TextView(this).apply { text="⚠️ AI ยังไม่ได้เสนอชุดที่ตรวจได้สำหรับ: "+missing.joinToString(", ")+" • โปรดตรวจด้วยตนเอง" })
                panel.addView(TextView(this).apply { text=(if(useVision) "$visionName ได้รับภาพ MASTER เพื่อช่วยเสนอชุด • ตรวจผลจริงก่อนใช้" else "AI เสนอจากข้อความบทเท่านั้น • ไม่ได้ดูภาพ MASTER")+"\nภาพ MASTER ใช้ยืนยันตัวละคร ไม่ใช่ภาพชุดที่เสนอ\nเลือกใช้ชุดที่เสนอหรือคงชุดเดิมแต่ละรายการก่อนบันทึก • ภาพฉากเดิมที่ได้รับผลต้องตรวจใหม่" })
                val decisions=MutableList<Int?>(proposals.size) { null }
                val inputs=mutableListOf<EditText>()
                val confirmations=mutableListOf<android.widget.CheckBox>()
                val cards=mutableListOf<LinearLayout>()
                val host=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                var currentCard=0
                val progress=TextView(this)
                panel.addView(progress)
                fun updateProgress() { progress.text="ตัวละคร ${currentCard+1} / ${decisions.size} • ตรวจแล้ว ${decisions.count { it!=null }} / ${decisions.size} • ยังไม่บันทึก" }
                panel.addView(host)
                proposals.forEachIndexed { index,row ->
                    val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)); visibility=if(index==0) android.view.View.VISIBLE else android.view.View.GONE }
                    cards.add(card); host.addView(card)
                    val id=referenceId(row.optString("master")); val day=row.optString("story_day").trim()
                    val original=groups[id.lowercase() to day.lowercase()]!!
                    val name=original.optString("name").ifBlank { id }
                    val affected=scenes.filter { it.optString("story_day").trim().equals(day,true) && FlowReferencePrompt.people(it).any { p -> referenceId(p.optString("master")).equals(id,true) } }.map { key(it) }
                    val originalOutfit=original.optString("outfit").trim()
                    val noDetails=WardrobeEvidence.isUnknownOrAllNone(originalOutfit)
                    card.addView(TextView(this).apply { text="ตัวละคร: $name\nวันในเรื่อง: $day • MASTER: $id"; setTypeface(null,Typeface.BOLD); textSize=18f })
                    val master=runCatching { masterFor(original) }.getOrNull()
                    if(master?.isFile==true) {
                        card.addView(TextView(this).apply { text="ภาพ MASTER ตัวละคร • แตะเพื่อขยาย" })
                        card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(master)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(180)); contentDescription="MASTER $name"; setOnClickListener { fullImage(master,"MASTER $name") } })
                    } else card.addView(TextView(this).apply { text="⚠️ ไม่พบไฟล์ MASTER ของ $name • ตรวจไฟล์ก่อนสร้างภาพ" })
                    card.addView(TextView(this).apply { text=if(noDetails) "ชุดปัจจุบัน: ข้อมูลยังไม่ชัดเจน • ค่า none อาจหมายถึงไม่ระบุหรือตั้งใจไม่มีชุดและเกราะ กรุณาตรวจ MASTER" else "ชุดปัจจุบันในบท: $originalOutfit" })
                    val summary=row.optString("summary_th").trim()
                    card.addView(TextView(this).apply { text=if(summary.isBlank()) "ชุดที่ AI เสนอ: ไม่มีคำสรุปภาษาไทย • ตรวจรายละเอียดด้านล่าง" else "ชุดที่ AI เสนอ (ตรวจความตรงกับรายละเอียด): $summary" })
                    card.addView(TextView(this).apply { text="ยังไม่มีภาพตัวอย่างชุดในแอป • ภาพ MASTER ด้านบนไม่ใช่ภาพชุดที่เสนอ" })
                    val input=EditText(this).apply { setText(row.optString("outfit")); minLines=3; textSize=16f; hint="รายละเอียดชุดที่เสนอ"; card.addView(this) }
                    inputs.add(input)
                    val warning=TextView(this).apply { text="⚠️ ข้อเสนอระบุว่าไม่มีเสื้อผ้า/เกราะ/อุปกรณ์ทั้งหมด • ระบบไม่ได้อ่านภาพ MASTER อัตโนมัติ กรุณาตรวจด้วยตนเอง" }
                    val confirm=android.widget.CheckBox(this).apply { text="ฉันตรวจแล้วและยืนยันว่าตั้งใจไม่ใช้เสื้อผ้า เกราะ และเครื่องประดับสำหรับ $name ใน $day" }
                    card.addView(warning); card.addView(confirm); confirmations.add(confirm)
                    fun refreshWarning() {
                        val ambiguous=WardrobeEvidence.isAllNone(input.text.toString())
                        warning.visibility=if(ambiguous) android.view.View.VISIBLE else android.view.View.GONE
                        confirm.visibility=if(ambiguous) android.view.View.VISIBLE else android.view.View.GONE
                    }
                    input.addTextChangedListener(object: android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { confirm.isChecked=false; refreshWarning() }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                    refreshWarning()
                    val showScenes=Button(this).apply { text="ดู ${affected.size} ฉากที่จะใช้ชุดนี้"; setOnClickListener { AlertDialog.Builder(this@StudioActivity).setTitle("ฉากของ $name • $day").setMessage(affected.joinToString("\n").ifBlank { "ไม่พบฉาก" }).setPositiveButton("ปิด",null).showDirector() } }
                    card.addView(showScenes)
                    val preview=Button(this).apply { text="สร้างภาพตัวอย่างชุดในแอปภาพ"; isEnabled=master?.isFile==true; setOnClickListener {
                        val description=input.text.toString().trim()
                        if(description.isBlank()) { notice("ยังไม่มีรายละเอียดชุด"); return@setOnClickListener }
                        if(master==null || !master.isFile) { notice("ไม่พบ MASTER ของ $name"); return@setOnClickListener }
                        val command="Create one wardrobe preview image of $name using the attached MASTER $id only for this character's identity. Outfit: $description. Preserve species and anatomy. Show the complete outfit clearly. This is a suggested visual preview, not a final scene. Compare the result with the MASTER and outfit; similarity is not guaranteed."
                        shareImageCommand(command,listOf(id to master),mapOf(id to name))
                    } }
                    card.addView(preview)
                    val previewLabel=TextView(this).apply { text="ยังไม่มีภาพตัวอย่างชุดที่เลือก" }
                    val previewImage=ImageView(this).apply { adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(240)); visibility=android.view.View.GONE }
                    card.addView(previewLabel); card.addView(previewImage)
                    card.addView(Button(this).apply { text="เลือกภาพตัวอย่างชุดที่สร้างแล้ว"; setOnClickListener {
                        wardrobePreviewResult={ uri ->
                            runCatching { previewBitmap(uri) }.onSuccess { bitmap ->
                                previewImage.setImageBitmap(bitmap); previewImage.visibility=android.view.View.VISIBLE
                                previewLabel.text="ภาพตัวอย่างที่คุณเลือก • ตรวจว่าเป็น $name และตรงกับชุดที่เสนอก่อนยืนยัน"
                                previewImage.setOnClickListener { AlertDialog.Builder(this@StudioActivity).setTitle("ภาพตัวอย่างชุด • $name").setView(ImageView(this@StudioActivity).apply { setImageBitmap(bitmap); adjustViewBounds=true }).setPositiveButton("ปิด",null).showDirector() }
                            }.onFailure { notice("เปิดภาพตัวอย่างไม่ได้ • กรุณาเลือกใหม่") }
                        }
                        wardrobePreviewPicker.launch(arrayOf("image/*"))
                    } })
                    card.addView(TextView(this).apply { text="สร้างภาพในแอปภาพภายนอกแล้วเลือกภาพกลับมาตรวจได้ • ภาพตัวอย่างไม่ถูกบันทึกในเรื่องอัตโนมัติ" })
                    val choice=RadioGroup(this).apply { orientation=RadioGroup.VERTICAL }
                    val use=RadioButton(this).apply { text="ใช้ชุดที่เสนอให้ $name • ${affected.size} ฉาก" }
                    val keep=RadioButton(this).apply { text="คงชุดเดิมของ $name • ไม่เปลี่ยนบท" }
                    use.id=android.view.View.generateViewId(); keep.id=android.view.View.generateViewId()
                    choice.addView(use); choice.addView(keep)
                    choice.setOnCheckedChangeListener { _,checked -> decisions[index]=if(checked==use.id) 1 else 0; updateProgress() }
                    card.addView(choice)
                    card.addView(Space(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(16)) })
                }
                val navigation=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
                val previous=Button(this).apply { text="◀ ตัวก่อนหน้า" }
                val next=Button(this).apply { text="ตัวถัดไป ▶" }
                navigation.addView(previous,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                navigation.addView(next,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                panel.addView(navigation)
                fun navigateTo(index: Int) {
                    if(index !in cards.indices) return
                    cards[currentCard].visibility=android.view.View.GONE
                    currentCard=index
                    cards[currentCard].visibility=android.view.View.VISIBLE
                    previous.isEnabled=currentCard>0
                    next.isEnabled=currentCard<cards.lastIndex
                    updateProgress()
                    reviewScroll.post { reviewScroll.scrollTo(0,0) }
                }
                previous.setOnClickListener { navigateTo(currentCard-1) }
                next.setOnClickListener { navigateTo(currentCard+1) }
                navigateTo(0)
                updateProgress()
                val dialog=AlertDialog.Builder(this).setTitle("ตรวจชุดที่ AI เสนอ").setView(reviewScroll).setPositiveButton("บันทึกชุดที่เลือก",null).setNegativeButton("ยกเลิก",null).showDirector()
                dialog.getButton(-1).setOnClickListener {
                    if(project.name!=projectId || !sameOpenStory(snapshot)) { notice("เรื่องเปลี่ยนระหว่างตรวจ กรุณาเริ่มใหม่"); return@setOnClickListener }
                    if(decisions.all { it==0 }) { dialog.dismiss(); notice("คงชุดเดิมทุกรายการ • ไม่ได้เปลี่ยนบท"); return@setOnClickListener }
                    runCatching {
                        val wasConfirmed=wardrobeReviewStatus(story)==HomeReviewState.WARDROBE_CONFIRMED
                        require(decisions.all { it!=null }) { "กรุณาเลือกใช้ชุดหรือคงชุดเดิมให้ครบทุกรายการ" }
                        proposals.forEachIndexed { i,row ->
                            if(decisions[i]==1 && WardrobeEvidence.isAllNone(inputs[i].text.toString()) && !confirmations[i].isChecked) {
                                navigateTo(i)
                                throw IllegalArgumentException("กรุณาตรวจ MASTER และยืนยันด้วยตนเอง หากตั้งใจไม่ใช้ชุด เกราะ และเครื่องประดับ")
                            }
                        }
                        val choices=proposals.mapIndexedNotNull { i,row -> if(decisions[i]!=1) null else WardrobeRevision.Choice(row.optString("master"),row.optString("story_day"),inputs[i].text.toString().trim()) }
                        require(choices.isNotEmpty()) { "เลือกคงชุดเดิมทุกรายการ • บทเดิมไม่เปลี่ยน กดยกเลิกเพื่อปิด" }
                        val chosen=choices.map { referenceId(it.master).lowercase() to it.day.trim().lowercase() }.toSet()
                        val affected=scenes.filter { scene ->
                            val day=scene.optString("story_day").trim().lowercase()
                            FlowReferencePrompt.people(scene).any { person -> (referenceId(person.optString("master")).lowercase() to day) in chosen }
                        }
                        val selectedNames=chosen.mapNotNull { groups[it]?.optString("name") }.distinct()
                        val unchanged=groups.filterKeys { it !in chosen }.values.map { it.optString("name") }.distinct()
                        val withImage=affected.filter { image(it).isFile }.map { key(it) }
                        val candidate=WardrobeRevision.apply(story,choices)
                        plan=candidate
                        try { saveStory() } catch(e: Exception) { plan=story; throw e }
                        val stamp=wardrobeReviewStamp(candidate)
                        val editor=prefs.edit().putString("wardrobe_selected_stamp_${project.name}",stamp).putBoolean("wardrobe_changed_${project.name}",!wasConfirmed)
                        if(wasConfirmed) editor.putString("wardrobe_review_${project.name}",stamp)
                        val statusSaved=editor.commit()
                        val details="บันทึกชุดที่เลือกแล้ว: ${selectedNames.joinToString(", ").ifBlank { "ตามรายการที่เลือก" }}\nฉากที่เกี่ยวข้อง: ${affected.map { key(it) }.joinToString(", ")}\nตัวละครหรือวันที่ไม่ได้เลือกยังใช้ชุดเดิม: ${unchanged.joinToString(", ").ifBlank { "ไม่มี" }}\nภาพที่มีอยู่และควรตรวจใหม่: ${withImage.joinToString(", ").ifBlank { "ไม่พบภาพที่บันทึกในแอป" }}"
                        val state=if(!statusSaved) "บันทึกบทสำเร็จ แต่บันทึกสถานะตรวจไม่สำเร็จ กรุณาตรวจชุดอีกครั้ง" else if(wasConfirmed) "ชุดอื่นคงเดิมตามที่ยืนยันไว้" else "บันทึกเฉพาะชุดที่เลือกแล้ว • ยังไม่ยืนยันชุดทั้งหมด"
                        details+"\n\n"+state
                    }.onSuccess { message -> dialog.dismiss(); render(); showWardrobeNext(message) }
                        .onFailure { notice(it.message ?: "บันทึกชุดไม่ได้") }
                }
            }
        }.start()
    }
    private fun suggestStoryProps(story: JSONObject) {
        val providers=StoryProvider.eligible(this).filter { it.hasKey(this) }
        if(providers.isEmpty()) return notice("ตั้งค่า API เขียนบทก่อนใช้ AI เสนออุปกรณ์")
        val projectId=project.name
        val scenes=rows(story)
        val storySnapshot=story.toString()
        val context=JSONArray(scenes.map { scene -> JSONObject().put("id",key(scene)).put("action",scene.optString("action")).put("start_state",scene.optString("start_state")).put("end_state",scene.optString("end_state")).put("dialogue",scene.optString("dialogue")).put("characters",JSONArray(FlowReferencePrompt.people(scene).map { it.optString("name") })) })
        val catalog=propCatalog
        val reviewed=runCatching { GeminiStoryFiles.read(File(project,"gemini_job/spec.json")).optJSONArray("selected_prop_masters") }.getOrNull()
        val reviewedIds=(0 until (reviewed?.length() ?: 0)).mapNotNull { reviewed?.optJSONObject(it)?.optString("prop_id")?.takeIf(String::isNotBlank) }.toSet()
        if(reviewed!=null && reviewedIds.isEmpty()) { notice("เรื่องนี้ไม่ได้เลือก MASTER อุปกรณ์ก่อนสร้างบท • เลือกอุปกรณ์ด้วยตนเองในซีนได้"); return }
        val manualBindings=scenes.flatMap { FlowReferencePrompt.people(it) }.distinctBy { it.optString("master") }.mapNotNull { person -> characterProp(person.optString("master")).takeIf { it.isNotBlank() }?.let { "${person.optString("name")} = $it" } }
        val registered=FlowCast.definitions(story)
        val reviewedBindings=(0 until (reviewed?.length() ?: 0)).mapNotNull { i ->
            val row=reviewed?.optJSONObject(i) ?: return@mapNotNull null
            val owner=row.optString("owner_library_id"); val id=row.optString("prop_id")
            val name=registered.singleOrNull { it.optString("library_id")==owner && owner.isNotBlank() }?.optString("name").orEmpty()
            if(name.isBlank() || id !in reviewedIds) null else "$name = $id"
        }
        val characterBindings=(manualBindings+reviewedBindings).distinct().joinToString("\n")
        val catalogText=catalog.mapIndexedNotNull { i,n -> val id=propId(i); if(reviewed!=null && id !in reviewedIds) null else "$id = $n" }.joinToString("\n")
        notice("กำลังให้ AI เสนออุปกรณ์ • ตรวจรายการก่อนบันทึก • บทเดิมไม่เปลี่ยน")
        Thread {
            val item=JSONObject().put("type","object").put("properties",JSONObject().put("scene_id",JSONObject().put("type","string")).put("prop_id",JSONObject().put("type","string")).put("holder",JSONObject().put("type","string"))).put("required",JSONArray().put("scene_id").put("prop_id").put("holder"))
            val schema=JSONObject().put("type","object").put("properties",JSONObject().put("assignments",JSONObject().put("type","array").put("items",item))).put("required",JSONArray().put("assignments"))
            val prompt="""Review the following movie scenes and choose matching prop references from this catalog. Return JSON assignments with scene_id, prop_id, holder. Only propose an object when it is visibly present in the START STATE of that scene, not merely mentioned in dialogue or future action. Do not add story events, objects or people. Assign the same prop ID across scenes for the same object. A holder must be an exact visible character name in that scene; if unclear use empty string. Respect any transfers explicitly in the story. If the object is unclear, omit it for human review. Do not assign covers.
CHARACTER PROP BINDINGS (only when visibly present in this scene; don't invent appearances or change ownership without story evidence):
$characterBindings
CATALOG:
$catalogText
SCENES:
$context"""
            var result: JSONObject?=null
            for(provider in providers) {
                result=runCatching { StoryClientFactory.create(provider,provider.key(this),provider.model(this),{ false },automaticRetry=false,context=this@StudioActivity).generate(prompt,schema) }.getOrNull()?.takeIf { it.optJSONArray("assignments")!=null }
                if(result!=null) break
            }
            val answer=result
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                if(answer==null) { notice("AI ยังเสนอรายการไม่ได้ กรุณาตรวจ API แล้วลองใหม่ • ไม่มีการเปลี่ยนบท"); return@runOnUiThread }
                if(project.name!=projectId || !sameOpenStory(storySnapshot)) { notice("เรื่องเปลี่ยนระหว่างวิเคราะห์ กรุณาเริ่มใหม่"); return@runOnUiThread }
                val allowed=scenes.associateBy { key(it) }
                val a=answer.optJSONArray("assignments") ?: JSONArray()
                val proposals=(0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { row ->
                    val scene=allowed[row.optString("scene_id")]
                    val id=row.optString("prop_id")
                    val holder=row.optString("holder")
                    scene!=null && Regex("PROP_(0[1-9]|1[0-9]|20)_MASTER").matches(id) && (reviewed==null || id in reviewedIds) && (holder.isBlank() || FlowReferencePrompt.people(scene).any { it.optString("name")==holder })
                }.distinctBy { it.optString("scene_id")+"|"+it.optString("prop_id") }
                if(proposals.isEmpty()) { notice("AI ไม่พบรายการที่ยืนยันได้ • เลือกอุปกรณ์ด้วยตนเองในซีน หรือปรับบทแล้วลองใหม่"); return@runOnUiThread }
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                panel.addView(TextView(this).apply { text="ตรวจว่าของปรากฏในเฟรมเริ่มต้น เลือกผู้ถือหรือไม่ระบุ และยกเลิกรายการที่ไม่ต้องการ • การบันทึกแทนข้อเสนอ AI เดิมของทั้งเรื่อง ตัวเลือกที่เคยเลือกเองในซีนยังคงอยู่" })
                val boxes=mutableListOf<android.widget.CheckBox>()
                val holders=mutableListOf<android.widget.Spinner>()
                val propStates=mutableListOf<android.widget.Spinner>()
                val stateLabels=listOf("ยังไม่ระบุสถานะ • ตรวจบท", "ถืออยู่", "เก็บในฝัก / สะพาย", "วางอยู่ในฉาก")
                val stateValues=listOf("", "held", "sheathed_or_carried", "placed")
                proposals.forEach { row ->
                    val id=row.getString("prop_id")
                    val name=catalog[id.substring(5,7).toInt()-1]
                    val scene=allowed.getValue(row.getString("scene_id"))
                    val names=FlowReferencePrompt.people(scene).map { it.optString("name") }.filter { it.isNotBlank() }.distinct()
                    val options=listOf("ไม่ระบุผู้ถือ")+names
                    val box=android.widget.CheckBox(this).apply { text="${row.getString("scene_id")} • $name"; isChecked=true }
                    val spinner=android.widget.Spinner(this).apply {
                        adapter=android.widget.ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,options)
                        setSelection(names.indexOf(row.optString("holder")).let { if(it<0) 0 else it+1 })
                    }
                    val stateSpinner=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,stateLabels) }
                    boxes.add(box); holders.add(spinner); propStates.add(stateSpinner); panel.addView(box); panel.addView(spinner); panel.addView(stateSpinner)
                }
                AlertDialog.Builder(this).setTitle("ตรวจข้อเสนอ AI • ${proposals.size} รายการ").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("บันทึกรายการที่เลือก") { _,_ ->
                    runCatching {
                        val file=File(project,"prop_assignments.json")
                        check(project.name==projectId && sameOpenStory(storySnapshot)) { "เรื่องเปลี่ยนระหว่างตรวจ กรุณาเริ่มใหม่" }
                        val data=JSONObject()
                        val approved=proposals.mapIndexedNotNull { index,row ->
                            if (!boxes[index].isChecked) null else JSONObject(row.toString()).put("holder", if(holders[index].selectedItemPosition==0) "" else holders[index].selectedItem.toString()).put("start_prop_state",stateValues[propStates[index].selectedItemPosition.coerceIn(0,stateValues.lastIndex)]).put("source_scene_digest",ScenePropBinding.signature(allowed.getValue(row.getString("scene_id"))))
                        }.groupBy { it.getString("scene_id") }
                        approved.forEach { (sceneId,items) -> data.put(sceneId,JSONArray(items)) }
                        val pending=File(project,"prop_assignments.json.pending"); pending.writeText(data.toString(2)); check(pending.renameTo(file)) { "บันทึกไม่สำเร็จ" }
                        notice("บันทึกข้อเสนอแล้ว ${approved.values.sumOf { it.size }} รายการ • เปิดซีนเพื่อตรวจ MASTER ก่อนส่ง • ตัวเลือกที่เคยบันทึกด้วยตนเองมีลำดับก่อน")
                    }.onFailure { notice("บันทึกไม่ได้: ${it.message}") }
                }.setNegativeButton("ยกเลิก",null).showDirector()
            }
        }.start()
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
    private fun returnToImageList(): Boolean {
        val story=plan ?: return false
        val number=prefs.getInt("last_image_deck_${project.name}",0)
        val eps=story.optJSONArray("episodes") ?: return false
        if(number<=0 || (0 until eps.length()).none { eps.optJSONObject(it)?.optInt("ep")==number }) return false
        activeProductionDialog?.dismiss()
        activeProductionDialog=null
        val saved=prefs.getStringSet("last_image_group_${project.name}_$number",emptySet()).orEmpty().toSet()
        productionTaskDeck(story,number,true,saved.takeIf { it.isNotEmpty() })
        return true
    }
    private fun productionTaskDeck(story: JSONObject,number: Int,images: Boolean,queueIds: Set<String>?=null) {
        val coverSelection=listOf("STORY","EP%02d".format(number)).mapNotNull { scope ->
            val key="cover_cast_${project.name}_$scope"
            if(prefs.contains(key)) scope to prefs.getStringSet(key,emptySet()).orEmpty().toSet() else null
        }.toMap()
        val allTasks=runCatching { if(images) SimpleProductionCommands.imageTasks(story,number,coverSelection) else SimpleProductionCommands.videoTasks(story,number) }.getOrElse {
            if(images && coverSelection.isNotEmpty()) {
                AlertDialog.Builder(this).setTitle("ตรวจรายการปก").setMessage((it.message ?: "ข้อมูลคำสั่งไม่ครบ")+"\nหากตัวละครในบทเปลี่ยน สามารถล้างรายการปกที่เคยเลือก แล้วเลือกใหม่ได้")
                    .setPositiveButton("ล้างรายการปกและเลือกใหม่") { _,_ ->
                        val editor=prefs.edit(); coverSelection.keys.forEach { scope -> editor.remove("cover_cast_${project.name}_$scope") }; editor.apply()
                        productionTaskDeck(story,number,images,queueIds)
                    }.setNegativeButton("ยกเลิก",null).showDirector()
            } else { notice(it.message ?: "ข้อมูลคำสั่งไม่ครบ"); productionCheckMenu(story) }
            return
        }
        if(queueIds==null) {
            val covers=if(images) allTasks.filter { it.id.contains("_COVER_") } else emptyList()
            val groups=listOf(allTasks.filter { !it.id.contains("_COVER_") }).filter { it.isNotEmpty() }
            val storyCovers=covers.filter { it.id.startsWith("STORY_COVER_") }
            val episodeCovers=covers.filter { !it.id.startsWith("STORY_COVER_") }
            val groupsWithCovers=listOf(storyCovers,episodeCovers).filter { it.isNotEmpty() }+groups
            val options=groupsWithCovers.map { group ->
                val label=if(group.first().id.startsWith("STORY_COVER_")) "ปกรวมเรื่อง • แนวตั้งและแนวนอน" else if(group.first().id.contains("_COVER_")) "ปก EP.$number • แนวตั้งและแนวนอน" else "${group.first().id} – ${group.last().id}"
                val completed=group.count { task -> rows(story).firstOrNull { key(it)==task.id }?.let { sceneFrameApproved(it) } == true }
                if(group.first().id.contains("_COVER_")) label else if(images) "$label • ตรวจผ่าน $completed/${group.size}" else "$label • ${group.size} ซีน"
            }
            if(options.isEmpty()) { notice("ยังไม่มีรายการภาพใน EP นี้"); return }
            val episode=story.optJSONArray("episodes")?.let { eps -> (0 until eps.length()).mapNotNull { eps.optJSONObject(it) }.firstOrNull { it.optInt("ep")==number } }
            val episodeTitle=episode?.optString("title")?.takeIf { it.isNotBlank() } ?: "ยังไม่ได้ตั้งชื่อตอน"
            val storyTitle=story.optString("title").ifBlank { "ยังไม่ระบุชื่อเรื่อง" }
            choiceMenu("เรื่อง: $storyTitle\nEP.$number • $episodeTitle\nเลือกชุด${if(images) "ภาพ" else "คำสั่ง Flow"}",options) { selected ->
                productionTaskDeck(story,number,images,groupsWithCovers[selected].map { it.id }.toSet())
            }
            return
        }
        val tasks=if(queueIds==null) allTasks else allTasks.filter { it.id in queueIds }
        if(tasks.isEmpty()) { if(images) prefs.edit().remove("last_image_group_${project.name}_$number").apply(); notice("ไม่พบรายการภาพ กรุณาเลือกชุดใหม่"); return }
        if(images) prefs.edit().putInt("last_image_deck_${project.name}",number).putStringSet("last_image_group_${project.name}_$number",tasks.map { it.id }.toSet()).apply()
        val legacyPreference="production_cursor_${project.name}_${number}_$images"
        val groupDigest=java.security.MessageDigest.getInstance("SHA-256").digest(tasks.map { it.id }.sorted().joinToString("|").toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
        val preference="${legacyPreference}_$groupDigest"
        val previous=prefs.getString(preference,null) ?: prefs.getString(legacyPreference,"")
        val start=tasks.indexOfFirst { it.id==previous }.coerceAtLeast(0)
        val options=listOf("ทำต่อจากล่าสุดที่เปิด • ${tasks[start].id}")+tasks.map { task ->
            val scene=rows(story).firstOrNull { key(it)==task.id }
            val status=if(scene==null || !images) "" else when {
                sceneFrameApproved(scene) -> " • ตรวจผ่าน"
                !image(scene).isFile -> " • รอภาพ"
                scene.optString("user_review")=="needs_revision" -> " • ต้องแก้"
                else -> " • รอตรวจ"
            }
            "${task.id} • ${task.title}$status"+(scene?.let { "\n"+SceneOverview.short(it) } ?: "")
        }
        choiceMenu("EP.$number • เลือก${if(images) "รูป" else "คำสั่ง Flow"} ในชุด",options) { selected ->
            val index=if(selected==0) start else selected-1; val task=tasks[index]
            prefs.edit().putString(preference,task.id).putString(legacyPreference,task.id).apply()
            val next=tasks.getOrNull(index+1)?.id ?: "จบชุดนี้ • เลือกชุดถัดไป"
            val sceneSummary=rows(story).firstOrNull { key(it)==task.id }?.let { SceneOverview.detail(it,task.id) }
            val sceneForFlow=if(images) null else rows(story).firstOrNull { key(it)==task.id }
            val flowSpec=sceneForFlow?.let { scene ->
                val original=ProductionCommandCheck.spec(scene)
                val prefKey="prop_master_${project.name}_${task.id}"
                val assigned=ScenePropBinding.valid(scene,runCatching { JSONObject(File(project,"prop_assignments.json").readText()).optJSONArray(task.id) }.getOrNull())
                val manual=if(prefs.contains(prefKey)) prefs.getStringSet(prefKey,emptySet()).orEmpty() else null
                val notes=(0 until (assigned?.length() ?: 0)).mapNotNull { assigned?.optJSONObject(it) }.filter { manual==null || it.optString("prop_id") in manual }.map { row ->
                    "${row.optString("prop_id")} with ${row.optString("holder").ifBlank { "holder unspecified" }}; initial state ${row.optString("start_prop_state").ifBlank { "unspecified; follow first frame" }}"
                }
                if(notes.isEmpty()) original else original.copy(action=original.action+". Prop continuity: "+notes.joinToString("; ")+". Preserve the actual first frame; do not create missing objects.")
            }
            val flowCamera=when(sceneForFlow?.optString("camera_movement")) { "track" -> "Gentle tracking movement."; "locked" -> "Locked camera."; "push_in" -> "Slow push-in."; else -> "Slow gentle pan." }
            val preview=if(!images && flowSpec!=null && FlowScenePrompt.issues(flowSpec).isEmpty()) task.copy(command=runCatching { ShortFlowPrompt.build(flowSpec,flowCamera) }.getOrElse { "Flow prompt needs review: non-English scene direction." }) else task
            val wardrobeNotice=if(images) task.references.mapNotNull { person ->
                val outfit=person.optString("outfit")
                val pieces=runCatching { AutoDirectorWardrobe.parts(outfit) }.getOrNull() ?: return@mapNotNull null
                val name=person.optString("name").ifBlank { "ตัวละคร" }
                val checks=mutableListOf<String>()
                if(pieces["Shoes"].orEmpty().trim().equals("none",true)) checks.add("Shoes: none = เท้าเปล่า โปรดตรวจว่าตั้งใจหรือไม่")
                val top=pieces["Top"].orEmpty()
                if(top.length<35 && !top.equals("none",true)) checks.add("รายละเอียดชุดอาจไม่พอ กรุณาตรวจทรง แขน คอ สี และเนื้อผ้า")
                if(checks.isEmpty()) null else "$name: ${checks.joinToString(" • ")}"
            } else emptyList()
            val warning=if(wardrobeNotice.isEmpty()) "" else "ตรวจชุดก่อนสร้างภาพ (ยังไม่เปลี่ยนข้อมูลบท):\n"+wardrobeNotice.joinToString("\n")+"\n\n"
            val code=warning+(sceneSummary?.plus("\n\n") ?: "")+SimpleProductionCommands.block(preview,index,images)+"\n\nล่าสุดที่เปิด: ${task.id} | ถัดไป: $next\nสถานะนี้บันทึกการเปิดคำสั่ง ไม่ได้ยืนยันว่าภาพหรือคลิปสร้างสำเร็จแล้ว"
            val problems=ProductionCommandCheck.issues(story)
            val dialog=showFlowCode("${task.id} • ถัดไป $next",code,"${task.id}_${if(images) "IMAGE" else "FLOW"}.txt")
            if(images) activeProductionDialog=dialog
            dialog.getButton(-2).text=if(images) "กลับรายการรูป/ซีน" else "กลับรายการซีน"
            dialog.getButton(-2).setOnClickListener {
                dialog.dismiss()
                if(images) activeProductionDialog=null
                productionTaskDeck(story,number,images,queueIds)
            }
            if(problems.isNotEmpty()) dialog.getButton(-1).setOnClickListener { notice("คำสั่งยังมีจุดต้องแก้ • เปิด ตรวจคำสั่ง / แก้เฉพาะจุด ก่อนคัดลอก") }
            dialog.getButton(-1).text=if(images) "เลือกแอปสร้างภาพ + MASTER" else "คัดลอกคำสั่ง Flow"
            dialog.getButton(-1).setOnClickListener {
                if(!images) {
                    if(problems.isNotEmpty() || flowSpec==null) { productionCheckMenu(story); return@setOnClickListener }
                    val value=runCatching { ShortFlowPrompt.build(flowSpec,flowCamera) }.getOrElse { error ->
                        if(error.message?.contains("คำบรรยายการกระทำ ตำแหน่ง หรือเสียงยังมีภาษาไทย")==true && sceneForFlow!=null) offerFlowTranslation(story,sceneForFlow,flowSpec,flowCamera,false)
                        else notice(error.message ?: "ตรวจคำสั่ง Flow ก่อนคัดลอก")
                        return@setOnClickListener
                    }
                    copyCommand(value)
                    return@setOnClickListener
                }
                if(problems.isNotEmpty()) { productionCheckMenu(story); return@setOnClickListener }
                val refs=task.references.map { person -> referenceId(person.getString("master")) to masterFor(person) }
                if(refs.isEmpty()) { notice("ไม่มีตัวละครหรือ MASTER ที่ระบุในรายการนี้"); return@setOnClickListener }
                val missing=task.references.zip(refs).filter { !it.second.second.isFile }.map { (person,ref) -> "${person.optString("name")} • ${ref.first}" }
                if(missing.isNotEmpty()) { notice("ไม่พบ MASTER ของตัวละครต่อไปนี้ กรุณาตรวจหรือเพิ่มภาพก่อนสร้าง:\n"+missing.joinToString("\n")); return@setOnClickListener }
                if(refs.map { it.first.lowercase() }.distinct().size != refs.size) { notice("รหัส MASTER ซ้ำกัน กรุณาตรวจตัวละครก่อนสร้างภาพ"); return@setOnClickListener }
                val hashes=runCatching { refs.map { MasterLibrary.digest(it.second.readBytes()) } }.getOrElse { notice("อ่าน MASTER ไม่ได้ กรุณาตรวจไฟล์ภาพ"); return@setOnClickListener }
                if(hashes.distinct().size != hashes.size) { notice("MASTER ของตัวละครต่างคนเป็นไฟล์ภาพเดียวกัน กรุณาตรวจและเปลี่ยนภาพก่อนสร้าง"); return@setOnClickListener }
                val labels=task.references.associate { referenceId(it.getString("master")) to it.getString("name") }.toMutableMap()
                val scene=rows(story).firstOrNull { key(it)==task.id }
                val all=rows(story); val sceneIndex=if(scene==null) -1 else all.indexOfFirst { key(it)==task.id }
                val previous=if(scene==null || sceneIndex<0 || scene.optString("location").isBlank()) null else all.take(sceneIndex).firstOrNull {
                    it.optString("location").trim().equals(scene.optString("location").trim(),true) && sceneFrameApproved(it)
                }
                val context=previous?.let { prior ->
                    val id=key(prior); labels[id]="ภาพสถานที่ที่ผู้ใช้ตรวจแล้ว • ใช้ดูสถานที่และสิ่งของเท่านั้น"; listOf(id to image(prior))
                }.orEmpty()
                // A reviewed single-character frame may serve as a clothing reference only.
                // Never infer that an arbitrary group frame or unreviewed output matches an outfit.
                val candidates=all.take(sceneIndex.coerceAtLeast(0)).mapNotNull { prior ->
                    val people=FlowReferencePrompt.people(prior)
                    val person=people.singleOrNull() ?: return@mapNotNull null
                    OutfitReferencePolicy.Candidate(key(prior),referenceId(person.optString("master")),prior.optString("story_day"),person.optString("outfit"),sceneFrameApproved(prior),people.size)
                }
                val outfitRefs=if(scene==null) emptyList() else task.references.mapNotNull { person ->
                    val candidate=OutfitReferencePolicy.choose(candidates,task.id,referenceId(person.optString("master")),scene.optString("story_day"),person.optString("outfit")) ?: return@mapNotNull null
                    val prior=all.first { key(it)==candidate.sceneId }
                    val id="OUTFIT_${candidate.sceneId}_${referenceId(person.optString("master"))}"
                    labels[id]="ภาพชุดที่ตรวจแล้ว • ${person.optString("name")} • ${candidate.sceneId} • ตรวจว่ามองเห็นชุดครบ"
                    Triple(id,image(prior),person.optString("name"))
                }
                val selectedPiaOutfit=File(project,"presenter_outfit_reference.image")
                val piaPerson=task.references.firstOrNull { FlowCast.id(it)?.equals("support_%08x".format(1999999999),true)==true }
                val piaRef=if(piaPerson!=null && selectedPiaOutfit.isFile && plan?.optString("channel")=="นายเปีย บ้านคลองขุด") {
                    val id="OUTFIT_PIA_SELECTED_${referenceId(piaPerson.getString("master"))}"
                    labels[id]="ชุดเริ่มต้นที่เลือก • ${piaPerson.optString("name")} • ติ๊กเฉพาะเมื่อชุดตรงกับฉากนี้"
                    listOf(id to selectedPiaOutfit)
                } else emptyList()
                previewImageReferences(task,refs+context+outfitRefs.map { it.first to it.second }+piaRef,labels)
            }
            dialog.getButton(-3).text="แก้เฉพาะรายการนี้"
            dialog.getButton(-3).setOnClickListener {
                dialog.dismiss()
                val scene=rows(story).firstOrNull { key(it)==task.id }
                choiceMenu("แก้ ${task.id}",if(scene==null) listOf("เลือกตัวละครบนปก","แก้การจัดภาพปก","แก้ข้อความบนปก") else if(images) listOf("แก้ทิศทางภาพเฉพาะจุด","แก้บท / การกระทำ / ชุด / ตำแหน่ง / เสียง","เสนอขยายบทสั้น") else listOf("แก้ทิศทางวิดีโอเฉพาะจุด","แก้บท / การกระทำ / ชุด / ตำแหน่ง / เสียง","เลือกการเคลื่อนกล้อง","เสนอขยายบทสั้น")) { action ->
                    if(scene==null && action==0) {
                        val overall=task.id.startsWith("STORY_"); val scope=if(overall) "STORY" else "EP%02d".format(number)
                        val candidates=SimpleProductionCommands.coverCandidates(story,number,overall)
                        val chosen=task.references.map { referenceId(it.getString("master")) }.toMutableSet()
                        val list=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
                        list.addView(TextView(this).apply { text="เลือกตัวละครสำคัญจาก${if(overall) "ทั้งเรื่อง" else "ทุกฉากในตอน"} • แนะนำสูงสุด 4 ตัวละคร (MASTER + ภาพครอปใบหน้า รวมไม่เกิน 9 ภาพ) • แตะภาพเพื่อขยาย" })
                        var coverRow: LinearLayout?=null
                        candidates.forEachIndexed { index, person ->
                            if(index%2==0) { coverRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; list.addView(coverRow) }
                            val id=referenceId(person.getString("master")); val file=masterFor(person)
                            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)); layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
                            coverRow?.addView(card)
                            val box=android.widget.CheckBox(this).apply { text="${person.optString("name")}\n$id"+(if(file.isFile) "" else "\nไม่พบ MASTER"); isChecked=id in chosen; isEnabled=file.isFile }
                            if(file.isFile) card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(file)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(140)); contentDescription=box.text; setOnClickListener { fullImage(file,box.text.toString()) } })
                            card.addView(box)
                            box.setOnCheckedChangeListener { _,checked -> if(checked) chosen.add(id) else chosen.remove(id) }
                        }
                        val picker=AlertDialog.Builder(this).setTitle("เลือกตัวละครบนปก").setView(ScrollView(this).apply { addView(list) }).setPositiveButton("บันทึก",null).setNegativeButton("ยกเลิก",null).showDirector()
                        picker.getButton(-1).setOnClickListener {
                            if(chosen.isEmpty() || chosen.size>4) notice("กรุณาเลือกตัวละครบนปก 1–4 คน เพื่อเก็บ MASTER ต้นฉบับและภาพครอปใบหน้าของทุกคนไว้ครบ โดยไม่ต้องสร้างเรื่องใหม่")
                            else if(candidates.filter { referenceId(it.getString("master")) in chosen }.any { !masterFor(it).isFile }) notice("ภาพ MASTER บางตัวหาย กรุณาเลือกใหม่")
                            else { prefs.edit().putStringSet("cover_cast_${project.name}_$scope",chosen.toSet()).apply(); picker.dismiss(); notice("บันทึกรายการปกแล้ว • เปิดปกอีกครั้งเพื่อตรวจและส่ง") }
                        }
                    }
                    else if(scene==null && action==2) editProductionCover(story)
                    else if(scene!=null && (images && action==2 || !images && action==3)) suggestLongerSpeech(scene)
                    else if(action==2 && scene!=null && !images) {
                        val modes=listOf("อัตโนมัติตามซีน","ซูมเข้าอย่างช้า ๆ","แพนกล้อง","เคลื่อนตามการกระทำ","กล้องนิ่ง")
                        val keys=listOf("","push_in","pan","track","locked")
                        choiceMenu("เลือกกล้อง • ${task.id}",modes) { selected ->
                            scene.put("camera_movement",keys[selected]); saveStory(); render()
                            notice("บันทึกการเคลื่อนกล้องแล้ว • เปิดซีนเดิมเพื่อคัดลอกคำสั่งใหม่")
                        }
                    } else if(action==1 && scene!=null) { editScene(scene) }
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
    private fun wardrobeReviewStamp(story: JSONObject): String = ReviewFingerprints.wardrobe(rows(story))
    private fun wardrobeReviewStatus(story: JSONObject): String {
        val all=rows(story)
        val current=ReviewFingerprints.wardrobe(all)
        var saved=prefs.getString("wardrobe_review_${project.name}","").orEmpty()
        if(saved.isNotBlank() && saved==ReviewFingerprints.legacyWardrobe(all)) {
            prefs.edit().putString("wardrobe_review_${project.name}",current).apply()
            saved=current
        }
        val partial=prefs.getString("wardrobe_selected_stamp_${project.name}","").orEmpty()
        return HomeReviewState.wardrobeProgress(saved,partial,current,prefs.getBoolean("wardrobe_changed_${project.name}",false))
    }
    private fun confirmAllWardrobe(story: JSONObject) {
        val groups=rows(story).flatMap { scene -> FlowReferencePrompt.people(scene).map { p -> Triple(p.optString("name"),scene.optString("story_day"),p.optString("outfit")) } }.distinct()
        if(groups.isEmpty()) return notice("ยังไม่มีตัวละครในบทให้ตรวจชุด")
        val summary=groups.joinToString("\n\n") { "${it.first} • ${it.second.ifBlank { "ไม่ระบุวัน" }}\nชุด: ${it.third.ifBlank { "ยังไม่ระบุ" }}" }
        val before=wardrobeReviewStamp(story)
        val warnings=WardrobeReviewAudit.warnings(rows(story))
        val review=if(warnings.isEmpty()) "" else "\n\nจุดที่ควรตรวจ:\n"+warnings.take(20).joinToString("\n")
        AlertDialog.Builder(this).setTitle("ตรวจและยืนยันชุดทั้งหมดในบท")
            .setMessage(summary+review+"\n\nการยืนยันหมายถึงคุณตรวจชุดในบท ไม่ได้ตรวจภาพจริง")
            .setPositiveButton("ยืนยันชุดทั้งหมด") { _,_ ->
                if(plan!==story || wardrobeReviewStamp(story)!=before) notice("บทเปลี่ยนแล้ว กรุณาตรวจใหม่")
                else {
                    val ok=prefs.edit().putString("wardrobe_review_${project.name}",before).putString("wardrobe_selected_stamp_${project.name}",before).putBoolean("wardrobe_changed_${project.name}",false).commit()
                    render()
                    if(ok) showWardrobeNext("ยืนยันชุดทั้งหมดในบทแล้ว • กลับมาแก้ได้") else notice("ยืนยันสถานะไม่สำเร็จ กรุณาลองใหม่")
                }
            }.setNegativeButton("กลับ",null).showDirector()
    }
    private fun showWardrobeNext(message: String) {
        AlertDialog.Builder(this).setTitle("👕 ขั้นตอน 1 • ชุดตัวละคร").setMessage(message)
            .setPositiveButton("ไปตรวจสิ่งของ →") { _,_ -> plan?.let { reviewSceneProps(it) } ?: notice("ไม่พบเรื่อง") }
            .setNeutralButton("ตรวจและยืนยันชุดทั้งหมด") { _,_ -> plan?.let { confirmAllWardrobe(it) } ?: notice("ไม่พบเรื่อง") }
            .setNegativeButton("กลับหน้าแรก",null).showDirector()
    }
    private fun showPropsNext(message: String) {
        val current=plan ?: return notice("ไม่พบเรื่อง")
        val scenes=rows(current)
        val reviewed=scenes.count { propReviewStatus(it)=="ผู้ใช้ตรวจบทแล้ว" }
        val savedAt=prefs.getLong("flow_props_saved_at_${project.name}",0L)
        val savedTime=if(savedAt>0L) java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale("th","TH")).format(java.util.Date(savedAt)) else "ยังไม่มีเวลาบันทึก"
        AlertDialog.Builder(this).setTitle("🧰 ขั้นตอน 2 • สิ่งของในบท")
            .setMessage(message+"\n\nสถานะ: ยืนยันสิ่งของในบท $reviewed/${scenes.size} ฉาก\nบันทึกล่าสุด: $savedTime\nขั้นตอนถัดไป: เปิดคำสั่ง Flow")
            .setPositiveButton("ไปคำสั่ง Flow →") { _,_ -> commandCardsMenu() }
            .setNeutralButton("ตรวจฉากอื่น") { _,_ -> plan?.let { reviewSceneProps(it) } ?: notice("ไม่พบเรื่อง") }
            .setNegativeButton("กลับหน้าแรก",null).showDirector()
    }
    private fun wardrobeMenu(story: JSONObject) {
        choiceMenu("ขั้นตอน 1 • ตรวจชุดตัวละคร\nสถานะ: ${wardrobeReviewStatus(story)}",listOf("ดูและยืนยันชุดในบท\nตรวจชุดเดิมและยืนยันเมื่อถูกต้อง","ให้ AI เสนอชุดใหม่\nเลือกเฉพาะชุดที่ต้องการก่อนบันทึก","แก้ชุดเฉพาะฉาก\nปรับรายละเอียดในฉากที่ต้องการ")) { selected ->
            when(selected) { 0 -> confirmAllWardrobe(story); 1 -> choiceMenu("เลือกวิธีวิเคราะห์ชุด",listOf("วิเคราะห์ภาพ MASTER ด้วย Gemini • ส่งภาพไป Google","วิเคราะห์ภาพ MASTER ด้วย OpenAI API • อาจมีค่าบริการ","วิเคราะห์จากข้อความบทเท่านั้น • ไม่ส่งภาพ")) { mode -> suggestStoryWardrobe(story,mode) }; else -> productionCheckMenu(story) }
        }
    }
    private fun smartPreflightReview(story: JSONObject,number: Int) {
        val scenes=rows(story).filter { it.optInt("ep")==number }
        val propFields=listOf("props","backpack","bag","shoes","footwear","accessories")
        val keywords=listOf("กระเป๋า","เป้","รองเท้า","วิทยุ","โทรศัพท์","เครื่องมือ","เชือก","ไฟฉาย","อาวุธ",
            "backpack","bag","shoe","radio","phone","rope","flashlight","tool")
        val missing=mutableListOf<String>()
        val documented=mutableListOf<String>()
        val noEvidence=mutableListOf<String>()
        scenes.forEach { scene ->
            val description=listOf("action","start_state","end_state").joinToString(" ") { scene.optString(it) }
            val hasEvidence=keywords.any { description.contains(it,ignoreCase=true) }
            val fields=propFields.mapNotNull { field -> scene.optString(field).trim().takeIf { it.isNotBlank() }?.let { "$field: $it" } }
            val id=key(scene)
            when {
                fields.isNotEmpty() -> documented.add("• $id: "+fields.joinToString("; ").take(130))
                hasEvidence -> missing.add("• $id: บทกล่าวถึงสิ่งของ แต่ยังไม่มีรายละเอียดในช่องสิ่งของ")
                else -> noEvidence.add("• $id: ไม่พบหลักฐานสิ่งของจากข้อความบท (ยังไม่ได้ตรวจภาพ)")
            }
        }
        val summary="ตรวจจากข้อความบท EP.$number เท่านั้น ไม่ใช่การตรวจภาพจริง\\n\\n" +
            "มีข้อมูลสิ่งของ ${documented.size} ฉาก\\n" +
            "ควรเติมข้อมูล ${missing.size} ฉาก\\n" +
            "ไม่พบหลักฐานสิ่งของ ${noEvidence.size} ฉาก\\n\\n" +
            (documented+missing+noEvidence).joinToString("\\n").take(6500) +
            "\\n\\nระบบไม่ยืนยันแทนผู้ใช้ และไม่สรุปว่าฉากที่ไม่พบสิ่งของไม่มีอุปกรณ์จริง"
        AlertDialog.Builder(this).setTitle("ตรวจสิ่งของอัตโนมัติจากบท")
            .setMessage(summary)
            .setPositiveButton("ให้ AI ช่วยเสนอสิ่งของ") { _,_ -> suggestSceneObjects(story) }
            .setNeutralButton("ตรวจและยืนยันด้วยตนเอง") { _,_ -> reviewSceneProps(story) }
            .setNegativeButton("กลับ",null).showDirector()
    }
    private fun confirmEpisode(story: JSONObject,number: Int) {
        val episodeScenes=rows(story).filter { it.optInt("ep")==number }
        fun open() { productionTaskDeck(story,number,false) }
        fun check() {
            val pending=episodeScenes.filter { propReviewStatus(it)!="ผู้ใช้ตรวจบทแล้ว" }
            val wardrobe=wardrobeReviewStatus(story)
            if(!HomeReviewState.shouldWarn(wardrobe,pending.size)) { open(); return }
            val detail=pending.take(12).joinToString("\n") { scene ->
                "• ${key(scene)}: ${propReviewStatus(scene)}" +
                    if(scene.optString("props").isBlank()) " (ยังไม่ระบุสิ่งของ)" else " (มีข้อมูลสิ่งของในบท)"
            }
            val overflow=if(pending.size>12) "\nและอีก ${pending.size-12} ฉาก" else ""
            val summary="ชุดตัวละคร: $wardrobe\nสิ่งของ EP.$number: รอตรวจ ${pending.size}/${episodeScenes.size} ฉาก" +
                if(pending.isEmpty()) "" else "\n\nฉากที่ต้องตรวจ:\n$detail$overflow"
            AlertDialog.Builder(this).setTitle("ตรวจความพร้อมก่อนสร้างภาพและ Flow")
                .setMessage(summary+"\n\nข้อมูลบทและภาพจริงเป็นการตรวจคนละขั้นตอน การข้ามไม่ถือว่าอนุมัติข้อมูลหรือยืนยันภาพ")
                .setPositiveButton("ตรวจรายการที่ยังค้าง") { _,_ ->
                    val options=mutableListOf<String>()
                    if(wardrobe!="ยืนยันแล้ว") options.add("ตรวจชุดตัวละคร • $wardrobe")
                    if(pending.isNotEmpty()) options.add("ตรวจสิ่งของ • ${pending.size} ฉาก")
                    options.add("ตรวจสิ่งของอัตโนมัติจากบท • ไม่ยืนยันภาพจริง")
                    options.add("ตรวจสถานะอีกครั้ง")
                    choiceMenu("เลือกส่วนที่ต้องการตรวจ",options) { selected ->
                        when {
                            options[selected].startsWith("ตรวจชุด") -> wardrobeMenu(story)
                            options[selected].startsWith("ตรวจสิ่งของอัตโนมัติ") -> smartPreflightReview(story,number)
                            options[selected].startsWith("ตรวจสิ่งของ") -> reviewSceneProps(story)
                            else -> check()
                        }
                    }
                }
                .setNeutralButton("ดำเนินการต่อโดยยังไม่ยืนยัน") { _,_ ->
                    prefs.edit().putString("preflight_skipped_${project.name}_ep$number",
                        "wardrobe=$wardrobe;pending=${pending.joinToString(",") { key(it) }}").apply()
                    open()
                }
                .setNegativeButton("ยกเลิก",null).showDirector()
        }
        check()
    }
    private fun extraToolsMenu(current: JSONObject?) {
        val options=mutableListOf("📥 นำเข้าบทที่มีอยู่","⚙️ DIRECTOR X • ภาพและวิดีโอ","🧪 ทดสอบสร้างวิดีโอ","🎭 แม่แบบและคลังตัวละคร")
        if(current!=null) options.add("📋 ข้อมูลโพสต์ Facebook / TikTok")
        if(current!=null && current.optString("clip_format")=="short") options.add("💬 ชื่อเรื่อง เรื่องย่อ และแคปชั่น")
        if(current!=null && prefs.getInt("last_image_deck_${project.name}",0)>0) options.add("↩ กลับรายการรูป / ซีนล่าสุด")
        if(File(project,"last_image_payload.json").isFile) options.add("🔁 ส่งคำสั่งและรูปอีกครั้ง")
        if(current!=null && current.optString("aspect_ratio","16:9")!="9:16") options.add("↔ สร้างสำเนาแนวตั้ง 9:16")
        choiceMenu("🧰 เครื่องมือเพิ่มเติม",options) { index ->
            when(options[index]) {
                "📥 นำเข้าบทที่มีอยู่" -> packagePicker.launch(arrayOf("*/*"))
                "⚙️ DIRECTOR X • ภาพและวิดีโอ" -> autoDirectorMenu()
                "🧪 ทดสอบสร้างวิดีโอ" -> flowTestMenu()
                "🎭 แม่แบบและคลังตัวละคร" -> choiceMenu("ตัวละคร",listOf("สร้างตัวละครจากแม่แบบ","คลังตัวละครใหม่","ตัวละครของเรื่องที่เปิด")) { when(it) { 0 -> bodyTemplateMenu(); 1 -> actorCatalogMenu(); else -> castMenu() } }
                "📋 ข้อมูลโพสต์ Facebook / TikTok" -> if(current!=null) postDetails(current)
                "💬 ชื่อเรื่อง เรื่องย่อ และแคปชั่น" -> if(current!=null) AlertDialog.Builder(this).setTitle("ข้อมูลและแคปชั่นคลิปสั้น").setMessage(ShortStoryDetails.text(current)).setPositiveButton("คัดลอกทั้งหมด") { _,_ -> copyCommand(ShortStoryDetails.text(current)) }.setNegativeButton("ปิด",null).showDirector()
                "↩ กลับรายการรูป / ซีนล่าสุด" -> if(!returnToImageList()) notice("ไม่พบ EP ที่เคยเปิดในเรื่องนี้")
                "🔁 ส่งคำสั่งและรูปอีกครั้ง" -> retryLastImage()
                "↔ สร้างสำเนาแนวตั้ง 9:16" -> landscapeCopyMenu()
            }
        }
    }
    private fun flowHome() {
        val current=plan
        val fantasy=current?.let { ChannelFeaturePolicy.hasFantasyTools(it.optString("channel")) } == true
        val channel=current?.optString("channel")?.takeIf { it.isNotBlank() } ?: "ยังไม่ระบุ"
        val title=current?.optString("title")?.takeIf { it.isNotBlank() } ?: "ยังไม่มีเรื่องที่เปิด"
        val episodes=current?.optJSONArray("episodes")?.length() ?: 0
        val storyFile=File(project,"story.json")
        val exported=current!=null && storyFile.isFile && File(project,"flow_pack_exported.sha256").takeIf { it.isFile }?.readText()==EpisodeMedia.digest(storyFile)
        val scenes=current?.let { rows(it) }.orEmpty()
        val reviewed=scenes.count { propReviewStatus(it)=="ผู้ใช้ตรวจบทแล้ว" }
        val wardrobe=current?.let { wardrobeReviewStatus(it) } ?: "รอสร้างบท"
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14))
            background=backdrop(if(fantasy) Color.rgb(35,24,68) else Color.rgb(17,34,55),Color.rgb(13,20,39),Color.rgb(155,113,55))
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10); bottomMargin=dp(8) }
        }
        card.addView(TextView(this).apply { text="✦ เรื่องที่เปิดอยู่ ✦"; textSize=13f; setTextColor(gold); setTypeface(null,Typeface.BOLD) })
        card.addView(TextView(this).apply { text="ช่อง: $channel\nหมวด: ${DisplayText.category(current?.optString("category")?.takeIf { it.isNotBlank() } ?: "ยังไม่ระบุ")}\nเรื่อง: $title"; textSize=16f; setTextColor(ice); setTypeface(null,Typeface.BOLD); setPadding(0,dp(8),0,dp(6)) })
        card.addView(TextView(this).apply { text=if(current==null) "สร้างบทใหม่ หรือเปิดเรื่องที่บันทึกไว้" else "$episodes EP • ${scenes.size} ฉาก • ${current.optString("aspect_ratio","9:16")}"+(if(exported) " • เคยส่งออกบทนี้แล้ว" else ""); textSize=12f; setTextColor(muted) })
        root.addView(card)
        if(File(project,"gemini_job/spec.json").isFile) {
            root.addView(TextView(this).apply { text="🤖 งานเขียนบทล่าสุด"; textSize=16f; setTextColor(gold); setTypeface(null,Typeface.BOLD); setPadding(dp(8),dp(12),dp(8),0) })
            geminiStatusView=TextView(this).apply { text=geminiStatusText(readGeminiStatus()); setTextColor(ice); textSize=14f; setPadding(dp(8),dp(8),dp(8),dp(8)) }
            root.addView(geminiStatusView)
            geminiProgressView=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max=100; progress=readGeminiStatus()?.optInt("percent")?.coerceIn(0,100) ?: 0
                progressTintList=ColorStateList.valueOf(Color.rgb(0,210,240)); contentDescription="ความคืบหน้าบทที่บันทึกแล้ว"
            }
            root.addView(geminiProgressView,LinearLayout.LayoutParams(-1,dp(10)).apply { setMargins(dp(8),0,dp(8),dp(8)) })
            button("🤖 งานเขียนบท • ดูสถานะ / ทำต่อ",false) { geminiJobMenu() }
        } else { geminiStatusView=null; geminiProgressView=null }
        button("🎬 สร้างเรื่องใหม่",true) { flowPlanning() }
        root.addView(TextView(this).apply { text="เลือกช่องและแนวเรื่อง ให้ AI ช่วยสร้างบท"; textSize=12f; gravity=Gravity.CENTER; setTextColor(ice); setPadding(0,0,0,dp(8)) })
        fun menuRow(items: List<Triple<Int,Pair<String,String>,()->Unit>>) {
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            items.forEachIndexed { index,item ->
                val tile=LinearLayout(this).apply {
                    orientation=LinearLayout.VERTICAL
                    minimumHeight=dp(208)
                    background=backdrop(Color.rgb(32,27,20),Color.rgb(11,11,13),Color.rgb(176,129,49))
                    setPadding(dp(5),dp(5),dp(5),dp(8))
                    isClickable=true; isFocusable=true
                    contentDescription=item.second.first+" • "+item.second.second
                    setOnClickListener { if(!importing) item.third() }
                }
                tile.addView(ImageView(this).apply {
                    setImageResource(item.first); scaleType=ImageView.ScaleType.CENTER_CROP
                    contentDescription=null
                },LinearLayout.LayoutParams(-1,dp(104)))
                tile.addView(TextView(this).apply {
                    text=item.second.first; textSize=16f; setTextColor(ice)
                    setTypeface(null,Typeface.BOLD); setPadding(dp(5),dp(5),dp(5),0)
                })
                tile.addView(TextView(this).apply {
                    text=item.second.second; textSize=12f; setTextColor(ice)
                    setPadding(dp(5),dp(3),dp(5),0)
                })
                row.addView(tile,LinearLayout.LayoutParams(0,-1,1f).apply { if(index==0) marginEnd=dp(5) else marginStart=dp(5) })
            }
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
        }
        menuRow(listOf(
            Triple(R.drawable.menu_cast,"ตัวละคร" to "จัดการตัวละคร MASTER",{ unifiedMasterMenu() }),
            Triple(R.drawable.menu_script,"บทภาพยนตร์" to "ตรวจบทและฉาก",{ current?.let { productionCheckMenu(it) } ?: notice("เปิดเรื่องที่มีบทก่อน") })
        ))
        menuRow(listOf(
            Triple(R.drawable.menu_cover,"สร้างภาพ" to "ภาพปกและภาพฉาก",{ current?.let { story ->
                val eps=story.optJSONArray("episodes")
                if(eps==null || eps.length()==0) notice("ยังไม่มีตอนในบท")
                else choiceMenu("สร้างภาพ • ${story.optString("title").ifBlank { "ยังไม่ระบุชื่อเรื่อง" }}", (0 until eps.length()).map { i -> val ep=eps.getJSONObject(i); "EP.${ep.optInt("ep")} • ${ep.optString("title").ifBlank { "ยังไม่ได้ตั้งชื่อตอน" }}" } + "จัดการอุปกรณ์อ้างอิง") { i ->
                    if(i<eps.length()) productionTaskDeck(story,eps.getJSONObject(i).optInt("ep"),true)
                    else choiceMenu("จัดการอุปกรณ์",listOf("ผูกอุปกรณ์กับตัวละคร","ให้ AI เสนออุปกรณ์รายซีน")) { mode -> if(mode==0) characterPropMenu(story) else suggestStoryProps(story) }
                }
            } ?: notice("เปิดเรื่องที่มีบทก่อน") }),
            Triple(R.drawable.menu_scene,"คำสั่ง Flow" to "สร้างคำสั่งวิดีโอ",{ commandCardsMenu() })
        ))
        root.addView(TextView(this).apply { text="ขั้นตอนการทำงาน"; textSize=18f; setTextColor(gold); setTypeface(null,Typeface.BOLD); setPadding(0,dp(6),0,dp(4)) })
        val active=if(current==null || wardrobe!="ยืนยันชุดในบทแล้ว") 1 else if(reviewed<scenes.size) 2 else 3
        stepCard(1,"👕","ตรวจชุดตัวละคร","ตรวจชุด หรือให้ AI ช่วยเลือก",enabled=current!=null,completed=current!=null && wardrobe=="ยืนยันชุดในบทแล้ว",currentStep=active,statusText=wardrobe) { current?.let { wardrobeMenu(it) } }
        stepCard(2,"🧰","ตรวจสิ่งของในบท","ตรวจสิ่งของ หรือให้ AI ช่วยเลือก",enabled=current!=null,completed=current!=null && scenes.isNotEmpty() && reviewed==scenes.size,currentStep=active,statusText=if(current==null) "รอสร้างบท" else "ตรวจบทแล้ว $reviewed/${scenes.size} ฉาก") { current?.let { reviewSceneProps(it) } }
        stepCard(3,"🎬","คำสั่ง Flow","เลือกฉากและตรวจภาพก่อนใช้ Flow",enabled=current!=null,currentStep=active,statusText=if(current==null) "รอสร้างบท" else "เปิดใช้งานได้ • ตรวจข้อมูลก่อนใช้") { commandCardsMenu() }
        button("🧰 เครื่องมือเพิ่มเติม",false) { extraToolsMenu(current) }
        splitActions("📚 เรื่องที่บันทึก","⚙️ ตั้งค่า",{ storiesMenu() },{ flowSettings() })
    }
    private fun flowSettings() {
        choiceMenu("ตั้งค่า DIRECTOR X",listOf("อัปเดตแอป","ดาวน์โหลดแม่บท + MASTER","สำรองเรื่องทั้งหมด","กู้คืนชุดสำรอง","เริ่มเรื่องใหม่","ตั้งค่า API")) { i -> when(i) {
            0 -> updater.check()
            1 -> { starterFlowPack=true; flowPackPicker.launch("DIRECTOR_FLOW_STARTER.zip") }
            2 -> if(plan==null) notice("ยังไม่มีเรื่องให้สำรอง") else { backupMode=true; zipPicker.launch(StoryFiles.zipName(plan!!.optString("title")+"_สำรอง")) }
            3 -> restorePicker.launch(arrayOf("application/zip"))
            4 -> AlertDialog.Builder(this).setTitle("เริ่มเรื่องใหม่")
                .setMessage("เรื่องปัจจุบันยังอยู่ในรายการเรื่องที่บันทึก")
                .setPositiveButton("เริ่มใหม่") { _,_ -> prefs.edit().putString("project",UUID.randomUUID().toString()).apply(); render() }
                .setNegativeButton("ยกเลิก",null).showDirector()
            5 -> apiSettingsCenter()
        } }
    }
    private fun apiSettingsCenter() {
        choiceMenu("⚙️ ตั้งค่า API", listOf("🎬 API เขียนบท", "🛒 API โหมดนายเปียพาช้อป", "🖼️ API สร้างภาพ AI")) { index ->
            when (index) {
                0 -> geminiSettings()
                1 -> shoppingApiSettings()
                2 -> imageApiSettings()
            }
        }
    }
    private fun imageApiSettings() {
        val store = ImageApiKeySettings(this)
        val options = (1..9).map { slot ->
            "API สร้างภาพเว็บที่ $slot • " + if (store.hasKey(slot)) "มีคีย์แล้ว • รอเชื่อมต่อ" else "ยังไม่มีคีย์ • รอตั้งค่า"
        }
        choiceMenu("🖼️ API สร้างภาพ AI • ตั้งค่าคีย์", options) { selected ->
            val slot = selected + 1
            val input = EditText(this).apply {
                hint = if (store.hasKey(slot)) "มีคีย์แล้ว • กรอกเพื่อเปลี่ยนคีย์" else "วาง API Key"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(18), dp(16), dp(18), dp(16))
            }
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(input)
                addView(CheckBox(this@StudioActivity).apply {
                    text = "แสดง API Key ที่กำลังกรอก"
                    setOnCheckedChangeListener { _, checked ->
                        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            (if (checked) android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                             else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        input.setSelection(input.text.length)
                    }
                })
            }
            AlertDialog.Builder(this)
                .setTitle("API สร้างภาพเว็บที่ $slot")
                .setMessage("บันทึกคีย์เข้ารหัสบนเครื่อง • ยังไม่ผูกผู้ให้บริการหรือเรียก API • ไม่เสียเครดิตจากหน้านี้")
                .setView(panel)
                .setPositiveButton("บันทึก") { _, _ ->
                    val value = input.text.toString()
                    if (value.isEmpty()) {
                        notice("ยังไม่ได้กรอก API Key • ไม่มีการเปลี่ยนแปลง")
                    } else if (value.any { it.isWhitespace() }) {
                        notice("API Key ต้องไม่มีช่องว่างหรือขึ้นบรรทัดใหม่ • ไม่ได้บันทึก")
                    } else if (value.length !in 8..4096) {
                        notice("API Key ต้องมีความยาว 8–4096 ตัวอักษร • ไม่ได้บันทึก")
                    } else {
                        runCatching { store.save(slot, value) }
                            .onSuccess { notice("บันทึก API Key เว็บที่ $slot สำเร็จ • ยังไม่เชื่อมต่อ") }
                            .onFailure { notice("บันทึกคีย์ไม่สำเร็จ • กรุณาลองอีกครั้ง") }
                    }
                    imageApiSettings()
                }
                .setNeutralButton("ลบคีย์") { _, _ ->
                    AlertDialog.Builder(this)
                        .setTitle("ยืนยันลบ API Key เว็บที่ $slot")
                        .setMessage("ต้องการลบ API Key ที่บันทึกไว้ในเว็บที่ $slot หรือไม่? การลบไม่สามารถย้อนกลับได้")
                        .setPositiveButton("ยืนยันลบ") { _, _ ->
                            runCatching { store.clear(slot) }
                                .onSuccess { notice("ลบ API Key เว็บที่ $slot แล้ว") }
                                .onFailure { notice("ลบคีย์ไม่สำเร็จ") }
                            imageApiSettings()
                        }
                        .setNegativeButton("ยกเลิก") { _, _ -> imageApiSettings() }
                        .showDirector()
                }
                .setNegativeButton("ยกเลิก", null).showDirector()
        }
    }
    private fun shoppingApiSettings() {
        val services = listOf("tavily" to "Tavily", "exa" to "Exa", "serpapi" to "SerpApi", "firecrawl" to "Firecrawl", "longsearch" to "Long Search")
        val store = FreeSearchSettings(this)
        val entries = services.map { (id, name) ->
            val keyState = if (store.key(id).isBlank()) "ไม่พบ API" else if (id=="longsearch") "รอ Endpoint" else store.status(id)
            "$name • $keyState" + if (!store.enabled(id)) " • ปิดใช้งาน" else ""
        } + listOf("🧪 ตรวจ API ที่เลือก", "🔎 ตรวจ API ทั้งหมด", "🛡 API สำรอง: " + if(store.fallbackEnabled()) "เปิด" else "ปิด", "📊 สรุปการใช้งาน")
        choiceMenu("🛒 API โหมดนายเปียพาช้อป", entries) { selected ->
            when(selected) {
                services.size -> shoppingApiTest()
                services.size+1 -> shoppingApiTestAll()
                services.size+2 -> { store.setFallbackEnabled(!store.fallbackEnabled()); shoppingApiSettings() }
                services.size+3 -> {
                    val info=services.filter { it.first!="longsearch" }.joinToString("\n\n") { (id,name) ->
                        val time=store.tested(id).takeIf { it>0 }?.let { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "ยังไม่ทดสอบ"
                        "$name: ${store.status(id)}\nตรวจล่าสุด: $time\nเรียกบนเครื่องเดือนนี้: ${store.used(id)} ครั้ง (ขีดจำกัดตั้งเอง ไม่ใช่เครดิตจริง)"
                    }
                    AlertDialog.Builder(this).setTitle("สถานะ API ค้นหาสินค้า").setMessage(info).setPositiveButton("ตกลง",null).showDirector()
                }
                else -> {
                    val (id,name)=services[selected]
                    val input=EditText(this).apply {
                        hint="วาง API Key ของ $name"
                        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        setPadding(dp(18),dp(16),dp(18),dp(16))
                    }
                    val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)); addView(input) }
                    box.addView(CheckBox(this).apply {
                        text="แสดง API Key"
                        setOnCheckedChangeListener { _,checked ->
                            input.inputType=android.text.InputType.TYPE_CLASS_TEXT or (if(checked) android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
                            input.setSelection(input.text.length)
                        }
                    })
                    box.addView(CheckBox(this).apply {
                        text="อนุญาตให้ใช้ API นี้"; isChecked=store.enabled(id)
                        setOnCheckedChangeListener { _,checked -> store.setEnabled(id,checked) }
                    })
                    AlertDialog.Builder(this).setTitle("ตั้งค่า $name")
                        .setMessage(if(id=="longsearch") "ยังไม่มี Endpoint ที่ยืนยัน • บันทึกคีย์ได้แต่ยังค้นหาจริงไม่ได้" else "ไม่กรอกคีย์ใหม่ = เก็บคีย์เดิม • คีย์เข้ารหัสบนเครื่อง")
                        .setView(box).setPositiveButton("บันทึก") { _,_ ->
                            val value=input.text.toString().trim()
                            if(value.isNotEmpty()) runCatching { store.save(id,value) }.onFailure { notice("รูปแบบ API Key ไม่ถูกต้อง") }
                            shoppingApiSettings()
                        }.setNeutralButton("ลบคีย์") { _,_ -> store.clear(id); shoppingApiSettings() }
                        .setNegativeButton("ยกเลิก",null).showDirector()
                }
            }
        }
    }
    private fun shoppingApiTest() {
        val services=listOf("tavily" to "Tavily","exa" to "Exa","serpapi" to "SerpApi","firecrawl" to "Firecrawl")
        choiceMenu("🧪 ทดสอบ API ค้นหาสินค้า",services.map { it.second }) { selected ->
            val (id,name)=services[selected]
            shoppingApiRunTest(id,name) { shoppingApiSettings() }
        }
    }
    private fun shoppingApiRunTest(id: String,name: String,done: () -> Unit) {
        val store=FreeSearchSettings(this)
        if(store.key(id).isBlank()) { store.record(id,"ไม่พบ API"); notice("ยังไม่ได้บันทึก API Key ของ $name"); done(); return }
        if(!store.enabled(id)) { notice("$name ถูกปิดใช้งาน"); done(); return }
        val control=ProductSearchControl()
        val progress=AlertDialog.Builder(this).setTitle("ทดสอบ $name").setMessage("กำลังค้นหาสินค้าทดสอบ • อาจใช้โควตา API")
            .setNegativeButton("ยกเลิก") { _,_ -> control.cancel() }.showDirector()
        Thread {
            val result=runCatching { FreeProductSearch.search(applicationContext,id,"แก้วน้ำ",1,control) }
            val status=result.fold({ if(it.isEmpty()) "ผ่าน • ไม่พบสินค้า" else "ผ่าน • พบสินค้า ${it.size} รายการ" },{ error ->
                val msg=ProductSearchRotation.friendly(error)
                when {
                    msg.contains("โควตา") || msg.contains("429") -> "เครดิตหมดหรือถูกจำกัด"
                    msg.contains("API Key") -> "ไม่พบ API"
                    else -> "ไม่ผ่าน • $msg"
                }
            })
            if(!control.cancelled()) store.record(id,status)
            runOnUiThread {
                progress.dismiss()
                if(!isFinishing && !isDestroyed) AlertDialog.Builder(this).setTitle("ผลทดสอบ $name")
                    .setMessage(if(control.cancelled()) "ยกเลิกการทดสอบ" else status)
                    .setPositiveButton("ตกลง") { _,_ -> done() }.showDirector()
            }
        }.start()
    }
    private fun shoppingApiTestAll() {
        val services=listOf("tavily" to "Tavily","exa" to "Exa","serpapi" to "SerpApi","firecrawl" to "Firecrawl")
        val store=FreeSearchSettings(this)
        val eligible=services.filter { store.enabled(it.first) && store.key(it.first).isNotBlank() }
        if(eligible.isEmpty()) { notice("ไม่พบ API ที่เปิดใช้งานและมีคีย์"); return }
        AlertDialog.Builder(this).setTitle("ตรวจ API ทั้งหมด")
            .setMessage("จะทดสอบ ${eligible.size} บริการและอาจใช้โควตาจริง ต้องการดำเนินการหรือไม่?")
            .setPositiveButton("ทดสอบ") { _,_ ->
                val control=ProductSearchControl()
                val progress=AlertDialog.Builder(this).setTitle("ตรวจ API ทั้งหมด").setMessage("กำลังทดสอบตามลำดับ")
                    .setNegativeButton("ยกเลิก") { _,_ -> control.cancel() }.showDirector()
                Thread {
                    val lines=mutableListOf<String>()
                    eligible.forEachIndexed { index,(id,name) ->
                        if(control.cancelled()) return@forEachIndexed
                        runOnUiThread { progress.setMessage("กำลังทดสอบ ${index+1}/${eligible.size}: $name") }
                        val result=runCatching { FreeProductSearch.search(applicationContext,id,"แก้วน้ำ",1,control) }
                        val status=result.fold({ "ผ่าน • ${it.size} รายการ" },{ "ไม่ผ่าน • ${ProductSearchRotation.friendly(it)}" })
                        if(!control.cancelled()) { store.record(id,status); lines.add("$name: $status") }
                    }
                    runOnUiThread {
                        progress.dismiss()
                        if(!isFinishing && !isDestroyed) AlertDialog.Builder(this).setTitle("ผลตรวจ API")
                            .setMessage(lines.joinToString("\n") + if(control.cancelled()) "\nยกเลิกแล้ว" else "")
                            .setPositiveButton("ตกลง") { _,_ -> shoppingApiSettings() }.showDirector()
                    }
                }.start()
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun readGeminiStatus(): JSONObject? = runCatching { GeminiStoryFiles.read(File(project,"gemini_job/status.json")) }.getOrNull()
    private fun geminiStatusText(status: JSONObject?,details: Boolean=false): String {
        if(status==null) return "${StoryProvider.label(this)} • ยังไม่เริ่มงาน"
        val spec=runCatching { GeminiStoryFiles.read(File(project,"gemini_job/spec.json")) }.getOrNull()
        val outline=runCatching { GeminiStoryFiles.read(File(project,"gemini_job/outline.json")) }.getOrNull()
        val saved=runCatching { GeminiStoryFiles.read(File(project,"story.json")) }.getOrNull()
        val channel=spec?.optString("channel")?.takeIf { it.isNotBlank() } ?: saved?.optString("channel")?.takeIf { it.isNotBlank() } ?: "ยังไม่ระบุ"
        val title=outline?.optString("title")?.takeIf { it.isNotBlank() } ?: saved?.optString("title")?.takeIf { it.isNotBlank() } ?: "กำลังตั้งชื่อเรื่อง"
        val category=spec?.optString("category")?.takeIf { it.isNotBlank() } ?: saved?.optString("category")?.takeIf { it.isNotBlank() } ?: "ยังไม่ระบุ"
        return "ช่อง: $channel\nหมวด: ${DisplayText.category(category)}\nเรื่อง: $title\n"+StoryStatusText.format(status,GeminiStoryService.running && GeminiStoryService.projectId==project.name,details=details)
    }
    private fun geminiSettings(afterSave: (() -> Unit)?=null) {
        val providers=StoryProvider.activeProviders
        val choices=providers.map { it.label+"\n"+StoryProvider.lastTest(this,it) }+
            listOf("☑️ เลือก API ที่อนุญาต", "🔍 ตรวจ API ที่เลือก", "🛡️ อนุญาต API สำรอง (ยังไม่เปิดสลับอัตโนมัติ)")
        choiceMenu("ตั้งค่าคีย์ API • ใช้อัตโนมัติ",choices) { index ->
            if(index==providers.size) selectStoryApiProviders(afterSave)
            else if(index==providers.size+1) { val selected=StoryProvider.eligible(this); if(selected.isEmpty()) notice("ยังไม่มี API ที่เลือกในโหมดปัจจุบัน") else testStoryApis(selected) }
            else if(index==providers.size+2) selectStoryFallbackConsent()
            else if(providers[index].extra()==null) storyProviderSettings(providers[index],afterSave)
            else extraApiKeyDialog(providers[index].extra()!!,afterSave)
        }
    }
    private fun selectStoryFallbackConsent() {
        val providers=StoryProvider.eligible(this)
        if(providers.isEmpty()) return notice("เลือก API ที่อนุญาตก่อนตั้งค่า API สำรอง")
        val saved=prefs.getStringSet("story_fallback_approved_ids", emptySet())?.toSet().orEmpty()
        val checks=BooleanArray(providers.size) { providers[it].id in saved }
        val labels=providers.map { provider ->
            "${provider.label} • ${if(provider.hasKey(this)) "มีคีย์" else "ยังไม่มีคีย์"}"
        }.toTypedArray()
        val dialog=AlertDialog.Builder(this)
            .setTitle("อนุญาต API สำรอง")
            .setMessage("เลือกเฉพาะ API ที่คุณยอมรับเงื่อนไขและค่าบริการแล้ว • ยังไม่เปิดการสลับ API อัตโนมัติในเวอร์ชันนี้ • ไม่มีการทดสอบหรือเรียก API จากหน้านี้")
            .setMultiChoiceItems(labels,checks) { _,index,checked -> checks[index]=checked }
            .setPositiveButton("บันทึก",null)
            .setNeutralButton("ปิดการอนุญาตทั้งหมด",null)
            .setNegativeButton("ยกเลิก",null)
            .showDirector()
        dialog.getButton(-1).setOnClickListener {
            val selected=providers.filterIndexed { index,provider -> checks[index] && provider.hasKey(this) }
                .map { it.id }.toSet()
            prefs.edit().putStringSet("story_fallback_approved_ids",selected).apply()
            dialog.dismiss()
            notice("บันทึก API สำรอง ${selected.size} ราย • ยังไม่เปิดสลับอัตโนมัติ")
        }
        dialog.getButton(-3).setOnClickListener {
            prefs.edit().remove("story_fallback_approved_ids").apply()
            dialog.dismiss()
            notice("ปิดการอนุญาต API สำรองทั้งหมดแล้ว")
        }
    }
    private fun selectStoryApiProviders(afterSave: (() -> Unit)?=null) {
        val providers=StoryProvider.activeProviders
        val allowed=StoryProvider.allowedIds(this)
        val checks=BooleanArray(providers.size) { providers[it].id in allowed }
        val labels=providers.map { "${it.label} • ${StoryProvider.lastTest(this,it)}" }.toTypedArray()
        val dialog=AlertDialog.Builder(this)
            .setTitle("เลือก API ที่อนุญาต\nตรวจแพ็กเกจและค่าบริการก่อนเลือก • ผลทดสอบอาจเปลี่ยนได้\nโหมดฟรีใช้เฉพาะ OpenRouter • มีผลเมื่อเริ่ม/ทำต่อ")
            .setMultiChoiceItems(labels,checks) { _,which,checked -> checks[which]=checked }
            .setPositiveButton("บันทึก",null).setNegativeButton("ยกเลิก",null).showDirector()
        dialog.getButton(-1).setOnClickListener {
            val chosen=providers.filterIndexed { i,_ -> checks[i] }.map { it.id }.toSet()
            runCatching { StoryProvider.setAllowed(this,chosen) }
                .onSuccess { dialog.dismiss(); notice(if(StoryProvider.freeOnly(this) && StoryProvider.OPENROUTER.id !in chosen) "บันทึกแล้ว • โหมดฟรียังเปิดอยู่ แต่ไม่ได้เลือก OpenRouter จึงเริ่มงานไม่ได้" else "บันทึก API ที่อนุญาตแล้ว • มีผลกับงานเริ่มหรือทำต่อครั้งถัดไป") }
                .onFailure { notice(it.message ?: "เลือก API อย่างน้อยหนึ่งเจ้า") }
        }
    }
    private fun extraApiSettingsMenu() {
        val providers=ExtraApiProvider.activeProviders
        val choices=providers.map { provider ->
            provider.label + if(ExtraApiSettings(this,provider).hasKey()) " • มีคีย์แล้ว" else " • ยังไม่มีคีย์"
        }
        choiceMenu("จัดเก็บ API Key เพิ่มเติม",choices) { index -> extraApiKeyDialog(providers[index]) }
    }
    private fun extraApiKeyDialog(provider: ExtraApiProvider,afterSave: (() -> Unit)?=null) {
        if(provider !in ExtraApiProvider.activeProviders) { notice("API นี้ถูกยกเลิกถาวร"); return }
        val settings=ExtraApiSettings(this,provider)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        panel.addView(TextView(this).apply { text="${provider.label} • คีย์เก็บแบบเข้ารหัสบนเครื่องและไม่รวมในไฟล์สำรอง • ระบบเลือกค่ายอัตโนมัติ • การเรียกใช้ขึ้นอยู่กับโควตาและค่าบริการของบัญชี" })
        val key=EditText(this).apply {
            hint=if(settings.hasKey()) "มีคีย์แล้ว • พิมพ์เฉพาะเมื่อเปลี่ยนคีย์" else "${provider.label} API Key"
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            panel.addView(this)
        }
        if(provider==ExtraApiProvider.OPENAI) panel.addView(TextView(this).apply { text="OpenAI API แยกจาก ChatGPT Plus • อาจมีค่าบริการ • ภาพ MASTER จะถูกส่งเมื่อคุณเลือกวิเคราะห์ด้วย OpenAI" })
        val model=EditText(this).apply { hint="ชื่อโมเดล"; setSingleLine(true); setText(settings.model()); panel.addView(this) }
        panel.addView(Button(this).apply { text="บันทึกและทดสอบ ${provider.label}"; setOnClickListener {
            runCatching { settings.save(key.text.toString().trim().ifBlank { settings.key() },model.text.toString().trim()) }
                .onSuccess { key.setText(""); key.hint="มีคีย์แล้ว • พิมพ์เฉพาะเมื่อเปลี่ยนคีย์"; testExtraApi(provider) }
                .onFailure { notice(it.message ?: "ตรวจคีย์และชื่อโมเดล") }
        } })
        val dialog=AlertDialog.Builder(this).setTitle("${provider.label} • จัดเก็บ API Key")
            .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("บันทึก",null)
            .setNeutralButton("ลบคีย์") { _,_ -> settings.clear(); notice("ลบคีย์ ${provider.label} จากเครื่องแล้ว") }
            .setNegativeButton("ปิด",null).showDirector()
        dialog.getButton(-1).setOnClickListener {
            runCatching {
                val value=key.text.toString().trim()
                settings.save(value.ifBlank { settings.key() }, model.text.toString().trim())
            }.onSuccess { key.setText(""); dialog.dismiss(); afterSave?.invoke() ?: notice("บันทึก ${provider.label} แล้ว • ระบบเลือกค่ายอัตโนมัติ") }
                .onFailure { Toast.makeText(this,it.message ?: "บันทึกไม่ได้",Toast.LENGTH_LONG).show() }
        }
    }
    private fun testExtraApi(provider: ExtraApiProvider) {
        if(provider !in ExtraApiProvider.activeProviders) { notice("API นี้ถูกยกเลิกถาวร"); return }
        if(StoryProvider.freeOnly(this) && provider!=ExtraApiProvider.OPENROUTER) { notice("โหมดฟรีเปิดอยู่ • ทดสอบได้เฉพาะ OpenRouter โมเดลฟรี") ; return }
        if(GeminiStoryService.running) { notice("กรุณารอหรือหยุดงานเขียนบทก่อนทดสอบ API"); return }
        val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
        val active=java.util.concurrent.atomic.AtomicReference<StoryTextClient?>(null)
        val view=TextView(this).apply { setPadding(dp(20),dp(16),dp(20),dp(16)); text="กำลังทดสอบ ${provider.label} ด้วยข้อความสั้น • ใช้โควตาของบัญชี • ไม่ส่งบทหรือภาพ"; setTextIsSelectable(true) }
        val dialog=AlertDialog.Builder(this).setTitle("ทดสอบ ${provider.label}").setView(ScrollView(this).apply { addView(view) }).setNegativeButton("ปิด / หยุด",null).showDirector()
        dialog.setOnDismissListener { stopped.set(true); active.get()?.cancel() }
        Thread {
            var passed=false
                var failureReason=""
            val result=try {
                val settings=ExtraApiSettings(this,provider)
                val client=CompatibleStoryClient(provider.service,settings.key(),settings.model(),{ stopped.get() },automaticRetry=false)
                active.set(client)
                if(stopped.get()) throw java.util.concurrent.CancellationException()
                StoryApiProbe.check(client)
                passed=true
                "${provider.label}: ผ่าน • ${settings.model()} • รับคำตอบ JSON สำเร็จ\n\nผลนี้ไม่รับประกันโควตาระหว่างเขียนเรื่องยาว"
            } catch(e: Exception) { failureReason=StoryApiProbe.error(e); "${provider.label}: "+failureReason }
            finally { active.set(null) }
            if(!stopped.get()) runCatching { StoryProvider.recordTest(this,StoryProvider.from(provider.id),passed,failureReason) }
            runOnUiThread { if(!stopped.get() && !isFinishing && !isDestroyed) view.text=result }
        }.apply { name="director-extra-api-probe"; start() }
    }
    private fun testStoryApis(providers: List<StoryProvider>) {
        if(StoryProvider.freeOnly(this) && providers.any { it!=StoryProvider.OPENROUTER }) { notice("โหมดฟรีเปิดอยู่ • ทดสอบได้เฉพาะ OpenRouter โมเดลฟรี"); return }
        if(GeminiStoryService.running) { notice("กรุณารอหรือหยุดงานเขียนบทก่อนทดสอบ API"); return }
        val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
        val active=java.util.concurrent.atomic.AtomicReference<StoryTextClient?>(null)
        val view=TextView(this).apply { setPadding(dp(20),dp(16),dp(20),dp(16)); text="กำลังตรวจคีย์และโมเดลที่บันทึกไว้…\nส่งคำขอทดสอบสั้นจริงไปแต่ละบริการ ใช้โควตาของบัญชีนั้น ไม่ส่งบทหรือรูป"; setTextIsSelectable(true) }
        val dialog=AlertDialog.Builder(this).setTitle("ทดสอบ API • ไม่สลับบริการแทนผลทดสอบ")
            .setView(ScrollView(this).apply { addView(view) }).setNegativeButton("ปิด / หยุด",null).showDirector()
        dialog.setOnDismissListener { stopped.set(true); active.get()?.cancel() }
        Thread {
            val results=mutableListOf<String>()
            for(target in providers) {
                if(stopped.get()) break
                if(target==StoryProvider.GEMINI) {
                    var passed=0
                    var tested=0
                    var lastFailure=""
                    val model=target.model(this)
                    for(index in 0 until 5) {
                        if(stopped.get()) break
                        val settings=GeminiSettings(this,StoryProvider.GEMINI,index)
                        var reason=""
                        val result=try {
                            if(!settings.hasSaved()) "Gemini คีย์ ${index+1}: ยังไม่ได้ตั้งค่า"
                            else {
                                val key=settings.key()
                                require(key.isNotBlank()) { "อ่านคีย์ไม่ได้" }
                                val client=GeminiClient(key,model,{ stopped.get() },automaticRetry=false)
                                active.set(client)
                                tested++
                                StoryApiProbe.check(client)
                                passed++
                                "Gemini คีย์ ${index+1}: ผ่าน • $model"
                            }
                        } catch(e: Exception) {
                            reason=StoryApiProbe.error(e); lastFailure=reason
                            "Gemini คีย์ ${index+1}: $reason"
                        } finally { active.set(null) }
                        results.add(result)
                        val text=results.joinToString("\n\n")+"\n\nทดสอบทีละคีย์ • พักเฉพาะช่องที่ได้รับ 429 (แต่ละช่องต้องอยู่คนละโปรเจกต์)"
                        runOnUiThread { if(!stopped.get() && !isFinishing && !isDestroyed) view.text=text }
                    }
                    if(!stopped.get() && tested>0) runCatching { StoryProvider.recordTest(this,target,passed==tested,lastFailure) }
                    continue
                }
                var passed=false
                var failureReason=""
                val result=try {
                    if(!target.hasKey(this)) "${target.label}: ยังไม่ได้ตั้งคีย์"
                    else {
                        val model=target.model(this)
                        val client=StoryClientFactory.create(target,target.key(this),model,{ stopped.get() },automaticRetry=false,context=this@StudioActivity)
                        active.set(client)
                        if(stopped.get()) throw java.util.concurrent.CancellationException()
                        StoryApiProbe.check(client)
                        passed=true
                        "${target.label}: ผ่าน • $model • รับคำตอบ JSON สำเร็จ"
                    }
                } catch(e: Exception) { failureReason=StoryApiProbe.error(e); "${target.label}: "+failureReason }
                finally { active.set(null) }
                if(!stopped.get() && target.hasKey(this)) runCatching { StoryProvider.recordTest(this,target,passed,failureReason) }
                val stamp=java.text.SimpleDateFormat("dd/MM HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date())
                results.add("$result\nทดสอบเมื่อ $stamp")
                val text=results.joinToString("\n\n")+"\n\nผลทดสอบ ณ เวลานี้ ไม่รับประกันโควตาระหว่างเขียนเรื่องยาว"+
                    if(results.size<providers.size) "\nกำลังทดสอบบริการถัดไป…" else ""
                runOnUiThread { if(!stopped.get() && !isFinishing && !isDestroyed) view.text=text }
            }
        }.apply { name="director-api-probe"; start() }
    }
    private fun geminiMultiKeyDialog(afterSave: (() -> Unit)?=null) {
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        panel.addView(TextView(this).apply {
            text="Gemini สูงสุด 5 คีย์ • ช่อง 1 คือคีย์เดิม\nคีย์เก็บเข้ารหัสบนเครื่อง • ไม่แสดงคีย์เดิม\nระบบเลือกคีย์ตามงานใหม่ ใช้ทีละคำขอ เว้นอย่างน้อย 3 วินาที แต่ละช่องต้องเป็นคนละโปรเจกต์ • หากได้รับ 429 จะพักเฉพาะคีย์นั้น และใช้บริการอื่นที่ตั้งค่าไว้หากพร้อม\nคีย์ในโปรเจกต์เดียวกันใช้โควตาร่วมกัน • ค่าใช้จ่ายขึ้นอยู่กับแต่ละโปรเจกต์"
        })
        val model=EditText(this).apply { hint="ชื่อโมเดล Gemini ที่ใช้ร่วมกัน"; setSingleLine(true); setText(runCatching { GeminiSettings.firstModel(this@StudioActivity) }.getOrDefault(GeminiSettings.DEFAULT_MODEL)); panel.addView(this) }
        panel.addView(Button(this).apply { text="บันทึกชื่อโมเดลร่วม"; setOnClickListener {
            val name=model.text.toString().trim()
            if(!Regex("gemini-[a-zA-Z0-9.-]+").matches(name)) { notice("ชื่อโมเดล Gemini ไม่ถูกต้อง"); return@setOnClickListener }
            getSharedPreferences("gemini_key_pool",android.content.Context.MODE_PRIVATE).edit().putString("model",name).apply()
            notice("บันทึกโมเดลร่วมแล้ว • มีผลกับงานถัดไป")
        } })
        panel.addView(Button(this).apply { text="ทดสอบ Gemini ทั้ง 5 คีย์ทีละช่อง"; setOnClickListener { testStoryApis(listOf(StoryProvider.GEMINI)) } })
        val statusViews=mutableListOf<TextView>()
        for(index in 0 until 5) {
            val settings=GeminiSettings(this,StoryProvider.GEMINI,index)
            val heading=TextView(this).apply { text="คีย์ช่องที่ ${index+1}"+if(index==0) " • คีย์เดิม" else ""; textSize=18f; setTypeface(null,Typeface.BOLD); panel.addView(this) }
            val status=TextView(this).apply {
                text=if(!settings.hasSaved()) "ยังไม่มีคีย์" else if(settings.hasKey()) "มีคีย์เข้ารหัสแล้ว • ว่างเพื่อคงคีย์เดิม" else "อ่านคีย์ไม่ได้ • กรุณาตรวจ"
                panel.addView(this)
            }
            statusViews.add(status)
            val input=EditText(this).apply {
                hint=if(settings.hasSaved()) "มีคีย์แล้ว • กรอกเฉพาะเมื่อต้องการเปลี่ยน" else "วาง Gemini API Key ช่อง ${index+1}"
                inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true); panel.addView(this)
            }
            panel.addView(Button(this).apply { text="บันทึกคีย์ช่อง ${index+1}"; setOnClickListener {
                runCatching {
                    val value=input.text.toString().trim().ifBlank { settings.key() }
                    require(value.isNotBlank()) { "กรุณากรอกคีย์ช่อง ${index+1}" }
                    val other=(0 until 5).filter { it!=index }.map { GeminiSettings(this@StudioActivity,StoryProvider.GEMINI,it) }.filter { it.hasSaved() }.map { it.key() }
                    require(other.none { it==value }) { "คีย์นี้ซ้ำกับช่องอื่น • กรุณาตรวจ" }
                    val modelName=model.text.toString().trim()
                    settings.save(value,modelName)
                    getSharedPreferences("gemini_key_pool",android.content.Context.MODE_PRIVATE).edit().putString("model",modelName).apply()
                }.onSuccess {
                    input.setText(""); input.hint="มีคีย์แล้ว • กรอกเฉพาะเมื่อต้องการเปลี่ยน"
                    status.text="บันทึกคีย์ช่อง ${index+1} แล้ว • ยังไม่ได้ทดสอบ"
                }.onFailure { notice(it.message ?: "บันทึกคีย์ไม่ได้") }
            } })
            panel.addView(Button(this).apply { text="ทดสอบคีย์ช่อง ${index+1}"; setOnClickListener {
                if(input.text.toString().isNotBlank()) { notice("กรุณาบันทึกคีย์ช่อง ${index+1} ก่อนทดสอบ"); return@setOnClickListener }
                val key=runCatching { settings.key() }.getOrNull().orEmpty()
                val selectedModel=model.text.toString().trim()
                if(key.isBlank()) { notice("ยังไม่มีคีย์ในช่อง ${index+1}"); return@setOnClickListener }
                isEnabled=false; status.text="กำลังทดสอบคีย์ช่อง ${index+1} • รอผล"
                val button=this
                Thread {
                    val passed=runCatching { StoryApiProbe.check(GeminiClient(key,selectedModel,{ false },automaticRetry=false)) }.isSuccess
                    runOnUiThread {
                        if(isFinishing || isDestroyed) return@runOnUiThread
                        button.isEnabled=true
                        status.text=if(passed) "คีย์ช่อง ${index+1} • ทดสอบผ่าน" else "คีย์ช่อง ${index+1} • ทดสอบไม่ผ่าน ตรวจคีย์ โมเดล สิทธิ์ และโควตา"
                    }
                }.start()
            } })
            panel.addView(Button(this).apply { text="ลบคีย์ช่อง ${index+1}"; setOnClickListener {
                AlertDialog.Builder(this@StudioActivity).setTitle("ลบคีย์ Gemini ช่อง ${index+1}?")
                    .setMessage("ลบเฉพาะคีย์ช่องนี้จากเครื่อง • คีย์ช่องอื่นไม่เปลี่ยน")
                    .setPositiveButton("ลบ") { _,_ -> settings.clear(); input.setText(""); status.text="ลบคีย์ช่อง ${index+1} แล้ว"; input.hint="วาง Gemini API Key ช่อง ${index+1}" }
                    .setNegativeButton("ยกเลิก",null).showDirector()
            } })
        }
        panel.addView(Button(this).apply { text="เปิด Google AI Studio เพื่อสร้างคีย์"; setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(StoryProvider.GEMINI.keyUrl))) }.onFailure { notice("เปิด Google AI Studio ไม่ได้") }
        } })
        AlertDialog.Builder(this).setTitle("Gemini • คีย์ 1–5")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("เสร็จสิ้น") { _,_ -> afterSave?.invoke() }
            .setNegativeButton("ปิด",null).showDirector()
    }
    private fun storyProviderSettings(provider: StoryProvider,afterSave: (() -> Unit)?=null) {
        if(provider==StoryProvider.GEMINI) { geminiMultiKeyDialog(afterSave); return }
        val settings=GeminiSettings(this,provider)
        val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        panel.addView(TextView(this).apply { text="${provider.label} • คีย์เก็บแยกและเข้ารหัสบนเครื่อง ไม่ส่งออกในสำรองข้อมูล • ระบบเลือกและส่งต่อระหว่างค่ายอัตโนมัติ • ส่งเฉพาะข้อความบท/ลักษณะตัวละคร ไม่ส่งรูป MASTER • โควตาและค่าบริการขึ้นกับบัญชีแต่ละค่าย" })
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
            .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("บันทึก",null)
            .setNeutralButton("ลบคีย์") { _,_ -> settings.clear(); notice("ลบคีย์ ${provider.label} จากเครื่องแล้ว") }.setNegativeButton("ปิด",null).showDirector()
        dialog.getButton(-1).setOnClickListener {
            runCatching { settings.save(key.text.toString().trim().ifBlank { settings.key() },model.text.toString().trim()) }
                .onSuccess { dialog.dismiss(); afterSave?.invoke() ?: notice("บันทึก ${provider.label} แล้ว • ระบบเลือกค่ายอัตโนมัติ") }
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
    private fun resolvePendingCharacterName() {
        if(GeminiStoryService.running) { notice("กรุณาหยุดงานก่อนแก้ชื่อ"); return }
        val job=File(project,"gemini_job")
        val status=readGeminiStatus() ?: return notice("ไม่พบสถานะงาน")
        val match=Regex("EP ([0-9]+) ซีน ([0-9]+): พบชื่อภาษาอังกฤษ '([A-Za-z][A-Za-z0-9_]*)'").find(status.optString("message"))
            ?: return notice("ไม่พบชื่อที่ต้องจับคู่ในสถานะปัจจุบัน")
        val ep=match.groupValues[1].toInt()
        val sceneNumber=match.groupValues[2].toInt()
        val files=job.listFiles().orEmpty().filter { it.name.matches(Regex("EP%02d_[0-9]+[.]json[.]pending[.]json".format(ep))) }
        val candidates=files.filter { file -> runCatching {
            val rows=GeminiStoryFiles.read(file).getJSONArray("scenes")
            (0 until rows.length()).count { rows.getJSONObject(it).optInt("scene")==sceneNumber }==1
        }.getOrDefault(false) }
        if(candidates.size!=1) { notice("ไม่พบช่วงซีนที่ค้างเพียงรายการเดียว จึงยังไม่แก้ข้อมูล"); return }
        val file=candidates.single()
        val specFile=File(job,"spec.json")
        val state=runCatching {
            val batch=GeminiStoryFiles.read(file)
            val spec=GeminiStoryFiles.read(specFile)
            Triple(batch.toString(),spec,PendingNameReview.collect(batch,spec,CharacterAliasRegistry.read(job)))
        }.getOrElse { notice("อ่านรายการที่ค้างไม่ได้ กรุณาตรวจงานเดิม"); return }
        val (snapshot,spec,issues)=state
        if(issues.isEmpty()) {
            if(!StoryProvider.ready(this)) { geminiSettings { geminiJobMenu() }; return }
            runCatching {
                GeminiStoryFiles.write(specFile,StoryProvider.configure(this,GeminiStoryFiles.read(specFile)))
                startGeminiJob(project)
            }.onFailure { notice("เริ่มงานต่อไม่ได้: "+(it.message ?: "กรุณาลองใหม่")) }
            return
        }
        choiceMenu("EP $ep • ชื่อที่ต้องตรวจในชุดนี้ (${issues.size})",issues.map { "ซีน ${it.scene} • ${it.alias}" }) { index ->
            val issue=issues[index]
            val definitions=FlowCast.definitions(spec)
            val people=issue.people.filter { person ->
                person.optString("name").isNotBlank() && person.optString("master").isNotBlank() &&
                    definitions.count { it.optString("name")==person.optString("name") && it.optString("master").equals(person.optString("master"),true) }==1
            }
            if(people.isEmpty() || people.map { it.optString("name") }.distinct().size!=people.size) {
                notice("รายชื่อตัวละครในซีนไม่ครบหรือซ้ำ กรุณาตรวจบทก่อน"); return@choiceMenu
            }
            AlertDialog.Builder(this).setTitle("ตรวจ ${issue.alias} • ซีน ${issue.scene}")
                .setMessage("การกระทำในซีน:\n${issue.action}\n\nเลือกเฉพาะเมื่อแน่ใจว่าเป็นคนเดียวกัน")
                .setPositiveButton("เลือกรายชื่อ") { _,_ ->
                    chooseStoryCharacterGrid("เลือกตัวละครที่ตรงกับ ${issue.alias}",people) { selected ->
                        val person=people[selected]
                        val chosen=person.getString("name")
                        val master=person.getString("master")
                        AlertDialog.Builder(this).setTitle("ยืนยันตัวละคร")
                            .setMessage("จับคู่ ${issue.alias} กับ $chosen สำหรับเรื่องนี้ แล้วตรวจชื่อที่เหลือต่อ?")
                            .setPositiveButton("ยืนยัน") { _,_ ->
                                if(GeminiStoryService.running) { notice("งานกำลังทำงานอยู่ กรุณาหยุดก่อน"); return@setPositiveButton }
                                runCatching {
                                    require(GeminiStoryFiles.read(file).toString()==snapshot) { "ซีนที่ค้างเปลี่ยนไป กรุณาเปิดรายการใหม่" }
                                    val freshSpec=GeminiStoryFiles.read(specFile)
                                    val fresh=FlowCast.definitions(freshSpec)
                                    require(fresh.count { it.optString("name")==chosen && it.optString("master").equals(master,true) }==1) { "ตัวละครหรือ MASTER ไม่ตรงทะเบียนเรื่อง" }
                                    require(fresh.none { it.optString("name")!=chosen && it.optString("english_name").equals(issue.alias,true) }) { "ชื่อนี้เป็นชื่อของตัวละครอื่นในทะเบียน" }
                                    val registry=CharacterAliasRegistry.read(job)
                                    CharacterAliasRegistry.add(registry,issue.alias,chosen,master)
                                    CharacterAliasRegistry.save(job,registry)
                                }.onSuccess { resolvePendingCharacterName() }
                                 .onFailure { notice("บันทึกการจับคู่ไม่ได้: "+(it.message ?: "กรุณาลองใหม่")) }
                            }.setNegativeButton("ยกเลิก",null).showDirector()
                    }
                }.setNegativeButton("กลับ",null).showDirector()
        }
    }
    private fun regeneratePendingScene() {
        if(GeminiStoryService.running) return notice("กรุณาหยุดงานก่อน")
        val job=File(project,"gemini_job")
        val match=Regex("EP ([0-9]+) ซีน ([0-9]+): พบชื่อภาษาอังกฤษ").find(readGeminiStatus()?.optString("message").orEmpty())
            ?: return notice("ไม่พบซีนที่ต้องแก้ในสถานะปัจจุบัน")
        val ep=match.groupValues[1].toInt()
        val scene=match.groupValues[2].toInt()
        val candidates=job.listFiles().orEmpty().filter { it.name.matches(Regex("EP%02d_[0-9]+[.]json[.]pending[.]json".format(ep))) }.filter { file ->
            runCatching {
                val rows=GeminiStoryFiles.read(file).getJSONArray("scenes")
                (0 until rows.length()).count { rows.getJSONObject(it).optInt("scene")==scene }==1
            }.getOrDefault(false)
        }
        if(candidates.size!=1) return notice("ไม่พบช่วงซีนที่ค้างเพียงรายการเดียว จึงยังไม่แก้ข้อมูล")
        AlertDialog.Builder(this).setTitle("เขียนซีนที่ค้างใหม่")
            .setMessage("สำรองช่วงเดิม แล้วเขียนใหม่เฉพาะ EP $ep ซีน $scene โดยใช้โครงเรื่องเดิม เนื้อหาซีนอาจเปลี่ยน ซีนที่บันทึกแล้วจะยังคงอยู่")
            .setPositiveButton("เขียนซีนใหม่") { _,_ ->
                if(GeminiStoryService.running) notice("กรุณาหยุดงานก่อน")
                else runCatching {
                    GeminiStoryEngine.prepareSceneRegeneration(job,candidates.single(),ep,scene)
                    if(!StoryProvider.ready(this)) geminiSettings { geminiJobMenu() }
                    else {
                        val specFile=File(job,"spec.json")
                        GeminiStoryFiles.write(specFile,StoryProvider.configure(this,GeminiStoryFiles.read(specFile)))
                        startGeminiJob(project)
                    }
                }.onFailure { notice("เตรียมซีนใหม่ไม่ได้: "+(it.message ?: "กรุณาตรวจงานเดิม")) }
            }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun geminiJobMenu() {
        val state=readGeminiStatus()?.optString("state")
        if(state=="complete" && plan!=null) { if(plan!!.optString("content_profile")==AutoDirectorContent.profile) autoDirectorMenu() else episodesMenu(); return }
        if(GeminiStoryService.running) {
            val live=TextView(this).apply { setPadding(dp(20),dp(16),dp(20),dp(16)); text=geminiStatusText(readGeminiStatus(),true); setTextIsSelectable(true) }
            geminiLiveDialog=live
            val dialog=AlertDialog.Builder(this).setTitle("สถานะงาน • อัปเดตทุกวินาที")
                .setView(live)
                .setPositiveButton("ดูต่อ",null).setNeutralButton("หยุดงาน") { _,_ -> startService(Intent(this,GeminiStoryService::class.java).setAction(GeminiStoryService.CANCEL)) }
                .setNegativeButton("ปิด",null).showDirector()
            dialog.setOnDismissListener { if(geminiLiveDialog===live) geminiLiveDialog=null }
        } else {
            if(readGeminiStatus()?.optString("message")?.contains("พบชื่อภาษาอังกฤษ")==true) {
                choiceMenu("งานค้างเพราะชื่อตัวละคร • เลือกวิธีทำต่อ",listOf(
                    "เขียนซีนที่ค้างใหม่ • เก็บซีนที่ผ่านแล้ว",
                    "จับคู่ชื่อตัวละครด้วยตัวเอง",
                    "ตั้งค่า API"
                )) { selected ->
                    when(selected) {
                        0 -> regeneratePendingScene()
                        1 -> resolvePendingCharacterName()
                        else -> geminiSettings()
                    }
                }
                return
            }
            AlertDialog.Builder(this).setTitle("เขียนบทต่อจากที่บันทึก")
                .setMessage(geminiStatusText(readGeminiStatus(),true)+"\nจะใช้ ${StoryProvider.label(this@StudioActivity)} และโมเดลที่ตั้งค่าปัจจุบัน เขียนเฉพาะช่วงที่ยังไม่สำเร็จ")
                .setPositiveButton("ทำต่อ") { _,_ ->
                    if(!StoryProvider.ready(this)) geminiSettings { geminiJobMenu() }
                    else runCatching {
                        val specFile=File(project,"gemini_job/spec.json"); val spec=StoryProvider.configure(this,GeminiStoryFiles.read(specFile))
                        GeminiStoryFiles.write(specFile,spec); startGeminiJob(project)
                    }.onFailure { notice("อ่านงานเดิมไม่ได้ กรุณาเริ่มเรื่องใหม่") }
                }.setNeutralButton(if(readGeminiStatus()?.optString("message")?.contains("พบชื่อภาษาอังกฤษ")==true) "แก้ซีนที่ค้าง" else "ตั้งค่า API") { _,_ ->
                    if(readGeminiStatus()?.optString("message")?.contains("พบชื่อภาษาอังกฤษ")==true)
                        choiceMenu("แก้ซีนที่ค้าง",listOf("จับคู่ชื่อตัวละคร","เขียนซีนที่ค้างใหม่","ตั้งค่า API")) { when(it) { 0 -> resolvePendingCharacterName(); 1 -> regeneratePendingScene(); else -> geminiSettings() } }
                    else geminiSettings()
                }.setNegativeButton("ปิด",null).showDirector()
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
        choiceMenu("DIRECTOR X",listOf("สร้างเรื่องใหม่ • เลือกหมวดหลัก / AI คิดพล็อต","Phase 1 • ดูโครงเรื่องที่เปิด","Phase 2–4 • ทำงานทีละ EP","ส่งออก ZIP • ตอนละ 2 ไฟล์","คำสั่งภาพ / Flow • เลือก EP","ตรวจจุดที่ควรทบทวน")) { action ->
            if(action==0) { flowPlanning(true); return@choiceMenu }
            val story=plan
            if(story==null) { if(File(project,"gemini_job/spec.json").isFile) geminiJobMenu() else notice("ยังไม่มีบท กรุณาสร้างเรื่องใหม่หรือนำเข้าบทก่อน"); return@choiceMenu }
            if(story.optString("content_profile")!=AutoDirectorContent.profile) {
                notice("เรื่องนี้ใช้รูปแบบเดิม เลือกสร้างเรื่องใหม่ใน DIRECTOR X เพื่อเลือกเวลาคลิป เสื้อผ้า 4 ส่วน และไฟล์ Gemini / Flow XML"); return@choiceMenu
            }
            if(story.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return@choiceMenu }
            val wardrobeIssues=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(story))
            if(action in 2..4 && wardrobeIssues.isNotEmpty()) { notice(wardrobeIssues.take(12).joinToString("\n")); return@choiceMenu }
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
                5 -> notice(ContinuityReview.review(story).joinToString("\n").ifBlank { "ไม่พบข้อสังเกตจากข้อมูลที่ตรวจได้ • ยังต้องตรวจเนื้อเรื่องและภาพจริงด้วยตนเอง" })
                3 -> {
                    val issues=FlowProductionPack.issues(story)
                    if(issues.isNotEmpty()) notice(issues.take(30).joinToString("\n")) else { starterFlowPack=false; flowPackPicker.launch(StoryFiles.zipName(story.optString("title"))) }
                }
            }
        }
    }
    private fun postDetails(story: JSONObject) {
        val episodes=story.optJSONArray("episodes")
        if(episodes==null || episodes.length()==0) { notice("เรื่องนี้ยังไม่มีข้อมูลตอน"); return }
        choiceMenu("เลือกตอนสำหรับคัดลอก",(0 until episodes.length()).map { i -> episodes.getJSONObject(i).let { "EP.${it.optInt("ep",i+1)} • ${it.optString("title").ifBlank { "ยังไม่มีชื่อตอน" }}" } }) { index ->
            val text=SocialPostText.build(story,index)
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
            panel.addView(TextView(this).apply { this.text="ตรวจและแก้ข้อความก่อนคัดลอก • คำอธิบายอาจมาจากเรื่องย่อ โปรดตรวจว่าไม่เปิดเผยตอนจบ • หากข้อมูลขาดสามารถพิมพ์เพิ่มได้" })
            val edit=EditText(this).apply { setText(text); minLines=8; gravity=android.view.Gravity.TOP; panel.addView(this) }
            val dialog=AlertDialog.Builder(this).setTitle("ข้อมูลโพสต์ • ตรวจและคัดลอก").setView(ScrollView(this).apply { addView(panel) })
                .setPositiveButton("คัดลอกทั้งหมด",null).setNegativeButton("ปิด",null).showDirector()
            dialog.getButton(-1).setOnClickListener {
                val result=edit.text.toString().trim()
                if(result.isBlank()) { notice("ยังไม่มีข้อมูลให้คัดลอก"); return@setOnClickListener }
                copyCommand(result); dialog.dismiss()
            }
        }
    }
    private fun savedChannelNames(): List<String> = runCatching {
        val a=JSONArray(prefs.getString("story_channels","[]")); (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
    private fun chooseStoryChannel(done: (String)->Unit) {
        // Only the requested two channels are offered for new work. Existing stories stay untouched.
        val names=listOf("นายเปีย บ้านคลองขุด",originalChannel)
        choiceMenu("ขั้นแรก • เลือกชื่อช่อง",names) { index ->
            prefs.edit().putString("selected_story_channel",names[index]).apply()
            done(names[index])
        }
    }
    private var selectedStoryTopic: String = ""
    private fun chooseStoryTopic(index: Int, done: (Int) -> Unit) {
        val topics=ChannelCatalog.shortTopics(ChannelCatalog.allCategories[index].id)
        if(topics.isEmpty()) { selectedStoryTopic=""; done(index); return }
        choiceMenu("ขั้นที่ 3 • เลือกสถานการณ์ • ${ChannelCatalog.allCategories[index].name}",listOf("🎲 สุ่มสถานการณ์")+topics) { choice ->
            selectedStoryTopic=if(choice==0) topics.random() else topics[choice-1]
            done(index)
        }
    }
    private fun chooseTopicWithRandom(channel: String,category: String,topics: List<String>,done: (String)->Unit) {
        if(topics.isEmpty()) { notice("ไม่มีหมวดย่อยให้เลือก"); return }
        val prefKey="last_topic_"+java.lang.Integer.toUnsignedString((channel+"|"+category).hashCode(),16)
        var previous=prefs.getString(prefKey,"").orEmpty()
        fun accept(value: String) { prefs.edit().putString(prefKey,value).apply(); done(value) }
        lateinit var pick: ()->Unit
        pick={
            val choices=ChannelFeaturePolicy.candidates(topics,previous)
            val result=choices.random()
            previous=result
            choiceMenu("🎲 สุ่มได้ • $result",listOf("✅ ใช้หมวดนี้","🎲 สุ่มอีกครั้ง","↩ เลือกเอง")) { answer ->
                when(answer) { 0 -> accept(result); 1 -> pick(); else -> chooseTopicWithRandom(channel,category,topics,done) }
            }
        }
        choiceMenu("เลือกหมวดย่อย • $category",listOf("🎲 สุ่มหมวดย่อย")+topics) { choice ->
            if(choice==0) pick() else accept(topics[choice-1])
        }
    }
    private fun chooseChannelTopic(channel: String, done: (Int) -> Unit) {
        if(channel=="นายเปีย บ้านคลองขุด") {
            val ids=listOf("pia_benefits","pia_safety","personal_social_drama","pia_products")
            val labels=listOf("📌 สวัสดิการและสิทธิประโยชน์\nข้อมูลสิทธิและข่าวสวัสดิการ", "💡 ความรู้และเตือนภัย\nความรู้ใกล้ตัวและภัยมิจฉาชีพ", "🎬 ละครสั้นสะท้อนสังคม\nเรื่องเล่าและข้อคิด", "🛍️ นายเปียพาช้อป\nรีวิวและแนะนำสินค้า")
            val subcategories=listOf(
                listOf("สิทธิผู้สูงอายุ", "สิทธิผู้พิการ", "สวัสดิการเด็กและครอบครัว", "บัตรสวัสดิการแห่งรัฐ", "ประกันสังคม", "สิทธิรักษาพยาบาล", "ขั้นตอนตรวจสอบสิทธิ", "ข่าวสวัสดิการอื่น ๆ"),
                listOf("เตือนภัยมิจฉาชีพ", "โทรศัพท์และข้อความหลอกลวง", "หลอกโอนเงินและซื้อขายออนไลน์", "ความปลอดภัยบัญชีและข้อมูล", "เรื่องไอทีและมือถือ", "วิธีใช้แอปใกล้ตัว", "ความรู้ในชีวิตประจำวัน", "รู้เท่าทันข่าวและข้อมูล"),
                listOf("ครอบครัวและความกตัญญู", "ผู้สูงอายุและการดูแล", "น้ำใจและการช่วยเหลือ", "ความซื่อสัตย์และความไว้ใจ", "คนทำงานและอาชีพ", "ปัญหาในชุมชน", "การให้โอกาสและเริ่มต้นใหม่", "มิตรภาพและสัตว์เลี้ยง"),
                listOf("เครื่องมือช่าง", "เครื่องมือสวนและเกษตร", "ของใช้ในบ้าน", "เครื่องใช้ไฟฟ้าในครัว", "อุปกรณ์ไอทีและมือถือ", "ของใช้ส่วนตัว", "ของใช้ในรถ", "เปรียบเทียบและเลือกซื้อสินค้า")
            )
            choiceMenu("หมวดช่อง • นายเปีย บ้านคลองขุด",labels) { selected ->
                val index=ChannelCatalog.allCategories.indexOfFirst { it.id==ids[selected] }
                require(index>=0) { "ไม่พบหมวดที่เลือก" }
                chooseTopicWithRandom(channel,ChannelCatalog.allCategories[index].name,subcategories[selected]) { topic ->
                    selectedStoryTopic=topic
                    done(index)
                }
            }
            return
        }
        val film=ChannelCatalog.filmCategories
        choiceMenu("ขั้นที่ 1 • เลือกหมวดหลัก • ร้อยฉาก",film.map { it.menuLabel }) { selected ->
            val index=ChannelCatalog.allCategories.indexOfFirst { it.id==film[selected].id }
            val topics=ChannelCatalog.shortTopics(film[selected].id)
            chooseTopicWithRandom(channel,film[selected].id,topics) { topic ->
                selectedStoryTopic=topic
                done(index)
            }
        }
    }
    private fun chooseDramaTopic(done: (Int) -> Unit) {
        val shelves=ChannelCatalog.storyShelves
        choiceMenu("ขั้นที่ 1 • เลือกหมวดหลัก",shelves.map { it.menuLabel }) { selected ->
            val shelf=shelves[selected]
            val choices=shelf.categoryIds.map { id -> ChannelCatalog.allCategories.indexOfFirst { it.id==id }.also { require(it>=0) } }
            choiceMenu("ขั้นที่ 2 • เลือกแนวย่อย",choices.map { ChannelCatalog.genreMenuLabel(ChannelCatalog.allCategories[it]) }) { picked ->
                chooseStoryTopic(choices[picked],done)
            }
        }
    }
    private fun chooseClip(done: (String,Int) -> Unit) {
        choiceMenu("เลือกรูปแบบ • ทุกแบบแนวตั้ง 9:16",listOf("คลิปสั้น","คลิปยาว")) { format ->
            val times=if(format==0) ClipPlan.shortSeconds else ClipPlan.longSeconds
            choiceMenu(if(format==0) "เวลาเนื้อหาคลิปสั้น • ไม่รวมปก" else "เวลาเนื้อหาต่อ EP • ไม่รวมปกเปิด",times.map { ClipPlan.label(it)+(if(it==if(format==0) 30 else 300) " • แนะนำเริ่มต้น" else "") }) { index -> done(if(format==0) "short" else "long",times[index]) }
        }
    }
    private fun flowPlanning(autoDirector: Boolean = false) {
        if(GeminiStoryService.running) { geminiJobMenu(); return }
        if(!StoryProvider.ready(this)) { geminiSettings { flowPlanning(autoDirector) }; return }
        var chosenChannel=originalChannel
        val configure: (Int) -> Unit = { index ->
            chooseClip { format, seconds ->
            val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(8),dp(16),dp(8)) }
            panel.addView(TextView(this).apply { text="หมวด: ${categories[index]}\nสถานการณ์ตั้งต้น: $selectedStoryTopic\n${if(chosenChannel=="นายเปีย บ้านคลองขุด") "เลือกนายเปียเป็นผู้ดำเนินเรื่องและเลือกชุดเริ่มต้น" else "AI เลือกนักแสดงจากคลัง MASTER และตั้งชื่ออัตโนมัติตามบท"} ${if(format=="short") "สร้างเรื่องจบในคลิปเดียว" else "แล้วเขียนเรื่องต่อครบทุก EP"}"; textSize=14f })
            fun stepHeader(number: String, label: String, color: Int) {
                val row=LinearLayout(this).apply {
                    orientation=LinearLayout.HORIZONTAL
                    gravity=Gravity.CENTER_VERTICAL
                    setPadding(dp(12),dp(8),dp(12),dp(8))
                    background=GradientDrawable().apply { setColor(color); cornerRadius=dp(12).toFloat() }
                }
                row.addView(TextView(this).apply { text=number; textSize=30f; setTypeface(null,Typeface.BOLD); setTextColor(Color.WHITE); setPadding(0,0,dp(12),0) })
                row.addView(TextView(this).apply { text=label; textSize=17f; setTypeface(null,Typeface.BOLD); setTextColor(Color.WHITE) },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
                panel.addView(row,LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin=dp(10); bottomMargin=dp(6) })
            }
            fun colorAction(button: Button, color: Int, darkText: Boolean=false) {
                button.backgroundTintList=ColorStateList.valueOf(color)
                button.setTextColor(if(darkText) Color.rgb(25,31,36) else Color.WHITE)
                button.textSize=16f
                button.setTypeface(null,Typeface.BOLD)
                button.minHeight=dp(52)
            }
            stepHeader("1","AI คิดเรื่องจากแนวที่เลือก",Color.rgb(34,105,160))
            panel.addView(TextView(this).apply { text="ล็อกแนวคิดเรื่องแล้ว • AI จะคิดเรื่องจากหมวดและสถานการณ์ที่เลือก"; setTextColor(Color.WHITE); setPadding(dp(12),dp(8),dp(12),dp(8)) })
            val needsFacts=ChannelCatalog.allCategories[index].id in setOf("pia_benefits","pia_safety","pia_products")
            val secondaryGenre = SecondaryGenreControl(this, panel, needsFacts, categories[index], selectedStoryTopic)
            val premise=EditText(this).apply {
                hint="ข้อมูลจริงและแหล่งอ้างอิงที่ตรวจสอบแล้ว"
                maxLines=4
                if(needsFacts) panel.addView(this)
            }
            if(needsFacts) panel.addView(TextView(this).apply { text="หมวดนี้ต้องระบุข้อมูลจริงที่ตรวจสอบแล้ว • AI จะใช้สร้างบท ไม่แต่งข้อมูลสินค้า ความปลอดภัย หรือสิทธิประโยชน์เอง"; setTextColor(Color.WHITE); setPadding(dp(8),dp(8),dp(8),dp(8)); setBackgroundColor(Color.rgb(149,54,40)) })
            var productButton: Button? = null
            if(ChannelCatalog.allCategories[index].id=="pia_products") {
                productButton=Button(this).apply { text="ค้นหาสินค้า • รับลิงก์ Affiliate"; colorAction(this,Color.rgb(34,105,160)); setOnClickListener { ProductAssist.show(this@StudioActivity,premise,selectedStoryTopic) } }
                panel.addView(productButton)
                panel.addView(Button(this).apply { text="ดูตัวอย่างโฆษณาสินค้า TikTok"; colorAction(this,Color.rgb(225,161,35),true); setOnClickListener {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://ads.tiktok.com/business/creativecenter/inspiration/topads/pc/en?region=TH"))) }.onFailure { notice("เปิด TikTok Creative Center ไม่ได้") }
                } })
            }
            val advancedControls=mutableListOf<android.view.View>()
            panel.addView(Button(this).apply {
                text="⚙ ตัวเลือกเพิ่มเติม • แตะเพื่อเปิด"
                colorAction(this,Color.rgb(245,245,245),true)
                setOnClickListener {
                    val show=advancedControls.any { it.visibility!=android.view.View.VISIBLE }
                    advancedControls.forEach { it.visibility=if(show) android.view.View.VISIBLE else android.view.View.GONE }
                    text=if(show) "⚙ ซ่อนตัวเลือกเพิ่มเติม" else "⚙ ตัวเลือกเพิ่มเติม • แตะเพื่อเปิด"
                }
            })
            val presenters=runCatching { if(chosenChannel=="นายเปีย บ้านคลองขุด") { installPiaDefaults(); JSONArray().put(piaCatalog().getJSONObject(0)) } else castingCatalog() }.getOrDefault(JSONArray())
            val presenter=Spinner(this).apply {
                val labels=listOf(if(chosenChannel=="นายเปีย บ้านคลองขุด") "เลือก MASTER ผู้ดำเนินเรื่อง" else "ให้ AI เลือกตัวละครหลัก")+(0 until presenters.length()).map { presenters.getJSONObject(it).optString("display_name").ifBlank { presenters.getJSONObject(it).optString("id") } }
                adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,labels)
                panel.addView(this)
                if(chosenChannel=="นายเปีย บ้านคลองขุด" && presenters.length()>0 && presenters.getJSONObject(0).optString("id")==piaActorId) setSelection(1)
            }
            presenter.visibility=android.view.View.GONE
            if (chosenChannel=="นายเปีย บ้านคลองขุด") panel.addView(TextView(this).apply {
                text=if (presenters.length()>0) "ตัวละครหลัก: นายเปีย บ้านคลองขุด ✓" else "ไม่พบ MASTER นายเปีย • กรุณาตรวจคลังตัวละคร"
                setTextColor(Color.WHITE); setPadding(dp(8),dp(8),dp(8),dp(8))
            })
            val presenterButton=Button(this).apply {
                fun updateLabel() { text=(if(chosenChannel=="นายเปีย บ้านคลองขุด") "ตัวละครหลัก (ต้องเลือก): " else "ตัวละครหลัก (ไม่บังคับ): ")+presenter.selectedItem.toString() }
                updateLabel()
                setOnClickListener {
                    val items=(0 until presenter.count).map { i ->
                        presenter.getItemAtPosition(i).toString() to if(i==0) null else runCatching { castingSource(presenters.getJSONObject(i-1)) }.getOrNull()
                    }
                    chooseNamedCharacterGrid("เลือกตัวละครหลัก",items) { i -> presenter.setSelection(i); updateLabel() }
                }
            }
            if (chosenChannel=="นายเปีย บ้านคลองขุด") {
                presenterButton.text="ตัวละครหลัก: นายเปีย บ้านคลองขุด ✓"
                presenterButton.setOnClickListener(null)
                presenterButton.isEnabled=false
            }
            presenterButton.visibility=android.view.View.GONE
            colorAction(presenterButton,Color.rgb(62,86,110))
            advancedControls.add(presenterButton); panel.addView(presenterButton)
            fun proposalProgress(label: String, stopped: java.util.concurrent.atomic.AtomicBoolean, active: java.util.concurrent.atomic.AtomicReference<StoryTextClient?>, providers: List<StoryProvider>): Pair<androidx.appcompat.app.AlertDialog, java.util.concurrent.atomic.AtomicInteger> {
                val current=java.util.concurrent.atomic.AtomicInteger(1)
                val start=android.os.SystemClock.elapsedRealtime()
                val handler=android.os.Handler(android.os.Looper.getMainLooper())
                val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(16),dp(24),dp(8)) }
                val status=TextView(this).apply { textSize=18f }
                content.addView(status)
                content.addView(ProgressBar(this).apply { isIndeterminate=true })
                val dialog=AlertDialog.Builder(this).setTitle(label).setView(content).setNegativeButton("ยกเลิก",null).showDirector()
                val tick=object: Runnable {
                    override fun run() {
                        if(!dialog.isShowing) return
                        val seconds=(android.os.SystemClock.elapsedRealtime()-start)/1000
                        val waiting=if(seconds>=60) "\nใช้เวลานานกว่าปกติ • รอต่อหรือกดยกเลิกได้" else ""
                        status.text="กำลังติดต่อ ${providers.getOrNull(current.get()-1)?.label ?: "API"} • ${current.get()}/${providers.size}\nเวลารวม %02d:%02d\nหากไม่ได้รับคำตอบ ระบบจะลอง API ถัดไป%s".format(seconds/60,seconds%60,waiting)
                        handler.postDelayed(this,1000)
                    }
                }
                dialog.setOnDismissListener { handler.removeCallbacks(tick); stopped.set(true); active.get()?.cancel() }
                handler.post(tick)
                return dialog to current
            }
            val selectedActors=linkedSetOf<String>().apply { if(chosenChannel=="นายเปีย บ้านคลองขุด" && presenters.length()>0) add(piaActorId) }
            val selectedProps=linkedMapOf<String,String>()
            val proposedProps=linkedMapOf<String,String>()
            var automaticEquipmentUnresolved=false
            val propSummary=TextView(this).apply { text="ไม่บังคับ • ข้ามได้หากเรื่องไม่ใช้อุปกรณ์"; setPadding(dp(8),dp(8),dp(8),dp(8)); setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(28,62,95)) }
            var automaticPropsPending=false
            var automaticPropsStart: (() -> Unit)?=null
            var automaticCastPending=false
            var autoRunning=false
            var automaticCreate: (() -> Unit)?=null
            val castButton=Button(this).apply { text=if(chosenChannel=="นายเปีย บ้านคลองขุด") "②.2 ตรวจและเลือกตัวละคร • นายเปียเป็นตัวหลัก" else "②.2 ตรวจและเลือกตัวละครจาก MASTER" }
            val castSummary=TextView(this).apply { text="ยังไม่ได้ยืนยันตัวละคร"; setPadding(dp(8),dp(8),dp(8),dp(8)); setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(146,57,38)) }
            stepHeader("2","AI เลือกตัวละครจาก MASTER",Color.rgb(19,116,80))
            val suggestedRoles=linkedMapOf<String,String>()
            var castProposalPending=false
            val analyzeCast=Button(this).apply {
                text="②.1 ✨ วิเคราะห์และเสนอตัวละคร"
                setOnClickListener {
                    val automatic=automaticCastPending
                    automaticCastPending=false
                    val catalog=runCatching {
                        val all=if(chosenChannel=="นายเปีย บ้านคลองขุด") piaCatalog() else castingCatalog()
                        JSONArray().apply {
                            for(i in 0 until all.length()) {
                                val row=all.getJSONObject(i)
                                val file=runCatching { castingSource(row) }.getOrNull()
                                if(file?.isFile==true && runCatching { MasterLibrary.digest(file.readBytes())==row.getString("sha256") }.getOrDefault(false)) put(row)
                            }
                        }
                    }.getOrElse { if(automatic) autoRunning=false; notice("ตรวจคลัง MASTER ไม่ได้: ${it.message}"); return@setOnClickListener }
                    if(catalog.length()==0 || (chosenChannel=="นายเปีย บ้านคลองขุด" && (0 until catalog.length()).none { catalog.getJSONObject(it).optString("id")==piaActorId })) {
                        if(automatic) autoRunning=false
                        notice("ไม่พบภาพ MASTER ที่ตรวจสอบได้ กรุณาเพิ่มหรือตรวจคลังตัวละคร"); return@setOnClickListener
                    }
                    val providers=StoryProvider.eligible(this@StudioActivity).filter { it.hasKey(this@StudioActivity) }
                    if(providers.isEmpty()) { if(automatic) autoRunning=false; notice("ยังไม่มี API สำหรับวิเคราะห์ กรุณาตั้งค่า API หรือเลือก MASTER เอง"); return@setOnClickListener }
                    val channel=chosenChannel; val situation=selectedStoryTopic; val detail=premise.text.toString(); val category=categories[index]
                    val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
                    val active=java.util.concurrent.atomic.AtomicReference<StoryTextClient?>(null)
                    val (loading, providerNumber)=proposalProgress("กำลังวิเคราะห์ตัวละครจาก MASTER",stopped,active,providers)
                    if(automatic) {
                        loading.setOnCancelListener { autoRunning=false }
                        loading.getButton(-2).setOnClickListener { autoRunning=false; loading.dismiss() }
                    }
                    Thread {
                        var result: JSONObject?=null
                        for((providerIndex,provider) in providers.withIndex()) {
                            providerNumber.set(providerIndex+1)
                            if(stopped.get()) break
                            val answer=runCatching {
                                val client=StoryClientFactory.create(provider,provider.key(this@StudioActivity),provider.model(this@StudioActivity),{ stopped.get() },automaticRetry=false,context=this@StudioActivity)
                                client.useProposalTimeouts()
                                active.set(client)
                                val prompt="Suggest a cast for this Thai story. Channel: $channel; category: $category; situation: $situation; details: $detail. Use ONLY the provided library IDs. Suggest only characters suitable for the story, with a short proposed role in Thai. Do not infer occupation or identity from a face; roles are fictional suggestions for user review. Use as many as needed, from 1 to 8. "+(if(channel=="นายเปีย บ้านคลองขุด") "Include $piaActorId as central protagonist and suitable supporting roles. " else "")+"Catalog: "+AutoCast.promptCatalog(catalog).toString()
                                client.generate(prompt,AutoCast.schema()).also { AutoCast.validate(it,catalog); if(channel=="นายเปีย บ้านคลองขุด") AutoCast.requirePresenter(it,piaActorId) }
                            }.getOrNull()
                            if(answer!=null) { result=answer; break }
                        }
                        val proposal=result
                        runOnUiThread {
                            if(stopped.get() || isFinishing || isDestroyed) return@runOnUiThread
                            active.set(null); loading.dismiss()
                            if(proposal==null) { if(automatic) autoRunning=false; notice("ยังวิเคราะห์ตัวละครไม่ได้ • กรุณาเลือกจากคลัง MASTER เอง"); return@runOnUiThread }
                            if(situation!=selectedStoryTopic || detail!=premise.text.toString()) { if(automatic) autoRunning=false; notice("ข้อมูลเรื่องเปลี่ยนระหว่างวิเคราะห์ • กรุณาวิเคราะห์อีกครั้ง"); return@runOnUiThread }
                            suggestedRoles.clear()
                            val cast=proposal.getJSONArray("cast")
                            for(i in 0 until cast.length()) cast.getJSONObject(i).let { suggestedRoles[it.getString("library_id")]=it.getString("role") }
                            if(automatic) {
                                selectedActors.clear(); selectedActors.addAll(suggestedRoles.keys)
                                selectedProps.clear(); proposedProps.clear()
                                automaticEquipmentUnresolved=ChannelFeaturePolicy.hasFantasyTools(channel)
                                if(automaticEquipmentUnresolved) propSummary.text="กำลังวิเคราะห์อุปกรณ์ • รอผลก่อนสร้างบท"
                                castProposalPending=false
                                val names=(0 until catalog.length()).map { catalog.getJSONObject(it) }.filter { it.optString("id") in selectedActors }
                                    .map { "${it.optString("display_name").ifBlank { it.optString("id") }} • ${it.optString("id")} • ${suggestedRoles[it.optString("id")].orEmpty()}" }
                                castButton.text="②.2 AI เลือกแล้ว ${selectedActors.size} ตัว • แตะเพื่อแก้"
                                castSummary.text="✓ AI เลือกอัตโนมัติ ${names.size} ตัว:\n"+names.joinToString("\n")
                                castSummary.setBackgroundColor(Color.rgb(19,112,72))
                                if(ChannelFeaturePolicy.hasFantasyTools(channel)) automaticPropsStart?.invoke()
                                else { autoRunning=false; automaticCreate?.invoke() }
                            } else {
                                castProposalPending=true
                                castSummary.text="ระบบเสนอ ${suggestedRoles.size} ตัว • รอตรวจภาพและยืนยัน"
                                castSummary.setBackgroundColor(Color.rgb(146,57,38))
                                castButton.performClick()
                            }
                        }
                    }.start()
                }
            }
            panel.addView(Button(this).apply {
                text="✨ กดครั้งเดียว • AI เลือกและสร้างบท"
                colorAction(this,Color.rgb(18,139,89))
                setOnClickListener {
                    if(autoRunning) { notice("กำลังทำงานอยู่ • กรุณารอ"); return@setOnClickListener }
                    if(needsFacts && premise.text.toString().trim().isEmpty()) { notice("หมวดนี้ต้องระบุข้อมูลจริงที่ตรวจสอบแล้วก่อน"); return@setOnClickListener }
                    autoRunning=true; automaticCastPending=true; analyzeCast.performClick()
                }
            })
            colorAction(analyzeCast,Color.rgb(224,143,30),true)
            colorAction(castButton,Color.rgb(244,192,57),true)
            analyzeCast.visibility=android.view.View.GONE; castButton.visibility=android.view.View.GONE
            advancedControls.add(analyzeCast); advancedControls.add(castButton)
            panel.addView(analyzeCast)
            panel.addView(castButton)
            panel.addView(castSummary)
            castButton.setOnClickListener {
                val choices=runCatching { if(chosenChannel=="นายเปีย บ้านคลองขุด") piaCatalog() else castingCatalog() }.getOrElse { notice("เปิดคลังตัวละครไม่ได้"); return@setOnClickListener }
                val draft=selectedActors.toMutableSet().apply { if(castProposalPending) addAll(suggestedRoles.keys) }
                if(chosenChannel=="นายเปีย บ้านคลองขุด") draft.add(piaActorId)
                val grid=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
                grid.addView(TextView(this).apply { text="ติ๊กตัวละครที่ต้องการใช้ในเรื่อง สูงสุด 8 ตัว • ตรวจภาพและบทบาทก่อนยืนยัน" })
                val order=(0 until choices.length()).sortedBy { if(choices.getJSONObject(it).optString("id") in draft) 0 else 1 }
                var row: LinearLayout?=null
                for((position,n) in order.withIndex()) {
                    if(position%2==0) { row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; grid.addView(row) }
                    val actor=choices.getJSONObject(n); val id=actor.getString("id")
                    val source=runCatching { castingSource(actor) }.getOrNull()
                    val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)); layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
                    row?.addView(card)
                    val box=android.widget.CheckBox(this).apply {
                        text=(actor.optString("display_name").ifBlank { id })+"\n"+id+(suggestedRoles[id]?.let { "\nบทบาทที่เสนอ: $it" } ?: "")+(if(source?.isFile==true) "" else "\nไม่พบภาพ MASTER")
                        isChecked=id in draft
                        isEnabled=source?.isFile==true && !(chosenChannel=="นายเปีย บ้านคลองขุด" && id==piaActorId)
                    }
                    if(source?.isFile==true) card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(source)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(150)); contentDescription=box.text; setOnClickListener { fullImage(source,box.text.toString()) } })
                    card.addView(box)
                    box.setOnCheckedChangeListener { _,checked -> if(checked) draft.add(id) else draft.remove(id) }
                }
                AlertDialog.Builder(this).setTitle("เลือกภาพ MASTER ตัวละคร").setView(ScrollView(this).apply { addView(grid) }).setPositiveButton("ใช้รายการที่เลือก") { _,_ ->
                    if(draft.size>8) { notice("เลือกตัวละครได้สูงสุด 8 คน กรุณาเลือกใหม่"); return@setPositiveButton }
                    selectedActors.clear(); selectedActors.addAll(draft)
                    castProposalPending=false
                    castButton.text=if(selectedActors.isEmpty()) "②.2 ตรวจและเลือกตัวละครจาก MASTER" else "②.2 เลือกแล้ว ${selectedActors.size} ตัว • แตะเพื่อแก้"
                    val names=(0 until choices.length()).map { choices.getJSONObject(it) }.filter { it.optString("id") in selectedActors }
                        .map { "${it.optString("display_name").ifBlank { it.optString("id") }} • ${it.optString("id")}" }
                    castSummary.text=if(names.isEmpty()) "ยังไม่ได้ยืนยันตัวละคร" else "✓ ยืนยันแล้ว ${names.size} ตัว:\n"+names.joinToString("\n")
                    castSummary.setBackgroundColor(if(names.isEmpty()) Color.rgb(146,57,38) else Color.rgb(19,112,72))
                }.setNegativeButton("ยกเลิก",null).showDirector()
            }
            if(!ChannelFeaturePolicy.hasFantasyTools(chosenChannel)) {
                stepHeader("3","อุปกรณ์ • ยังไม่มีตัวเลือกในช่องนี้",Color.rgb(181,99,30))
            }
            if(ChannelFeaturePolicy.hasFantasyTools(chosenChannel)) {
                stepHeader("3","อุปกรณ์ • AI เลือกให้เมื่อจำเป็น",Color.rgb(181,99,30))
                val chooseProps=Button(this).apply { text="③.2 ตรวจและเลือกอุปกรณ์จาก MASTER" }
                val analyzeProps=Button(this).apply {
                    text="③.1 ✨ วิเคราะห์และเสนออุปกรณ์"
                    setOnClickListener {
                        val automatic=automaticPropsPending
                        automaticPropsPending=false
                        val providers=StoryProvider.eligible(this@StudioActivity).filter { it.hasKey(this@StudioActivity) }
                        if(providers.isEmpty()) { if(automatic) { autoRunning=false; propSummary.text="วิเคราะห์อุปกรณ์ไม่ได้ • เลือกเองหรือกดวิเคราะห์ใหม่" }; notice("ยังไม่มี API สำหรับวิเคราะห์ • เลือกอุปกรณ์เองได้"); return@setOnClickListener }
                        val all=runCatching { castingCatalog() }.getOrElse { if(automatic) autoRunning=false; notice("เปิดคลังตัวละครไม่ได้"); return@setOnClickListener }
                        val actors=(0 until all.length()).map { all.getJSONObject(it) }.filter { it.optString("id") in selectedActors }
                        val available=propCatalog.mapIndexedNotNull { n,name -> val id=propId(n); if(runCatching { assets.open("prop_masters/$id.png").use { it.read()>=0 } }.getOrDefault(false)) id to name else null }
                        if(available.isEmpty()) { if(automatic) { autoRunning=false; propSummary.text="ไม่พบ MASTER อุปกรณ์ • กรุณาตรวจหรือเลือกเอง" }; notice("ไม่พบภาพ MASTER อุปกรณ์ • กรุณาตรวจคลัง"); return@setOnClickListener }
                        val topic=selectedStoryTopic; val details=premise.text.toString(); val category=categories[index]
                        val actorSnapshot=selectedActors.toSet()
                        val actorText=actors.joinToString("\n") { "${it.optString("id")} = ${it.optString("display_name")}" }
                        val catalogText=available.joinToString("\n") { "${it.first} = ${it.second}" }
                        val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
                        val active=java.util.concurrent.atomic.AtomicReference<StoryTextClient?>(null)
                        val (loading, providerNumber)=proposalProgress("กำลังวิเคราะห์อุปกรณ์จาก MASTER",stopped,active,providers)
                        if(automatic) {
                            loading.setOnCancelListener { autoRunning=false; propSummary.text="ยกเลิกการวิเคราะห์อุปกรณ์ • เลือกเองหรือเริ่มใหม่" }
                            loading.getButton(-2).setOnClickListener { autoRunning=false; propSummary.text="ยกเลิกการวิเคราะห์อุปกรณ์ • เลือกเองหรือเริ่มใหม่"; loading.dismiss() }
                        }
                        Thread {
                            val item=JSONObject().put("type","object").put("properties",JSONObject().put("prop_id",JSONObject().put("type","string")).put("owner_library_id",JSONObject().put("type","string"))).put("required",JSONArray().put("prop_id").put("owner_library_id"))
                            val schema=JSONObject().put("type","object").put("properties",JSONObject().put("assignments",JSONObject().put("type","array").put("items",item))).put("required",JSONArray().put("assignments"))
                            val prompt="Suggest only story-relevant equipment from the real catalog below for human review. Return assignments with prop_id and owner_library_id. Use an exact listed character ID only when the story clearly supports an owner, otherwise use an empty string. Do not assign equipment to everyone. Return an empty array when no equipment is appropriate. Category: $category; situation: $topic; details: $details\nCHARACTERS:\n$actorText\nEQUIPMENT:\n$catalogText"
                            var answer: JSONObject?=null
                            for((providerIndex,provider) in providers.withIndex()) {
                                providerNumber.set(providerIndex+1)
                                if(stopped.get()) break
                                answer=runCatching { val client=StoryClientFactory.create(provider,provider.key(this@StudioActivity),provider.model(this@StudioActivity),{ stopped.get() },automaticRetry=false,context=this@StudioActivity); client.useProposalTimeouts(); active.set(client); client.generate(prompt,schema) }.getOrNull()?.takeIf { it.optJSONArray("assignments")!=null }
                                if(answer!=null) break
                            }
                            val result=answer
                            runOnUiThread {
                                if(stopped.get() || isFinishing || isDestroyed) return@runOnUiThread
                                active.set(null); loading.dismiss()
                                if(actorSnapshot!=selectedActors.toSet() || topic!=selectedStoryTopic || details!=premise.text.toString()) { if(automatic) autoRunning=false; notice("ข้อมูลเปลี่ยนระหว่างวิเคราะห์ • กรุณาวิเคราะห์อีกครั้ง"); return@runOnUiThread }
                                if(result==null) { if(automatic) { autoRunning=false; propSummary.text="วิเคราะห์อุปกรณ์ไม่สำเร็จ • เลือกเองหรือกดวิเคราะห์ใหม่" }; notice("ยังเสนออุปกรณ์ไม่ได้ • เลือกจากคลังเองได้"); return@runOnUiThread }
                                proposedProps.clear()
                                val assignments=result.optJSONArray("assignments") ?: JSONArray()
                                val allowed=available.map { it.first }.toSet()
                                for(i in 0 until assignments.length()) assignments.optJSONObject(i)?.let { row ->
                                    val id=row.optString("prop_id"); val owner=row.optString("owner_library_id")
                                    if(id in allowed && (owner.isBlank() || owner in actorSnapshot)) proposedProps[id]=owner
                                }
                                if(proposedProps.isEmpty()) {
                                    if(assignments.length()>0) { if(automatic) { autoRunning=false; propSummary.text="จับคู่อุปกรณ์ไม่ได้ • กรุณาตรวจเอง" }; notice("AI เสนออุปกรณ์ที่จับคู่ MASTER หรือผู้ใช้ไม่ได้ • กรุณาตรวจเอง"); return@runOnUiThread }
                                    if(automatic) { selectedProps.clear(); automaticEquipmentUnresolved=false; propSummary.text="AI ไม่เสนออุปกรณ์สำหรับเรื่องนี้ • กำลังสร้างบท"; propSummary.setBackgroundColor(Color.rgb(28,62,95)); autoRunning=false; automaticCreate?.invoke() }
                                    else notice("AI ไม่เสนออุปกรณ์ • เลือกเองหรือไม่ใช้อุปกรณ์ได้")
                                    return@runOnUiThread
                                }
                                if(automatic) {
                                    if(proposedProps.size!=assignments.length()) { autoRunning=false; notice("ข้อเสนออุปกรณ์บางรายการจับคู่ไม่ได้ • กรุณาตรวจเอง"); return@runOnUiThread }
                                    selectedProps.clear(); selectedProps.putAll(proposedProps)
                                    automaticEquipmentUnresolved=false
                                    proposedProps.clear()
                                    val names=selectedProps.map { (id,owner) ->
                                        val n=propCatalog.indices.first { propId(it)==id }
                                        val actor=actors.firstOrNull { it.optString("id")==owner }?.optString("display_name").orEmpty()
                                        "${propCatalog[n]} • $id • "+actor.ifBlank { "ยังไม่ระบุผู้ใช้" }
                                    }
                                    propSummary.text="✓ AI เลือกอุปกรณ์ ${names.size} ชิ้น:\n"+names.joinToString("\n")
                                    propSummary.setBackgroundColor(Color.rgb(19,112,72))
                                    autoRunning=false; automaticCreate?.invoke()
                                } else {
                                    propSummary.text="ระบบเสนอ ${proposedProps.size} ชิ้น • ตรวจภาพและยืนยันรายการ"
                                    chooseProps.performClick()
                                }
                            }
                        }.start()
                    }
                }
                automaticPropsStart={ automaticPropsPending=true; analyzeProps.performClick() }
                colorAction(analyzeProps,Color.rgb(224,143,30),true)
                colorAction(chooseProps,Color.rgb(244,192,57),true)
                analyzeProps.visibility=android.view.View.GONE; chooseProps.visibility=android.view.View.GONE
                advancedControls.add(analyzeProps); advancedControls.add(chooseProps)
                panel.addView(analyzeProps)
                panel.addView(chooseProps)
                panel.addView(propSummary)
                chooseProps.setOnClickListener {
                    val choices=runCatching { castingCatalog() }.getOrElse { notice("เปิดคลังตัวละครไม่ได้"); return@setOnClickListener }
                    val actors=(0 until choices.length()).map { choices.getJSONObject(it) }.filter { it.optString("id") in selectedActors }
                    val actorIds=listOf("")+actors.map { it.optString("id") }
                    val actorNames=listOf("ยังไม่ระบุผู้ใช้")+actors.map { it.optString("display_name").ifBlank { it.optString("id") }+" • "+it.optString("id") }
                    val draft=proposedProps.toMutableMap().apply { putAll(selectedProps) }
                    val grid=LinearLayout(this).apply { orientation=1; setPadding(dp(12),dp(8),dp(12),dp(8)) }
                    grid.addView(TextView(this).apply { text="ตรวจภาพและเลือกเฉพาะอุปกรณ์ที่เกี่ยวข้อง • ผู้ใช้กำหนดภายหลังได้ • แตะภาพเพื่อขยาย" })
                    propCatalog.forEachIndexed { index,name ->
                        val id=propId(index)
                        val available=runCatching { assets.open("prop_masters/$id.png").use { it.read()>=0 } }.getOrDefault(false)
                        val box=android.widget.CheckBox(this).apply { text="$name • $id"+if(available) "" else " • ไม่พบภาพ MASTER"; isChecked=id in draft; isEnabled=available; grid.addView(this) }
                        val owner=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,actorNames); setSelection(actorIds.indexOf(draft[id]).coerceAtLeast(0)); grid.addView(this) }
                        val image=ImageView(this).apply { adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(150)); visibility=if(box.isChecked) android.view.View.VISIBLE else android.view.View.GONE; grid.addView(this) }
                        fun showImage() { runCatching {
                            val file=File(cacheDir,"prop_masters/$id.png"); file.parentFile!!.mkdirs()
                            if(!file.isFile) assets.open("prop_masters/$id.png").use { input -> file.outputStream().use { input.copyTo(it) } }
                            image.setImageBitmap(castingThumbnail(file)); image.setOnClickListener { fullImage(file,name) }
                        }.onFailure { notice("เปิดภาพ $name ไม่ได้") } }
                        if(box.isChecked) showImage()
                        owner.onItemSelectedListener=object: android.widget.AdapterView.OnItemSelectedListener {
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, rowId: Long) { if(box.isChecked) draft[id]=actorIds[position] }
                        }
                        box.setOnCheckedChangeListener { _,checked ->
                            image.visibility=if(checked) android.view.View.VISIBLE else android.view.View.GONE
                            if(checked) { draft[id]=actorIds[owner.selectedItemPosition.coerceAtLeast(0)]; showImage() } else draft.remove(id)
                        }
                    }
                    AlertDialog.Builder(this).setTitle("เลือก MASTER อุปกรณ์").setView(ScrollView(this).apply { addView(grid) })
                        .setPositiveButton("ยืนยันรายการ") { _,_ ->
                            selectedProps.clear(); selectedProps.putAll(draft)
                            automaticEquipmentUnresolved=false
                            proposedProps.clear()
                            val names=selectedProps.map { (id,owner) -> val i=propCatalog.indices.first { propId(it)==id }; "${propCatalog[i]} • $id • "+(actors.firstOrNull { it.optString("id")==owner }?.optString("display_name") ?: "ยังไม่ระบุผู้ใช้") }
                            propSummary.text=if(names.isEmpty()) "ไม่ได้เลือกอุปกรณ์ • ข้ามได้" else "✓ อุปกรณ์ที่เลือก ${names.size} ชิ้น:\n"+names.joinToString("\n")
                            propSummary.setBackgroundColor(if(names.isEmpty()) Color.rgb(28,62,95) else Color.rgb(19,112,72))
                        }.setNegativeButton("ยกเลิก",null).showDirector()
                }
            }
            val presenterOutfits=listOf("เลือกชุดตามเนื้อเรื่อง", "สูทกรมท่า เนกไทลายฟ้า", "ชุดช่างสีเขียวเข้ม", "ชุดช่างสีกรมท่าส้ม", "ชุดช่างสีเทาเข้ม", "ชุดช่างสีน้ำเงิน", "เสื้อเชิ้ตสีครีม กางเกงเขียว", "เสื้อเชิ้ตสีฟ้า กางเกงกรมท่า", "เสื้อเชิ้ตสีขาว กางเกงดำ", "เสื้อเชิ้ตสีดำ กางเกงเทา", "เสื้อช่างกรม กางเกงเทา ถุงมือ", "ชุดทำสวนสีครีม หมวกและรองเท้าบูท", "ชุดทำสวนกรมท่า หมวกคลุมต้นคอและรองเท้าบูท", "ชุดดำลำลอง • 3 มุมมอง")
            // Keep slot indices compatible with the existing picker and story snapshot.
            val presenterOutfit=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,presenterOutfits) }
            val piaFaceLock=android.widget.CheckBox(this).apply { text="ล็อกใบหน้านายเปียจาก MASTER ต้นฉบับ"; isChecked=true }
            val piaFaceStatus=TextView(this).apply { text="ยังไม่ได้เลือกกรอบใบหน้า • ใช้ภาพ MASTER ต้นฉบับ" }
            val piaFacePreview=ImageView(this).apply { adjustViewBounds=true; visibility=android.view.View.GONE; layoutParams=LinearLayout.LayoutParams(-1,dp(150)) }
            fun showPiaFace(target: File) {
                piaFaceStatus.text="เลือกกรอบใบหน้าแล้ว • ตรวจภาพก่อนสร้าง"
                piaFacePreview.setImageBitmap(previewBitmap(Uri.fromFile(target)))
                piaFacePreview.visibility=android.view.View.VISIBLE
                piaFacePreview.setOnClickListener { fullImage(target,"ใบหน้านายเปียจาก MASTER") }
            }
            val choosePiaFace=Button(this).apply { text="✂️ วาดกรอบใบหน้า / ล็อกหน้า" }
            if(chosenChannel=="นายเปีย บ้านคลองขุด") {
                panel.addView(TextView(this).apply { text=if(piaIdentityFile.isFile) "ใช้ MASTER นายเปียที่เลือกเอง • แตะภาพเพื่อตรวจ" else "ใช้ภาพนายเปียที่มาพร้อมแอป • เลือก MASTER ต้นฉบับของคุณได้" })
                runCatching { castingSource(piaCatalog().getJSONObject(0)) }.getOrNull()?.takeIf { it.isFile }?.let { master ->
                    panel.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(master)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(150)); setOnClickListener { fullImage(master,"MASTER นายเปียที่ใช้สร้างเรื่องใหม่") } })
                }
                panel.addView(Button(this).apply { text="เลือกภาพ MASTER ต้นฉบับนายเปีย"; setOnClickListener { piaIdentityPicker.launch(arrayOf("image/*")) } })
                panel.addView(piaFaceLock)
                panel.addView(TextView(this).apply { text="เลือกกรอบใบหน้ามุมตรงจาก MASTER ครั้งเดียว แล้วระบบใช้ซ้ำได้ • ตรวจภาพก่อนส่งสร้าง" })
                panel.addView(choosePiaFace)
                panel.addView(piaFaceStatus)
                panel.addView(piaFacePreview)
                val faceStore=FaceReferenceStore(File(filesDir,"master_face_references"))
                runCatching { faceStore.latest(castingSource(piaCatalog().getJSONObject(0))) }.getOrNull()?.let { showPiaFace(it) }
                choosePiaFace.setOnClickListener {
                    runCatching {
                        val master=castingSource(piaCatalog().getJSONObject(0))
                        val cropView=FaceReferenceCropView(this@StudioActivity,master)
                        val layout=LinearLayout(this@StudioActivity).apply { orientation=1; addView(TextView(context).apply { text="ลากกรอบรอบใบหน้ามุมตรงของนายเปีย แล้วกดบันทึก" }); addView(cropView,LinearLayout.LayoutParams(-1,dp(520))) }
                        val dialog=AlertDialog.Builder(this@StudioActivity).setTitle("วาดกรอบใบหน้านายเปีย").setView(layout).setPositiveButton("บันทึกกรอบ",null).setNegativeButton("ยกเลิก",null).showDirector()
                        dialog.getButton(-1).setOnClickListener {
                            runCatching {
                                val target=faceStore.newFile(master); val pending=File(target.parentFile,target.name+".pending")
                                try { cropView.save(pending); check(pending.renameTo(target)) { "บันทึกภาพครอปไม่ได้" } } finally { pending.delete() }
                                showPiaFace(target); piaFaceLock.isChecked=true; dialog.dismiss()
                            }.onFailure { notice(it.message ?: "บันทึกกรอบใบหน้าไม่ได้") }
                        }
                    }.onFailure { notice(it.message ?: "เปิด MASTER ไม่ได้") }
                }
            }
            if(chosenChannel=="นายเปีย บ้านคลองขุด") {
                panel.addView(TextView(this).apply { text="ชุดนายเปีย • ติ๊กเลือก 1 ชุด หรือให้ระบบเลือกตามเรื่อง" })
                val buttons=mutableListOf<android.widget.RadioButton>()
                val auto=android.widget.RadioButton(this).apply { text="เลือกชุดตามเนื้อเรื่อง"; isChecked=true; panel.addView(this) }
                buttons.add(auto)
                auto.setOnClickListener { presenterOutfit.setSelection(0); buttons.forEachIndexed { i,button -> button.isChecked=i==0 } }
                for(row in 0 until 7) {
                    val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; panel.addView(this) }
                    for(column in 0 until 2) {
                        val slot=row*2+column+1
                        if(slot>13) continue
                        val card=LinearLayout(this).apply { orientation=1; setPadding(dp(3),dp(4),dp(3),dp(4)); layoutParams=LinearLayout.LayoutParams(0,-2,1f); line.addView(this) }
                        val preview=ImageView(this).apply { val file=presenterOutfitFile(piaActorId,slot); setImageBitmap(castingThumbnail(file)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(135)); contentDescription=presenterOutfits[slot]; card.addView(this) }
                        val radio=android.widget.RadioButton(this).apply { text=presenterOutfits[slot]; textSize=12f; card.addView(this) }
                        buttons.add(radio)
                        val choose={ presenterOutfit.setSelection(slot); buttons.forEachIndexed { i,button -> button.isChecked=i==slot } }
                        radio.setOnClickListener { choose() }; preview.setOnClickListener { choose() }; card.setOnClickListener { choose() }
                    }
                }
                panel.addView(TextView(this).apply { text="หากเปลี่ยนไฟล์ภาพชุด ให้เปิดหน้าสร้างเรื่องใหม่เพื่อดูภาพล่าสุด"; textSize=12f })
            }
            if(chosenChannel=="นายเปีย บ้านคลองขุด") panel.addView(Button(this).apply {
                text="เพิ่มหรือเปลี่ยนรูปของชุดที่เลือก"
                setOnClickListener {
                    if(presenter.selectedItemPosition<=0 || presenterOutfit.selectedItemPosition<=0) {
                        notice("เลือก MASTER ผู้ดำเนินเรื่องและชุดก่อนเพิ่มรูป")
                    } else {
                        prefs.edit().putString("presenter_outfit_actor",presenters.getJSONObject(presenter.selectedItemPosition-1).getString("id"))
                            .putInt("presenter_outfit_slot",presenterOutfit.selectedItemPosition).apply()
                        presenterOutfitPicker.launch(arrayOf("image/*"))
                    }
                }
            })
            if(chosenChannel=="นายเปีย บ้านคลองขุด") panel.addView(Button(this).apply {
                text="✂️ วาดกรอบใบหน้า / ล็อกหน้า"
                setOnClickListener { choosePiaFace.performClick() }
            })
            val episodes=Spinner(this).apply { adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,listOf("1 EP • จบในตอน","ให้ AI เลือก 4–7 EP","3 EP","4 EP","5 EP","6 EP","7 EP")); if(format!="short") panel.addView(this) }
            if(format=="short") panel.addView(TextView(this).apply { text="คลิปสั้น • จบในคลิปเดียว" })
            panel.addView(TextView(this).apply { text="${if(format=="short") "คลิปสั้น" else "คลิปยาว"} • ${ClipPlan.label(seconds)}${if(format=="short") "" else " ต่อ EP"} • ไม่รวมปกเปิด 3 วินาที • สร้างเป็นเรื่องใหม่ เก็บเรื่องเดิมไว้\n\nทั้งสองช่องใช้ API เขียนบทชุดเดียวกันที่ตั้งค่าไว้ • ระบบเลือกค่ายอัตโนมัติจากคีย์ที่พร้อม • เนื้อหาอาจถูกส่งไปยังค่ายที่รับช่วง • โควตาและค่าบริการขึ้นอยู่กับแต่ละบัญชี • เขียนเฉพาะบทและคำสั่ง ส่งวิดีโอไปสร้างใน Flow ภายหลัง" })
            stepHeader("4","AI สร้างบทต่อให้อัตโนมัติ",Color.rgb(157,101,26))
            val createDialog=AlertDialog.Builder(this).setTitle(if(autoDirector) "DIRECTOR X • ${categories[index]}" else "สร้างบทด้วย ${StoryProvider.label(this@StudioActivity)}")
                .setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("สร้างบทในแอป",null)
                .setNeutralButton(if(autoDirector) "กลับเมนู DIRECTOR X" else "ใช้แชร์ไฟล์แทน") { _,_ -> if(autoDirector) autoDirectorMenu() else flowPlanningShare() }.setNegativeButton("ยกเลิก",null).showDirector()
            if (productButton != null && getSharedPreferences("product_assist", MODE_PRIVATE).getString("shared_link", "").isNullOrBlank()) {
                createDialog.window?.decorView?.post { if (createDialog.isShowing && !isFinishing && !isDestroyed) ProductAssist.show(this@StudioActivity,premise,selectedStoryTopic,true) }
            }
            automaticCreate={ if(createDialog.isShowing && !isFinishing && !isDestroyed) createDialog.getButton(-1).performClick() }
            createDialog.getButton(-1).setOnClickListener {
                    runCatching {
                        if(ChannelCatalog.allCategories[index].id in setOf("pia_benefits","pia_safety","pia_products")) require(premise.text.toString().trim().isNotEmpty()) { "กรุณาระบุข้อมูลที่ตรวจสอบแล้วหรือรายละเอียดสินค้าก่อนสร้างบท" }
                        require(secondaryGenre.ready) { "กรุณารอ AI เลือกแนวรอง หรือเลือกแนวรองเอง" }
                        require(!autoRunning) { "AI กำลังเลือกตัวละครและอุปกรณ์ • กรุณารอหรือยกเลิก" }
                        require(selectedActors.isNotEmpty()) { "กรุณาเลือกตัวละครด้วย AI หรือเลือกจาก MASTER ก่อนสร้างบท" }
                        require(!automaticEquipmentUnresolved) { "การเลือกอุปกรณ์อัตโนมัติยังไม่เสร็จหรือจับคู่ไม่ได้ • ลองใหม่หรือเลือกอุปกรณ์ด้วยตนเอง" }
                        val allCatalog=if(chosenChannel=="นายเปีย บ้านคลองขุด") piaCatalog() else castingCatalog()
                        val catalog=CastingSelection.filter(allCatalog,selectedActors)
                        val presenterId=if(chosenChannel=="นายเปีย บ้านคลองขุด") {
                            require(presenter.selectedItemPosition>0) { "กรุณาเพิ่มรูป MASTER ผู้ดำเนินเรื่องในคลัง แล้วเลือกจากรายการ" }
                            presenters.getJSONObject(presenter.selectedItemPosition-1).getString("id")
                        } else if(presenter.selectedItemPosition>0) presenters.getJSONObject(presenter.selectedItemPosition-1).getString("id") else ""
                        require(presenterId.isBlank() || selectedActors.isEmpty() || presenterId in selectedActors) { "ตัวละครหลักที่เลือกไม่อยู่ในภาพที่ติ๊ก กรุณาติ๊กเพิ่มหรือเลือกตัวละครหลักใหม่" }
                        require(selectedProps.values.all { it.isBlank() || it in selectedActors }) { "ผู้ใช้อุปกรณ์ไม่อยู่ในตัวละครที่เลือก กรุณาตรวจรายการอุปกรณ์อีกครั้ง" }
                        selectedProps.keys.forEach { id -> require(runCatching { assets.open("prop_masters/$id.png").use { it.read()>=0 } }.getOrDefault(false)) { "ไม่พบภาพ MASTER อุปกรณ์ $id" } }
                        val reviewedProps=selectedProps.map { (id,owner) ->
                            val n=propCatalog.indices.first { propId(it)==id }
                            val who=(0 until allCatalog.length()).map { allCatalog.getJSONObject(it) }.firstOrNull { it.optString("id")==owner }?.optString("display_name").orEmpty()
                            "${propCatalog[n]} ($id)"+(if(who.isBlank()) " • ไม่ระบุผู้ใช้" else " • ผู้ใช้ที่เลือก: $who ($owner)")
                        }
                        val reviewedRoles=selectedActors.mapNotNull { id -> suggestedRoles[id]?.let { "$id: $it" } }
                        val storyPremise=listOf("สถานการณ์ตั้งต้นที่เลือก: "+selectedStoryTopic,premise.text.toString(),if(reviewedRoles.isEmpty()) "" else "บทบาทที่เลือกจากข้อเสนอ (ตรวจความสอดคล้องกับเรื่อง): "+reviewedRoles.joinToString("; "),if(reviewedProps.isEmpty()) "" else "อุปกรณ์ MASTER ที่เลือก: "+reviewedProps.joinToString("; ")+". ใช้ตามเหตุการณ์ ระบุฉาก ผู้ถือ และความต่อเนื่อง ห้ามเดาผู้ใช้ที่ยังไม่ระบุ").filter { it.isNotBlank() }.joinToString("\n")
                        if(presenterId.isNotBlank() && presenterOutfit.selectedItemPosition>0) require(presenterOutfitFile(presenterId,presenterOutfit.selectedItemPosition).isFile) { "กรุณาเพิ่มรูปของชุดที่เลือก หรือเลือกชุดตามเนื้อเรื่อง" }
                        require(catalog.length()>0) { "คลัง MASTER ว่าง กรุณาเพิ่มตัวละครก่อน" }
                        if(ChannelCatalog.allCategories[index].id=="younger_man_older_woman") {
                            val candidates=(0 until catalog.length()).map { catalog.getJSONObject(it) }
                            val men=candidates.filter { AutoCast.adultRomanceEligible(it) && it.optString("character_gender")=="man" && it.optInt("character_age") in 25..30 }
                            val women=candidates.filter { AutoCast.adultRomanceEligible(it) && it.optString("character_gender")=="woman" && it.optInt("character_age") in 45..55 }
                            require(men.any { man -> women.any { woman -> woman.optInt("character_age")-man.optInt("character_age")>=15 } }) {
                                "หมวดนี้ต้องมีผู้ชายอายุ 25–30 และผู้หญิงอายุ 45–55 ห่างกันอย่างน้อย 15 ปี ในคลัง MASTER • พบชาย ${men.size} คน หญิง ${women.size} คน กรุณาตรวจข้อมูลในคลังหรือเลือกแนวทั่วไป"
                            }
                        }
                        if(ChannelCatalog.allCategories[index].id=="older_man_younger_woman") {
                            val candidates=(0 until catalog.length()).map { catalog.getJSONObject(it) }
                            val men=candidates.filter { AutoCast.adultRomanceEligible(it) && it.optString("character_gender")=="man" && it.optInt("character_age") in 45..60 }
                            val women=candidates.filter { AutoCast.adultRomanceEligible(it) && it.optString("character_gender")=="woman" && it.optInt("character_age") in 25..35 }
                            require(men.any { man -> women.any { woman -> man.optInt("character_age")-woman.optInt("character_age")>=15 } }) {
                                "แนวนี้ต้องมี MASTER ผู้ชายอายุ 45–60 ปี และผู้หญิงอายุ 25–35 ปี โดยผู้ชายอายุมากกว่าอย่างน้อย 15 ปี กรุณาตรวจคลังตัวละคร"
                            }
                        }
                        val folder=File(base,UUID.randomUUID().toString()).apply { mkdirs() }
                        if (ChannelCatalog.allCategories[index].id == "pia_products") {
                            ProductAssist.confirmedProduct(this@StudioActivity, premise.text.toString())?.let { product ->
                                GeminiStoryFiles.write(File(folder, "product_info.json"), product)
                            }
                        }
                        for(n in 0 until catalog.length()) {
                            val row=catalog.getJSONObject(n); val target=File(folder,"master_candidates/"+row.getString("file")); target.parentFile!!.mkdirs()
                            val original=castingSource(row); require(original.isFile && MasterLibrary.digest(original.readBytes())==row.getString("sha256")) { "ภาพ MASTER ของ ${row.optString("display_name")} สูญหายหรือเปลี่ยนไป" }
                            val outfitImage=if(row.getString("id")==presenterId && presenterOutfit.selectedItemPosition>0) presenterOutfitFile(presenterId,presenterOutfit.selectedItemPosition) else null
                            PiaReferencePolicy.capture(original,outfitImage,target,File(folder,"presenter_outfit_reference.image"))
                        }
                        val count=if(format=="short") 1 else when(episodes.selectedItemPosition) { 0 -> 1; 1 -> 0; else -> episodes.selectedItemPosition+1 }
                        val reviewedPropRecords=JSONArray().apply { selectedProps.forEach { (id,owner) ->
                            val n=propCatalog.indices.first { propId(it)==id }
                            put(JSONObject().put("prop_id",id).put("name",propCatalog[n]).put("owner_library_id",owner))
                        } }
                        GeminiStoryFiles.write(File(folder,"gemini_job/spec.json"),StoryProvider.configure(this,ClipPlan.apply(JSONObject(),format,seconds).put("category",categories[index]).put("channel",chosenChannel).put("presenter_library_id",presenterId)
                            .put("secondary_genre",secondaryGenre.selectedId).put("pia_face_lock",chosenChannel=="นายเปีย บ้านคลองขุด" && piaFaceLock.isChecked).put("required_cast_ids",JSONArray(selectedActors.toList())).put("selected_prop_masters",reviewedPropRecords).put("presenter_outfit",if(chosenChannel=="นายเปีย บ้านคลองขุด" && presenterOutfit.selectedItemPosition>0) presenterOutfits[presenterOutfit.selectedItemPosition] else "").put("content_profile",if(autoDirector) AutoDirectorContent.profile else "").put("autodirector_version",if(autoDirector) AutoDirectorContent.version else 0).put("performance_version",1).put("audio_version",1).put("command_version",1).put("outline_review_required",false).put("creative_seed",UUID.randomUUID().toString()).put("auto_cast",true).put("casting_version",3).put("recent_casts",AutoCast.recentCasts(base,folder)).put("master_catalog",catalog).put("supporting_characters",JSONArray()).put("lead_references",JSONObject()).put("story_situation",selectedStoryTopic).put("premise",storyPremise).put("episodes",count)))
                        prefs.edit().putString("project",folder.name).apply(); createDialog.dismiss(); render(); startGeminiJob(folder)
                    }.onFailure { notice(it.message ?: "เริ่มงานไม่ได้") }
                }
        }
        }
        chooseStoryChannel { channel -> chosenChannel=channel; chooseChannelTopic(channel,configure) }
    }
    private fun flowPlanningShare() {
        chooseStoryChannel { channel ->
        if(channel=="นายเปีย บ้านคลองขุด") { notice("ช่องนี้ต้องเลือก MASTER ผู้ดำเนินเรื่องผ่านเมนูสร้างบทในแอป"); return@chooseStoryChannel }
        chooseChannelTopic(channel) { index ->
            chooseClip { format, seconds ->
            val premise=EditText(this).apply { hint="แนวคิดเรื่อง (เว้นว่างให้คิดใหม่)" }
            fun prepare(): String {
                val code=FlowProductionPack.masterCommand(categories[index],channel)+"\n"+ChannelCatalog.direction(categories[index])+"\n"+(if(format=="short") ChannelCatalog.shortDirection(categories[index],ClipPlan.scenes(seconds)) else ChannelCatalog.longDirection)+"\n"+ChannelCatalog.storyConsistency+"\nUSER DURATION OVERRIDE: ${if(format=="short") "One standalone episode only; beginning, middle and ending within this one clip. " else ""}${ClipPlan.scenes(seconds)} scenes per EP, 8 seconds each, ${ClipPlan.time(ClipPlan.scenes(seconds)*8)} content per EP. Supersedes default 24 scenes and 180-second minimum above. Set clip_plan_version=1, clip_format=$format, target_seconds=$seconds, scenes_per_episode=${ClipPlan.scenes(seconds)} in story.json.\nแนวย่อย: "+selectedStoryTopic+"\nแนวคิด: "+premise.text.toString().ifBlank { "คิดเรื่องใหม่อัตโนมัติ" }
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
    }
    private fun flowTestMenu() {
        val ids=FlowProductionPack.names.keys.toList(); val checked=BooleanArray(ids.size)
        val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        var row: LinearLayout?=null
        ids.forEachIndexed { i,id ->
            if(i%2==0) { row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; grid.addView(row) }
            val file=File(filesDir,"character_masters/$id.master")
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)); layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
            row?.addView(card)
            if(file.isFile) card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(file)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(140)); contentDescription=FlowProductionPack.names.getValue(id); setOnClickListener { fullImage(file,FlowProductionPack.names.getValue(id)) } })
            card.addView(android.widget.CheckBox(this).apply { text=FlowProductionPack.names.getValue(id)+(if(file.isFile) "" else "\nไม่พบ MASTER"); isEnabled=file.isFile; setOnCheckedChangeListener { _,value -> checked[i]=value } })
        }
        val dialog=AlertDialog.Builder(this).setTitle("เลือกตัวละครทดสอบวิดีโอ")
            .setView(ScrollView(this).apply { addView(grid) })
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
        choiceMenu("${key(s)} • ${if(sceneFrameApproved(s)) "ภาพผ่านแล้ว" else "รอตรวจภาพ"}",listOf("1. สร้างภาพฉากจาก MASTER","3. สร้างวิดีโอจากภาพที่ผ่าน","แก้บท / ชุด / สถานที่","นำเข้าภาพจาก Gemini / Flow","นำเข้าคลิป MP4","2. เทียบ MASTER / ยืนยันภาพ","ดูคลิป","เวอร์ชันไฟล์","เสนอขยายบทสั้น • ตรวจยืนยันก่อนบันทึก")) { i -> when(i) {
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
            8 -> suggestLongerSpeech(s)
        } }
    }

    private fun suggestLongerSpeech(scene: JSONObject) {
        if(ProductionCommandCheck.dialogueTurns(scene).isNotEmpty()) return notice("ขณะนี้ให้แก้บทหลายคนในเมนูแก้บททีละช่วงก่อน")
        val field=if(scene.optString("dialogue").trim().let { it.isNotBlank() && it !in listOf("ไม่มี","none") }) "dialogue" else "narration"
        val original=scene.optString(field).trim()
        if(original.isBlank() || original in listOf("ไม่มี","none","ไม่มีเสียงบรรยาย")) return notice("ฉากนี้ไม่มีบทพูดหรือบรรยายให้ขยาย")
        val providers=StoryProvider.eligible(this).filter { it.hasKey(this) }
        if(providers.isEmpty()) return notice("ตั้งค่า API เขียนบทก่อนใช้ระบบเสนอขยายบท")
        val snapshot=scene.toString()
        val projectId=project.name
        val sceneId=key(scene)
        val sceneContext=JSONObject().put("action",scene.optString("action")).put("speaker",scene.optString("speaker")).put("location",scene.optString("location")).put("speech",original)
        notice("กำลังเสนอขยายบท • บทเดิมยังไม่เปลี่ยน")
        Thread {
            val schema=JSONObject().put("type","object").put("properties",JSONObject().put("text",JSONObject().put("type","string"))).put("required",JSONArray().put("text"))
            val prompt="""Suggest a natural Thai ${if(field=="dialogue") "dialogue line" else "narration"} for an eight-second scene based only on the supplied scene. Expand the original only where the meaning and events support it; do not add facts, characters, plot events or stage directions. Keep it naturally speakable without rushing. Return JSON with a single text field. The user must review it before saving. Scene: $sceneContext"""
            var result: String?=null
            for(provider in providers) {
                result=runCatching { StoryClientFactory.create(provider,provider.key(this),provider.model(this),{ false },automaticRetry=false,context=this@StudioActivity).generate(prompt,schema).optString("text").trim() }.getOrNull()?.takeIf { it.isNotBlank() }
                if(result!=null) break
            }
            val proposal=result
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                if(proposal==null) { notice("ไม่สามารถขอข้อเสนอบทได้ กรุณาตรวจ API แล้วลองใหม่"); return@runOnUiThread }
                if(project.name!=projectId || scene.toString()!=snapshot || plan?.let { current -> rows(current).none { it===scene } } != false) { notice("ซีนเปลี่ยนระหว่างขอข้อเสนอ กรุณาเปิดใหม่"); return@runOnUiThread }
                val panel=LinearLayout(this).apply { orientation=1; setPadding(dp(16),dp(12),dp(16),dp(12)) }
                panel.addView(TextView(this).apply { text="บทเดิม:\n$original\n\nตรวจความหมาย เหตุการณ์ และทดลองอ่านให้เหมาะกับ 8 วินาที ก่อนยืนยัน" })
                val edit=EditText(this).apply { setText(proposal); minLines=3; panel.addView(this) }
                val dialog=AlertDialog.Builder(this).setTitle("ตรวจข้อเสนอบท • $sceneId").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("ยืนยันและบันทึก",null).setNegativeButton("ใช้บทเดิม",null).showDirector()
                dialog.getButton(-1).setOnClickListener {
                    val value=edit.text.toString().trim()
                    if(value.isBlank()) { notice("กรุณาระบุบทหรือเลือกใช้บทเดิม"); return@setOnClickListener }
                    if(project.name!=projectId || scene.toString()!=snapshot || plan?.let { current -> rows(current).none { it===scene } } != false) { dialog.dismiss(); notice("ซีนเปลี่ยนแล้ว กรุณาขอข้อเสนอใหม่"); return@setOnClickListener }
                    scene.put(field,value); scene.put("user_review","needs_revision"); scene.remove("flow_image_revision")
                    saveStory(); dialog.dismiss(); render(); notice("บันทึกบทที่ยืนยันแล้ว • โปรดตรวจภาพและคำสั่ง Flow อีกครั้ง")
                }
            }
        }.apply { name="director-dialogue-suggestion"; start() }
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
        if (importing) return notice("กำลังนำเข้าภาพ กรุณารอให้เสร็จก่อน")
        val activeStory = plan ?: return notice("กรุณาเปิดโครงการก่อนนำเข้าภาพ")
        val scenes = rows(activeStory)
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
        val importStory = activeStory
        val importProjectId = importFolder.name
        importing = true
        val remaining = uris.toMutableList()
        var savedCount=0; var skippedCount=0
        val failures=mutableListOf<String>()
        val progressDialog=AlertDialog.Builder(this).setTitle("Smart Image Import • ชุดละ 5").setMessage("กำลังเตรียม ${uris.size} ภาพ • ${((uris.size+4)/5)} ชุด").setCancelable(false).showDirector()
        fun next() {
            if (remaining.isEmpty()) {
                importing = false; pendingRecovery.deleteRecursively(); render()
                progressDialog.dismiss()
                if (failures.isEmpty() && savedCount > 0) {
                    notice("Smart Import: สำเร็จ $savedCount ภาพ • ข้าม $skippedCount • เปิดคิวอีกครั้งเพื่อตรวจสถานะ")
                } else {
                    showImportReceipt("ครั้งนี้ • บันทึกภาพ $savedCount • ข้าม $skippedCount • ล้มเหลว ${failures.size}" + if(failures.isEmpty()) "" else "\n"+failures.joinToString("\n"))
                }
                return
            }
            val processed = uris.size - remaining.size
            progressDialog.setMessage("ชุด ${processed/5+1}/${(uris.size+4)/5} • ภาพ ${processed+1}/${uris.size}\nบันทึกสำเร็จ $savedCount • ข้าม $skippedCount • ผิดพลาด ${failures.size}")
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
                        check(!target.exists()) { "ฉากนี้มีภาพแล้ว • ไม่เขียนทับ" }
                        check(tmp.renameTo(target)) { "บันทึกภาพไม่ได้" }
                        // Record completion only after a durable image save.
                        val episodeNumber = s.getInt("ep")
                        val sceneId = s.getInt("scene")
                        val episodes = importStory.optJSONArray("episodes")
                        val episode = (0 until (episodes?.length() ?: 0)).mapNotNull { episodes?.optJSONObject(it) }
                            .firstOrNull { it.optInt("ep") == episodeNumber }
                        val episodeScenes = episode?.optJSONArray("scenes")
                        val sceneIds = (0 until (episodeScenes?.length() ?: 0)).mapNotNull { index ->
                            episodeScenes?.optJSONObject(index)?.optInt("scene", 0)?.takeIf { it > 0 }
                        }
                        val progressSaved = if (sceneIds.isNotEmpty() &&
                            sceneIds.size == episodeScenes?.length() &&
                            sceneIds.distinct().size == sceneIds.size && sceneId in sceneIds
                        ) {
                            val store = ImageSceneProgressStore(this@StudioActivity)
                            val projectId = "$importProjectId:EP$episodeNumber"
                            val current = store.load(projectId, sceneIds)
                            store.recordCompletedScene(projectId, current, sceneId) != null
                        } else false
                        runOnUiThread {
                            savedCount++
                            if (!progressSaved) failures.add("$name: บันทึกภาพแล้ว แต่บันทึกสถานะคิวไม่สำเร็จ")
                            next()
                        }
                    } catch (e: Exception) { runOnUiThread { failures.add("$name: ${e.message}"); next() } }
                }.start()
            }
            fun assign(s: JSONObject) {
                // Smart import never overwrites an existing image; replacement remains a separate manual action.
                if (image(s).exists()) { skippedCount++; next() }
                else save(s)
            }
            if (assignments[uri] != null) assign(assignments.getValue(uri))
            else choiceMenu("🧩 จับคู่ภาพ: $name", scenes.map { "🎞️ " + key(it) + if (image(it).exists()) " ✓" else " • ขาดภาพ" }, cancel = "⏭️ ข้าม", cancelable = false, onCancel = { next() }) { i -> assign(scenes[i]) }
        }
        next()
    }
    private fun castingThumbnail(source: File): android.graphics.Bitmap? = runCatching {
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeFile(source.path,bounds)
        val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=1 }
        while(bounds.outWidth/options.inSampleSize>256 || bounds.outHeight/options.inSampleSize>384) options.inSampleSize *= 2
        android.graphics.BitmapFactory.decodeFile(source.path,options)
    }.getOrNull()
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
            plan?.optInt("duration_seconds",8) ?: 8, plan?.optString("aspect_ratio","9:16") ?: "9:16",SoundDesign.notes(s),ProductionCommandCheck.offscreen(s),ProductionCommandCheck.dialogueTurns(s))
    }
    private fun flowMappingCurrent(s: JSONObject): Boolean = image(s).exists() &&
        s.optLong("flow_image_revision",-1L) == image(s).lastModified()
    private fun flowCommand(s: JSONObject): String {
        if (!sceneFrameApproved(s) || FlowScenePrompt.issues(flowSpec(s)).isNotEmpty()) return "ยังไม่พร้อมส่ง Flow • เปิดภาพ ${key(s)} และยืนยันลักษณะ/ตำแหน่งผู้พูดในเมนูกำหนดตัวละครก่อน ไม่เดาตำแหน่งจากข้อความบท"
        return ShortFlowPrompt.build(flowSpec(s),"Slow gentle pan.")
    }
    private fun landscapeSceneImage(file: File): Boolean {
        val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
        android.graphics.BitmapFactory.decodeFile(file.path,bounds)
        return bounds.outHeight>0 && kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight-9.0/16)<0.04
    }
    private fun frameReviewFacts(scene: JSONObject): String {
        val base=flowSpec(scene).toString()
        val selected=FlowReferencePrompt.people(scene).mapNotNull { person ->
            val id=referenceId(person.optString("master"))
            val master=runCatching { masterFor(person) }.getOrNull() ?: return@mapNotNull null
            val digest=runCatching { MasterLibrary.digest(master.readBytes()) }.getOrNull() ?: return@mapNotNull null
            val key=wardrobeSourceKey(project.name,id,digest,person)
            if(prefs.getBoolean(key,WardrobeEvidence.useMasterOutfit(person.optString("outfit")))) id else null
        }.sorted()
        return if(selected.isEmpty()) base else base+"|wardrobe_master="+selected.joinToString(",")
    }
    private fun sceneFrameApproved(s: JSONObject): Boolean = landscapeSceneImage(image(s)) && SceneFrameReview.current(s,image(s),FlowReferencePrompt.people(s).map { masterFor(it) },frameReviewFacts(s))
    private fun reviewedVideoCommand(s: JSONObject, copy: Boolean=false) {
        if(plan?.optString("aspect_ratio","16:9")!="9:16") { landscapeCopyMenu(); return }
        if(plan?.optString("content_profile")==AutoDirectorContent.profile) {
            val issues=AutoDirectorWardrobe.issues(AutoDirectorWardrobe.shots(plan!!))
            if(issues.isNotEmpty()) { notice(issues.take(12).joinToString("\n")); return }
        }
        if(!image(s).isFile) { notice("สร้างภาพฉากใน Gemini / Flow แล้วนำเข้าภาพก่อน จากนั้นเทียบ MASTER และยืนยันภาพ"); return }
        if(!sceneFrameApproved(s) || FlowScenePrompt.issues(flowSpec(s)).isNotEmpty()) { compareScene(s); return }
        val code=runCatching { ShortFlowPrompt.build(flowSpec(s),"Slow gentle pan.") }.getOrElse { notice(it.message ?: "ตรวจคำสั่ง Flow"); return }
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
            val master=runCatching { masterFor(person) }.getOrNull()
            val sourceId=referenceId(person.optString("master"))
            val digest=master?.takeIf { it.isFile }?.let { runCatching { MasterLibrary.digest(it.readBytes()) }.getOrNull() }
            val sourceKey=wardrobeSourceKey(project.name,sourceId,digest.orEmpty(),person)
            val fromMaster=digest!=null && prefs.getBoolean(sourceKey,WardrobeEvidence.useMasterOutfit(person.optString("outfit")))
            panel.addView(TextView(this).apply { text="${person.optString("name")} • แหล่งชุด: ${if(fromMaster) "MASTER" else "ตามบท"}\n${if(fromMaster) "เทียบชุดกับภาพ MASTER ด้านล่าง" else "ชุดในบท: ${person.optString("outfit")}"}"; setTypeface(null,Typeface.BOLD) })
            if(master?.isFile==true) panel.addView(ImageView(this).apply {
                setImageBitmap(previewBitmap(Uri.fromFile(master))); adjustViewBounds=true
                contentDescription="MASTER ของ ${person.optString("name")} • แตะเพื่อขยาย"
                setOnClickListener { fullImage(master,"MASTER • ${person.optString("name")}") }
            },LinearLayout.LayoutParams(-1,dp(150)))
            val appearance=EditText(this).apply { hint="ลักษณะจากภาพ เช่น ผู้หญิงผมสั้นประบ่า"; setText(person.optString("visual_description")); panel.addView(this) }
            val position=EditText(this).apply { hint="ตำแหน่งจากภาพ เช่น ซ้าย / กลาง / ขวา"; setText(person.optString("screen_position")); panel.addView(this) }
            appearance to position
        }
        // Explicit anatomical wrist selection is required: screen-left/right is never inferred.
        val watchSides=(0 until cast.length()).map { i ->
            val actor=cast.getJSONObject(i)
            val choices=arrayOf("ไม่ล็อกนาฬิกา", "ข้อมือซ้ายของตัวละคร", "ข้อมือขวาของตัวละคร")
            panel.addView(TextView(this).apply { text="ตำแหน่งนาฬิกา • ${actor.optString("name")} (ยืนยันจากภาพต้นฉบับ)" })
            android.widget.Spinner(this).apply {
                adapter=ArrayAdapter(this@StudioActivity,android.R.layout.simple_spinner_dropdown_item,choices)
                setSelection(when(actor.optString("watch_side")) { "left" -> 1; "right" -> 2; else -> 0 })
                panel.addView(this)
            }
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
            watchSides.forEachIndexed { i, spinner ->
                val actor=people.getJSONObject(i)
                when(spinner.selectedItemPosition) {
                    1 -> actor.put("watch_side","left")
                    2 -> actor.put("watch_side","right")
                    else -> actor.remove("watch_side")
                }
            }
            val contradictions=AccessoryContinuityContract.conflicts((0 until people.length()).map { people.getJSONObject(it) })
            if(contradictions.isNotEmpty()) { notice(contradictions.joinToString("\\n")); return@setOnClickListener }
            candidate.put("speaker",speaker.text.toString().trim())
            val issues=FlowScenePrompt.issues(flowSpec(candidate))
            if (issues.isNotEmpty()) notice(issues.joinToString("\n")) else {
                // Carry confirmed anatomical wrist across the story, but never silently
                // overwrite an existing contradictory lock on another scene.
                val locks=(0 until people.length()).map { people.getJSONObject(it) }
                    .filter { it.optString("watch_side") in setOf("left","right") }
                    .associate { it.optString("name") to it.optString("watch_side") }
                val episodes=plan?.optJSONArray("episodes")
                val targets=mutableListOf<JSONObject>()
                if(episodes!=null) for(e in 0 until episodes.length()) {
                    val scenes=episodes.optJSONObject(e)?.optJSONArray("scenes") ?: continue
                    for(j in 0 until scenes.length()) {
                        val actors=scenes.optJSONObject(j)?.optJSONArray("characters") ?: continue
                        for(k in 0 until actors.length()) {
                            val actor=actors.optJSONObject(k) ?: continue
                            val locked=locks[actor.optString("name")] ?: continue
                            val prior=actor.optString("watch_side")
                            if(prior.isNotBlank() && prior!=locked) {
                                notice("นาฬิกาของ ${actor.optString("name")} ถูกล็อกต่างข้างในฉากอื่น • กรุณาแก้ความขัดแย้งก่อน")
                                return@setOnClickListener
                            }
                            targets.add(actor)
                        }
                    }
                }
                s.put("characters",people).put("speaker",candidate.optString("speaker")).put("flow_image_revision",image(s).lastModified())
                targets.forEach { actor -> locks[actor.optString("name")]?.let { actor.put("watch_side",it) } }
                val approved=runCatching { SceneFrameReview.approve(s,image(s),FlowReferencePrompt.people(s).map { masterFor(it) },frameReviewFacts(s)) }
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
        AlertDialog.Builder(this).setTitle("เปรียบเทียบต้นฉบับ").setView(ScrollView(this).apply { addView(panel) }).setPositiveButton("ตรวจแล้ว / ยืนยันภาพ") { _,_ -> mapFlowCharacters(s) }.setNeutralButton("แก้ตัวละครรายคน") { _,_ -> repairSceneCharacter(s) }.setNegativeButton("ปิด",null).showDirector()
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
        Button(this).apply {
            text = "จัดการวัตถุในฉาก (Cinematic Props)"
            setOnClickListener {
                CinematicPropDialog.show(this@StudioActivity, "${project.name}:${key(s)}") {
                    val inventory = CinematicPropStore(this@StudioActivity).load("${project.name}:${key(s)}")
                    s.put("cinematic_props", JSONArray(CinematicPropCodec.encode(inventory)))
                    saveStory()
                    notice("บันทึกรายการวัตถุของฉากแล้ว")
                }
            }
            panel.addView(this)
        }
        val turnInput=EditText(this).apply {
            hint="บทสนทนาหลายคน: ชื่อผู้พูด: ข้อความ (หนึ่งบรรทัดต่อหนึ่งช่วง) • เว้นบทเดี่ยวและบรรยายให้ว่าง"
            setText(draft.optString("dialogue_turns_draft").ifBlank { ProductionCommandCheck.dialogueTurns(draft).joinToString("\n") { "${it.speaker}: ${it.text}" } })
            minLines=2; panel.addView(this)
        }
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
            copy.put("dialogue_turns_draft",turnInput.text.toString())
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
        (inputs.map { it.second }+outfits.map { it.second }+positions.map { it.second }+soundInputs.map { it.second }+turnInput).forEach { it.addTextChangedListener(watcher) }
        AlertDialog.Builder(this).setTitle("แก้บท ${key(s)} • เก็บร่างอัตโนมัติ")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("บันทึก") { _,_ ->
                if(inputs.any { it.first in listOf("action","image_prompt","flow_prompt") && it.second.text.isBlank() }) { notice("เหตุการณ์ คำสั่งภาพ และคำสั่ง Flow ต้องไม่ว่าง"); return@setPositiveButton }
                val turnLines=turnInput.text.toString().lines().map { it.trim() }.filter { it.isNotBlank() }
                if(turnLines.any { ':' !in it || it.substringBefore(':').isBlank() || it.substringAfter(':').isBlank() }) { notice("แต่ละบรรทัดต้องเป็น ชื่อผู้พูด: ข้อความ"); return@setPositiveButton }
                val turns=JSONArray().apply { turnLines.forEach { line -> put(JSONObject().put("speaker",line.substringBefore(':').trim()).put("text",line.substringAfter(':').trim())) } }
                val candidate=JSONObject(s.toString())
                inputs.forEach { (key,input) -> candidate.put(key,input.text.toString().trim()) }
                candidate.put("dialogue_turns",turns)
                if(turnLines.isNotEmpty()) {
                    val turnIssues=FlowScenePrompt.issues(ProductionCommandCheck.spec(candidate)).filter { it.contains("บทสนทนา") || it.contains("ผู้พูดลำดับ") }
                    if(turnIssues.isNotEmpty()) { notice(turnIssues.joinToString("\n")); return@setPositiveButton }
                }
                inputs.forEach { (key,input) -> s.put(key,input.text.toString().trim()) }
                if(turns.length()>0) {
                    s.put("dialogue_turns",turns)
                    val speakers=ProductionCommandCheck.dialogueTurns(s).map { it.speaker }.toSet()
                    FlowReferencePrompt.people(s).filter { it.optString("name") in speakers }.forEach { person ->
                        person.optJSONObject("performance")?.let { performance -> if(performance.optString("voice")=="silent") performance.put("voice","natural Thai dialogue") }
                    }
                } else s.remove("dialogue_turns")
                outfits.forEach { (i,input) -> cast.getJSONObject(i).put("outfit",input.text.toString().trim()) }
                PerformanceDirection.normalizeSilentVoices(s)
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
        val snapshot=File(project,"original_masters/$id.master")
        val frozen=FlowCast.supporting(plan).any { it.optBoolean("auto_cast") && FlowCast.id(it)==id }
        return if(frozen || snapshot.isFile) snapshot else File(filesDir,"character_masters/$id.master")
    }
    private fun chooseNamedCharacterGrid(title: String, items: List<Pair<String,File?>>, onSelect: (Int) -> Unit) {
        val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(grid) }).setNegativeButton("ยกเลิก",null).create()
        var row: LinearLayout?=null
        items.forEachIndexed { index, (name,file) ->
            if(index%2==0) { row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; grid.addView(row) }
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)); layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
            row?.addView(card)
            if(file?.isFile==true) card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(file)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(140)); contentDescription=name; setOnClickListener { fullImage(file,name) } })
            card.addView(Button(this).apply { text=name+(if(file!=null && !file.isFile) "\nไม่พบ MASTER" else ""); setOnClickListener { dialog.dismiss(); onSelect(index) } })
        }
        dialog.show()
    }
    private fun chooseStoryCharacterGrid(title: String, people: List<JSONObject>, onSelect: (Int) -> Unit) {
        val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(grid) }).setNegativeButton("ยกเลิก",null).create()
        var row: LinearLayout?=null
        people.forEachIndexed { index, person ->
            if(index%2==0) { row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; grid.addView(row) }
            val file=runCatching { masterFor(person) }.getOrNull()
            val label=person.optString("name")+"\n"+person.optString("master")+(if(file?.isFile==true) "" else "\nไม่พบ MASTER")
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(4),dp(6),dp(4),dp(6)); layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
            row?.addView(card)
            if(file?.isFile==true) card.addView(ImageView(this).apply { setImageBitmap(castingThumbnail(file)); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(140)); contentDescription=label })
            card.addView(Button(this).apply { text=label; setOnClickListener { dialog.dismiss(); onSelect(index) } })
        }
        dialog.show()
    }
    private fun repairSceneCharacter(s: JSONObject) {
        if (!image(s).isFile) return notice("นำเข้าภาพฉากก่อนแก้ตัวละคร")
        val people=FlowReferencePrompt.people(s)
        if(people.isEmpty()) return notice("ฉากนี้ไม่มีตัวละคร")
        chooseStoryCharacterGrid("เลือกตัวละครที่ต้องการแก้",people) { index ->
            val person=people[index]; val master=masterFor(person); val name=person.optString("name")
            if(!master.isFile) { notice("ขาด MASTER ของ $name"); return@chooseStoryCharacterGrid }
            val input=EditText(this).apply { hint="เช่น ปรับทรงผมให้ตรง MASTER" }
            AlertDialog.Builder(this).setTitle("แก้เฉพาะ $name • ${key(s)}").setView(input)
                .setPositiveButton("เลือกแอปและส่ง") { _,_ ->
                    val change=input.text.toString().trim()
                    if(change.isBlank()) { notice("ระบุสิ่งที่ต้องการแก้ก่อน"); return@setPositiveButton }
                    val id=referenceId(person.getString("master"))
                    val sceneId=key(s)
                    val command="""Edit the attached scene image $sceneId. The attached reference $id is the MASTER of $name, positioned ${person.optString("screen_position")}.
Change only $name: $change. Match that person's face, hairstyle, skin tone and body proportions to their MASTER. Preserve all other people, their appearance and positions, clothing, background, lighting, text and composition. Keep the existing scene and props. Return one edited image. Review the actual result; identity matching is not guaranteed.""".trimIndent()
                    shareImageCommand(command,listOf(id to master, sceneId to image(s)),mapOf(id to name,sceneId to "ภาพฉากเดิม"))
                }.setNegativeButton("ยกเลิก",null).showDirector()
        }
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
        fun channelOf(folder: File): String = runCatching { JSONObject(File(folder,"story.json").readText()).optString("channel").trim() }.getOrDefault("").ifBlank {
            runCatching { GeminiStoryFiles.read(File(folder,"gemini_job/spec.json")).optString("channel").trim() }.getOrDefault("")
        }.let { name ->
            when {
                name.isBlank() -> "ไม่ระบุชื่อช่อง"
                name.replace(" ","")=="นายเปียบ้านคลองขุด" -> "นายเปีย บ้านคลองขุด"
                else -> ChannelCatalog.currentChannel(name)
            }
        }
        if(folders.isEmpty()) { notice("ยังไม่มีเรื่องที่บันทึก"); return }
        val channels=folders.map { channelOf(it) }.distinct().sorted()
        choiceMenu("📚 เลือกชื่อช่อง",channels.map { channel -> "$channel • ${folders.count { channelOf(it)==channel }} เรื่อง" }) { selected ->
        val chosen=channels[selected]; val grouped=folders.filter { channelOf(it)==chosen }
        choiceMenu("📚 $chosen",grouped.map { f ->
            runCatching { JSONObject(File(f,"story.json").readText()).optString("title") }.getOrElse { runCatching { GeminiStoryFiles.read(File(f,"gemini_job/outline.json")).getString("title")+" • ยังเขียนไม่จบ" }.getOrDefault("งานเขียนบทที่ยังไม่จบ") } + if(File(f,"archived").exists()) " • เก็บแล้ว" else ""
        }) { i ->
            prefs.edit().putString("project",grouped[i].name).apply(); render()
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
                    ShortCatalogIds(File(filesDir,"short_catalog_ids.json")).mergeBackup(File(staging,"__director_meta/short_catalog_ids.json"))
                    val folder=File(base,UUID.randomUUID().toString()); check(staging.copyRecursively(folder))
                    prefs.edit().putString("project",folder.name).commit()
                } finally { staging.deleteRecursively() }
            } }.setNegativeButton("ยกเลิก",null).showDirector()
    }
    private fun retryLastImage() {
        val file=File(project,"last_image_payload.json")
        if(!file.isFile) return notice("ยังไม่มีชุดสร้างภาพล่าสุด กรุณาเลือกรูปปกหรือซีนเพื่อส่งก่อน")
        runCatching {
            val payload=JSONObject(file.readText()); val images=payload.getJSONArray("images")
            require(images.length()>0) { "ไม่พบภาพอ้างอิงในชุดล่าสุด กรุณาเลือกปกหรือซีนอีกครั้ง" }
            val references=(0 until images.length()).map { i ->
                val uri=Uri.parse(images.getString(i))
                require(uri.authority=="$packageName.fileprovider") { "แหล่งภาพส่งซ้ำไม่ถูกต้อง" }
                val relative=uri.path?.trimStart('/') ?: error("ไม่มีเส้นทางภาพ")
                require(relative.startsWith("character_master_exports/") || relative.startsWith("scene_reference_exports/")) { "ภาพไม่ได้อยู่ในพื้นที่ส่งออก" }
                val source=File(cacheDir,relative)
                require(source.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator) && source.isFile) { "ไฟล์ชั่วคราวหมดอายุ กรุณาเลือกปกหรือซีนเดิมเพื่อส่งใหม่" }
                source.nameWithoutExtension to source
            }
            shareImageCommand(payload.getString("text"),references)
        }.onFailure { notice(it.message ?: "ส่งรูปซ้ำไม่ได้") }
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
        chooseStoryCharacterGrid("🎭 ตัวละครเรื่องนี้",people) { i ->
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


