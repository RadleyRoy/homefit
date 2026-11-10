package com.radley.homefit.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks cues over whatever the user is listening to.
 *
 * Audio focus is requested as "transient, may duck" for each utterance and released when the
 * queue drains, so Spotify drops in volume under the voice and comes straight back, rather
 * than pausing for the whole workout. Utterances queued before the engine finishes binding
 * are dropped, not buffered: a cue that arrives late is worse than no cue.
 */
class VoiceCoach(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    @Volatile
    private var ready = false
    private val pending = AtomicInteger(0)
    private val ids = AtomicInteger(0)

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.setAudioAttributes(attributes)
            val locale = Locale.getDefault()
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finished()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished()
                override fun onError(utteranceId: String?, errorCode: Int) = finished()
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
            })
            ready = true
        }
    }

    // ToneGenerator can fail to initialise on some devices when audio is busy; beeps are optional.
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()

    /** [interrupt] cuts off whatever is being said; used for countdown numbers so they stay on time. */
    fun say(text: String, interrupt: Boolean = false) {
        if (!ready || text.isBlank()) return
        if (pending.getAndIncrement() == 0) audio.requestAudioFocus(focusRequest)
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (interrupt) pending.set(1)
        tts.speak(text, mode, null, "cue-${ids.incrementAndGet()}")
    }

    fun beep(long: Boolean = false) {
        tones?.startTone(if (long) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, if (long) 400 else 150)
    }

    fun stop() {
        if (ready) tts.stop()
        pending.set(0)
        audio.abandonAudioFocusRequest(focusRequest)
    }

    private fun finished() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    /** For the "Test voice" button in settings. */
    fun openTtsSettings(context: Context) {
        val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
