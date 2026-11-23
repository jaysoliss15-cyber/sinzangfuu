package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LogEntry
import com.example.data.LogLevel
import com.example.data.PartStatus
import com.example.data.PipelineProgress
import com.example.data.RenderFilterMode
import com.example.data.ScriptQaResult
import com.example.data.StudioConfig
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberDim
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardDeep
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanDim
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioGreenDim
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioRedDim
import com.example.ui.theme.StudioSectionLabelStyle
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale

@Composable
fun PartWorkspaceCard(
  config: StudioConfig,
  partStatuses: List<PartStatus>,
  playingAudioPart: Int?,
  isBusy: Boolean,
  onUpdatePartFolders: (Int) -> Unit,
  onSelectPart: (Int) -> Unit,
  onPickPartVideo: (Int) -> Unit,
  onPickPartAudio: (Int) -> Unit,
  onLoadTxt: () -> Unit,
  onTogglePlayAudio: (Int) -> Unit,
  onClearPart: (Int) -> Unit,
) {
  var pendingPartCount by remember(config.partCount) { mutableIntStateOf(config.partCount) }
  var partCountDropdownExpanded by remember { mutableStateOf(false) }
  var targetPartDropdownExpanded by remember { mutableStateOf(false) }
  val selectedPartStatus =
    remember(partStatuses, config.selectedPart) {
      partStatuses.firstOrNull { it.partNumber == config.selectedPart }
    }

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "PART SETUP & WORKSPACE",
        style = StudioSectionLabelStyle,
        color = StudioCyan,
      )

      // Part Count Selector (1..30) + Update Folders
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { partCountDropdownExpanded = true },
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioOutline),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("part_count_dropdown"),
          ) {
            Text(
              text = "Parts: $pendingPartCount",
              color = StudioTextPrimary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select part count", tint = StudioTextSecondary)
          }
          DropdownMenu(
            expanded = partCountDropdownExpanded,
            onDismissRequest = { partCountDropdownExpanded = false },
          ) {
            (1..30).forEach { count ->
              DropdownMenuItem(
                text = { Text("$count part(s)") },
                onClick = {
                  pendingPartCount = count
                  partCountDropdownExpanded = false
                },
              )
            }
          }
        }

        FilledTonalButton(
          onClick = { onUpdatePartFolders(pendingPartCount) },
          enabled = !isBusy,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = StudioCyanDim,
              contentColor = StudioCyan,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("update_folders_button"),
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Update Folders",
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Target Part Dropdown + Active Part Summary
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { targetPartDropdownExpanded = true },
            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("target_part_dropdown"),
          ) {
            Text(
              text = "Target: Part ${config.selectedPart}",
              color = StudioCyan,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select target part", tint = StudioCyan)
          }
          DropdownMenu(
            expanded = targetPartDropdownExpanded,
            onDismissRequest = { targetPartDropdownExpanded = false },
          ) {
            (1..config.partCount).forEach { num ->
              DropdownMenuItem(
                text = { Text("Part $num") },
                onClick = {
                  onSelectPart(num)
                  targetPartDropdownExpanded = false
                },
              )
            }
          }
        }

        val statusSummary =
          buildString {
            append(if (selectedPartStatus?.hasAudio == true) "Audio Ready" else "No Audio")
            append(" • ")
            append(if (selectedPartStatus?.hasCustomVideo == true) "Custom MP4" else "Stock Loop")
          }
        Text(
          text = statusSummary,
          style = MaterialTheme.typography.labelMedium,
          color = if (selectedPartStatus?.hasAudio == true) StudioGreen else StudioTextSecondary,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Horizontal scrollable Part Status Chips (P1, P2, P3...)
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        items(partStatuses, key = { it.partNumber }) { status ->
          val isSelected = status.partNumber == config.selectedPart
          val borderColor =
            when {
              isSelected -> StudioCyan
              status.hasAudio -> StudioGreen.copy(alpha = 0.55f)
              else -> StudioOutline
            }
          val bgColor =
            when {
              isSelected -> StudioCyanDim
              status.hasAudio -> StudioGreenDim.copy(alpha = 0.55f)
              else -> StudioCardElevated
            }
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            modifier =
              Modifier.heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onSelectPart(status.partNumber) }
                .testTag("part_chip_${status.partNumber}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Text(
                text = "P${status.partNumber}",
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) StudioCyan else StudioTextPrimary,
                maxLines = 1,
                softWrap = false,
              )
              if (status.hasAudio) {
                Icon(
                  imageVector = Icons.Filled.Audiotrack,
                  contentDescription = "Audio ready",
                  tint = StudioGreen,
                  modifier = Modifier.size(14.dp),
                )
              }
              if (status.hasCustomVideo) {
                Icon(
                  imageVector = Icons.Filled.Videocam,
                  contentDescription = "Custom video ready",
                  tint = StudioCyan,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }

      HorizontalDivider(color = StudioOutline)

      // Compact 2-column action grid so AutoPilotCard and DialogueEditor appear higher on screen
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { onPickPartVideo(config.selectedPart) },
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioOutline),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pick_part_mp4_button"),
          ) {
            Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Pick Part MP4",
              color = StudioTextPrimary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }

          OutlinedButton(
            onClick = { onPickPartAudio(config.selectedPart) },
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioOutline),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("pick_part_audio_button"),
          ) {
            Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioGreen)
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Pick Part Audio",
              color = StudioTextPrimary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onLoadTxt,
            border = BorderStroke(1.dp, StudioOutline),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("load_txt_button"),
          ) {
            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAmber)
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Load .TXT",
              color = StudioTextPrimary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }

          val isPlayingThisPart = playingAudioPart == config.selectedPart
          FilledTonalButton(
            onClick = { onTogglePlayAudio(config.selectedPart) },
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isPlayingThisPart) StudioRedDim else StudioCardElevated,
                contentColor = if (isPlayingThisPart) StudioRed else StudioGreen,
              ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("play_stop_part_audio_button"),
          ) {
            Icon(
              imageVector = if (isPlayingThisPart) Icons.Filled.Stop else Icons.Filled.PlayArrow,
              contentDescription = if (isPlayingThisPart) "Stop audio" else "Play audio",
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = if (isPlayingThisPart) "Stop Audio" else "Play Audio",
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        OutlinedButton(
          onClick = { onClearPart(config.selectedPart) },
          enabled = !isBusy,
          border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.5f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clear_part_button"),
        ) {
          Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioRed)
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Clear Part P${config.selectedPart}",
            color = StudioRed,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
fun DialogueEditorCard(
  dialogueText: String,
  qaResult: ScriptQaResult,
  selectedPart: Int,
  autoPasteEnabled: Boolean,
  autoNextPartEnabled: Boolean,
  isBusy: Boolean,
  onDialogueChange: (String) -> Unit,
  onClearDialogue: () -> Unit,
  onPasteDialogue: () -> Unit,
  onSanitizeDialogue: () -> Unit,
  onOpenAutoPilot: () -> Unit,
  onRunSelfAudit: () -> Unit,
  onAutoPasteToggle: (Boolean) -> Unit,
  onAutoNextToggle: (Boolean) -> Unit,
  onDownloadVoice: () -> Unit,
) {
  val charColor = if (qaResult.charCount in 4000..5000) StudioGreen else StudioAmber

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Header bar + live char counter
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "DIALOGUE / SCRIPT BLOCK",
          style = StudioSectionLabelStyle,
          color = StudioCyan,
        )
        Surface(
          color = if (qaResult.charCount in 4000..5000) StudioGreenDim else StudioAmberDim,
          border = BorderStroke(1.dp, charColor.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = "Chars: ${String.format(Locale.US, "%,d", qaResult.charCount)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = charColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Action Toolbar: Clear + Paste + Sanitize + Auto-Pilot + Self-Audit + Auto-Paste + Auto-Next
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onClearDialogue,
          border = BorderStroke(1.dp, StudioOutline),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("clear_dialogue_button"),
        ) {
          Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAmber)
          Spacer(Modifier.width(6.dp))
          Text("Clear", color = StudioTextPrimary, maxLines = 1, softWrap = false)
        }

        OutlinedButton(
          onClick = onPasteDialogue,
          border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("paste_dialogue_button"),
        ) {
          Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
          Spacer(Modifier.width(6.dp))
          Text("Paste", color = StudioCyan, maxLines = 1, softWrap = false)
        }

        OutlinedButton(
          onClick = onSanitizeDialogue,
          enabled = !isBusy && dialogueText.isNotBlank(),
          border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.55f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("sanitize_dialogue_button"),
        ) {
          Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAmber)
          Spacer(Modifier.width(6.dp))
          Text("Sanitize", color = StudioAmber, maxLines = 1, softWrap = false)
        }

        OutlinedButton(
          onClick = onOpenAutoPilot,
          enabled = !isBusy,
          border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.65f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("gemini_autopilot_button"),
        ) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
          Spacer(Modifier.width(6.dp))
          Text("Auto-Pilot", color = StudioCyan, maxLines = 1, softWrap = false)
        }

        OutlinedButton(
          onClick = onRunSelfAudit,
          enabled = !isBusy && dialogueText.isNotBlank(),
          border = BorderStroke(1.dp, StudioGreen.copy(alpha = 0.65f)),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("gemini_self_audit_button"),
        ) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioGreen)
          Spacer(Modifier.width(6.dp))
          Text("Self-Audit", color = StudioGreen, maxLines = 1, softWrap = false)
        }

        Surface(
          color = StudioCardElevated,
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
          ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAmber)
            Spacer(Modifier.width(4.dp))
            Text("Auto-Paste", style = MaterialTheme.typography.labelMedium, color = StudioTextPrimary, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Switch(
              checked = autoPasteEnabled,
              onCheckedChange = onAutoPasteToggle,
              colors = SwitchDefaults.colors(checkedThumbColor = StudioBackground, checkedTrackColor = StudioGreen),
              modifier = Modifier.testTag("auto_paste_switch"),
            )
          }
        }

        Surface(
          color = StudioCardElevated,
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
          ) {
            Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
            Spacer(Modifier.width(4.dp))
            Text("Auto-Next", style = MaterialTheme.typography.labelMedium, color = StudioTextPrimary, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(6.dp))
            Switch(
              checked = autoNextPartEnabled,
              onCheckedChange = onAutoNextToggle,
              colors = SwitchDefaults.colors(checkedThumbColor = StudioBackground, checkedTrackColor = StudioCyan),
              modifier = Modifier.testTag("auto_next_switch"),
            )
          }
        }
      }

      // Multiline scrollable dialogue field
      OutlinedTextField(
        value = dialogueText,
        onValueChange = onDialogueChange,
        minLines = 6,
        maxLines = 12,
        placeholder = {
          Text(
            "Paste Part $selectedPart narration script here...\nSupports [SFX: eerie wind] and [DEAD SILENCE DROP: 2.5s] cues.",
            color = StudioTextMuted,
          )
        },
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioOutline,
            focusedContainerColor = StudioCardDeep,
            unfocusedContainerColor = StudioCardDeep,
            focusedTextColor = StudioTextPrimary,
            unfocusedTextColor = StudioTextPrimary,
          ),
        modifier = Modifier.fillMaxWidth().testTag("dialogue_text_field"),
      )

      // Live QA feedback banner
      if (dialogueText.isNotBlank() && (qaResult.errors.isNotEmpty() || qaResult.warnings.isNotEmpty())) {
        Surface(
          color = StudioCardDeep,
          border = BorderStroke(1.dp, if (qaResult.errors.isNotEmpty()) StudioRed.copy(alpha = 0.45f) else StudioAmber.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            qaResult.errors.forEach { err ->
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = StudioRed, modifier = Modifier.size(16.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = StudioRed)
              }
            }
            qaResult.warnings.forEach { warn ->
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
                Text(warn, style = MaterialTheme.typography.bodySmall, color = StudioAmber)
              }
            }
          }
        }
      }

      Button(
        onClick = onDownloadVoice,
        enabled = !isBusy,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = StudioGreen,
            contentColor = Color(0xFF002111),
          ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("download_voice_button"),
      ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Download Voice (Part $selectedPart)",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun RenderControlsCard(
  config: StudioConfig,
  isBusy: Boolean,
  onFilterSelected: (RenderFilterMode) -> Unit,
  onPreviewFilter: () -> Unit,
  onTurboToggle: (Boolean) -> Unit,
  onSmartResumeToggle: (Boolean) -> Unit,
  onSaveTransitions: (String) -> Unit,
  onAutoCleanToggle: (Boolean) -> Unit,
  onRequestTotalZero: () -> Unit,
  onStartSmartRender: () -> Unit,
) {
  var filterDropdownExpanded by remember { mutableStateOf(false) }
  var transitionsInput by remember(config.transitionsAfter) { mutableStateOf(config.transitionsAfter) }

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        text = "RENDER PIPELINE & FILTERS",
        style = StudioSectionLabelStyle,
        color = StudioCyan,
      )

      // Filter Mode Dropdown + 3s Preview Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.weight(1f)) {
          OutlinedButton(
            onClick = { filterDropdownExpanded = true },
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioOutline),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("filter_mode_dropdown"),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = config.filterMode.displayName,
              color = StudioTextPrimary,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select render filter", tint = StudioTextSecondary)
          }
          DropdownMenu(
            expanded = filterDropdownExpanded,
            onDismissRequest = { filterDropdownExpanded = false },
          ) {
            RenderFilterMode.entries.forEach { mode ->
              DropdownMenuItem(
                text = { Text(mode.displayName) },
                onClick = {
                  onFilterSelected(mode)
                  filterDropdownExpanded = false
                },
              )
            }
          }
        }

        FilledTonalButton(
          onClick = onPreviewFilter,
          enabled = !isBusy,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = StudioCardElevated,
              contentColor = StudioCyan,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("filter_preview_button"),
        ) {
          Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("3s Filter Preview", maxLines = 1, softWrap = false)
        }
      }

      // Turbo Mode + Smart Resume switches
      StudioSwitchRow(
        label = "Turbo Mobile Render (Fast / Cool CPU)",
        checked = config.turboModeEnabled,
        enabled = !isBusy,
        testTag = "turbo_mode_switch",
        onCheckedChange = onTurboToggle,
      )
      StudioSwitchRow(
        label = "Smart Resume (Skip matching .sig parts)",
        checked = config.smartResumeEnabled,
        enabled = !isBusy,
        testTag = "smart_resume_switch",
        onCheckedChange = onSmartResumeToggle,
      )

      // Transitions After input + Save Setting
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = transitionsInput,
          onValueChange = { transitionsInput = it },
          singleLine = true,
          label = { Text("Transitions After (e.g. 2,4,6 or ALL)") },
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = StudioCyan,
              unfocusedBorderColor = StudioOutline,
              focusedContainerColor = StudioCardDeep,
              unfocusedContainerColor = StudioCardDeep,
              focusedTextColor = StudioTextPrimary,
              unfocusedTextColor = StudioTextPrimary,
            ),
          modifier = Modifier.weight(1f).testTag("transitions_after_input"),
        )
        FilledTonalButton(
          onClick = { onSaveTransitions(transitionsInput) },
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = StudioCyanDim,
              contentColor = StudioCyan,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
          modifier = Modifier.heightIn(min = 52.dp).testTag("save_transitions_button"),
        ) {
          Text("Save Setting", maxLines = 1, softWrap = false)
        }
      }

      StudioSwitchRow(
        label = "Auto-clean temp workspace after export",
        checked = config.autoCleanAfterExport,
        enabled = !isBusy,
        testTag = "auto_clean_switch",
        onCheckedChange = onAutoCleanToggle,
      )

      OutlinedButton(
        onClick = onRequestTotalZero,
        enabled = !isBusy,
        border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.55f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clear_workspace_total_zero_button"),
      ) {
        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioRed)
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Clear Workspace (TOTAL ZERO)",
          color = StudioRed,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Button(
        onClick = onStartSmartRender,
        enabled = !isBusy,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = StudioCyan,
            contentColor = Color(0xFF002022),
          ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("smart_render_button"),
      ) {
        Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = "1-Click Smart Render",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun StudioSwitchRow(
  label: String,
  checked: Boolean,
  enabled: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = StudioTextPrimary,
      modifier = Modifier.weight(1f).padding(end = 8.dp),
    )
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(checkedThumbColor = StudioBackground, checkedTrackColor = StudioGreen),
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
fun ProgressAndLogCard(
  progress: PipelineProgress,
  logs: List<LogEntry>,
  onCancel: () -> Unit,
  onClearLogs: () -> Unit,
) {
  val logScrollState = rememberScrollState()
  LaunchedEffect(logs.size) {
    logScrollState.animateScrollTo(logScrollState.maxValue)
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (progress.isRunning) "${progress.taskTitle} • ${progress.progressPercent}%" else "PROGRESS & SYSTEM LOG",
          style = if (progress.isRunning) MaterialTheme.typography.titleSmall else StudioSectionLabelStyle,
          color = if (progress.isRunning) StudioAmber else StudioCyan,
          modifier = Modifier.weight(1f).padding(end = 8.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (progress.isRunning && (progress.elapsedText.isNotBlank() || progress.etaText.isNotBlank())) {
          val timingLabel =
            buildString {
              if (progress.elapsedText.isNotBlank()) {
                append(progress.elapsedText)
              }
              if (progress.etaText.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("ETA: ")
                append(progress.etaText)
              }
            }
          Text(
            text = timingLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = StudioAmber,
            maxLines = 1,
            softWrap = false,
          )
        }
      }

      // Live Progress Bar + visible Red Cancel Button directly next to it
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          LinearProgressIndicator(
            progress = { (progress.progressPercent.coerceIn(0, 100)) / 100f },
            color = if (progress.isRunning) StudioCyan else StudioGreen,
            trackColor = StudioCardElevated,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
          )
          if (progress.stepText.isNotBlank()) {
            Text(
              text = progress.stepText,
              style = MaterialTheme.typography.bodySmall,
              color = StudioTextSecondary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (progress.isRunning) {
          Button(
            onClick = onCancel,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = StudioRed,
                contentColor = Color.White,
              ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_pipeline_button"),
          ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Cancel", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
          }
        }
      }

      HorizontalDivider(color = StudioOutline)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Live System Log (${logs.size})",
          style = MaterialTheme.typography.labelMedium,
          color = StudioTextSecondary,
        )
        TextButton(onClick = onClearLogs, modifier = Modifier.testTag("clear_logs_button")) {
          Text("Clear Log", color = StudioTextSecondary, maxLines = 1, softWrap = false)
        }
      }

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioCardDeep)
            .padding(10.dp)
            .verticalScroll(logScrollState),
      ) {
        if (logs.isEmpty()) {
          Text(
            text = "No log events yet.",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = StudioTextMuted,
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            logs.forEach { entry ->
              val color =
                when (entry.level) {
                  LogLevel.INFO -> StudioTextSecondary
                  LogLevel.SUCCESS -> StudioGreen
                  LogLevel.WARNING -> StudioAmber
                  LogLevel.ERROR -> StudioRed
                }
              Text(
                text = "[${entry.timestamp}] ${entry.message}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = color,
              )
            }
          }
        }
      }
    }
  }
}
