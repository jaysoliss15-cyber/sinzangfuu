package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
          Text("Wipe Workspace")
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
          Text("Render Anyway")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissLowStorageWarning) { Text("Cancel") }
      },
    )
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.widthIn(max = 820.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        onAutoPasteToggle = onAutoPasteToggle,
        onAutoNextToggle = onAutoNextToggle,
        onDownloadVoice = onDownloadVoice,
      )

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

      ProgressAndLogCard(
        progress = progress,
        logs = logs,
        onCancel = onCancelActiveTask,
        onClearLogs = onClearLogs,
      )
    }
  }
}
