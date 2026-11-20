package com.example.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

object WavAudioSerializer {
    const val SAMPLE_RATE = 24000

    fun ensurePresetWav(cacheDir: File, preset: LobbyMusicPreset): File {
        val dir = File(cacheDir, "audio_cache").apply { mkdirs() }
        val target = File(dir, "lobby_${preset.name.lowercase()}.wav")
        if (!target.exists() || target.length() <= 44L) {
            val samples = synthesizeLobbyLoop(preset)
            writePcm16MonoWav(samples, SAMPLE_RATE, target)
        }
        return target
    }

    fun ensureSfxWav(cacheDir: File, sfx: GameSfx): File {
        val dir = File(cacheDir, "audio_cache").apply { mkdirs() }
        val target = File(dir, "sfx_${sfx.name.lowercase()}.wav")
        if (!target.exists() || target.length() <= 44L) {
            val samples = synthesizeSfx(sfx)
            writePcm16MonoWav(samples, SAMPLE_RATE, target)
        }
        return target
    }

    fun wrapRawPcmAsWav(cacheDir: File, rawPcmBytes: ByteArray, tag: String): File {
        val dir = File(cacheDir, "audio_cache").apply { mkdirs() }
        val target = File(dir, "custom_${tag.hashCode()}.wav")
        if (!target.exists() || target.length() <= 44L) {
            val shorts = ShortArray(rawPcmBytes.size / 2)
            for (i in shorts.indices) {
                val lo = rawPcmBytes[i * 2].toInt() and 0xFF
                val hi = rawPcmBytes[i * 2 + 1].toInt()
                shorts[i] = ((hi shl 8) or lo).toShort()
            }
            writePcm16MonoWav(shorts, SAMPLE_RATE, target)
        }
        return target
    }

    fun writePcm16MonoWav(samples: ShortArray, sampleRate: Int, outFile: File) {
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // AudioFormat = PCM
            putShort(1) // NumChannels = Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // ByteRate
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }
        val pcmBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            pcmBuffer.putShort(sample)
        }
        FileOutputStream(outFile).use { fos ->
            fos.write(header.array())
            fos.write(pcmBuffer.array())
        }
    }

    fun synthesizeLobbyLoop(preset: LobbyMusicPreset): ShortArray {
        val beatSec = 60.0 / preset.bpm
        val totalSec = beatSec * 8.0
        val count = (SAMPLE_RATE * totalSec).toInt()
        val out = ShortArray(count)

        val roots = when (preset) {
            LobbyMusicPreset.NEON_LOUNGE -> listOf(110.0, 87.31, 98.0, 82.41)
            LobbyMusicPreset.CYBER_PULSE -> listOf(130.81, 116.54, 98.0, 116.54)
            LobbyMusicPreset.CAMPUS_CHILL -> listOf(146.83, 123.47, 110.0, 130.81)
        }
        val arpSteps = listOf(2.0, 3.0, 4.0, 3.0, 2.5, 4.0, 5.0, 3.0)
        val edgeSamples = (SAMPLE_RATE * 0.04).toInt()

        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val barPos = (t / (beatSec * 2.0)).toInt().coerceIn(0, roots.lastIndex)
            val root = roots[barPos]
            val beatFrac = (t % beatSec) / beatSec
            val halfBeatFrac = (t % (beatSec * 0.5)) / (beatSec * 0.5)
            val stepIdx = ((t / (beatSec * 0.5)).toInt()) % arpSteps.size
            val arpFreq = root * arpSteps[stepIdx]

            val bassEnv = exp(-3.5 * beatFrac)
            val bass = sin(2.0 * PI * root * 0.5 * t) * bassEnv * 0.36
            val pad = (sin(2.0 * PI * root * 2.0 * t) + 0.6 * sin(2.0 * PI * root * 3.0 * t)) * 0.14
            val arpEnv = exp(-7.5 * halfBeatFrac)
            val arp = sin(2.0 * PI * arpFreq * t) * arpEnv * 0.18

            val loopFade = when {
                i < edgeSamples -> i.toDouble() / edgeSamples
                i > count - edgeSamples -> (count - i).toDouble() / edgeSamples
                else -> 1.0
            }

            val mixed = tanh((bass + pad + arp) * 1.15) * loopFade
            out[i] = (mixed * 18000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun synthesizeSfx(sfx: GameSfx): ShortArray {
        return when (sfx) {
            GameSfx.OPENING_STING -> synthNotes(listOf(329.63 to 0.09, 440.0 to 0.09, 554.37 to 0.12, 659.25 to 0.28), 0.72)
            GameSfx.ROOM_JOIN -> synthNotes(listOf(440.0 to 0.07, 554.37 to 0.08, 659.25 to 0.16), 0.75)
            GameSfx.ROUND_START -> synthNotes(listOf(293.66 to 0.07, 440.0 to 0.08, 587.33 to 0.10, 880.0 to 0.22), 0.82)
            GameSfx.MATCH_START -> synthNotes(listOf(220.0 to 0.08, 329.63 to 0.08, 440.0 to 0.10, 880.0 to 0.22), 0.80)
            GameSfx.CHAT_SEND -> synthNotes(listOf(659.25 to 0.045, 987.77 to 0.07), 0.58)
            GameSfx.CHAT_PING -> synthNotes(listOf(783.99 to 0.045, 1046.5 to 0.07), 0.50)
            GameSfx.MOVE_CONFIRM -> synthNotes(listOf(587.33 to 0.05, 880.0 to 0.09), 0.65)
            GameSfx.VOTE_SELECT -> synthNotes(listOf(523.25 to 0.05), 0.55)
            GameSfx.VOTE_LOCK -> synthNotes(listOf(293.66 to 0.07, 587.33 to 0.12), 0.78)
            GameSfx.URGENT_TICK -> synthNotes(listOf(987.77 to 0.045), 0.52)
            GameSfx.REVEAL_WIN -> synthNotes(listOf(523.25 to 0.08, 659.25 to 0.08, 783.99 to 0.09, 1046.5 to 0.25), 0.80)
            GameSfx.REVEAL_GLITCH -> synthNotes(listOf(311.13 to 0.10, 277.18 to 0.11, 207.65 to 0.24), 0.82)
        }
    }

    private fun synthNotes(notes: List<Pair<Double, Double>>, peakAmp: Double): ShortArray {
        val totalSamples = notes.sumOf { (it.second * SAMPLE_RATE).toInt() }
        val buf = ShortArray(totalSamples)
        var cursor = 0
        for ((freq, dur) in notes) {
            val n = (dur * SAMPLE_RATE).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-5.0 * (i.toDouble() / n.coerceAtLeast(1))) * minOf(1.0, i / 120.0)
                val sig = sin(2.0 * PI * freq * t) + 0.25 * sin(4.0 * PI * freq * t)
                buf[cursor + i] = (sig * env * peakAmp * 16000.0)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            cursor += n
        }
        return buf
    }
}
