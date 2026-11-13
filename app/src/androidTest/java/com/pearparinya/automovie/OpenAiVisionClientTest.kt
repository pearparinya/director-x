package com.pearparinya.automovie

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAiVisionClientTest {
    @Test fun requestKeepsLabelsAndImagesPairedAndExcludesKey() {
        val root=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("vision-test").apply { mkdirs() }
        try {
            fun make(name: String,color: Int)=root.resolve(name).apply {
                val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                bitmap.recycle()
            }
            val a=make("a.png",0xffff0000.toInt()); val b=make("b.png",0xff0000ff.toInt())
            val client=OpenAiVisionClient("test-secret-key","gpt-4.1-mini")
            val request=client.buildRequest("Analyze","schema".let { JSONObject().put("type","object") },listOf("ACTOR_A" to a,"ACTOR_B" to b))
            assertFalse(request.toString().contains("test-secret-key"))
            val parts=request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            assertTrue(parts.getJSONObject(1).getString("text").contains("ACTOR_A"))
            assertTrue(parts.getJSONObject(2).getJSONObject("image_url").getString("url").startsWith("data:image/png;base64,"))
            assertTrue(parts.getJSONObject(3).getString("text").contains("ACTOR_B"))
            assertNotEquals(parts.getJSONObject(2).toString(),parts.getJSONObject(4).toString())
            assertTrue(runCatching { client.buildRequest("x",JSONObject(),listOf("A" to a,"a" to b)) }.isFailure)
            assertTrue(runCatching { client.buildRequest("x",JSONObject(),listOf("MISSING" to root.resolve("missing"))) }.isFailure)
        } finally { root.deleteRecursively() }
    }
}
