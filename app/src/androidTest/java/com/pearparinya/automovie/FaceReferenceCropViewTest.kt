package com.pearparinya.automovie

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FaceReferenceCropViewTest {
    @Test fun manuallySelectedRegionIsSavedWithoutChangingOriginal() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.cacheDir,"crop-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val source=File(dir,"master.png")
            val image=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
            val digest=MasterLibrary.digest(source.readBytes())
            val target=File(dir,"face.png")
            instrumentation.runOnMainSync {
                val view=FaceReferenceCropView(instrumentation.targetContext,source)
                view.layout(0,0,200,400)
                val frame=Bitmap.createBitmap(200,400,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(frame)); frame.recycle()
                assertTrue(runCatching { view.save(target) }.isFailure)
                val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20f,20f,0)
                val up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,100f,120f,0)
                view.onTouchEvent(down); view.onTouchEvent(up); down.recycle(); up.recycle()
                view.save(target)
            }
            assertTrue(target.isFile)
            assertEquals(digest,MasterLibrary.digest(source.readBytes()))
            val cropped=android.graphics.BitmapFactory.decodeFile(target.path)
            assertEquals(80,cropped.width); assertEquals(100,cropped.height); cropped.recycle()
        } finally { dir.deleteRecursively() }
    }
}
