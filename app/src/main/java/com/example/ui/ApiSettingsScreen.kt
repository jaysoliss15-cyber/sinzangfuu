package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.StudioConfig
import com.example.data.SubscriptionInfo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardDeep
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanDim
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ApiSettingsScreen(
  config: StudioConfig,
  subscriptionInfo: SubscriptionInfo?,
  isCheckingSubscription: Boolean,
  onSaveSettings: (apiKey: String, voiceId: String, modelId: String, sfxModelId: String) -> Unit,
  onCheckCredits: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
  var voiceId by remember(config.voiceId) { mutableStateOf(config.voiceId) }
  var modelId by remember(config.modelId) { mutableStateOf(config.modelId) }
  var sfxModelId by remember(config.sfxModelId) { mutableStateOf(config.sfxModelId) }
  var showApiKey by remember { mutableStateOf(false) }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = StudioCyan,
      unfocusedBorderColor = StudioOutline,
      focusedContainerColor = StudioCardDeep,
      unfocusedContainerColor = StudioCardDeep,
      focusedTextColor = StudioTextPrimary,
      unfocusedTextColor = StudioTextPrimary,
    )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.widthIn(max = 820.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Key, contentDescription = null, tint = StudioCyan)
            Text(
              text = "ELEVENLABS API & VOICE CONFIG",
              style = MaterialTheme.typography.titleSmall,
              color = StudioCyan,
            )
          }

          OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            singleLine = true,
            label = { Text("Single ElevenLabs API Key") },
            placeholder = { Text("sk_...") },
            visualTransformation =
              if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(
                onClick = { showApiKey = !showApiKey },
                modifier = Modifier.testTag("toggle_api_key_visibility"),
              ) {
                Icon(
                  imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                  contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                  tint = StudioTextSecondary,
                )
              }
            },
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
          )

          OutlinedTextField(
            value = voiceId,
            onValueChange = { voiceId = it },
            singleLine = true,
            label = { Text("Voice ID") },
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("voice_id_input"),
          )

          OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            singleLine = true,
            label = { Text("TTS Model ID") },
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("tts_model_id_input"),
          )

          OutlinedTextField(
            value = sfxModelId,
            onValueChange = { sfxModelId = it },
            singleLine = true,
            label = { Text("SFX Model ID") },
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth().testTag("sfx_model_id_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = {
                voiceId = StudioConfig.DEFAULT_VOICE_ID
                modelId = StudioConfig.DEFAULT_MODEL_ID
                sfxModelId = StudioConfig.DEFAULT_SFX_MODEL_ID
              },
              border = BorderStroke(1.dp, StudioOutline),
              modifier = Modifier.heightIn(min = 48.dp).testTag("reset_defaults_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioTextSecondary)
              Spacer(Modifier.width(6.dp))
              Text("Reset Defaults", color = StudioTextSecondary)
            }

            Button(
              onClick = { onSaveSettings(apiKey, voiceId, modelId, sfxModelId) },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = StudioGreen,
                  contentColor = Color(0xFF002111),
                ),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_api_settings_button"),
            ) {
              Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Save API Settings", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // Credits & Renewal Date Checker
      Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = StudioCyan)
            Text(
              text = "CREDITS & RENEWAL DATE CHECKER",
              style = MaterialTheme.typography.titleSmall,
              color = StudioCyan,
            )
          }

          FilledTonalButton(
            onClick = {
              onSaveSettings(apiKey, voiceId, modelId, sfxModelId)
              onCheckCredits()
            },
            enabled = !isCheckingSubscription,
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = StudioCyanDim,
                contentColor = StudioCyan,
              ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("check_credits_button"),
          ) {
            if (isCheckingSubscription) {
              CircularProgressIndicator(
                color = StudioCyan,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text("Checking /v1/user/subscription...")
            } else {
              Text("Check Credits & Renewal Date", fontWeight = FontWeight.Bold)
            }
          }

          if (subscriptionInfo != null) {
            if (subscriptionInfo.errorMessage != null) {
              Surface(
                color = StudioCardDeep,
                border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = subscriptionInfo.errorMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioRed,
                  modifier = Modifier.padding(12.dp),
                )
              }
            } else {
              val limit = subscriptionInfo.characterLimit.coerceAtLeast(1L)
              val usageRatio = (subscriptionInfo.characterCount.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
              val renewalDisplay = formatRenewal(subscriptionInfo.nextResetUnixSeconds)

              Surface(
                color = StudioCardElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "Tier: ${subscriptionInfo.tier.uppercase(Locale.US)}",
                      style = MaterialTheme.typography.labelLarge,
                      color = StudioCyan,
                    )
                    Text(
                      text = "Status: ${subscriptionInfo.status}",
                      style = MaterialTheme.typography.labelMedium,
                      color = StudioGreen,
                    )
                  }

                  LinearProgressIndicator(
                    progress = { usageRatio },
                    color = if (usageRatio > 0.9f) StudioAmber else StudioGreen,
                    trackColor = StudioCardDeep,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                  )

                  Text(
                    text =
                      "Used: ${String.format(Locale.US, "%,d", subscriptionInfo.characterCount)} / ${String.format(Locale.US, "%,d", subscriptionInfo.characterLimit)} chars",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = StudioTextPrimary,
                  )
                  Text(
                    text =
                      "Remaining Credits: ${String.format(Locale.US, "%,d", subscriptionInfo.remainingCharacters)} chars",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = StudioGreen,
                  )
                  Text(
                    text = "Renewal Date: $renewalDisplay",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioAmber,
                  )
                  if (subscriptionInfo.checkedAtFormatted.isNotBlank()) {
                    Text(
                      text = "Last checked: ${subscriptionInfo.checkedAtFormatted}",
                      style = MaterialTheme.typography.labelSmall,
                      color = StudioTextSecondary,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun formatRenewal(unixSeconds: Long?): String {
  if (unixSeconds == null || unixSeconds <= 0L) return "Not available"
  val resetMs = unixSeconds * 1000L
  val formatted = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US).format(Date(resetMs))
  val diffMs = (resetMs - System.currentTimeMillis()).coerceAtLeast(0L)
  val days = TimeUnit.MILLISECONDS.toDays(diffMs)
  val hours = TimeUnit.MILLISECONDS.toHours(diffMs) % 24
  return "$formatted (in ${days}d ${hours}h)"
}
