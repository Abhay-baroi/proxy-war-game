package com.example.audio

import android.content.Context
import android.util.Log
import com.example.data.local.GeneratedTrackEntity
import com.example.data.local.LocalSqlRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

enum class LyriaModelTier(val modelId: String, val label: String, val durationSec: Int) {
    CLIP_30S("lyria-3-clip-preview", "30s Clip (Fast)", 30),
    PRO_FULL("lyria-3-pro-preview", "Full Track (Pro)", 90)
}

class LyriaMusicRepository(
    private val context: Context,
    private val sqlRepo: LocalSqlRepository
) {
    private val clipModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = LyriaModelTier.CLIP_30S.modelId,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    private val proModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = LyriaModelTier.PRO_FULL.modelId,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
            }
        )
    }

    private val bluffCoachModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun generateLobbyTrack(
        prompt: String,
        tier: LyriaModelTier
    ): Result<GeneratedTrackEntity> {
        val cleanPrompt = prompt.trim().take(240)
            .ifBlank { "Futuristic dark neon campus game lobby beat with warm synth bass and crisp hi-hats" }

        return try {
            val model = when (tier) {
                LyriaModelTier.CLIP_30S -> clipModel
                LyriaModelTier.PRO_FULL -> proModel
            }

            val response = withTimeout(60_000L) {
                model.generateContent(cleanPrompt)
            }

            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>().firstOrNull()
                ?: return Result.failure(IllegalStateException("No audio stream returned by ${tier.modelId}."))

            val bytes = audioPart.inlineData
            val mimeType = audioPart.mimeType
            val ext = when {
                mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
                mimeType.contains("wav") -> "wav"
                mimeType.contains("ogg") -> "ogg"
                else -> "pcm"
            }

            val savedFile = withContext(Dispatchers.IO) {
                val tracksDir = File(context.filesDir, "tracks").apply { mkdirs() }
                val outFile = File(tracksDir, "lyria_${System.currentTimeMillis()}.$ext")
                outFile.writeBytes(bytes)
                outFile
            }

            val title = cleanPrompt.split(",", ".").firstOrNull()?.take(28)?.ifBlank { "Custom Lobby Track" }
                ?: "Custom Lobby Track"

            val entity = GeneratedTrackEntity(
                title = title,
                prompt = cleanPrompt,
                modelId = tier.modelId,
                filePath = savedFile.absolutePath,
                mimeType = mimeType,
                durationSec = tier.durationSec
            )
            val newId = sqlRepo.saveGeneratedTrack(entity)
            Result.success(entity.copy(id = newId))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Lyria track generation timed out", e)
            Result.failure(IllegalStateException("Music generation timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lyria track generation failed for ${tier.modelId}", e)
            Result.failure(e)
        }
    }

    suspend fun suggestGlitchOrHumanReply(
        promptText: String,
        asGlitch: Boolean
    ): Result<String> {
        return try {
            val instruction = if (asGlitch) {
                "You are playing a campus social deduction party game. Write a short, plausible 1-sentence answer (max 110 characters) to this prompt that subtly mimics an AI trying to sound like a tired college student: \"$promptText\""
            } else {
                "You are playing a campus social deduction party game. Write a casual, punchy 1-sentence college-student answer (max 110 characters) to this prompt: \"$promptText\""
            }
            val response = withTimeout(30_000L) {
                bluffCoachModel.generateContent(instruction)
            }
            val line = response.text?.trim()?.replace("\"", "")?.take(140)
                ?: return Result.failure(IllegalStateException("AI returned an empty suggestion."))
            Result.success(line)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Reply suggestion timed out", e)
            Result.failure(IllegalStateException("AI suggestion timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reply suggestion failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "LyriaMusicRepo"
    }
}
