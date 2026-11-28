package com.pearparinya.automovie

import com.pearparinya.automovie.stand.*
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EpisodeExportTest {
    @Test
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun imageIntroAndTwoClipsProducePortraitMp4WithSilentIntroAndAudio() {
        val instrumentation=InstrumentationRegistry.getInstrumentation(); val context=instrumentation.targetContext
        val folder=File(context.filesDir,"test_episode").apply { deleteRecursively(); mkdirs() }
        fun image(name: String,color: Int): File {
            val file=File(folder,name); val b=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888); b.eraseColor(color)
            file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }; b.recycle(); return file
        }
        val cover=image("cover.png",Color.YELLOW); val red=image("red.png",Color.RED); val blue=image("blue.png",Color.BLUE)
        fun export(items: List<EditedMediaItem>, path: File, tone: File?=null) {
            val latch=CountDownLatch(1); var failure: Throwable?=null; var transformer: Transformer?=null
            instrumentation.runOnMainSync {
                val encoder=DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(24000000).build()).setEnableFallback(false).build()
                transformer=Transformer.Builder(context).setPortraitEncodingEnabled(false).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(encoder).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition,result: ExportResult) { latch.countDown() }
                    override fun onError(composition: Composition,result: ExportResult,exception: ExportException) { failure=exception; latch.countDown() }
                }).build()
                val sequences=mutableListOf(EditedMediaItemSequence.Builder(items).build())
                if(tone!=null) sequences.add(EditedMediaItemSequence.Builder(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(tone))).build()).build())
                transformer!!.start(Composition.Builder(sequences).experimentalSetForceAudioTrack(true).build(),path.path)
            }
            if(!latch.await(180,TimeUnit.SECONDS)) { instrumentation.runOnMainSync { transformer?.cancel() }; fail("export timed out") }
            failure?.let { throw AssertionError("real encoder export failed",it) }
        }
        fun still(file: File,ms: Long)=EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(file)).setImageDurationMs(ms).build()).setFrameRate(30).setEffects(Effects(emptyList(),listOf(Presentation.createForWidthAndHeight(1080,1920,Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        val first=File(folder,"clip1.mp4"); val second=File(folder,"clip2.mp4")
        fun tone(name: String,frequency: Double): File {
            val file=File(folder,name); val count=44100; val bytes=count*2
            val header=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36+bytes).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes)
            val pcm=java.nio.ByteBuffer.allocate(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for(i in 0 until count) pcm.putShort((kotlin.math.sin(2*Math.PI*frequency*i/44100)*6000).toInt().toShort())
            file.outputStream().use { it.write(header.array()); it.write(pcm.array()) }; return file
        }
        export(listOf(still(red,1000)),first,tone("tone1.wav",440.0)); export(listOf(still(blue,1000)),second,tone("tone2.wav",660.0))
        // Exercise the Stand scene workflow with real portrait image and MP4 inputs.
        verifyStandMovie(folder, listOf(red, blue), listOf(first, second))
        val before=listOf(EpisodeMedia.digest(first),EpisodeMedia.digest(second))
        val job=File(folder,"job").apply { mkdirs() }
        val spec=org.json.JSONObject().put("aspect_ratio","9:16").put("ep",1).put("filename","ทดสอบ_EP01_ปกและฉาก.mp4").put("cover",cover.path)
            .put("clips",org.json.JSONArray(listOf(first.path,second.path))).put("fps",30).put("bitrate",24000000)
            .put("duration_ms",3000+EpisodeMedia.inspect(first).durationMs+EpisodeMedia.inspect(second).durationMs).put("input_hashes",org.json.JSONArray(listOf(EpisodeMedia.digest(cover))+before)).put("fingerprint","test").put("lossless",false).put("crop",false).put("has_audio",true)
        File(job,"spec.json").writeText(spec.toString())
        val activity=instrumentation.startActivitySync(android.content.Intent(context,StudioActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { androidx.core.content.ContextCompat.startForegroundService(context,android.content.Intent(context,EpisodeRenderService::class.java).putExtra("job",job.path)) }
        val deadline=System.currentTimeMillis()+180000
        var state=org.json.JSONObject()
        while(System.currentTimeMillis()<deadline) {
            state=runCatching { org.json.JSONObject(File(job,"status.json").readText()) }.getOrDefault(org.json.JSONObject())
            if(state.optString("state") in listOf("complete","failed","cancelled")) break
            Thread.sleep(500)
        }
        assertEquals("actual service export failed: ${state.optString("message")}","complete",state.optString("state"))
        val output=File(job,"output.mp4")
        assertTrue(File(job,"result.json").isFile)
        val mvJob=File(folder,"mv_job").apply { mkdirs() }
        val mvSpec=org.json.JSONObject().put("music",tone("original.wav",880.0).path)
            .put("clips",org.json.JSONArray(listOf(first.path,second.path)))
            .put("trims",org.json.JSONArray(listOf(600,400))).put("duration_ms",1000)
            .put("has_audio",true).put("fps",30).put("bitrate",8000000)
            .put("filename","mv.mp4").put("fingerprint","mv-test")
        File(mvJob,"spec.json").writeText(mvSpec.toString())
        instrumentation.runOnMainSync { androidx.core.content.ContextCompat.startForegroundService(context,android.content.Intent(context,EpisodeRenderService::class.java).putExtra("job",mvJob.path)) }
        val mvDeadline=System.currentTimeMillis()+180000
        while(System.currentTimeMillis()<mvDeadline) {
            val status=runCatching { org.json.JSONObject(File(mvJob,"status.json").readText()) }.getOrNull()
            if(status?.optString("state")=="failed") fail(status.optString("message"))
            if(status?.optString("state")=="complete") break
            Thread.sleep(500)
        }
        assertTrue("MV render did not complete",File(mvJob,"result.json").isFile)
        val mvInfo=EpisodeMedia.inspect(File(mvJob,"output.mp4"))
        assertEquals(1080,mvInfo.displayWidth)
        assertEquals(1920,mvInfo.displayHeight)
        assertNotNull(mvInfo.audioMime)
        assertTrue("Expected trimmed one-second MV",kotlin.math.abs(mvInfo.durationMs-1000)<150)
        instrumentation.runOnMainSync { activity.finish() }

        val info=EpisodeMedia.inspect(output); assertEquals(1080,info.displayWidth); assertEquals(1920,info.displayHeight); assertTrue(info.rotation in listOf(0,90,180,270))
        assertTrue(kotlin.math.abs(info.durationMs-5000)<300); assertNotNull(info.audioMime)
        val reader=android.media.MediaMetadataRetriever(); reader.setDataSource(output.path)
        try {
            val frame=reader.getFrameAtTime(500000,android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)!!
            val color=frame.getPixel(frame.width/2,frame.height/2); assertTrue(Color.red(color)>150 && Color.green(color)>150 && Color.blue(color)<100); frame.recycle()
            val redFrame=reader.getFrameAtTime(3500000,android.media.MediaMetadataRetriever.OPTION_CLOSEST)!!
            val redPixel=redFrame.getPixel(redFrame.width/2,redFrame.height/2); assertTrue(Color.red(redPixel)>150 && Color.blue(redPixel)<100); redFrame.recycle()
            val blueFrame=reader.getFrameAtTime(4500000,android.media.MediaMetadataRetriever.OPTION_CLOSEST)!!
            val bluePixel=blueFrame.getPixel(blueFrame.width/2,blueFrame.height/2); assertTrue(Color.blue(bluePixel)>150 && Color.red(bluePixel)<100); blueFrame.recycle()
        } finally { reader.release() }
        var silentEnergy=0.0; var silentCount=0; var firstEnergy=0.0; var firstCount=0; var secondEnergy=0.0; var secondCount=0
        decodeAudio(output) { pcm,time,_ ->
            while(pcm.remaining()>=2) {
                val value=pcm.short.toDouble(); val energy=value*value
                when(time) { in 500000L..2000000L -> { silentEnergy+=energy; silentCount++ }; in 3200000L..3800000L -> { firstEnergy+=energy; firstCount++ }; in 4300000L..4800000L -> { secondEnergy+=energy; secondCount++ } }
            }
        }
        assertTrue(silentCount>0 && firstCount>0 && secondCount>0)
        assertTrue(kotlin.math.sqrt(silentEnergy/silentCount)<200); assertTrue(kotlin.math.sqrt(firstEnergy/firstCount)>500); assertTrue(kotlin.math.sqrt(secondEnergy/secondCount)>500)
        val windows=Array(2) { ArrayList<Double>() }
        var rate=0
        decodeAudio(File(mvJob,"output.mp4")) { pcm,time,format ->
            val channels=format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            rate=format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            val samples=when(time) { in 100000L..350000L -> windows[0]; in 700000L..900000L -> windows[1]; else -> null }
            if(samples!=null) while(pcm.remaining()>=2*channels) {
                samples.add(pcm.short.toDouble())
                pcm.position(pcm.position()+2*(channels-1))
            }
        }
        for(samples in windows) {
            assertTrue(samples.size>rate/10)
            fun amplitude(hz: Int): Double {
                var real=0.0; var imaginary=0.0
                samples.forEachIndexed { index,value ->
                    val angle=2*Math.PI*hz*index/rate
                    real+=value*kotlin.math.cos(angle); imaginary+=value*kotlin.math.sin(angle)
                }
                return kotlin.math.hypot(real,imaginary)/samples.size
            }
            val song=amplitude(880)
            assertTrue("Original song must dominate both clip tones",song>500 && song>10*maxOf(amplitude(440),amplitude(660)))
        }
        assertEquals(before,listOf(EpisodeMedia.digest(first),EpisodeMedia.digest(second)))
        // Strict packet-copy either verifies compatibility or rejects it; it never encodes silently.
        val copy=File(folder,"copy.mp4")
        try { EpisodeMedia.remux(listOf(first,second),copy,{false},{})
            assertEquals(1080,EpisodeMedia.inspect(copy).displayWidth); assertTrue(kotlin.math.abs(EpisodeMedia.inspect(copy).durationMs-2000)<300)
        } catch(e: IllegalArgumentException) { assertTrue(e.message!!.contains("คุณภาพสูง")) }
        folder.deleteRecursively()
    }
    private fun decodeAudio(file: File, consume: (java.nio.ByteBuffer,Long,android.media.MediaFormat)->Unit) {
        val extractor=android.media.MediaExtractor(); extractor.setDataSource(file.path)
        val audio=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        val format=extractor.getTrackFormat(audio); extractor.selectTrack(audio)
        val decoder=android.media.MediaCodec.createDecoderByType(format.getString(android.media.MediaFormat.KEY_MIME)!!)
        decoder.configure(format,null,null,0); decoder.start()
        var inputDone=false; var outputDone=false 
        val deadlineAudio=System.currentTimeMillis()+30000
        try {
            val bufferInfo=android.media.MediaCodec.BufferInfo()
            while(!outputDone && System.currentTimeMillis()<deadlineAudio) {
                if(!inputDone) {
                    val index=decoder.dequeueInputBuffer(10000)
                    if(index>=0) { val input=decoder.getInputBuffer(index)!!; val size=extractor.readSampleData(input,0)
                        if(size<0) { decoder.queueInputBuffer(index,0,0,0,android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                        else { decoder.queueInputBuffer(index,0,size,extractor.sampleTime,0); extractor.advance() }
                    }
                }
                val index=decoder.dequeueOutputBuffer(bufferInfo,10000)
                if(index>=0) {
                    val pcm=decoder.getOutputBuffer(index)!!.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN); pcm.position(bufferInfo.offset); pcm.limit(bufferInfo.offset+bufferInfo.size)
                    consume(pcm,bufferInfo.presentationTimeUs,decoder.outputFormat)
                    outputDone=bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0; decoder.releaseOutputBuffer(index,false)
                }
            }
            assertTrue(outputDone)
        } finally { decoder.stop(); decoder.release(); extractor.release() }
    }

    private fun verifyStandMovie(root: File, frames: List<File>, videos: List<File>) {
        val project = StandProject(StandChannel.PIA, "stand-export-test")
        val ownership = StandOwnership.restore(project).assign("ST-013", "PIEROS")
        val master = frames.first()
        val records = StandCatalog.all.map { stand ->
            StandMasterRecord(stand.id, if (stand.id == "ST-013") EpisodeMedia.digest(master)
                else StandMasterIntegrity.digest(stand.id.byteInputStream()))
        }
        val store = StandMasterStore(File(root, "stand-masters"), records)
        assertTrue(master.inputStream().use { store.importOriginal("ST-013", it) })
        val presence = StandMovementEngine.summon(ownership,
            StandMovementEngine.initial(ownership, "ST-013", "PIEROS", StandPosition(0.0, 0.0)))
        val clips = frames.indices.map { index ->
            val scene = StandStoryScene(index + 1, listOf(presence.presence))
            val request = StandCinematicEngine.compose(ownership, scene, records, store,
                "PIEROS", "ฉากทดสอบ " + scene.number, listOf(presence))
            val folder = File(root, "stand-scenes/" + scene.number)
            frames[index].inputStream().use { StandSceneMedia.importMedia(folder, "image", null, it) }
            val stamp = requireNotNull(StandSceneMedia.stamp(folder, request))
            assertFalse(StandSceneMedia.approved(folder, stamp))
            assertTrue(runCatching { videos[index].inputStream().use {
                StandSceneMedia.importMedia(folder, "video", stamp, it)
            } }.isFailure)
            assertTrue(StandSceneMedia.approve(folder, request, stamp))
            assertTrue(StandSceneMedia.approved(folder, stamp))
            videos[index].inputStream().use { StandSceneMedia.importMedia(folder, "video", stamp, it) }
            StandSceneMedia.verifiedClip(folder, request)
        }
        val movie = File(root, "stand-movie.mp4")
        EpisodeMedia.remux(clips, movie, { false }) { }
        val info = EpisodeMedia.inspect(movie)
        assertTrue(info.displayHeight > info.displayWidth)
        assertEquals(clips.sumOf { EpisodeMedia.inspect(it).durationMs }.toDouble(),
            info.durationMs.toDouble(), 200.0)
    }
}
