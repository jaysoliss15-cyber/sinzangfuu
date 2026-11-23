package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.StudioFileManager
import com.example.service.StudioController
import java.io.File
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
}
