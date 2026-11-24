package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LogEntry
import com.example.data.LowStorageWarning
import com.example.data.PartStatus
import com.example.data.PipelineProgress
import com.example.data.RenderFilterMode
import com.example.data.ScriptQaResult
import com.example.data.StudioConfig
import com.example.ui.components.DialogueEditorCard
import com.example.ui.components.PartWorkspaceCard
import com.example.ui.components.ProgressAndLogCard
import com.example.ui.components.RenderControlsCard
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioTextPrimary
import java.util.Locale

/**
 * Isolated Studio tab route so high-frequency progress/log emissions only recompose StudioScreen.
 */
@Composable
fun StudioTabRoute(
  viewModel: StudioViewModel,
  onPickPartVideo: (Int) -> Unit,
  onPickPartAudio: (Int) -> Unit,
  onLoadTxt: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val config by viewModel.config.collectAsStateWithLifecycle()
  val partStatuses by viewModel.partStatuses.collectAsStateWithLifecycle()
  val dialogueText by viewModel.dialogueText.collectAsStateWithLifecycle()
  val qaResult by viewModel.qaResult.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val logs by viewModel.logs.collectAsStateWithLifecycle()
  val playingAudioPart by viewModel.playingAudioPart.collectAsStateWithLifecycle()
  val lowStorageWarning by viewModel.lowStorageWarning.collectAsStateWithLifecycle()

  StudioScreen(
    config = config,
    partStatuses = partStatuses,
    dialogueText = dialogueText,
    qaResult = qaResult,
    progress = progress,
    logs = logs,
    playingAudioPart = playingAudioPart,
    lowStorageWarning = lowStorageWarning,
    onUpdatePartFolders = { viewModel.updatePartFolders(it) },
    onSelectPart = { viewModel.selectPart(it) },
    onPickPartVideo = onPickPartVideo,
    onPickPartAudio = onPickPartAudio,
    onLoadTxt = onLoadTxt,
    onTogglePlayAudio = { viewModel.togglePlayPartAudio(it) },
    onClearPart = { viewModel.clearPart(it) },
    onDialogueChange = { viewModel.onDialogueChanged(it) },
    onClearDialogue = { viewModel.clearDialogue() },
    onPasteDialogue = { viewModel.pasteFromClipboard() },
    onSanitizeDialogue = { viewModel.sanitizeDialogue() },
    onAutoPasteToggle = { viewModel.setAutoPasteEnabled(it) },
    onAutoNextToggle = { viewModel.setAutoNextPartEnabled(it) },
    onDownloadVoice = { viewModel.startDownloadVoice() },
    onFilterSelected = { viewModel.setFilterMode(it) },
    onPreviewFilter = { viewModel.startFilterPreview() },
    onTurboToggle = { viewModel.setTurboModeEnabled(it) },
    onSmartResumeToggle = { viewModel.setSmartResumeEnabled(it) },
    onSaveTransitions = { viewModel.saveTransitionsAfter(it) },
    onAutoCleanToggle = { viewModel.setAutoCleanAfterExport(it) },
    onClearWorkspaceTotalZero = { viewModel.clearWorkspaceTotalZero() },
    onStartSmartRender = { viewModel.startSmartRender(ignoreLowStorage = false) },
    onConfirmLowStorageRender = { viewModel.startSmartRender(ignoreLowStorage = true) },
    onDismissLowStorageWarning = { viewModel.dismissLowStorageWarning() },
    onCancelActiveTask = { viewModel.cancelActiveTask() },
    onClearLogs = { viewModel.clearLogs() },
    modifier = modifier,
  )
}

