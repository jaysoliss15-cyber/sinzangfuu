package com.example.data

import com.example.domain.ScriptValidator
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AudioPipelineProcessor(
  private val fileManager: StudioFileManager,
  private val elevenLabsClient: ElevenLabsClient,
  private val ffmpegExecutor: FfmpegExecutor,
) {
  private sealed interface ExpandedWorkItem {
    data class SpeechChunk(val text: String, val index: Int) : ExpandedWorkItem
    data class SfxCue(val prompt: String, val index: Int) : ExpandedWorkItem
    data class SilenceCue(val durationSeconds: Double, val index: Int) : ExpandedWorkItem
  }

  suspend fun processPartVoice(
    partNumber: Int,
    qaResult: ScriptQaResult,
    config: StudioConfig,
    onProgress: (percent: Int, step: String, eta: String) -> Unit,
    onLog: (String, LogLevel) -> Unit,
  ) {
    require(qaResult.isValid) {
      qaResult.errors.firstOrNull() ?: "Script QA validation failed."
    }
    require(config.apiKey.isNotBlank()) {
      "ElevenLabs API Key is missing. Configure it in API & Settings."
    }

    val partDir = fileManager.getPartDir(partNumber).apply { mkdirs() }

    // Save cleaned transcript
    val transcriptFile = File(partDir, "${partNumber}_transcript.txt")
    transcriptFile.writeText(qaResult.cleanedTranscript, Charsets.UTF_8)
    onLog("Saved cleaned transcript to part $partNumber/${transcriptFile.name}", LogLevel.INFO)

    // Expand speech chunks (max 900-1000 chars, zero overlap)
    val workItems = mutableListOf<ExpandedWorkItem>()
    var speechCounter = 0
    var sfxCounter = 0
    var silenceCounter = 0

    for (event in qaResult.events) {
      when (event) {
        is ScriptEvent.Speech -> {
          val chunks = ScriptValidator.chunkSpeechSegment(event.text, maxChars = 1000, softMin = 900)
          for (chunk in chunks) {
            speechCounter++
            workItems += ExpandedWorkItem.SpeechChunk(chunk, speechCounter)
          }
        }
        is ScriptEvent.Sfx -> {
          sfxCounter++
          workItems += ExpandedWorkItem.SfxCue(event.prompt, sfxCounter)
        }
        is ScriptEvent.Silence -> {
          silenceCounter++
          workItems += ExpandedWorkItem.SilenceCue(event.durationSeconds, silenceCounter)
        }
      }
    }

    if (speechCounter == 0) {
      throw IllegalStateException("No speech chunks found after parsing script.")
    }

    onLog(
      "Part $partNumber audio plan: $speechCounter speech chunk(s), $sfxCounter SFX, $silenceCounter silence drop(s).",
      LogLevel.INFO,
    )

    val startTimeMs = System.currentTimeMillis()
    val totalSteps = workItems.size + 1

    // Single speech chunk with 0 SFX and 0 Silence -> direct conversion to part X/X.wav
    if (speechCounter == 1 && sfxCounter == 0 && silenceCounter == 0) {
      val singleSpeech = workItems.first() as ExpandedWorkItem.SpeechChunk
      val rawMp3 = File(partDir, ".tmp_${partNumber}_raw.mp3")
      val outWav = File(partDir, "$partNumber.wav")
      val tmpWav = File(partDir, ".tmp_${partNumber}.wav")
      try {
        onProgress(15, "Part $partNumber: Downloading ElevenLabs TTS...", "Calculating...")
        elevenLabsClient.synthesizeTts(
          apiKey = config.apiKey,
          voiceId = config.voiceId,
          modelId = config.modelId,
          text = singleSpeech.text,
          outFile = rawMp3,
        )
        currentCoroutineContext().ensureActive()

        onProgress(65, "Part $partNumber: Applying FFmpeg voice camouflage...", "~3s")
        convertSpeechToCamoWav(rawMp3, tmpWav, onLog)
        rawMp3.delete()

        val dur = ffmpegExecutor.probeDurationSeconds(tmpWav) ?: 0.0
        if (dur <= 0.0) {
          throw IllegalStateException("Generated WAV duration is 0s.")
        }

        fileManager.clearPartAudioFiles(partNumber)
        atomicReplace(tmpWav, outWav)
        onProgress(100, "Part $partNumber: Voice ready (${formatSeconds(dur)})", "Done")
        onLog(
          "Part $partNumber single-chunk voice saved: ${outWav.name} (${formatSeconds(dur)}).",
          LogLevel.SUCCESS,
        )
      } catch (e: CancellationException) {
        rawMp3.delete()
        tmpWav.delete()
        throw e
      } catch (e: Exception) {
        rawMp3.delete()
        tmpWav.delete()
        throw e
      }
      return
    }

    // Multi-segment path using _audio_generation_work/
    fileManager.clearPartAudioFiles(partNumber)
    val workDir = File(partDir, "_audio_generation_work").apply { mkdirs() }
    val speechWavs = mutableListOf<File>()
    val mixWavs = mutableListOf<File>()

    val speechOutTarget = File(partDir, "$partNumber.wav")
    val mixOutTarget = File(partDir, "${partNumber}_mix.wav")
    val tmpSpeechConcat = File(partDir, ".tmp_${partNumber}_speech.wav")
    val tmpMixConcat = File(partDir, ".tmp_${partNumber}_mix.wav")

    try {
      workItems.forEachIndexed { idx, item ->
        currentCoroutineContext().ensureActive()
        val doneSteps = idx
        val pct = ((doneSteps.toDouble() / totalSteps) * 90.0).toInt().coerceIn(5, 92)
        val eta = estimateEta(startTimeMs, doneSteps, totalSteps)

        when (item) {
          is ExpandedWorkItem.SpeechChunk -> {
            onProgress(
              pct,
              "Part $partNumber: TTS chunk ${item.index}/$speechCounter (${item.text.length} chars)...",
              eta,
            )
            val rawMp3 = File(workDir, "raw_speech_${item.index}.mp3")
            val wavFile = File(workDir, String.format(Locale.US, "seg_%04d_speech.wav", idx + 1))
            elevenLabsClient.synthesizeTts(
              apiKey = config.apiKey,
              voiceId = config.voiceId,
              modelId = config.modelId,
              text = item.text,
              outFile = rawMp3,
            )
            currentCoroutineContext().ensureActive()
            convertSpeechToCamoWav(rawMp3, wavFile, onLog)
            rawMp3.delete()

            val dur = ffmpegExecutor.probeDurationSeconds(wavFile) ?: 0.0
            if (dur <= 0.0) {
              throw IllegalStateException("Speech segment ${item.index} produced 0s WAV.")
            }
            speechWavs += wavFile
            mixWavs += wavFile
            onLog("Speech chunk ${item.index}/$speechCounter ready (${formatSeconds(dur)}).", LogLevel.INFO)
          }
          is ExpandedWorkItem.SfxCue -> {
            onProgress(
              pct,
              "Part $partNumber: Generating SFX ${item.index}/$sfxCounter...",
              eta,
            )
            val rawMp3 = File(workDir, "raw_sfx_${item.index}.mp3")
            val wavFile = File(workDir, String.format(Locale.US, "seg_%04d_sfx.wav", idx + 1))
            elevenLabsClient.generateSfx(
              apiKey = config.apiKey,
              sfxModelId = config.sfxModelId,
              prompt = item.prompt,
              outFile = rawMp3,
            )
            currentCoroutineContext().ensureActive()
            convertSfxToWav(rawMp3, wavFile)
            rawMp3.delete()

            val dur = ffmpegExecutor.probeDurationSeconds(wavFile) ?: 0.0
            if (dur <= 0.0) {
              throw IllegalStateException("SFX segment ${item.index} produced 0s WAV.")
            }
            mixWavs += wavFile
            onLog("SFX ${item.index}/$sfxCounter ready (${formatSeconds(dur)}).", LogLevel.INFO)
          }
          is ExpandedWorkItem.SilenceCue -> {
            onProgress(
              pct,
              "Part $partNumber: Generating ${item.durationSeconds}s silence drop...",
              eta,
            )
            val wavFile = File(workDir, String.format(Locale.US, "seg_%04d_silence.wav", idx + 1))
            generateSilenceWav(item.durationSeconds, wavFile)
            mixWavs += wavFile
            onLog("Silence drop ${item.durationSeconds}s generated.", LogLevel.INFO)
          }
        }
      }

      currentCoroutineContext().ensureActive()
      onProgress(93, "Part $partNumber: Assembling final WAV timeline...", "~2s")

      // Concat speech-only WAVs -> part X/X.wav
      concatWavList(workDir, "speech_concat.txt", speechWavs, tmpSpeechConcat)
      atomicReplace(tmpSpeechConcat, speechOutTarget)
      val speechDur = ffmpegExecutor.probeDurationSeconds(speechOutTarget) ?: 0.0
      onLog("Saved speech track: ${speechOutTarget.name} (${formatSeconds(speechDur)}).", LogLevel.SUCCESS)

      // If SFX or Silence cues exist, also concat full timeline WAVs -> part X/X_mix.wav
      if (sfxCounter > 0 || silenceCounter > 0) {
        concatWavList(workDir, "mix_concat.txt", mixWavs, tmpMixConcat)
        atomicReplace(tmpMixConcat, mixOutTarget)
        val mixDur = ffmpegExecutor.probeDurationSeconds(mixOutTarget) ?: 0.0
        onLog("Saved full mixed timeline: ${mixOutTarget.name} (${formatSeconds(mixDur)}).", LogLevel.SUCCESS)
      }

      workDir.deleteRecursively()
      onProgress(100, "Part $partNumber: Voice assembly complete.", "Done")
    } catch (e: CancellationException) {
      tmpSpeechConcat.delete()
      tmpMixConcat.delete()
      workDir.deleteRecursively()
      throw e
    } catch (e: Exception) {
      tmpSpeechConcat.delete()
      tmpMixConcat.delete()
      throw e
    }
  }

  private suspend fun convertSpeechToCamoWav(
    inputMp3: File,
    outputWav: File,
    onLog: (String, LogLevel) -> Unit,
  ) {
    val primaryFilter = "volume=2.5,bass=g=5,treble=g=3,aecho=0.8:0.8:10:0.05,rubberband=pitch=-1"
    val fallbackFilter =
      "volume=2.5,bass=g=5,treble=g=3,aecho=0.8:0.8:10:0.05,asetrate=44100*0.9438,atempo=1/0.9438"

    val primaryRes =
      ffmpegExecutor.runFfmpeg(
        listOf(
          "-i",
          inputMp3.absolutePath,
          "-af",
          primaryFilter,
          "-ar",
          "44100",
          "-ac",
          "2",
          "-c:a",
          "pcm_s16le",
          outputWav.absolutePath,
        )
      )

    if (primaryRes.exitCode == 0 && outputWav.exists() && outputWav.length() > 0L) {
      return
    }

    onLog("rubberband filter unavailable; switching to asetrate+atempo camouflage fallback.", LogLevel.WARNING)
    outputWav.delete()
    val fallbackRes =
      ffmpegExecutor.runFfmpeg(
        listOf(
          "-i",
          inputMp3.absolutePath,
          "-af",
          fallbackFilter,
          "-ar",
          "44100",
          "-ac",
          "2",
          "-c:a",
          "pcm_s16le",
          outputWav.absolutePath,
        )
      )

    if (fallbackRes.exitCode != 0 || !outputWav.exists() || outputWav.length() == 0L) {
      throw IllegalStateException("FFmpeg speech conversion failed: ${fallbackRes.output.takeLast(300)}")
    }
  }

  private suspend fun convertSfxToWav(inputMp3: File, outputWav: File) {
    val res =
      ffmpegExecutor.runFfmpeg(
        listOf(
          "-i",
          inputMp3.absolutePath,
          "-af",
          "volume=0.3",
          "-ar",
          "44100",
          "-ac",
          "2",
          "-c:a",
          "pcm_s16le",
          outputWav.absolutePath,
        )
      )
    if (res.exitCode != 0 || !outputWav.exists() || outputWav.length() == 0L) {
      throw IllegalStateException("FFmpeg SFX conversion failed: ${res.output.takeLast(300)}")
    }
  }

  private suspend fun generateSilenceWav(durationSeconds: Double, outputWav: File) {
    val durStr = String.format(Locale.US, "%.3f", durationSeconds)
    val res =
      ffmpegExecutor.runFfmpeg(
        listOf(
          "-f",
          "lavfi",
          "-i",
          "anullsrc=channel_layout=stereo:sample_rate=44100",
          "-t",
          durStr,
          "-ac",
          "2",
          "-ar",
          "44100",
          "-c:a",
          "pcm_s16le",
          outputWav.absolutePath,
        )
      )
    if (res.exitCode != 0 || !outputWav.exists() || outputWav.length() == 0L) {
      throw IllegalStateException("FFmpeg silence generation failed: ${res.output.takeLast(300)}")
    }
  }

  private suspend fun concatWavList(
    workDir: File,
    listFileName: String,
    wavFiles: List<File>,
    outputFile: File,
  ) {
    val listFile = File(workDir, listFileName)
    val contents =
      wavFiles.joinToString("\n") { file ->
        "file '${file.absolutePath.replace("'", "'\\''")}'"
      }
    listFile.writeText(contents + "\n", Charsets.UTF_8)

    val res =
      ffmpegExecutor.runFfmpeg(
        listOf(
          "-f",
          "concat",
          "-safe",
          "0",
          "-i",
          listFile.absolutePath,
          "-c",
          "copy",
          outputFile.absolutePath,
        )
      )
    if (res.exitCode != 0 || !outputFile.exists() || outputFile.length() == 0L) {
      throw IllegalStateException("FFmpeg WAV concat failed: ${res.output.takeLast(300)}")
    }
  }

  private fun atomicReplace(tmpFile: File, targetFile: File) {
    if (targetFile.exists()) targetFile.delete()
    if (!tmpFile.renameTo(targetFile)) {
      tmpFile.copyTo(targetFile, overwrite = true)
      tmpFile.delete()
    }
  }

  private fun estimateEta(startTimeMs: Long, done: Int, total: Int): String {
    if (done <= 0 || total <= done) return "Calculating..."
    val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
    val remainingSec = ((elapsedSec / done) * (total - done)).toInt().coerceAtLeast(1)
    return formatSeconds(remainingSec.toDouble())
  }

  private fun formatSeconds(sec: Double): String {
    val total = sec.toInt().coerceAtLeast(0)
    val mins = total / 60
    val rem = total % 60
    return if (mins > 0) "${mins}m ${rem}s" else "${rem}s"
  }
}
