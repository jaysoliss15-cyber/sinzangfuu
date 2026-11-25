package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudioFileManager
import com.example.service.StudioController
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Missing Page", appName)
  }

  @Test
  fun `auto-paste replaceDialogueWithUndo backs up previous text for undo restore`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controller = StudioController(context)

    controller.setDialogueText("Original narration before auto-paste.")
    controller.replaceDialogueWithUndo(
      newText = "New clipboard narration pasted on resume.",
      runLogQa = false,
      snackbarMessage = "Auto-pasted 41 chars",
    )

    assertEquals("New clipboard narration pasted on resume.", controller.dialogueText.value)
    assertEquals("Auto-pasted 41 chars" to "Undo", controller.snackbarEvent.value)

    controller.undoDialogueRestore()
    assertEquals("Original narration before auto-paste.", controller.dialogueText.value)
  }

  @Test
  fun `config repository persists elevenlabs voice and render settings cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("missing_page_studio_config", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    val repo = com.example.data.StudioConfigRepository(context)
    // Simulate legacy install with auto_clean_after_export=false before v2 migration
    prefs.edit().putBoolean("auto_clean_after_export", false).commit()
    val migrated = repo.load()
    assertTrue(migrated.autoCleanAfterExport)
    assertEquals(com.example.data.StudioConfig.DEFAULT_VOICE_ID, migrated.voiceId)
    assertEquals(com.example.data.StudioConfig.DEFAULT_MODEL_ID, migrated.modelId)
    assertEquals(com.example.data.StudioConfig.DEFAULT_SFX_MODEL_ID, migrated.sfxModelId)

    val updated =
      migrated.copy(
        apiKey = "el_key_123",
        voiceId = "voice_custom_456",
        modelId = "eleven_turbo_v2_5",
        sfxModelId = "eleven_text_to_sound_v2",
        partCount = 6,
        selectedPart = 3,
        filterMode = com.example.data.RenderFilterMode.STANDARD_HORROR,
        turboModeEnabled = false,
        smartResumeEnabled = true,
        transitionsAfter = "2,4",
        autoCleanAfterExport = false,
      )
    repo.save(updated)

    val reloaded = repo.load()
    assertEquals("el_key_123", reloaded.apiKey)
    assertEquals("voice_custom_456", reloaded.voiceId)
    assertEquals("eleven_turbo_v2_5", reloaded.modelId)
    assertEquals("eleven_text_to_sound_v2", reloaded.sfxModelId)
    assertEquals(6, reloaded.partCount)
    assertEquals(3, reloaded.selectedPart)
    assertEquals(com.example.data.RenderFilterMode.STANDARD_HORROR, reloaded.filterMode)
    assertFalse(reloaded.turboModeEnabled)
    assertTrue(reloaded.smartResumeEnabled)
    assertEquals("2,4", reloaded.transitionsAfter)
    assertFalse(reloaded.autoCleanAfterExport)
  }

  @Test
  fun `audio pipeline asetrate fallback writes tmp wav cleanly and survives stale audio cleanup`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val fileManager = StudioFileManager(context)
      fileManager.ensurePartFolders(1)
      val partDir = fileManager.getPartDir(1)
      val staleMix = File(partDir, "1_mix.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }

      val invokedFilters = mutableListOf<String>()
      val capturedArgs = mutableListOf<List<String>>()
      val logs = mutableListOf<Pair<String, com.example.data.LogLevel>>()

      val processor =
        com.example.data.AudioPipelineProcessor(
          fileManager = fileManager,
          elevenLabsClient = com.example.data.ElevenLabsClient(),
          ffmpegExecutor = com.example.data.FfmpegExecutor(context),
          ffmpegRunner = { args ->
            capturedArgs += args
            val afIndex = args.indexOf("-af")
            val filter = if (afIndex >= 0) args[afIndex + 1] else ""
            invokedFilters += filter
            val outFile = File(args.last())
            if (filter.contains("rubberband")) {
              com.example.data.CommandExecutionResult(
                exitCode = 1,
                output = "No such filter: 'rubberband'",
              )
            } else {
              assertTrue(filter.contains("aresample=44100,asetrate=44100*0.9438,aresample=44100,atempo="))
              assertTrue(args.firstOrNull() == "-y")
              assertTrue(args.contains("-f") && args.contains("wav"))
              outFile.parentFile?.mkdirs()
              outFile.writeBytes(ByteArray(64) { 42 })
              com.example.data.CommandExecutionResult(exitCode = 0, output = "ok")
            }
          },
          durationProber = { f -> if (f.exists() && f.length() > 0L) 4.2 else null },
          ttsSynthesizer = { _, _, _, _, outFile ->
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(ByteArray(32) { 7 })
          },
        )

      val qa =
        com.example.domain.ScriptValidator.validateScript(
          "The archive door remained locked.\n...\n..."
        )
      processor.processPartVoice(
        partNumber = 1,
        qaResult = qa,
        config = com.example.data.StudioConfig(apiKey = "el_test_key"),
        onProgress = { _, _, _ -> },
        onLog = { msg, lvl -> logs += msg to lvl },
      )

      val finalWav = File(partDir, "1.wav")
      assertTrue(finalWav.exists() && finalWav.length() == 64L)
      assertFalse(File(partDir, ".tmp_1.wav").exists())
      assertFalse(staleMix.exists())
      assertEquals(2, invokedFilters.size)
      assertTrue(logs.any { it.first.contains("asetrate+atempo camouflage fallback") })
    }

  @Test
  fun `audio pipeline falls back to unpitched normalized wav when asetrate fails`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val fileManager = StudioFileManager(context)
      fileManager.ensurePartFolders(1)
      val partDir = fileManager.getPartDir(1)

      val invokedFilters = mutableListOf<String>()
      val logs = mutableListOf<Pair<String, com.example.data.LogLevel>>()

      val processor =
        com.example.data.AudioPipelineProcessor(
          fileManager = fileManager,
          elevenLabsClient = com.example.data.ElevenLabsClient(),
          ffmpegExecutor = com.example.data.FfmpegExecutor(context),
          ffmpegRunner = { args ->
            val afIndex = args.indexOf("-af")
            val filter = if (afIndex >= 0) args[afIndex + 1] else ""
            invokedFilters += filter
            val outFile = File(args.last())
            when {
              filter.contains("rubberband") ->
                com.example.data.CommandExecutionResult(1, "No such filter: 'rubberband'")
              filter.contains("asetrate") ->
                com.example.data.CommandExecutionResult(1, "Error initializing filter 'asetrate': invalid rate")
              else -> {
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(ByteArray(48) { 9 })
                com.example.data.CommandExecutionResult(0, "ok")
              }
            }
          },
          durationProber = { f -> if (f.exists() && f.length() > 0L) 3.5 else null },
          ttsSynthesizer = { _, _, _, _, outFile ->
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(ByteArray(32) { 7 })
          },
        )

      val qa =
        com.example.domain.ScriptValidator.validateScript(
          "The tape hissed softly.\n...\n..."
        )
      processor.processPartVoice(
        partNumber = 1,
        qaResult = qa,
        config = com.example.data.StudioConfig(apiKey = "el_test_key"),
        onProgress = { _, _, _ -> },
        onLog = { msg, lvl -> logs += msg to lvl },
      )

      val finalWav = File(partDir, "1.wav")
      assertTrue(finalWav.exists() && finalWav.length() == 48L)
      assertEquals(3, invokedFilters.size)
      assertEquals(com.example.data.AudioPipelineProcessor.UNPITCHED_NORMALIZED_FILTER, invokedFilters.last())
      assertTrue(
        logs.any {
          it.second == com.example.data.LogLevel.ERROR &&
            it.first.contains("Error initializing filter 'asetrate': invalid rate") &&
            it.first.contains("un-pitched normalized audio")
        }
      )
    }

  @Test
  fun `ffmpeg concat manifest escapes spaces and single quotes and extracts last 10 stderr lines`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val spacedDir = File(context.cacheDir, "part 1").apply { mkdirs() }
    val quotedFile = File(spacedDir, "joe's night clip.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    val emptyFile = File(spacedDir, "empty clip.mp4").apply { writeBytes(byteArrayOf()) }

    val manifest = com.example.data.FfmpegExecutor.buildConcatManifest(listOf(quotedFile))
    assertEquals("file '${spacedDir.absolutePath}/joe'\\''s night clip.mp4'\n", manifest)

    val missingFailure =
      runCatching {
        com.example.data.FfmpegExecutor.buildConcatManifest(listOf(quotedFile, emptyFile))
      }.exceptionOrNull()
    assertTrue(missingFailure is IllegalArgumentException)

    val twelveLines = (1..12).joinToString("\n") { "stderr line $it" } + "\n\n"
    val tail = com.example.data.FfmpegExecutor.lastStderrLines(twelveLines, maxLines = 10)
    val lines = tail.lines()
    assertEquals(10, lines.size)
    assertEquals("stderr line 3", lines.first())
    assertEquals("stderr line 12", lines.last())

    spacedDir.deleteRecursively()
  }

  @Test
  fun `video render pipeline logs last 10 ffmpeg stderr lines on failure`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val fileManager = StudioFileManager(context)
      fileManager.ensurePartFolders(1)
      fileManager.stockLoopsDir.mkdirs()
      val stockFile = File(fileManager.stockLoopsDir, "archive corridor.mp4").apply {
        writeBytes(ByteArray(128) { 1 })
      }
      val partDir = fileManager.getPartDir(1)
      File(partDir, "1.wav").writeBytes(ByteArray(128) { 2 })

      val stderrLines = (1..14).joinToString("\n") { "ffmpeg error line $it" }
      val logs = mutableListOf<Pair<String, com.example.data.LogLevel>>()

      val pipeline =
        com.example.data.VideoRenderPipeline(
          fileManager = fileManager,
          ffmpegExecutor = com.example.data.FfmpegExecutor(context),
          ffmpegRunner = { _, _ ->
            com.example.data.CommandExecutionResult(exitCode = 1, output = stderrLines)
          },
          durationProber = { 10.0 },
          audioStreamProber = { true },
        )

      val error =
        runCatching {
          pipeline.renderFilterPreview(
            partNumber = 1,
            filterMode = com.example.data.RenderFilterMode.STANDARD_HORROR,
            onProgress = { _, _, _ -> },
            onLog = { msg, lvl -> logs += msg to lvl },
          )
        }.exceptionOrNull()

      assertTrue(error is IllegalStateException)
      val errorLog = logs.firstOrNull { it.second == com.example.data.LogLevel.ERROR }?.first.orEmpty()
      assertTrue(errorLog.contains("FFmpeg failed at [3s Preview (Standard Horror)]"))
      assertTrue(errorLog.contains("ffmpeg error line 5"))
      assertTrue(errorLog.contains("ffmpeg error line 14"))
      assertFalse(errorLog.contains("ffmpeg error line 4\n"))

      stockFile.delete()
    }

  @Test
  fun `video render pipeline caps bitrate with vbv maxrate and cleans intermediate files`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val fileManager = StudioFileManager(context)
      fileManager.cleanTempWorkspaceOnly()
      fileManager.ensurePartFolders(1)
      fileManager.stockLoopsDir.mkdirs()
      val stockFile =
        File(fileManager.stockLoopsDir, "night_hallway.mp4").apply {
          writeBytes(ByteArray(128) { 1 })
        }
      val partDir = fileManager.getPartDir(1)
      File(partDir, "1.wav").writeBytes(ByteArray(128) { 2 })

      // Seed stale temp and legacy un-capped graded cache files
      val staleTmp = File(fileManager.workspaceDir, ".tmp_crashed_render.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
      val legacyGraded = File(fileManager.workspaceDir, "graded_night_hallway_standard_horror.mp4").apply { writeBytes(byteArrayOf(4, 5, 6)) }

      val capturedCommands = mutableListOf<List<String>>()
      val pipeline =
        com.example.data.VideoRenderPipeline(
          fileManager = fileManager,
          ffmpegExecutor = com.example.data.FfmpegExecutor(context),
          ffmpegRunner = { args, _ ->
            capturedCommands += args
            val outFile = File(args.last())
            outFile.parentFile?.mkdirs()
            outFile.writeBytes(ByteArray(256) { 9 })
            com.example.data.CommandExecutionResult(exitCode = 0, output = "time=00:00:05.00")
          },
          durationProber = { 10.0 },
          audioStreamProber = { true },
        )

      val cfg = com.example.data.StudioConfig()
      assertTrue(cfg.autoCleanAfterExport)

      val output =
        pipeline.renderFullDocumentary(
          config = cfg.copy(autoCleanAfterExport = false),
          onProgress = { _, _, _ -> },
          onLog = { _, _ -> },
        )

      assertTrue(output.exists() && output.length() > 0L)
      assertFalse(staleTmp.exists())
      assertFalse(legacyGraded.exists())
      assertFalse(File(fileManager.workspaceDir, "part_1_voice_raw.wav").exists())
      assertFalse(File(fileManager.workspaceDir, "MASTER_NO_BGM.mp4").exists())
      assertFalse(File(fileManager.workspaceDir, "master_video_list.txt").exists())

      val gradedCmd =
        capturedCommands.first { args ->
          args.last().contains("graded_night_hallway_standard_horror_v2.mp4")
        }
      assertTrue(gradedCmd.contains("-crf") && gradedCmd.contains(com.example.data.VideoRenderPipeline.DEFAULT_VIDEO_CRF))
      assertTrue(gradedCmd.contains("-maxrate") && gradedCmd.contains(com.example.data.VideoRenderPipeline.DEFAULT_VIDEO_MAXRATE))
      assertTrue(gradedCmd.contains("-bufsize") && gradedCmd.contains(com.example.data.VideoRenderPipeline.DEFAULT_VIDEO_BUFSIZE))

      fileManager.cleanTempWorkspaceOnly()
      output.delete()
      stockFile.delete()
    }
}