@Composable
fun StudioScreen(
  config: StudioConfig,
  partStatuses: List<PartStatus>,
  dialogueText: String,
  qaResult: ScriptQaResult,
  progress: PipelineProgress,
  logs: List<LogEntry>,
  playingAudioPart: Int?,
  lowStorageWarning: LowStorageWarning?,
  onUpdatePartFolders: (Int) -> Unit,
  onSelectPart: (Int) -> Unit,
  onPickPartVideo: (Int) -> Unit,
  onPickPartAudio: (Int) -> Unit,
  onLoadTxt: () -> Unit,
  onTogglePlayAudio: (Int) -> Unit,
  onClearPart: (Int) -> Unit,
  onDialogueChange: (String) -> Unit,
  onClearDialogue: () -> Unit,
  onPasteDialogue: () -> Unit,
  onSanitizeDialogue: () -> Unit,
  onAutoPasteToggle: (Boolean) -> Unit,
  onAutoNextToggle: (Boolean) -> Unit,
  onDownloadVoice: () -> Unit,
  onFilterSelected: (RenderFilterMode) -> Unit,
  onPreviewFilter: () -> Unit,
  onTurboToggle: (Boolean) -> Unit,
  onSmartResumeToggle: (Boolean) -> Unit,
  onSaveTransitions: (String) -> Unit,
  onAutoCleanToggle: (Boolean) -> Unit,
  onClearWorkspaceTotalZero: () -> Unit,
  onStartSmartRender: () -> Unit,
  onConfirmLowStorageRender: () -> Unit,
  onDismissLowStorageWarning: () -> Unit,
  onCancelActiveTask: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showTotalZeroConfirm by remember { mutableStateOf(false) }

  if (showTotalZeroConfirm) {
    AlertDialog(
      onDismissRequest = { showTotalZeroConfirm = false },
      containerColor = StudioCard,
      title = { Text("Clear Workspace (TOTAL ZERO)?", color = StudioRed) },
      text = {
        Text(
          "This will delete all part folders in episode_workspace/ and temp files in workspace/, then recreate empty part folders.\n\nYour Stock Loops and Global Assets (Transition, BGM, Outro) will be strictly preserved.",
          color = StudioTextPrimary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showTotalZeroConfirm = false
            onClearWorkspaceTotalZero()
          },
          colors = ButtonDefaults.buttonColors(containerColor = StudioRed, contentColor = Color.White),
        ) {
          Text("Wipe Workspace", maxLines = 1, softWrap = false)
        }
      },
      dismissButton = {
        TextButton(onClick = { showTotalZeroConfirm = false }) { Text("Cancel") }
      },
    )
  }

  if (lowStorageWarning != null) {
    AlertDialog(
      onDismissRequest = onDismissLowStorageWarning,
      containerColor = StudioCard,
      title = { Text("Low Storage Warning", color = StudioAmber) },
      text = {
        Text(
          "Internal storage has only ${String.format(Locale.US, "%.2f", lowStorageWarning.freeGb)} GB free (recommended >= 3.00 GB for multi-part FFmpeg rendering). Continue anyway?",
          color = StudioTextPrimary,
        )
      },
      confirmButton = {
        Button(
          onClick = onConfirmLowStorageRender,
          colors = ButtonDefaults.buttonColors(containerColor = StudioAmber, contentColor = Color.Black),
        ) {
          Text("Render Anyway", maxLines = 1, softWrap = false)
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissLowStorageWarning) { Text("Cancel") }
      },
    )
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "part_workspace") {
        PartWorkspaceCard(
          config = config,
          partStatuses = partStatuses,
          playingAudioPart = playingAudioPart,
          isBusy = progress.isRunning,
          onUpdatePartFolders = onUpdatePartFolders,
          onSelectPart = onSelectPart,
          onPickPartVideo = onPickPartVideo,
          onPickPartAudio = onPickPartAudio,
          onLoadTxt = onLoadTxt,
          onTogglePlayAudio = onTogglePlayAudio,
          onClearPart = onClearPart,
        )
      }

      item(key = "dialogue_editor") {
        DialogueEditorCard(
          dialogueText = dialogueText,
          qaResult = qaResult,
          selectedPart = config.selectedPart,
          autoPasteEnabled = config.autoPasteEnabled,
          autoNextPartEnabled = config.autoNextPartEnabled,
          isBusy = progress.isRunning,
          onDialogueChange = onDialogueChange,
          onClearDialogue = onClearDialogue,
          onPasteDialogue = onPasteDialogue,
          onSanitizeDialogue = onSanitizeDialogue,
          onAutoPasteToggle = onAutoPasteToggle,
          onAutoNextToggle = onAutoNextToggle,
          onDownloadVoice = onDownloadVoice,
        )
      }

      item(key = "render_controls") {
        RenderControlsCard(
          config = config,
          isBusy = progress.isRunning,
          onFilterSelected = onFilterSelected,
          onPreviewFilter = onPreviewFilter,
          onTurboToggle = onTurboToggle,
          onSmartResumeToggle = onSmartResumeToggle,
          onSaveTransitions = onSaveTransitions,
          onAutoCleanToggle = onAutoCleanToggle,
          onRequestTotalZero = { showTotalZeroConfirm = true },
          onStartSmartRender = onStartSmartRender,
        )
      }

      item(key = "progress_log") {
        ProgressAndLogCard(
          progress = progress,
          logs = logs,
          onCancel = onCancelActiveTask,
          onClearLogs = onClearLogs,
        )
      }
    }
  }
}
