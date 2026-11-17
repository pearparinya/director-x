package com.pearparinya.automovie

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** In-place update from the Studio screen; verifies package, version and signer. */
class StudioUpdater(private val activity: AppCompatActivity) {
    var onStatus: ((String) -> Unit)? = null
    private fun status(value: String) { activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) onStatus?.invoke(value) } }
    private var busy = false
    private var pending: File? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pending
        if (file != null && activity.packageManager.canRequestPackageInstalls()) install(file)
        else message("ยังไม่ได้อนุญาตติดตั้งอัปเดต กด Update เพื่อลองอีกครั้งครับ")
    }
    private fun message(text: String) = AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("DIRECTOR X").setMessage(text).setPositiveButton("ตกลง",null).showDirector()
    private val updatePrefs = activity.getSharedPreferences("director_x_updater", android.content.Context.MODE_PRIVATE)
    private val checkIntervalMs = 5 * 60 * 1000L
    private var lastCheckThisSession = 0L
    private val reminderIntervalMs = 24 * 60 * 60 * 1000L
    fun checkAutomatically() {
        val now = System.currentTimeMillis()
        if (lastCheckThisSession in 1..now && now - lastCheckThisSession < checkIntervalMs) return
        if (busy) return
        lastCheckThisSession = now
        check(true)
    }
    fun check(automatic: Boolean = false) {
        if (busy) {
            if (!automatic) message("ระบบกำลังตรวจสอบหรือดาวน์โหลดอัปเดตอยู่ กรุณารอสักครู่")
            return
        }
        busy = true
        status("checking")
        val dialog = if (automatic) null else AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("DIRECTOR X").setMessage("กำลังตรวจสอบรุ่นใหม่…").setCancelable(false).showDirector()
        Thread {
            try {
                val connection = connect("https://api.github.com/repos/pearparinya/director-x/releases/latest?cb=${System.currentTimeMillis()}")
                val release = try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) } finally { connection.disconnect() }
                require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "รุ่นนี้ยังไม่พร้อมเผยแพร่" }
                // The immutable release tag is authoritative; release notes may mention older builds.
                val build = Regex("-B(\\d+)", RegexOption.IGNORE_CASE)
                    .find(release.getString("tag_name"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: Regex("BUILD\\s*[:#]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
                    ?: error("รุ่นล่าสุดไม่มีข้อมูลการอัปเดต")
                val local = activity.packageManager.getPackageInfo(activity.packageName,0).versionCode.toLong()
                // Only published releases are eligible for in-app updates.
                // CI artifacts must never be presented as installable updates.
                if (build <= local) {
                    activity.runOnUiThread {
                        busy = false
                        dialog?.dismiss()
                        status("latest")
                        updatePrefs.edit().putLong("last_successful_check", System.currentTimeMillis()).apply()
                        if (!activity.isFinishing && !activity.isDestroyed && !automatic)
                            message("ติดตั้ง Build $local แล้ว • GitHub Release ล่าสุด Build $build • ยังไม่มีอัปเดตใหม่ที่เผยแพร่")
                    }
                    return@Thread
                }
                val assets = release.getJSONArray("assets")
                val releaseVersion = release.getString("tag_name")
                    .removePrefix("R").substringBefore("-B")
                val expectedApkName = "DIRECTOR-X-$releaseVersion.apk"
                // Fail closed: never install a different APK accidentally attached to a release.
                val asset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .singleOrNull { it.optString("name").equals(expectedApkName, ignoreCase = true) }
                    ?: error("ไม่พบไฟล์ $expectedApkName ใน Release หรือมีไฟล์ชื่อซ้ำ")
                require(asset.optLong("size",0L) > 100000L) { "ไฟล์ APK ใน Release ไม่สมบูรณ์" }
                val url = asset.getString("browser_download_url")
                require(url.startsWith("https://github.com/pearparinya/director-x/releases/download/") ||
                    url.startsWith("https://github.com/pearparinya/auto-movie-r6-4/releases/download/")) { "แหล่งดาวน์โหลดไม่ถูกต้อง" }
                activity.runOnUiThread {
                    busy = false; dialog?.dismiss()
                    status("available")
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    updatePrefs.edit().putLong("last_successful_check", System.currentTimeMillis()).apply()
                    if (automatic && !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) { lastCheckThisSession = 0L; return@runOnUiThread }
                    run {
                        val now = System.currentTimeMillis()
                        val dismissedBuild = updatePrefs.getLong("dismissed_build", -1L)
                        val dismissedAt = updatePrefs.getLong("dismissed_at", 0L)
                        if (automatic && dismissedBuild == build && dismissedAt in 1..now && now - dismissedAt < reminderIntervalMs) return@runOnUiThread
                        AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("DIRECTOR X รุ่นใหม่พร้อมแล้ว").setMessage("${release.optString("name", "รุ่นใหม่")}\nดาวน์โหลดและติดตั้งทับเพื่ออัปเดต งานที่บันทึกไว้จะยังอยู่")
                            .setPositiveButton("อัปเดต") { _,_ -> download(url,build,asset.optLong("size",-1),asset.optString("digest", "")) }
                            .setNegativeButton("ภายหลัง") { _,_ -> updatePrefs.edit().putLong("dismissed_build",build).putLong("dismissed_at",System.currentTimeMillis()).apply() }.showDirector()
                    }
                }
            } catch (e: Exception) { activity.runOnUiThread { busy = false; dialog?.dismiss(); status("error"); if (automatic) lastCheckThisSession = 0L; if (!automatic && !activity.isFinishing && !activity.isDestroyed) message(
                    if (e.message?.contains("HTTP 404") == true)
                        "ยังไม่มี GitHub Release สำหรับอัปเดตผ่านแอป • APK ทดสอบใน GitHub Actions ไม่ใช่รุ่นเผยแพร่"
                    else "ตรวจสอบอัปเดตไม่สำเร็จ: ${e.message}"
                ) } }
        }.start()
    }
    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000; readTimeout = 30000; useCaches = false
        setRequestProperty("User-Agent","Story-Studio-Android")
        setRequestProperty("Cache-Control","no-cache")
        require(responseCode in 200..299) { "เครือข่าย HTTP $responseCode" }
    }
    private fun download(url: String, build: Long, expectedSize: Long, expectedDigest: String) {
        if (busy) return
        busy = true
        val dialog = AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("อัปเดต DIRECTOR X").setMessage("กำลังดาวน์โหลด…").setCancelable(false).showDirector()
        Thread {
            var target: File? = null
            try {
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("ไม่มีพื้นที่ดาวน์โหลด")
                val file = File(dir,"DIRECTOR-X-$build.apk"); target = file
                val connection = connect(url)
                try {
                    val total = if (expectedSize > 0) expectedSize else connection.contentLengthLong
                    connection.inputStream.use { source ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var received = 0L
                            var lastPercent = -1
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                if (total > 0) {
                                    val percent = (received * 100 / total).toInt().coerceIn(0, 100)
                                    if (percent >= lastPercent + 5 || percent == 100) {
                                        lastPercent = percent
                                        activity.runOnUiThread {
                                            if (!activity.isFinishing && !activity.isDestroyed) dialog.setMessage("กำลังดาวน์โหลด… $percent%")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally { connection.disconnect() }
                require(file.length() > 100000 && (expectedSize <= 0 || file.length() == expectedSize)) { "ดาวน์โหลดไฟล์ไม่ครบ" }
                if (expectedDigest.isNotBlank()) {
                    require(expectedDigest.matches(Regex("sha256:[0-9a-fA-F]{64}"))) { "รูปแบบ SHA-256 ของ Release ไม่ถูกต้อง" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { stream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    require(actual.equals(expectedDigest.substringAfter("sha256:"), ignoreCase = true)) {
                        "SHA-256 ของ APK ไม่ตรงกับไฟล์ Release"
                    }
                }
                val pm = activity.packageManager
                val candidate = pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNATURES) ?: error("ไฟล์ APK ไม่ถูกต้อง")
                val installed = pm.getPackageInfo(activity.packageName,PackageManager.GET_SIGNATURES)
                require(candidate.packageName == activity.packageName && candidate.versionCode.toLong() > installed.versionCode.toLong()) { "ไฟล์ไม่ใช่อัปเดตของแอปนี้" }
                fun certificates(info: android.content.pm.PackageInfo) = info.signatures?.map { signature ->
                    MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
                }?.toSet() ?: emptySet()
                val signers = certificates(candidate)
                require(signers.isNotEmpty() && signers == certificates(installed)) { "ลายเซ็นไม่ตรงกับแอปที่ติดตั้ง" }
                activity.runOnUiThread { busy = false; dialog.dismiss(); if (!activity.isFinishing && !activity.isDestroyed) install(file) }
            } catch (e: Exception) {
                target?.delete()
                activity.runOnUiThread { busy = false; dialog.dismiss(); if (!activity.isFinishing && !activity.isDestroyed) message("อัปเดตไม่สำเร็จ: ${e.message}") }
            }
        }.start()
    }
    private fun install(file: File) {
        pending = file
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setIcon(R.drawable.studio_update).setTitle("อนุญาตอัปเดต DIRECTOR X").setMessage("เปิดสิทธิ์ติดตั้งจากแอปนี้ แล้วกลับมาเพื่อติดตั้งอัปเดต")
                .setPositiveButton("เปิดการตั้งค่า") { _,_ -> permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${activity.packageName}"))) }
                .setNegativeButton("ภายหลัง",null).showDirector()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(activity,"${activity.packageName}.fileprovider",file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri,"application/vnd.android.package-archive"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        } catch (e: Exception) { message("เปิดตัวติดตั้งไม่สำเร็จ: ${e.message}") }
    }
}

