package com.example

import android.content.Context
import android.net.Uri
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
  fun `config repository persists multi-provider llm settings and god prompt metadata`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("missing_page_studio_config", Context.MODE_PRIVATE)
    prefs.edit().clear()
      .putString("script_writer_god_prompt", "Legacy inline prompt")
      .putString("gemini_api_key", "AIzaMigratedKey")
      .putString("gemini_model", "gemini-2.5-pro")
      .commit()

    val repo = com.example.data.StudioConfigRepository(context)
    val migrated = repo.load()
    assertEquals(com.example.data.LlmProvider.GEMINI, migrated.llmProvider)
    assertEquals("https://generativelanguage.googleapis.com/v1beta", migrated.llmBaseUrl)
    assertEquals("AIzaMigratedKey", migrated.llmApiKey)
    assertEquals("AIzaMigratedKey", migrated.geminiApiKey)
    assertEquals("gemini-2.5-pro", migrated.llmModel)
    assertEquals(false, migrated.autoPilotFullAuto)

    val updated =
      migrated.copy(
        apiKey = "el_key_123",
        llmProvider = com.example.data.LlmProvider.OPENROUTER,
        llmBaseUrl = "https://openrouter.ai/api/v1",
        llmApiKey = "sk-or-v1-test",
        llmModel = "anthropic/claude-3.5-sonnet",
        godPromptFileName = "master_prompt.txt",
        godPromptCharCount = 12_400,
        godPromptWordCount = 2_050,
        godPromptUpdatedAt = 1_730_000_000_000L,
        autoPilotFullAuto = true,
      )
    repo.save(updated)

    val reloaded = repo.load()
    assertEquals("el_key_123", reloaded.apiKey)
    assertEquals(com.example.data.LlmProvider.OPENROUTER, reloaded.llmProvider)
    assertEquals("https://openrouter.ai/api/v1", reloaded.llmBaseUrl)
    assertEquals("sk-or-v1-test", reloaded.llmApiKey)
    assertEquals("anthropic/claude-3.5-sonnet", reloaded.llmModel)
    assertEquals("master_prompt.txt", reloaded.godPromptFileName)
    assertEquals(12_400, reloaded.godPromptCharCount)
    assertEquals(2_050, reloaded.godPromptWordCount)
    assertEquals(1_730_000_000_000L, reloaded.godPromptUpdatedAt)
    assertTrue(reloaded.hasGodPromptFile)
    assertEquals(true, reloaded.autoPilotFullAuto)
    assertFalse(prefs.contains("script_writer_god_prompt"))
  }

  @Test
  fun `file manager imports csv or txt god prompt file and generates preview cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fileManager = StudioFileManager(context)
    val sourceFile = File(context.cacheDir, "god_prompt_rules.csv")
    sourceFile.writeText("rule,instruction\n1,Never use digits in spoken narration.\n2,Include eerie atmospheric pauses.\n")

    val result = fileManager.importGodPromptFile(Uri.fromFile(sourceFile))
    assertEquals("god_prompt_rules.csv", result.fileName)
    assertTrue(result.charCount > 0)
    assertTrue(result.wordCount >= 10)
    assertTrue(result.previewText.contains("Never use digits"))
    assertTrue(fileManager.godPromptFile.exists())
    assertTrue(fileManager.readGodPromptPreview().contains("rule,instruction"))

    fileManager.clearGodPromptFile()
    assertFalse(fileManager.godPromptFile.exists())
    assertEquals("", fileManager.readGodPromptPreview())
    sourceFile.delete()
  }

  @Test
  fun `parses gemini models json filtering generateContent and stripping models prefix`() {
    val json =
      """
      {
        "models": [
          {
            "name": "models/gemini-2.5-pro",
            "supportedGenerationMethods": ["generateContent", "countTokens"]
          },
          {
            "name": "models/text-embedding-004",
            "supportedGenerationMethods": ["embedContent"]
          },
          {
            "name": "models/gemini-2.5-flash",
            "supportedGenerationMethods": ["generateContent"]
          },
          {
            "name": "models/gemini-2.5-flash",
            "supportedGenerationMethods": ["generateContent"]
          }
        ]
      }
      """.trimIndent()

    val parsed = com.example.data.GeminiScriptClient.parseGeminiModelsJson(json)
    assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-pro"), parsed)
  }

  @Test
  fun `parses openai compatible models json and chat completion content`() {
    val modelsJson =
      """
      {
        "object": "list",
        "data": [
          {"id": "openai/gpt-4o-mini"},
          {"id": "anthropic/claude-3.5-sonnet"},
          {"id": "deepseek/deepseek-chat"},
          {"id": "openai/gpt-4o-mini"},
          {"id": "  "}
        ]
      }
      """.trimIndent()

    val parsed = com.example.data.GeminiScriptClient.parseOpenAiModelsJson(modelsJson)
    assertEquals(
      listOf(
        "anthropic/claude-3.5-sonnet",
        "deepseek/deepseek-chat",
        "openai/gpt-4o-mini",
      ),
      parsed,
    )

    val completionJson =
      """
      {
        "choices": [
          {
            "message": {
              "role": "assistant",
              "content": "The corridor lights flickered twice.\n...\n..."
            }
          }
        ]
      }
      """.trimIndent()
    assertEquals(
      "The corridor lights flickered twice.\n...\n...",
      com.example.data.GeminiScriptClient.parseOpenAiChatCompletionContent(completionJson),
    )
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
  fun `custom api error formatting preserves http code truncated body and reasoning_content`() {
    val longRawBody = "upstream gateway error: " + "X".repeat(500)
    val httpErr =
      com.example.data.GeminiScriptClient.formatHttpError(
        provider = com.example.data.LlmProvider.CUSTOM,
        code = 502,
        url = "https://custom.llm.local/v1/chat/completions",
        rawBody = longRawBody,
      )
    assertTrue(httpErr.contains("HTTP 502"))
    assertTrue(httpErr.contains("upstream gateway error:"))
    assertTrue(httpErr.length < 520)

    val reasoningOnlyJson =
      """
      {
        "choices": [
          {
            "finish_reason": "length",
            "message": {
              "role": "assistant",
              "content": "",
              "reasoning_content": "Thinking step by step about the horror script structure before writing..."
            }
          }
        ]
      }
      """.trimIndent()
    val parsed = com.example.data.GeminiScriptClient.parseOpenAiChatResponse(reasoningOnlyJson)
    assertEquals("", parsed.content)
    assertTrue(parsed.reasoningContent.startsWith("Thinking step by step"))
    val reasoningError =
      com.example.data.GeminiScriptClient.formatEmptyOpenAiCompletionError(
        provider = com.example.data.LlmProvider.DEEPSEEK,
        httpCode = 200,
        rawBody = reasoningOnlyJson,
        parsed = parsed,
      )
    assertTrue(reasoningError.contains("HTTP 200"))
    assertTrue(reasoningError.contains("reasoning_content"))
    assertTrue(reasoningError.contains("finish_reason=length"))
    assertTrue(reasoningError.contains("Thinking step by step"))

    val malformedBodyJson = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant"}}]}"""
    val blankParsed = com.example.data.GeminiScriptClient.parseOpenAiChatResponse(malformedBodyJson)
    val blankError =
      com.example.data.GeminiScriptClient.formatEmptyOpenAiCompletionError(
        provider = com.example.data.LlmProvider.CUSTOM,
        httpCode = 200,
        rawBody = malformedBodyJson,
        parsed = blankParsed,
      )
    assertTrue(blankError.contains("HTTP 200"))
    assertTrue(blankError.contains("Body:"))
    assertTrue(blankError.contains("finish_reason=stop"))
  }
}
