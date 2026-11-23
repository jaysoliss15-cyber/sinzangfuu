package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.StudioController
import org.junit.Assert.assertEquals
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
  fun `config repository persists gemini settings and autoPilotFullAuto`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.StudioConfigRepository(context)

    val initial = repo.load()
    assertEquals("gemini-2.5-flash", initial.geminiModel)
    assertEquals(false, initial.autoPilotFullAuto)

    val updated =
      initial.copy(
        apiKey = "el_key_123",
        geminiApiKey = "AIzaTestKey123",
        geminiModel = "gemini-2.5-pro",
        scriptWriterGodPrompt = "Write slow-burn archival horror.",
        autoPilotFullAuto = true,
      )
    repo.save(updated)

    val reloaded = repo.load()
    assertEquals("el_key_123", reloaded.apiKey)
    assertEquals("AIzaTestKey123", reloaded.geminiApiKey)
    assertEquals("gemini-2.5-pro", reloaded.geminiModel)
    assertEquals("Write slow-burn archival horror.", reloaded.scriptWriterGodPrompt)
    assertEquals(true, reloaded.autoPilotFullAuto)
  }
}
