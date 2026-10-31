package com.alzadash.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/** What a voice command can ask the dashboard to do. */
interface CommandTarget {
    fun playRadio(station: Station)
    fun stopRadio()
    fun navigate(query: String, pkg: String?)
    fun showVideo(on: Boolean)
    fun resetTrip()
    fun say(message: String)
}

/** Listens once through the phone's microphone and runs the matching command. */
object VoiceCommands {
    const val HELP = "Try: \"play radio\", \"play <station>\", \"stop radio\", \"navigate to KLCC\", \"open Waze\", \"video\", \"dashboard\", \"reset trip\""

    private var recognizer: SpeechRecognizer? = null

    fun listen(context: Context, target: CommandTarget) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            target.say("Allow the microphone in the AlzaDash app on your phone first")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            target.say("Speech recognition isn't available on this phone")
            return
        }
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                handle(context, heard, target)
                done()
            }
            override fun onError(error: Int) {
                target.say("Didn't catch that. $HELP")
                done()
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            private fun done() {
                sr.destroy()
                if (recognizer === sr) recognizer = null
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        target.say("Listening…")
        sr.startListening(intent)
    }

    /** Listens once and hands back the words as text, for typing into apps on the car screen. */
    fun dictate(context: Context, onText: (String) -> Unit, fail: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Allow the microphone in the AlzaDash app on your phone first")
            return
        }
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (heard.isEmpty()) fail("Didn't catch that") else onText(heard.first())
                done()
            }
            override fun onError(error: Int) {
                fail("Didn't catch that")
                done()
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            private fun done() {
                sr.destroy()
                if (recognizer === sr) recognizer = null
            }
        })
        fail("Say what to type…")
        sr.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        )
    }

    /** Matches what was heard against the known commands. Public so it can be unit tested. */
    fun handle(context: Context, heard: List<String>, target: CommandTarget) {
        val stations = Prefs(context).stations
        for (raw in heard) {
            val s = raw.lowercase().trim()
            when {
                s.startsWith("navigate to ") || s.startsWith("drive to ") || s.startsWith("take me to ") -> {
                    val dest = s.substringAfter(" to ").trim()
                    val pkg = if (dest.endsWith(" with waze") || dest.endsWith(" on waze")) Pkg.WAZE else null
                    target.navigate(dest.removeSuffix(" with waze").removeSuffix(" on waze"), pkg)
                    return
                }
                s.contains("go home") || s == "home" -> {
                    val home = Prefs(context).homeAddress
                    if (home.isEmpty()) target.say("Set your home address in the phone app") else target.navigate(home, null)
                    return
                }
                s.contains("waze") -> { target.navigate("", Pkg.WAZE); return }
                s.contains("maps") -> { target.navigate("", Pkg.MAPS); return }
                s.contains("stop") && (s.contains("radio") || s.contains("music") || s == "stop") -> {
                    target.stopRadio(); return
                }
                s.contains("reset trip") -> { target.resetTrip(); return }
                s.contains("video") || s.contains("youtube") || s.contains("netflix") -> { target.showVideo(true); return }
                s.contains("dashboard") -> { target.showVideo(false); return }
                s.startsWith("play") || s.contains("radio") -> {
                    val name = s.removePrefix("play").replace("radio", "").trim()
                    val match = stations.firstOrNull { name.isNotEmpty() && it.name.lowercase().contains(name) }
                        ?: stations.firstOrNull { name.isNotEmpty() && name.contains(it.name.lowercase()) }
                        ?: Prefs(context).lastStation.takeIf { name.isEmpty() }
                        ?: stations.firstOrNull().takeIf { name.isEmpty() }
                    if (match != null) { target.playRadio(match); return }
                }
            }
        }
        target.say(if (heard.isEmpty()) HELP else "Heard \"${heard.first()}\". $HELP")
    }
}
