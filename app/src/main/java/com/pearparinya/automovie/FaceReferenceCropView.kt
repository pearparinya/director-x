package com.pearparinya.automovie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.io.File

/** User selects a rectangle on the actual MASTER; never infer a face or modify the source. */
internal class FaceReferenceCropView(context: Context, source: File): View(context) {
    private val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true; BitmapFactory.decodeFile(source.path,this) }
    private val sample=generateSequence(1) { it*2 }.first { bounds.outWidth/it<=2048 && bounds.outHeight/it<=2048 }
    private val bitmap=BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply { inSampleSize=sample }) ?: error("อ่านภาพ MASTER ไม่ได้")
    private val imageRect=RectF()
    private val selection=RectF()
    private var startX=0f
    private var startY=0f
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale=minOf(width.toFloat()/bitmap.width,height.toFloat()/bitmap.height)
        val w=bitmap.width*scale; val h=bitmap.height*scale
        imageRect.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
        canvas.drawBitmap(bitmap,null,imageRect,paint.apply { style=Paint.Style.FILL; color=Color.WHITE })
        if(!selection.isEmpty) canvas.drawRect(selection,paint.apply { style=Paint.Style.STROKE; strokeWidth=4f; color=Color.CYAN })
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(imageRect.isEmpty) return false
        val x=event.x.coerceIn(imageRect.left,imageRect.right); val y=event.y.coerceIn(imageRect.top,imageRect.bottom)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=x; startY=y; selection.setEmpty(); invalidate(); return true }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> { selection.set(minOf(startX,x),minOf(startY,y),maxOf(startX,x),maxOf(startY,y)); invalidate(); return true }
        }
        return true
    }
    fun save(target: File) {
        require(selection.width()>=20 && selection.height()>=20) { "ลากกรอบรอบใบหน้ามุมตรงก่อนบันทึก" }
        val left=((selection.left-imageRect.left)/imageRect.width()*bitmap.width).toInt().coerceIn(0,bitmap.width-1)
        val top=((selection.top-imageRect.top)/imageRect.height()*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
        val right=((selection.right-imageRect.left)/imageRect.width()*bitmap.width).toInt().coerceIn(left+1,bitmap.width)
        val bottom=((selection.bottom-imageRect.top)/imageRect.height()*bitmap.height).toInt().coerceIn(top+1,bitmap.height)
        val crop=Bitmap.createBitmap(bitmap,left,top,right-left,bottom-top)
        try { target.parentFile!!.mkdirs(); target.outputStream().use { check(crop.compress(Bitmap.CompressFormat.PNG,100,it)) } }
        finally { if(crop!==bitmap) crop.recycle() }
    }
}
