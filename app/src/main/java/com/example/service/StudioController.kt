package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.data.AssetSlotStatus
import com.example.data.AudioPipelineProcessor
import com.example.data.ElevenLabsClient
import com.example.data.FfmpegExecutor
import com.example.data.GeminiScriptClient
import com.example.data.LlmProvider
import com.example.data.LogEntry
import com.example.data.LogLevel
import com.example.data.LowStorageWarning
import com.example.data.MediaFileInfo
import com.example.data.MediaHubState
import com.example.data.ModelDetectionState
import com.example.data.PartStatus
import com.example.data.PipelineProgress
import com.example.data.PreviewPlayerTarget
import com.example.data.RenderFilterMode
import com.example.data.ScriptQaResult
import com.example.data.StudioConfig
import com.example.data.StudioConfigRepository
import com.example.data.StudioFileManager
import com.example.data.StudioRunStatus
import com.example.data.SubscriptionInfo
import com.example.data.VideoRenderPipeline
import com.example.domain.ScriptValidator
import com.example.domain.StrictCleanDialogueSanitizer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StudioController(private val appContext: Context) {
  val configRepo = StudioConfigRepository(appContext)
  val fileManager = StudioFileManager(appContext)
  val ffmpegExecutor = FfmpegExecutor(appContext)
  val elevenLabsClient = ElevenLabsClient()
  val geminiScriptClient = GeminiScriptClient(godPromptFile = fileManager.godPromptFile)
  private val audioProcessor = AudioPipelineProcessor(fileManager, elevenLabsClient, ffmpegExecutor)
  private val videoPipeline = VideoRenderPipeline(fileManager, ffmpegExecutor)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var activeJob: Job? = null
  private var activeTaskStartMs: Long = 0L

  private val _config = MutableStateFlow(configRepo.load())
  val config: StateFlow<StudioConfig> = _config.asStateFlow()

  private val _partStatuses = MutableStateFlow<List<PartStatus>>(emptyList())
  val partStatuses: StateFlow<List<PartStatus>> = _partStatuses.asStateFlow()

  private val _mediaHubState = MutableStateFlow(MediaHubState())
  val mediaHubState: StateFlow<MediaHubState> = _mediaHubState.asStateFlow()

  private val _dialogueText = MutableStateFlow("")
  val dialogueText: StateFlow<String> = _dialogueText.asStateFlow()

  private val _qaResult = MutableStateFlow(ScriptValidator.validateScript(""))
  val qaResult: StateFlow<ScriptQaResult> = _qaResult.asStateFlow()

  private val _runStatus = MutableStateFlow(StudioRunStatus.READY)
  val runStatus: StateFlow<StudioRunStatus> = _runStatus.asStateFlow()

  private val _progress = MutableStateFlow(PipelineProgress())
  val progress: StateFlow<PipelineProgress> = _progress.asStateFlow()

  private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
  val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

  private val _subscriptionInfo = MutableStateFlow<SubscriptionInfo?>(null)
  val subscriptionInfo: StateFlow<SubscriptionInfo?> = _subscriptionInfo.asStateFlow()

  private val _isCheckingSubscription = MutableStateFlow(false)
  val isCheckingSubscription: StateFlow<Boolean> = _isCheckingSubscription.asStateFlow()

  private val _previewTarget = MutableStateFlow<PreviewPlayerTarget?>(null)
  val previewTarget: StateFlow<PreviewPlayerTarget?> = _previewTarget.asStateFlow()

  private val _lowStorageWarning = MutableStateFlow<LowStorageWarning?>(null)
  val lowStorageWarning: StateFlow<LowStorageWarning?> = _lowStorageWarning.asStateFlow()

  private val _snackbarEvent = MutableStateFlow<Pair<String, String?>?>(null)
  val snackbarEvent: StateFlow<Pair<String, String?>?> = _snackbarEvent.asStateFlow()

  private val _godPromptPreview = MutableStateFlow("")
  val godPromptPreview: StateFlow<String> = _godPromptPreview.asStateFlow()

  private val _modelDetectionState = MutableStateFlow(ModelDetectionState())
  val modelDetectionState: StateFlow<ModelDetectionState> = _modelDetectionState.asStateFlow()

  private var lastClearedDialogueBackup: String? = null

  init {
    fileManager.ensurePartFolders(_config.value.partCount)
    refreshWorkspaceAndMediaHub()
    refreshGodPromptMetadataOnIo()
    appendLog("Studio initialized. Native FFmpeg ready: ${ffmpegExecutor.areBinariesAvailable()}", LogLevel.INFO)
  }

  private fun refreshGodPromptMetadataOnIo() {
    scope.launch(Dispatchers.IO) {
      val preview = fileManager.readGodPromptPreview()
      _godPromptPreview.value = preview
      if (preview.isEmpty() && _config.value.hasGodPromptFile) {
        updateConfig {
          it.copy(
            godPromptFileName = "",
            godPromptCharCount = 0,
            godPromptWordCount = 0,
            godPromptUpdatedAt = 0L,
          )
        }
      }
    }
  }

  fun importGodPromptFile(uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val result = fileManager.importGodPromptFile(uri)
        _godPromptPreview.value = result.previewText
        updateConfig {
          it.copy(
            godPromptFileName = result.fileName,
            godPromptCharCount = result.charCount,
            godPromptWordCount = result.wordCount,
            godPromptUpdatedAt = result.updatedAt,
          )
        }
        appendLog(
          "Imported God Prompt file: ${result.fileName} (${result.charCount} chars, ${result.wordCount} words).",
          LogLevel.SUCCESS,
        )
        emitSnackbar("God Prompt loaded: ${result.fileName}")
      }.onFailure { err ->
        appendLog("God Prompt import failed: ${err.message}", LogLevel.ERROR)
        emitSnackbar(err.message ?: "Failed to import God Prompt file")
      }
    }
  }

  fun clearGodPromptFile() {
    scope.launch(Dispatchers.IO) {
      fileManager.clearGodPromptFile()
      _godPromptPreview.value = ""
      updateConfig {
        it.copy(
          godPromptFileName = "",
          godPromptCharCount = 0,
          godPromptWordCount = 0,
          godPromptUpdatedAt = 0L,
        )
      }
      appendLog("Cleared master God Prompt file.", LogLevel.INFO)
      emitSnackbar("God Prompt cleared")
    }
  }

  fun clearDetectedModels() {
    _modelDetectionState.value = ModelDetectionState()
  }

  fun detectAvailableModels(
    provider: LlmProvider,
    baseUrl: String,
    apiKey: String,
  ) {
    _modelDetectionState.value =
      ModelDetectionState(
        isLoading = true,
        models =
          _modelDetectionState.value.models
            .takeIf { _modelDetectionState.value.detectedForProvider == provider }
            .orEmpty(),
        errorMessage = null,
        detectedForProvider = provider,
      )
    scope.launch {
      runCatching {
        geminiScriptClient.fetchAvailableModels(
          provider = provider,
          baseUrl = baseUrl,
          apiKey = apiKey,
        )
      }.onSuccess { models ->
        _modelDetectionState.value =
          ModelDetectionState(
            isLoading = false,
            models = models,
            errorMessage =
              if (models.isEmpty()) "No text-generation models returned by ${provider.displayName}."
              else null,
            detectedForProvider = provider,
          )
        appendLog(
          "Auto-detected ${models.size} model(s) from ${provider.displayName}.",
          if (models.isNotEmpty()) LogLevel.SUCCESS else LogLevel.WARNING,
        )
        emitSnackbar(
          if (models.isNotEmpty()) "Detected ${models.size} ${provider.displayName} models"
          else "No models returned by ${provider.displayName}",
        )
      }.onFailure { err ->
        val msg = err.message ?: "Failed to detect models"
        _modelDetectionState.value =
          ModelDetectionState(
            isLoading = false,
            models = emptyList(),
            errorMessage = msg,
            detectedForProvider = provider,
          )
        appendLog("Model detection failed (${provider.displayName}): $msg", LogLevel.ERROR)
        emitSnackbar(msg)
      }
    }
  }

  fun refreshWorkspaceAndMediaHub() {
    val cfg = _config.value
    _partStatuses.value = fileManager.getPartStatuses(cfg.partCount)
    scope.launch(Dispatchers.IO) {
      val stockFiles = fileManager.listStockLoops()
      val stockInfos =
        stockFiles.map { f ->
          MediaFileInfo(
            name = f.name,
            filePath = f.absolutePath,
            sizeBytes = f.length(),
            durationSeconds = ffmpegExecutor.probeDurationSeconds(f),
          )
        }

      val transFile = fileManager.transitionFile
      val transLoaded = transFile.exists() && transFile.length() > 0L
      val transStatus =
        if (transLoaded) {
          AssetSlotStatus(
            isLoaded = true,
            displayName = cfg.transitionOriginalName.ifBlank { transFile.name },
            filePath = transFile.absolutePath,
            durationSeconds = ffmpegExecutor.probeDurationSeconds(transFile),
            sizeBytes = transFile.length(),
          )
        } else {
          AssetSlotStatus(false, "Not Set")
        }

      val bgmFile = fileManager.bgmFile
      val bgmLoaded = bgmFile.exists() && bgmFile.length() > 0L
      val bgmStatus =
        if (bgmLoaded) {
          AssetSlotStatus(
            isLoaded = true,
            displayName = cfg.bgmOriginalName.ifBlank { bgmFile.name },
            filePath = bgmFile.absolutePath,
            durationSeconds = ffmpegExecutor.probeDurationSeconds(bgmFile),
            sizeBytes = bgmFile.length(),
          )
        } else {
          AssetSlotStatus(false, "Not Set")
        }

      val outroFile = fileManager.outroFile
      val outroLoaded = outroFile.exists() && outroFile.length() > 0L
      val outroStatus =
        if (outroLoaded) {
          AssetSlotStatus(
            isLoaded = true,
            displayName = cfg.outroOriginalName.ifBlank { outroFile.name },
            filePath = outroFile.absolutePath,
            durationSeconds = ffmpegExecutor.probeDurationSeconds(outroFile),
            sizeBytes = outroFile.length(),
          )
        } else {
          AssetSlotStatus(false, "Not Set")
        }

      val finishedInfos =
        fileManager.listFinishedOutputs().map { f ->
          MediaFileInfo(
            name = f.name,
            filePath = f.absolutePath,
            sizeBytes = f.length(),
            durationSeconds = ffmpegExecutor.probeDurationSeconds(f),
          )
        }

      _mediaHubState.value =
        MediaHubState(
          stockLoops = stockInfos,
          transitionAsset = transStatus,
          bgmAsset = bgmStatus,
          outroAsset = outroStatus,
          tempCacheBytes = fileManager.getWorkspaceSizeBytes(),
          finishedVideos = finishedInfos,
        )
    }
  }

  fun updateConfig(transform: (StudioConfig) -> StudioConfig) {
    val updated = transform(_config.value)
    _config.value = updated
    configRepo.save(updated)
  }

  fun updatePartFolders(newCount: Int) {
    val clamped = newCount.coerceIn(1, 30)
    fileManager.ensurePartFolders(clamped)
    updateConfig { cfg ->
      cfg.copy(
        partCount = clamped,
        selectedPart = cfg.selectedPart.coerceIn(1, clamped),
      )
    }
    refreshWorkspaceAndMediaHub()
    appendLog("Updated episode workspace to $clamped part folder(s).", LogLevel.SUCCESS)
    emitSnackbar("Updated workspace to $clamped parts")
  }

  fun selectPart(partNumber: Int) {
    val clamped = partNumber.coerceIn(1, _config.value.partCount)
    updateConfig { it.copy(selectedPart = clamped) }
  }

  fun setDialogueText(text: String, runLogQa: Boolean = false) {
    _dialogueText.value = text
    val qa = ScriptValidator.validateScript(text)
    _qaResult.value = qa
    if (runLogQa && text.isNotBlank()) {
      qa.errors.forEach { appendLog("QA ERROR: $it", LogLevel.ERROR) }
      qa.warnings.forEach { appendLog("QA WARN: $it", LogLevel.WARNING) }
      if (qa.isValid) {
        appendLog("QA passed (${qa.charCount} speech chars, ${qa.sfxCount} SFX, ${qa.silenceCount} silence).", LogLevel.SUCCESS)
      }
    }
  }

  fun clearDialogueWithUndo() {
    lastClearedDialogueBackup = _dialogueText.value
    setDialogueText("")
    _snackbarEvent.value = "Dialogue cleared" to "Undo"
  }

  fun replaceDialogueWithUndo(
    newText: String,
    runLogQa: Boolean = true,
    snackbarMessage: String,
  ) {
    lastClearedDialogueBackup = _dialogueText.value
    setDialogueText(newText, runLogQa = runLogQa)
    _snackbarEvent.value = snackbarMessage to "Undo"
  }

  fun undoDialogueRestore() {
    val backup = lastClearedDialogueBackup ?: return
    setDialogueText(backup, runLogQa = true)
    lastClearedDialogueBackup = null
  }

  fun clearSnackbar() {
    _snackbarEvent.value = null
  }

  fun emitSnackbar(message: String, actionLabel: String? = null) {
    _snackbarEvent.value = message to actionLabel
  }

  fun appendLog(message: String, level: LogLevel = LogLevel.INFO) {
    val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    _logs.update { current -> (current + LogEntry(ts, message, level)).takeLast(300) }
  }

  fun clearLogs() {
    _logs.value = emptyList()
  }

  fun sanitizeDialogue() {
    val current = _dialogueText.value
    if (current.isBlank()) {
      emitSnackbar("Dialogue editor is empty")
      return
    }
    val cleaned = StrictCleanDialogueSanitizer.sanitize(current)
    replaceDialogueWithUndo(
      newText = cleaned,
      runLogQa = true,
      snackbarMessage = "Sanitized dialogue (${cleaned.length} chars)",
    )
  }

  fun runGeminiAutoPilot(
    topicOrDraft: String,
    generateFromTopic: Boolean,
  ) {
    if (activeJob?.isActive == true) {
      emitSnackbar("Another studio task is currently running.")
      return
    }
    val inputText = topicOrDraft.trim()
    if (inputText.isEmpty()) {
      emitSnackbar(
        if (generateFromTopic) "Enter a story topic or outline for Gemini Auto-Pilot."
        else "Paste or type a script draft before running Self-Audit."
      )
      return
    }

    val cfg = _config.value
    val targetPart = cfg.selectedPart
    val taskTitle =
      if (generateFromTopic) "${cfg.llmProvider.displayName} Auto-Pilot (Part $targetPart)"
      else "${cfg.llmProvider.displayName} Self-Audit (Part $targetPart)"

    launchForegroundTask(taskTitle) {
      val outcome =
        geminiScriptClient.runAutoPilotAndAudit(
          topicOrDraft = inputText,
          partNumber = targetPart,
          totalParts = cfg.partCount,
          generateFromTopic = generateFromTopic,
          provider = cfg.llmProvider,
          baseUrl = cfg.llmBaseUrl,
          apiKey = cfg.llmApiKey,
          model = cfg.llmModel,
          onProgress = { pct, step ->
            _progress.value =
              PipelineProgress(
                isRunning = true,
                taskTitle = taskTitle,
                progressPercent = pct,
                stepText = step,
                etaText = computeDynamicEta(pct),
                elapsedText = formatElapsedFromStart(),
              )
          },
          onLog = ::appendLog,
        )
      replaceDialogueWithUndo(
        newText = outcome.cleanScript,
        runLogQa = true,
        snackbarMessage =
          "Part $targetPart script audited (${outcome.qaResult.charCount} chars, ${outcome.repairPassesUsed} repair(s))",
      )
    }
  }

  fun runAutoPilotEpisode(
    topicPrompt: String,
    fullAuto: Boolean,
  ) {
    if (activeJob?.isActive == true) {
      emitSnackbar("Another studio task is currently running.")
      return
    }
    val cleanTopic = topicPrompt.trim()
    if (cleanTopic.isEmpty()) {
      emitSnackbar("Enter an Episode Topic / Prompt first.")
      return
    }

    val cfg = _config.value
    if (fullAuto && cfg.apiKey.isBlank()) {
      _runStatus.value = StudioRunStatus.ERROR
      appendLog("ElevenLabs API Key missing for Full Auto. Set it in API & Settings or switch to Scripts Only.", LogLevel.ERROR)
      emitSnackbar("Missing ElevenLabs API Key for Full Auto")
      return
    }

    updateConfig { it.copy(autoPilotFullAuto = fullAuto) }
    val totalParts = cfg.partCount.coerceIn(1, 30)
    val taskTitle =
      if (fullAuto) "Full Auto-Pilot ($totalParts Parts)"
      else "Scripts Auto-Pilot ($totalParts Parts)"

    launchForegroundTask(taskTitle) {
      fileManager.ensurePartFolders(totalParts)
      var priorTail = ""
      var firstOrLatestScript = ""

      val scriptStageWeight = if (fullAuto) 35.0 else 100.0
      val perPartSpan = scriptStageWeight / totalParts.toDouble()

      for (partNum in 1..totalParts) {
        val partStart = (partNum - 1) * perPartSpan
        val scriptEnd =
          if (fullAuto) partStart + perPartSpan * 0.55
          else partStart + perPartSpan

        val outcome =
          geminiScriptClient.runAutoPilotAndAudit(
            topicOrDraft = cleanTopic,
            partNumber = partNum,
            totalParts = totalParts,
            generateFromTopic = true,
            provider = cfg.llmProvider,
            baseUrl = cfg.llmBaseUrl,
            apiKey = cfg.llmApiKey,
            model = cfg.llmModel,
            priorPartTail = priorTail,
            onProgress = { subPct, step ->
              val overall = (partStart + (scriptEnd - partStart) * (subPct / 100.0)).toInt().coerceIn(1, 99)
              _progress.value =
                PipelineProgress(
                  isRunning = true,
                  taskTitle = taskTitle,
                  progressPercent = overall,
                  stepText = step,
                  etaText = computeDynamicEta(overall),
                  elapsedText = formatElapsedFromStart(),
                )
            },
            onLog = ::appendLog,
          )

        val partDir = fileManager.getPartDir(partNum).apply { mkdirs() }
        val transcriptFile = File(partDir, "${partNum}_transcript.txt")
        transcriptFile.writeText(outcome.qaResult.cleanedTranscript, Charsets.UTF_8)
        appendLog("Saved Part $partNum clean transcript (${outcome.qaResult.charCount} chars).", LogLevel.INFO)

        priorTail = outcome.qaResult.cleanedTranscript.takeLast(500)
        if (firstOrLatestScript.isEmpty() || partNum == cfg.selectedPart) {
          firstOrLatestScript = outcome.cleanScript
        }

        if (fullAuto) {
          val ttsStart = scriptEnd
          val ttsEnd = partStart + perPartSpan
          audioProcessor.processPartVoice(
            partNumber = partNum,
            qaResult = outcome.qaResult,
            config = cfg,
            onProgress = { subPct, step, _ ->
              val overall = (ttsStart + (ttsEnd - ttsStart) * (subPct / 100.0)).toInt().coerceIn(1, 99)
              _progress.value =
                PipelineProgress(
                  isRunning = true,
                  taskTitle = taskTitle,
                  progressPercent = overall,
                  stepText = step,
                  etaText = computeDynamicEta(overall),
                  elapsedText = formatElapsedFromStart(),
                )
            },
            onLog = ::appendLog,
          )
        }
        refreshWorkspaceAndMediaHub()
      }

      if (firstOrLatestScript.isNotBlank()) {
        replaceDialogueWithUndo(
          newText = firstOrLatestScript,
          runLogQa = true,
          snackbarMessage =
            if (fullAuto) "Scripts + voices ready; starting video render..."
            else "Auto-Pilot generated $totalParts script(s)",
        )
      }

      if (fullAuto) {
        val finalVideo =
          videoPipeline.renderFullDocumentary(
            config = cfg,
            onProgress = { subPct, step, _ ->
              val overall = (35.0 + 65.0 * (subPct / 100.0)).toInt().coerceIn(35, 100)
              _progress.value =
                PipelineProgress(
                  isRunning = true,
                  taskTitle = taskTitle,
                  progressPercent = overall,
                  stepText = step,
                  etaText = computeDynamicEta(overall),
                  elapsedText = formatElapsedFromStart(),
                )
            },
            onLog = ::appendLog,
          )
        refreshWorkspaceAndMediaHub()
        emitSnackbar("Full Auto-Pilot complete: ${finalVideo.name}")
      } else {
        refreshWorkspaceAndMediaHub()
        emitSnackbar("Auto-Pilot generated $totalParts part script(s)")
      }
    }
  }

  private fun computeDynamicEta(pct: Int): String {
    if (pct >= 100) return "Done"
    if (pct < 3 || activeTaskStartMs <= 0L) return "Calculating..."
    val elapsedSec = (System.currentTimeMillis() - activeTaskStartMs) / 1000.0
    if (elapsedSec < 0.5) return "Calculating..."
    val remainingSec = (elapsedSec * (100 - pct) / pct.toDouble()).toInt().coerceAtLeast(1)
    val mins = remainingSec / 60
    val rem = remainingSec % 60
    return if (mins > 0) "${mins}m ${rem}s" else "${rem}s"
  }

  fun startDownloadVoice() {
    if (activeJob?.isActive == true) {
      emitSnackbar("Another studio task is currently running.")
      return
    }
    val rawText = _dialogueText.value
    val sanitizedText = StrictCleanDialogueSanitizer.sanitize(rawText)
    val qa = ScriptValidator.validateScript(sanitizedText)
    _qaResult.value = qa
    qa.errors.forEach { appendLog("QA ERROR: $it", LogLevel.ERROR) }
    qa.warnings.forEach { appendLog("QA WARN: $it", LogLevel.WARNING) }

    if (!qa.isValid || qa.hasFatalDigits) {
      _runStatus.value = StudioRunStatus.ERROR
      emitSnackbar(qa.errors.firstOrNull() ?: "Script QA failed.")
      return
    }
    if (sanitizedText != rawText.trim()) {
      replaceDialogueWithUndo(
        newText = sanitizedText,
        runLogQa = false,
        snackbarMessage = "Cleaned non-dialogue tags before TTS",
      )
    }
    val cfg = _config.value
    if (cfg.apiKey.isBlank()) {
      _runStatus.value = StudioRunStatus.ERROR
      appendLog("ElevenLabs API Key missing. Go to API & Settings tab.", LogLevel.ERROR)
      emitSnackbar("Missing ElevenLabs API Key in API & Settings")
      return
    }

    val targetPart = cfg.selectedPart
    launchForegroundTask("Voice Download (Part $targetPart)") {
      audioProcessor.processPartVoice(
        partNumber = targetPart,
        qaResult = qa,
        config = cfg,
        onProgress = { pct, step, eta ->
          _progress.value =
            PipelineProgress(
              isRunning = true,
              taskTitle = "Downloading Voice (Part $targetPart)",
              progressPercent = pct,
              stepText = step,
              etaText = eta,
              elapsedText = formatElapsedFromStart(),
            )
        },
        onLog = ::appendLog,
      )

      refreshWorkspaceAndMediaHub()
      if (_config.value.autoNextPartEnabled) {
        setDialogueText("")
        val nextPart = (targetPart + 1).coerceAtMost(_config.value.partCount)
        if (nextPart != targetPart) {
          selectPart(nextPart)
          appendLog("Auto-Next Part: cleared dialogue and switched to Part $nextPart.", LogLevel.INFO)
        } else {
          appendLog("Auto-Next Part: cleared dialogue on final Part $targetPart.", LogLevel.INFO)
        }
      }
      emitSnackbar("Part $targetPart voice ready")
    }
  }

  fun startFilterPreview() {
    if (activeJob?.isActive == true) {
      emitSnackbar("Another studio task is currently running.")
      return
    }
    val cfg = _config.value
    launchForegroundTask("3s Filter Preview (${cfg.filterMode.displayName})") {
      val previewFile =
        videoPipeline.renderFilterPreview(
          partNumber = cfg.selectedPart,
          filterMode = cfg.filterMode,
          onProgress = { pct, step, eta ->
            _progress.value =
              PipelineProgress(
                isRunning = true,
                taskTitle = "3s Filter Preview",
                progressPercent = pct,
                stepText = step,
                etaText = eta,
                elapsedText = formatElapsedFromStart(),
              )
          },
          onLog = ::appendLog,
        )
      _previewTarget.value =
        PreviewPlayerTarget(
          title = "3s Preview — ${cfg.filterMode.displayName}",
          filePath = previewFile.absolutePath,
        )
    }
  }

  fun requestSmartRender(ignoreLowStorage: Boolean = false) {
    if (activeJob?.isActive == true) {
      emitSnackbar("Another studio task is currently running.")
      return
    }
    if (!ignoreLowStorage) {
      val freeGb = fileManager.getFreeStorageGb()
      if (freeGb < 3.0) {
        _lowStorageWarning.value = LowStorageWarning(freeGb)
        return
      }
    }
    _lowStorageWarning.value = null
    val cfg = _config.value
    launchForegroundTask("1-Click Smart Render") {
      val finalVideo =
        videoPipeline.renderFullDocumentary(
          config = cfg,
          onProgress = { pct, step, eta ->
            _progress.value =
              PipelineProgress(
                isRunning = true,
                taskTitle = "Smart Render",
                progressPercent = pct,
                stepText = step,
                etaText = eta,
                elapsedText = formatElapsedFromStart(),
              )
          },
          onLog = ::appendLog,
        )
      refreshWorkspaceAndMediaHub()
      emitSnackbar("Render complete: ${finalVideo.name}")
    }
  }

  fun dismissLowStorageWarning() {
    _lowStorageWarning.value = null
  }

  fun openPreviewPlayer(title: String, filePath: String) {
    _previewTarget.value = PreviewPlayerTarget(title, filePath)
  }

  fun closePreviewPlayer() {
    _previewTarget.value = null
  }

  fun cancelActiveTask() {
    val job = activeJob
    if (job == null || !job.isActive) return
    appendLog("Cancellation requested — halting FFmpeg & active pipeline...", LogLevel.WARNING)
    ffmpegExecutor.cancelCurrentProcess()
    job.cancel()
    _runStatus.value = StudioRunStatus.CANCELLED
    _progress.value =
      PipelineProgress(
        isRunning = false,
        taskTitle = "Cancelled",
        progressPercent = 0,
        stepText = "Cancelled by user",
        etaText = "",
      )
    stopRenderService()
    refreshWorkspaceAndMediaHub()
    emitSnackbar("Task cancelled")
  }

  private fun launchForegroundTask(
    title: String,
    block: suspend () -> Unit,
  ) {
    ffmpegExecutor.resetCancellationFlag()
    activeTaskStartMs = System.currentTimeMillis()
    _runStatus.value = StudioRunStatus.BUSY
    _progress.value =
      PipelineProgress(
        isRunning = true,
        taskTitle = title,
        progressPercent = 1,
        stepText = "Starting $title...",
        etaText = "Calculating...",
        elapsedText = "0s",
      )
    startRenderService()

    activeJob =
      scope.launch {
        try {
          block()
          _runStatus.value = StudioRunStatus.DONE
          _progress.update { it.copy(isRunning = false, progressPercent = 100) }
        } catch (_: CancellationException) {
          _runStatus.value = StudioRunStatus.CANCELLED
          _progress.value =
            PipelineProgress(
              isRunning = false,
              taskTitle = "Cancelled",
              progressPercent = 0,
              stepText = "Cancelled",
              etaText = "",
            )
          appendLog("Task cancelled.", LogLevel.WARNING)
        } catch (e: Exception) {
          val msg = e.message ?: "Unknown pipeline error"
          _runStatus.value = StudioRunStatus.ERROR
          _progress.update {
            it.copy(isRunning = false, stepText = msg, etaText = "")
          }
          appendLog("ERROR: $msg", LogLevel.ERROR)
          emitSnackbar(msg)
        } finally {
          stopRenderService()
          refreshWorkspaceAndMediaHub()
        }
      }
  }

  private fun startRenderService() {
    val intent = Intent(appContext, StudioRenderService::class.java)
    ContextCompat.startForegroundService(appContext, intent)
  }

  private fun stopRenderService() {
    runCatching { appContext.stopService(Intent(appContext, StudioRenderService::class.java)) }
  }

  private fun formatElapsedFromStart(): String {
    if (activeTaskStartMs <= 0L) return "0s"
    val totalSec = ((System.currentTimeMillis() - activeTaskStartMs) / 1000L).toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val rem = totalSec % 60
    return if (mins > 0) "${mins}m ${rem}s" else "${rem}s"
  }

  fun checkSubscriptionCredits() {
    val key = _config.value.apiKey.trim()
    if (key.isEmpty()) {
      _subscriptionInfo.value = SubscriptionInfo(errorMessage = "Enter and save an API Key first.")
      return
    }
    scope.launch {
      _isCheckingSubscription.value = true
      val info = elevenLabsClient.fetchSubscriptionInfo(key)
      _subscriptionInfo.value = info
      _isCheckingSubscription.value = false
      if (info.errorMessage != null) {
        appendLog("Credits check failed: ${info.errorMessage}", LogLevel.ERROR)
      } else {
        appendLog(
          "ElevenLabs Credits: ${info.remainingCharacters} remaining (${info.characterCount}/${info.characterLimit}, tier: ${info.tier}).",
          LogLevel.SUCCESS,
        )
      }
    }
  }

  // Per-part and Media Hub operations
  fun importPartVideo(partNum: Int, uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val file = fileManager.copyPartVideoOverride(partNum, uri)
        appendLog("Part $partNum video override saved: ${file.name}", LogLevel.SUCCESS)
        refreshWorkspaceAndMediaHub()
        emitSnackbar("Part $partNum video loaded")
      }.onFailure {
        appendLog("Failed to import Part $partNum video: ${it.message}", LogLevel.ERROR)
        emitSnackbar(it.message ?: "Failed to import video")
      }
    }
  }

  fun importPartAudio(partNum: Int, uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val file = fileManager.copyPartAudioOverride(partNum, uri)
        appendLog("Part $partNum audio override saved: ${file.name}", LogLevel.SUCCESS)
        refreshWorkspaceAndMediaHub()
        emitSnackbar("Part $partNum audio loaded")
      }.onFailure {
        appendLog("Failed to import Part $partNum audio: ${it.message}", LogLevel.ERROR)
        emitSnackbar(it.message ?: "Failed to import audio")
      }
    }
  }

  fun importDialogueTxt(uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val text = fileManager.readUtf8Text(uri)
        launch(Dispatchers.Main) {
          setDialogueText(text, runLogQa = true)
          emitSnackbar("Loaded script (${text.length} chars)")
        }
      }.onFailure {
        appendLog("Failed to load .txt script: ${it.message}", LogLevel.ERROR)
        emitSnackbar(it.message ?: "Failed to read .txt")
      }
    }
  }

  fun clearSelectedPart(partNum: Int) {
    fileManager.clearPart(partNum)
    refreshWorkspaceAndMediaHub()
    appendLog("Cleared Part $partNum audio/video overrides.", LogLevel.INFO)
    emitSnackbar("Part $partNum cleared")
  }

  fun clearWorkspaceTotalZero() {
    fileManager.clearWorkspaceTotalZero(_config.value.partCount)
    refreshWorkspaceAndMediaHub()
    appendLog("TOTAL ZERO: Wiped episode_workspace/ and workspace/ (stock_loops & assets preserved).", LogLevel.WARNING)
    emitSnackbar("Workspace cleared (Stock & Assets kept)")
  }

  fun addStockLoops(uris: List<Uri>) {
    if (uris.isEmpty()) return
    scope.launch(Dispatchers.IO) {
      var added = 0
      uris.forEach { uri ->
        runCatching {
          val file = fileManager.addStockLoop(uri)
          added++
          appendLog("Added stock loop: ${file.name}", LogLevel.SUCCESS)
        }.onFailure {
          appendLog("Failed to add stock loop: ${it.message}", LogLevel.ERROR)
        }
      }
      refreshWorkspaceAndMediaHub()
      emitSnackbar("Added $added stock loop(s)")
    }
  }

  fun deleteStockLoop(filePath: String) {
    val file = File(filePath)
    if (file.delete()) {
      appendLog("Deleted stock loop: ${file.name}", LogLevel.INFO)
      refreshWorkspaceAndMediaHub()
    }
  }

  fun setTransitionBroll(uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val origName = fileManager.setTransitionAsset(uri)
        updateConfig { it.copy(transitionOriginalName = origName) }
        appendLog("Loaded Next Page transition B-roll: $origName", LogLevel.SUCCESS)
        refreshWorkspaceAndMediaHub()
        emitSnackbar("Transition B-roll loaded")
      }.onFailure {
        appendLog("Failed to load transition B-roll: ${it.message}", LogLevel.ERROR)
      }
    }
  }

  fun removeTransitionBroll() {
    fileManager.removeTransitionAsset()
    updateConfig { it.copy(transitionOriginalName = "") }
    appendLog("Removed Next Page transition B-roll.", LogLevel.INFO)
    refreshWorkspaceAndMediaHub()
  }

  fun setBgm(uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val origName = fileManager.setBgmAsset(uri)
        updateConfig { it.copy(bgmOriginalName = origName) }
        appendLog("Loaded Background Music: $origName", LogLevel.SUCCESS)
        refreshWorkspaceAndMediaHub()
        emitSnackbar("BGM loaded")
      }.onFailure {
        appendLog("Failed to load BGM: ${it.message}", LogLevel.ERROR)
      }
    }
  }

  fun removeBgm() {
    fileManager.removeBgmAsset()
    updateConfig { it.copy(bgmOriginalName = "") }
    appendLog("Removed Background Music.", LogLevel.INFO)
    refreshWorkspaceAndMediaHub()
  }

  fun setOutro(uri: Uri) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val origName = fileManager.setOutroAsset(uri)
        updateConfig { it.copy(outroOriginalName = origName) }
        appendLog("Loaded Outro Vanity Card: $origName", LogLevel.SUCCESS)
        refreshWorkspaceAndMediaHub()
        emitSnackbar("Outro card loaded")
      }.onFailure {
        appendLog("Failed to load Outro card: ${it.message}", LogLevel.ERROR)
      }
    }
  }

  fun removeOutro() {
    fileManager.removeOutroAsset()
    updateConfig { it.copy(outroOriginalName = "") }
    appendLog("Removed Outro Vanity Card.", LogLevel.INFO)
    refreshWorkspaceAndMediaHub()
  }

  fun cleanTempCache() {
    fileManager.cleanTempWorkspaceOnly()
    appendLog("Cleaned temp workspace/ cache.", LogLevel.INFO)
    refreshWorkspaceAndMediaHub()
    emitSnackbar("Temp cache cleaned")
  }

  fun saveFinishedVideoToGallery(filePath: String) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        val file = File(filePath)
        fileManager.saveVideoToGallery(file)
        appendLog("Saved to Gallery: Movies/TheMissingPage/${file.name}", LogLevel.SUCCESS)
        emitSnackbar("Saved to Movies/TheMissingPage")
      }.onFailure {
        appendLog("Gallery save failed: ${it.message}", LogLevel.ERROR)
        emitSnackbar(it.message ?: "Save failed")
      }
    }
  }

  fun deleteFinishedVideo(filePath: String) {
    val file = File(filePath)
    if (file.delete()) {
      appendLog("Deleted finished output: ${file.name}", LogLevel.INFO)
      refreshWorkspaceAndMediaHub()
    }
  }
}
