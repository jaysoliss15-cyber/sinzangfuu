package com.example.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.StudioApplication
import com.example.data.LogLevel
import com.example.data.RenderFilterMode
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudioViewModel(application: Application) : AndroidViewModel(application) {
  val controller = (application as StudioApplication).studioController

  val config = controller.config
  val partStatuses = controller.partStatuses
  val mediaHubState = controller.mediaHubState
  val dialogueText = controller.dialogueText
  val qaResult = controller.qaResult
  val runStatus = controller.runStatus
  val progress = controller.progress
  val logs = controller.logs
  val subscriptionInfo = controller.subscriptionInfo
  val isCheckingSubscription = controller.isCheckingSubscription
  val previewTarget = controller.previewTarget
  val lowStorageWarning = controller.lowStorageWarning
  val snackbarEvent = controller.snackbarEvent

  private var audioPlayer: MediaPlayer? = null
  private val _playingAudioPart = MutableStateFlow<Int?>(null)
  val playingAudioPart: StateFlow<Int?> = _playingAudioPart.asStateFlow()

  fun updatePartFolders(count: Int) = controller.updatePartFolders(count)
  fun selectPart(partNumber: Int) = controller.selectPart(partNumber)
  fun onDialogueChanged(text: String) = controller.setDialogueText(text, runLogQa = false)
  fun clearDialogue() = controller.clearDialogueWithUndo()
  fun undoDialogueClear() = controller.undoDialogueRestore()
  fun clearSnackbar() = controller.clearSnackbar()

  fun pasteFromClipboard() {
    val clipText = readClipboardPlainText()
    if (clipText.isNullOrBlank()) {
      controller.emitSnackbar("Clipboard is empty")
      return
    }
    controller.setDialogueText(clipText, runLogQa = true)
    controller.updateConfig { it.copy(lastAutoPastedHash = sha256(clipText)) }
    controller.emitSnackbar("Pasted ${String.format("%,d", clipText.length)} chars")
  }

  fun checkAutoPasteOnResume() {
    val cfg = config.value
    if (!cfg.autoPasteEnabled) return
    val clipText = readClipboardPlainText()?.takeIf { it.isNotBlank() } ?: return
    val hash = sha256(clipText)
    if (hash == cfg.lastAutoPastedHash) return

    controller.updateConfig { it.copy(lastAutoPastedHash = hash) }
    controller.appendLog("Auto-Pasted ${clipText.length} chars from clipboard.", LogLevel.INFO)
    controller.replaceDialogueWithUndo(
      newText = clipText,
      runLogQa = true,
      snackbarMessage = "Auto-pasted ${String.format("%,d", clipText.length)} chars",
    )
  }

  fun setAutoPasteEnabled(enabled: Boolean) {
    controller.updateConfig { it.copy(autoPasteEnabled = enabled) }
  }

  fun setAutoNextPartEnabled(enabled: Boolean) {
    controller.updateConfig { it.copy(autoNextPartEnabled = enabled) }
  }

  fun setFilterMode(mode: RenderFilterMode) {
    controller.updateConfig { it.copy(filterMode = mode) }
  }

  fun setTurboModeEnabled(enabled: Boolean) {
    controller.updateConfig { it.copy(turboModeEnabled = enabled) }
  }

  fun setSmartResumeEnabled(enabled: Boolean) {
    controller.updateConfig { it.copy(smartResumeEnabled = enabled) }
  }

  fun saveTransitionsAfter(value: String) {
    val cleaned = value.trim()
    controller.updateConfig { it.copy(transitionsAfter = cleaned) }
    controller.appendLog("Saved Transitions After setting: \"$cleaned\".", LogLevel.SUCCESS)
    controller.emitSnackbar("Saved transitions setting")
  }

  fun setAutoCleanAfterExport(enabled: Boolean) {
    controller.updateConfig { it.copy(autoCleanAfterExport = enabled) }
  }

  fun setAutoPilotFullAuto(fullAuto: Boolean) {
    controller.updateConfig { it.copy(autoPilotFullAuto = fullAuto) }
  }

  fun saveApiSettings(
    apiKey: String,
    voiceId: String,
    modelId: String,
    sfxModelId: String,
    geminiApiKey: String,
    geminiModel: String,
    scriptWriterGodPrompt: String,
  ) {
    controller.updateConfig {
      it.copy(
        apiKey = apiKey.trim(),
        voiceId = voiceId.trim().ifEmpty { com.example.data.StudioConfig.DEFAULT_VOICE_ID },
        modelId = modelId.trim().ifEmpty { com.example.data.StudioConfig.DEFAULT_MODEL_ID },
        sfxModelId = sfxModelId.trim().ifEmpty { com.example.data.StudioConfig.DEFAULT_SFX_MODEL_ID },
        geminiApiKey = geminiApiKey.trim(),
        geminiModel = geminiModel.trim().ifEmpty { com.example.data.StudioConfig.DEFAULT_GEMINI_MODEL },
        scriptWriterGodPrompt = scriptWriterGodPrompt,
      )
    }
    controller.appendLog("Saved ElevenLabs & Gemini Auto-Pilot API settings.", LogLevel.SUCCESS)
    controller.emitSnackbar("All API settings saved")
  }

  fun readClipboard(): String? = readClipboardPlainText()

  fun sanitizeDialogue() = controller.sanitizeDialogue()
  fun runGeminiAutoPilot(topicOrOutline: String) =
    controller.runGeminiAutoPilot(topicOrDraft = topicOrOutline, generateFromTopic = true)
  fun runAutoPilotEpisode(topicPrompt: String, fullAuto: Boolean) =
    controller.runAutoPilotEpisode(topicPrompt = topicPrompt, fullAuto = fullAuto)
  fun runGeminiSelfAudit() =
    controller.runGeminiAutoPilot(topicOrDraft = dialogueText.value, generateFromTopic = false)
  fun startDownloadVoice() = controller.startDownloadVoice()
  fun startFilterPreview() = controller.startFilterPreview()
  fun startSmartRender(ignoreLowStorage: Boolean = false) =
    controller.requestSmartRender(ignoreLowStorage)

  fun dismissLowStorageWarning() = controller.dismissLowStorageWarning()
  fun cancelActiveTask() = controller.cancelActiveTask()
  fun clearLogs() = controller.clearLogs()
  fun checkSubscriptionCredits() = controller.checkSubscriptionCredits()

  fun importPartVideo(partNumber: Int, uri: Uri) = controller.importPartVideo(partNumber, uri)
  fun importPartAudio(partNumber: Int, uri: Uri) = controller.importPartAudio(partNumber, uri)
  fun importDialogueTxt(uri: Uri) = controller.importDialogueTxt(uri)
  fun clearPart(partNumber: Int) {
    stopAudioPreview()
    controller.clearSelectedPart(partNumber)
  }

  fun clearWorkspaceTotalZero() {
    stopAudioPreview()
    controller.clearWorkspaceTotalZero()
  }

  fun togglePlayPartAudio(partNumber: Int) {
    if (_playingAudioPart.value == partNumber) {
      stopAudioPreview()
      return
    }
    stopAudioPreview()
    val audioFile = controller.fileManager.findPartAudioFile(partNumber)
    if (audioFile == null || !audioFile.exists()) {
      controller.emitSnackbar("No audio found for Part $partNumber")
      return
    }
    runCatching {
      val player =
        MediaPlayer().apply {
          setDataSource(audioFile.absolutePath)
          setOnCompletionListener { stopAudioPreview() }
          setOnErrorListener { _, _, _ ->
            stopAudioPreview()
            true
          }
          prepare()
          start()
        }
      audioPlayer = player
      _playingAudioPart.value = partNumber
    }.onFailure {
      stopAudioPreview()
      controller.emitSnackbar("Audio playback failed: ${it.message}")
    }
  }

  fun stopAudioPreview() {
    audioPlayer?.let { player ->
      runCatching {
        if (player.isPlaying) player.stop()
        player.release()
      }
    }
    audioPlayer = null
    _playingAudioPart.value = null
  }

  fun addStockLoops(uris: List<Uri>) = controller.addStockLoops(uris)
  fun deleteStockLoop(filePath: String) = controller.deleteStockLoop(filePath)
  fun setTransitionBroll(uri: Uri) = controller.setTransitionBroll(uri)
  fun removeTransitionBroll() = controller.removeTransitionBroll()
  fun setBgm(uri: Uri) = controller.setBgm(uri)
  fun removeBgm() = controller.removeBgm()
  fun setOutro(uri: Uri) = controller.setOutro(uri)
  fun removeOutro() = controller.removeOutro()
  fun cleanTempCache() = controller.cleanTempCache()
  fun playFinishedVideo(name: String, filePath: String) =
    controller.openPreviewPlayer(name, filePath)
  fun closePreviewPlayer() = controller.closePreviewPlayer()
  fun saveFinishedVideoToGallery(filePath: String) = controller.saveFinishedVideoToGallery(filePath)
  fun deleteFinishedVideo(filePath: String) = controller.deleteFinishedVideo(filePath)
  fun createShareIntent(filePath: String) =
    controller.fileManager.createShareIntent(java.io.File(filePath))

  private fun readClipboardPlainText(): String? {
    val cm =
      getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(getApplication())?.toString()
  }

  private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  override fun onCleared() {
    stopAudioPreview()
    super.onCleared()
  }
}
