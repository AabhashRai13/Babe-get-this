package com.babegetthis.android.core.voice.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import com.babegetthis.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        // Play the cue and WAIT for it to finish before opening the mic.
        // That ordering is the whole "don't record our own sound" trick.
        playStartTone()

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

    // suspendCancellableCoroutine bridges a callback API into a suspend function —
    // like wrapping a callback in a Completer in Dart and awaiting its future.
    private suspend fun playStartTone() = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer.create(context, R.raw.record_start)
        if (player == null) {
            // Sound failed to load — recording still works, just silently.
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        player.setOnCompletionListener {
            it.release()
            continuation.resume(Unit)
        }
        // If the coroutine is cancelled (user closes the sheet mid-tone), free the player.
        continuation.invokeOnCancellation { player.release() }
        player.start()
    }

    private fun buildRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
