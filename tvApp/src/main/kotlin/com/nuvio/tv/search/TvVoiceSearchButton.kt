package com.nuvio.tv.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.nuvio
import com.nuvio.tv.R

private val log = Logger.withTag("TvVoiceSearch")
private val ButtonShape = RoundedCornerShape(12.dp)

/**
 * Microphone button of the TV search (ported from NuvioTV): asks for the RECORD_AUDIO permission
 * the first time, listens with the system [SpeechRecognizer] and reports the recognized text.
 * While listening it pulses and follows the voice level.
 */
@Composable
fun TvVoiceSearchButton(onQuery: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnQuery by rememberUpdatedState(onQuery)
    val available = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember(context, available) {
        if (available) runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() else null
    }
    var listening by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }

    val msgNoSpeech = stringResource(R.string.tv_voice_no_speech)
    val msgPermission = stringResource(R.string.tv_voice_mic_permission)
    val msgFailed = stringResource(R.string.tv_voice_failed)
    val msgUnavailable = stringResource(R.string.tv_voice_unavailable)
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun startListening() {
        val speech = recognizer ?: return toast(msgUnavailable)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        runCatching {
            speech.cancel()
            speech.startListening(intent)
        }.onSuccess {
            log.i { "Listening" }
        }.onFailure { error ->
            listening = false
            log.e(error) { "Voice recognition could not start" }
            toast(msgUnavailable)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        log.i { "Microphone permission ${if (granted) "granted" else "denied"}" }
        if (granted) startListening() else toast(msgPermission)
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                // Typical values: -2 (silence) .. 10 (loud).
                rmsLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onError(error: Int) {
                listening = false
                rmsLevel = 0f
                log.w { "Voice recognition error $error" }
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> toast(msgNoSpeech)
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> Unit
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> toast(msgPermission)
                    else -> toast(msgFailed)
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                rmsLevel = 0f
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isEmpty()) {
                    toast(msgNoSpeech)
                } else {
                    log.i { "Recognized query (${text.length} chars)" }
                    currentOnQuery(text)
                }
            }
        })
        onDispose {
            recognizer?.setRecognitionListener(null)
            recognizer?.destroy()
        }
    }

    val onClick: () -> Unit = {
        when {
            recognizer == null -> toast(msgUnavailable)
            listening -> {
                recognizer.stopListening()
                listening = false
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> startListening()
        }
    }

    val accent = MaterialTheme.nuvio.colors.accent
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val pulse = rememberInfiniteTransition(label = "tvVoicePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "tvVoicePulseScale",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "tvVoicePulseAlpha",
    )
    val animatedRms by animateFloatAsState(
        targetValue = if (listening) rmsLevel else 0f,
        animationSpec = tween(100),
        label = "tvVoiceRms",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
        if (listening) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(color = accent.copy(alpha = pulseAlpha * 0.4f), radius = size.minDimension / 2f * pulseScale)
                if (animatedRms > 0.01f) {
                    drawCircle(
                        color = accent.copy(alpha = 0.25f + animatedRms * 0.25f),
                        radius = size.minDimension / 2f * (1f + animatedRms * 0.35f),
                        style = Stroke(width = 2.5f + animatedRms * 3f),
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(ButtonShape)
                .background(
                    if (listening) accent.copy(alpha = 0.15f) else MaterialTheme.nuvio.colors.surfaceCard,
                    ButtonShape,
                )
                .border(
                    width = if (focused || listening) 2.dp else 1.dp,
                    color = when {
                        listening -> accent
                        focused -> MaterialTheme.nuvio.colors.focusRing
                        else -> MaterialTheme.nuvio.colors.borderDefault
                    },
                    shape = ButtonShape,
                )
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(R.string.tv_voice_search),
                tint = if (listening) accent else MaterialTheme.nuvio.colors.textPrimary,
            )
        }
    }
}
