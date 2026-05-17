package com.babegetthis.android.core.voice.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = buildRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setMaxDuration(30_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    suspend fun stop(): File = withContext(Dispatchers.IO) {
        recorder?.apply {
            try {
                stop()
            } catch (_: RuntimeException) {
                // MediaRecorder.stop() throws if no audio was captured (e.g. stopped immediately
                // after start). The resulting file will be ~empty; backend will return no items.
            }
            release()
        }
        recorder = null
        outputFile!!.also { outputFile = null }
    }

    fun cancel() {
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun buildRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
