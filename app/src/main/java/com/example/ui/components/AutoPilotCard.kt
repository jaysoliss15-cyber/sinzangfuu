package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardDeep
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanDim
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioGreenDim
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun AutoPilotCard(
  partCount: Int,
  fullAuto: Boolean,
  isBusy: Boolean,
  onReadClipboard: () -> String?,
  onFullAutoToggle: (Boolean) -> Unit,
  onRunAutoPilotEpisode: (topicPrompt: String, fullAuto: Boolean) -> Unit,
) {
  var topicPrompt by rememberSaveable { mutableStateOf("") }

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().testTag("autopilot_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
          Text(
            text = "AUTO-PILOT",
            style = MaterialTheme.typography.titleSmall,
            color = StudioCyan,
          )
        }
        Text(
          text = "$partCount Part(s)",
          style = MaterialTheme.typography.labelMedium,
          color = StudioTextSecondary,
        )
      }

      // Episode Topic / Prompt helper buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Episode Topic / Prompt",
          style = MaterialTheme.typography.labelLarge,
          color = StudioTextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { topicPrompt = "" },
            enabled = !isBusy && topicPrompt.isNotEmpty(),
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.heightIn(min = 48.dp).testTag("clear_autopilot_topic_button"),
          ) {
            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAmber)
            Spacer(Modifier.width(4.dp))
            Text("Clear", color = StudioTextPrimary)
          }
          OutlinedButton(
            onClick = {
              val clip = onReadClipboard()
              if (!clip.isNullOrBlank()) {
                topicPrompt = clip
              }
            },
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.6f)),
            modifier = Modifier.heightIn(min = 48.dp).testTag("paste_autopilot_topic_button"),
          ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
            Spacer(Modifier.width(4.dp))
            Text("Paste", color = StudioCyan)
          }
        }
      }

      OutlinedTextField(
        value = topicPrompt,
        onValueChange = { topicPrompt = it },
        minLines = 3,
        maxLines = 6,
        enabled = !isBusy,
        placeholder = {
          Text(
            "Enter episode mystery, case notes, or story prompt for $partCount part(s)...",
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
        modifier = Modifier.fillMaxWidth().testTag("autopilot_topic_input"),
      )

      // Mode Toggle: Scripts Only vs Full Auto
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = { onFullAutoToggle(false) },
          enabled = !isBusy,
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = if (!fullAuto) StudioCyanDim else StudioCardElevated,
              contentColor = if (!fullAuto) StudioCyan else StudioTextSecondary,
            ),
          border = BorderStroke(1.dp, if (!fullAuto) StudioCyan else StudioOutline),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("autopilot_mode_scripts_only"),
        ) {
          Text("Scripts Only", fontWeight = if (!fullAuto) FontWeight.Bold else FontWeight.Medium)
        }

        OutlinedButton(
          onClick = { onFullAutoToggle(true) },
          enabled = !isBusy,
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor = if (fullAuto) StudioGreenDim else StudioCardElevated,
              contentColor = if (fullAuto) StudioGreen else StudioTextSecondary,
            ),
          border = BorderStroke(1.dp, if (fullAuto) StudioGreen else StudioOutline),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("autopilot_mode_full_auto"),
        ) {
          Text("Full Auto", fontWeight = if (fullAuto) FontWeight.Bold else FontWeight.Medium)
        }
      }

      Button(
        onClick = { onRunAutoPilotEpisode(topicPrompt, fullAuto) },
        enabled = !isBusy && topicPrompt.isNotBlank(),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (fullAuto) StudioGreen else StudioCyan,
            contentColor = Color(0xFF002022),
          ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("run_autopilot_button"),
      ) {
        Icon(Icons.Filled.Bolt, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(
          text = "⚡ Run Auto-Pilot",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
