package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardDeep
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanDim
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioGreenDim
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSectionLabelStyle
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
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
          Text(
            text = "SCRIPT AUTO-PILOT",
            style = StudioSectionLabelStyle,
            color = StudioCyan,
          )
        }
        Surface(
          color = StudioCardElevated,
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = "$partCount Part(s)",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
            color = StudioTextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

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
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("clear_autopilot_topic_button"),
          ) {
            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAmber)
            Spacer(Modifier.width(4.dp))
            Text("Clear", color = StudioTextPrimary, maxLines = 1, softWrap = false)
          }
          OutlinedButton(
            onClick = {
              val clip = onReadClipboard()
              if (!clip.isNullOrBlank()) {
                topicPrompt = clip
              }
            },
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("paste_autopilot_topic_button"),
          ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
            Spacer(Modifier.width(4.dp))
            Text("Paste", color = StudioCyan, maxLines = 1, softWrap = false)
          }
        }
      }

      OutlinedTextField(
        value = topicPrompt,
        onValueChange = { topicPrompt = it },
        minLines = 3,
        maxLines = 5,
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

      // Segmented Mode Toggle: Scripts Only vs Full Auto
      Surface(
        color = StudioCardDeep,
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (!fullAuto) StudioCyanDim else Color.Transparent)
                .clickable(enabled = !isBusy) { onFullAutoToggle(false) }
                .testTag("autopilot_mode_scripts_only"),
          ) {
            Text(
              text = "Scripts Only",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (!fullAuto) FontWeight.Bold else FontWeight.Medium,
              color = if (!fullAuto) StudioCyan else StudioTextSecondary,
              maxLines = 1,
              softWrap = false,
            )
          }

          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (fullAuto) StudioGreenDim else Color.Transparent)
                .clickable(enabled = !isBusy) { onFullAutoToggle(true) }
                .testTag("autopilot_mode_full_auto"),
          ) {
            Text(
              text = "Full Auto",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (fullAuto) FontWeight.Bold else FontWeight.Medium,
              color = if (fullAuto) StudioGreen else StudioTextSecondary,
              maxLines = 1,
              softWrap = false,
            )
          }
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("run_autopilot_button"),
      ) {
        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Run Auto-Pilot",
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
