package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LlmProvider
import com.example.data.ModelDetectionState
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
import com.example.ui.theme.StudioGreenDim
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioSectionLabelStyle
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Isolated tab route so progress ticks, timer updates, and logs in StudioTab NEVER
 * recompose ApiSettingsScreen.
 */
@Composable
fun ApiSettingsTabRoute(
  viewModel: StudioViewModel,
  onPickGodPromptFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val config by viewModel.config.collectAsStateWithLifecycle()
  val godPromptPreview by viewModel.godPromptPreview.collectAsStateWithLifecycle()
  val modelDetectionState by viewModel.modelDetectionState.collectAsStateWithLifecycle()
  val subscriptionInfo by viewModel.subscriptionInfo.collectAsStateWithLifecycle()
  val isCheckingSubscription by viewModel.isCheckingSubscription.collectAsStateWithLifecycle()

  ApiSettingsScreen(
    config = config,
    godPromptPreview = godPromptPreview,
    modelDetectionState = modelDetectionState,
    subscriptionInfo = subscriptionInfo,
    isCheckingSubscription = isCheckingSubscription,
    onReadClipboard = { viewModel.readClipboard() },
    onImportGodPromptFile = onPickGodPromptFile,
    onClearGodPromptFile = { viewModel.clearGodPromptFile() },
    onDetectModels = { provider, baseUrl, apiKey ->
      viewModel.detectAvailableModels(provider = provider, baseUrl = baseUrl, apiKey = apiKey)
    },
    onClearDetectedModels = { viewModel.clearDetectedModels() },
    onSaveSettings = { key, voice, model, sfxModel, provider, baseUrl, llmKey, llmModel ->
      viewModel.saveApiSettings(
        apiKey = key,
        voiceId = voice,
        modelId = model,
        sfxModelId = sfxModel,
        llmProvider = provider,
        llmBaseUrl = baseUrl,
        llmApiKey = llmKey,
        llmModel = llmModel,
      )
    },
    onCheckCredits = { viewModel.checkSubscriptionCredits() },
    modifier = modifier,
  )
}

@Composable
fun ApiSettingsScreen(
  config: StudioConfig,
  godPromptPreview: String,
  modelDetectionState: ModelDetectionState,
  subscriptionInfo: SubscriptionInfo?,
  isCheckingSubscription: Boolean,
  onReadClipboard: () -> String?,
  onImportGodPromptFile: () -> Unit,
  onClearGodPromptFile: () -> Unit,
  onDetectModels: (provider: LlmProvider, baseUrl: String, apiKey: String) -> Unit,
  onClearDetectedModels: () -> Unit,
  onSaveSettings: (
    apiKey: String,
    voiceId: String,
    modelId: String,
    sfxModelId: String,
    llmProvider: LlmProvider,
    llmBaseUrl: String,
    llmApiKey: String,
    llmModel: String,
  ) -> Unit,
  onCheckCredits: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
  var voiceId by remember(config.voiceId) { mutableStateOf(config.voiceId) }
  var modelId by remember(config.modelId) { mutableStateOf(config.modelId) }
  var sfxModelId by remember(config.sfxModelId) { mutableStateOf(config.sfxModelId) }
  var showApiKey by remember { mutableStateOf(false) }

  var llmProvider by remember(config.llmProvider) { mutableStateOf(config.llmProvider) }
  var llmBaseUrl by remember(config.llmBaseUrl) { mutableStateOf(config.llmBaseUrl) }
  var llmApiKey by remember(config.llmApiKey) { mutableStateOf(config.llmApiKey) }
  var llmModel by remember(config.llmModel) { mutableStateOf(config.llmModel) }
  var showLlmApiKey by remember { mutableStateOf(false) }

  var providerDropdownExpanded by remember { mutableStateOf(false) }
  var showModelPickerDialog by remember { mutableStateOf(false) }
  var openPickerOnNextResult by remember { mutableStateOf(false) }

  LaunchedEffect(modelDetectionState.isLoading, modelDetectionState.models, modelDetectionState.detectedForProvider) {
    if (
      openPickerOnNextResult &&
      !modelDetectionState.isLoading &&
      modelDetectionState.detectedForProvider == llmProvider &&
      modelDetectionState.models.isNotEmpty()
    ) {
      openPickerOnNextResult = false
      showModelPickerDialog = true
    } else if (openPickerOnNextResult && !modelDetectionState.isLoading && modelDetectionState.errorMessage != null) {
      openPickerOnNextResult = false
    }
  }

  val textFieldColors =
    OutlinedTextFieldDefaults.colors(
      focusedBorderColor = StudioCyan,
      unfocusedBorderColor = StudioOutline,
      focusedContainerColor = StudioCardDeep,
      unfocusedContainerColor = StudioCardDeep,
      focusedTextColor = StudioTextPrimary,
      unfocusedTextColor = StudioTextPrimary,
    )

  if (showModelPickerDialog) {
    AvailableModelsPickerDialog(
      providerName = llmProvider.displayName,
      models = modelDetectionState.models,
      selectedModel = llmModel,
      onSelectModel = { picked ->
        llmModel = picked
        showModelPickerDialog = false
      },
      onDismiss = { showModelPickerDialog = false },
    )
  }

  fun commitAllSettings() {
    onSaveSettings(
      apiKey,
      voiceId,
      modelId,
      sfxModelId,
      llmProvider,
      llmBaseUrl,
      llmApiKey,
      llmModel,
    )
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "elevenlabs_config") {
        Card(
          colors = CardDefaults.cardColors(containerColor = StudioCard),
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Key, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "ELEVENLABS API & VOICE CONFIG",
                style = StudioSectionLabelStyle,
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
          }
        }
      }

      item(key = "gemini_config") {
        Card(
          colors = CardDefaults.cardColors(containerColor = StudioCard),
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "SCRIPT AUTO-PILOT",
                style = StudioSectionLabelStyle,
                color = StudioCyan,
              )
            }

            // Provider selector
            Box(modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = { providerDropdownExpanded = true },
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("llm_provider_dropdown"),
              ) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "Provider: ${llmProvider.displayName}",
                  color = StudioTextPrimary,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select LLM provider", tint = StudioCyan)
              }

              DropdownMenu(
                expanded = providerDropdownExpanded,
                onDismissRequest = { providerDropdownExpanded = false },
              ) {
                LlmProvider.entries.forEach { option ->
                  DropdownMenuItem(
                    text = {
                      Column {
                        Text(
                          text = option.displayName,
                          fontWeight = if (option == llmProvider) FontWeight.Bold else FontWeight.Normal,
                          color = if (option == llmProvider) StudioCyan else StudioTextPrimary,
                        )
                        if (option.defaultBaseUrl.isNotEmpty()) {
                          Text(
                            text = option.defaultBaseUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextMuted,
                          )
                        }
                      }
                    },
                    onClick = {
                      val previousProvider = llmProvider
                      llmProvider = option
                      providerDropdownExpanded = false
                      if (option != previousProvider) {
                        llmBaseUrl =
                          if (option.isBaseUrlEditable) {
                            if (previousProvider.isBaseUrlEditable) llmBaseUrl else ""
                          } else {
                            option.defaultBaseUrl
                          }
                        if (option.defaultModel.isNotEmpty()) {
                          llmModel = option.defaultModel
                        }
                        onClearDetectedModels()
                      }
                    },
                    modifier = Modifier.testTag("llm_provider_option_${option.id}"),
                  )
                }
              }
            }

            // Base URL Field (auto-filled per preset provider; editable when CUSTOM)
            OutlinedTextField(
              value = llmBaseUrl,
              onValueChange = {
                if (llmProvider.isBaseUrlEditable) {
                  llmBaseUrl = it
                }
              },
              readOnly = !llmProvider.isBaseUrlEditable,
              singleLine = true,
              label = {
                Text(
                  if (llmProvider.isBaseUrlEditable) "Base URL (OpenAI-Compatible)"
                  else "Base URL (${llmProvider.displayName})",
                )
              },
              placeholder = { Text("https://your-endpoint.com/v1") },
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = StudioCyan,
                  unfocusedBorderColor = StudioOutline,
                  focusedContainerColor = StudioCardDeep,
                  unfocusedContainerColor = StudioCardDeep,
                  focusedTextColor =
                    if (llmProvider.isBaseUrlEditable) StudioTextPrimary else StudioTextSecondary,
                  unfocusedTextColor =
                    if (llmProvider.isBaseUrlEditable) StudioTextPrimary else StudioTextSecondary,
                ),
              modifier = Modifier.fillMaxWidth().testTag("llm_base_url_input"),
            )

            // API Key Field + Paste
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = llmApiKey,
                onValueChange = { llmApiKey = it },
                singleLine = true,
                label = { Text("API Key (${llmProvider.displayName})") },
                placeholder = {
                  Text(
                    if (llmProvider == LlmProvider.GEMINI) "Optional personal key (or blank for Firebase AI)"
                    else "Bearer API Key (sk-...)",
                  )
                },
                visualTransformation =
                  if (showLlmApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                  IconButton(
                    onClick = { showLlmApiKey = !showLlmApiKey },
                    modifier = Modifier.testTag("toggle_gemini_key_visibility"),
                  ) {
                    Icon(
                      imageVector = if (showLlmApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                      contentDescription = if (showLlmApiKey) "Hide API key" else "Show API key",
                      tint = StudioTextSecondary,
                    )
                  }
                },
                colors = textFieldColors,
                modifier = Modifier.weight(1f).testTag("gemini_api_key_input"),
              )

              OutlinedButton(
                onClick = {
                  val clip = onReadClipboard()?.trim()
                  if (!clip.isNullOrEmpty()) {
                    llmApiKey = clip
                  }
                },
                border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.heightIn(min = 52.dp).testTag("paste_gemini_key_button"),
              ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioCyan)
                Spacer(Modifier.width(4.dp))
                Text(
                  text = "Paste",
                  color = StudioCyan,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }

            // Model ID Field (manual entry supported at all times)
            OutlinedTextField(
              value = llmModel,
              onValueChange = { llmModel = it },
              singleLine = true,
              label = { Text("Model ID") },
              placeholder = {
                Text(llmProvider.defaultModel.ifEmpty { "Enter or detect model ID" })
              },
              colors = textFieldColors,
              modifier = Modifier.fillMaxWidth().testTag("gemini_model_input"),
            )

            // Detect Models & Select from Detected List
            val hasDetectedForProvider =
              modelDetectionState.detectedForProvider == llmProvider &&
                modelDetectionState.models.isNotEmpty()

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = {
                  openPickerOnNextResult = true
                  onDetectModels(llmProvider, llmBaseUrl, llmApiKey)
                },
                enabled = !modelDetectionState.isLoading,
                colors =
                  ButtonDefaults.filledTonalButtonColors(
                    containerColor = StudioCyanDim,
                    contentColor = StudioCyan,
                  ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detect_models_button"),
              ) {
                if (modelDetectionState.isLoading) {
                  CircularProgressIndicator(
                    color = StudioCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(8.dp))
                  Text("Detecting...", maxLines = 1, softWrap = false)
                } else {
                  Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "Detect Models",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              if (hasDetectedForProvider) {
                OutlinedButton(
                  onClick = { showModelPickerDialog = true },
                  border = BorderStroke(1.dp, StudioGreen.copy(alpha = 0.6f)),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                  modifier = Modifier.heightIn(min = 48.dp).testTag("browse_detected_models_button"),
                ) {
                  Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, modifier = Modifier.size(18.dp), tint = StudioGreen)
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "Select (${modelDetectionState.models.size})",
                    color = StudioGreen,
                    maxLines = 1,
                    softWrap = false,
                  )
                }
              }
            }

            if (modelDetectionState.errorMessage != null && modelDetectionState.detectedForProvider == llmProvider) {
              Surface(
                color = StudioCardDeep,
                border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("model_detection_error_banner"),
              ) {
                Text(
                  text = modelDetectionState.errorMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioRed,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }

            // Action Buttons (Full-width stack prevents button text line-break bugs on narrow screens)
            Button(
              onClick = { commitAllSettings() },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = StudioGreen,
                  contentColor = Color(0xFF002111),
                ),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("save_api_settings_button"),
            ) {
              Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Save All Settings",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }

            OutlinedButton(
              onClick = {
                voiceId = StudioConfig.DEFAULT_VOICE_ID
                modelId = StudioConfig.DEFAULT_MODEL_ID
                sfxModelId = StudioConfig.DEFAULT_SFX_MODEL_ID
                llmProvider = LlmProvider.GEMINI
                llmBaseUrl = LlmProvider.GEMINI.defaultBaseUrl
                llmModel = StudioConfig.DEFAULT_GEMINI_MODEL
                onClearDetectedModels()
              },
              border = BorderStroke(1.dp, StudioOutline),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("reset_defaults_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioTextSecondary)
              Spacer(Modifier.width(8.dp))
              Text(
                text = "Reset Defaults",
                color = StudioTextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      item(key = "god_prompt_file_card") {
        GodPromptFileCard(
          config = config,
          previewText = godPromptPreview,
          onImportFile = onImportGodPromptFile,
          onClearFile = onClearGodPromptFile,
        )
      }

      item(key = "credits_checker") {
        Card(
          colors = CardDefaults.cardColors(containerColor = StudioCard),
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
              Text(
                text = "CREDITS & RENEWAL CHECKER",
                style = StudioSectionLabelStyle,
                color = StudioCyan,
              )
            }

            FilledTonalButton(
              onClick = {
                commitAllSettings()
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
                Text(
                  text = "Checking subscription...",
                  maxLines = 1,
                  softWrap = false,
                )
              } else {
                Text(
                  text = "Check Credits & Renewal Date",
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            if (subscriptionInfo != null) {
              if (subscriptionInfo.errorMessage != null) {
                Surface(
                  color = StudioCardDeep,
                  border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.6f)),
                  shape = RoundedCornerShape(12.dp),
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
                  border = BorderStroke(1.dp, StudioOutline),
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
}

@Composable
private fun AvailableModelsPickerDialog(
  providerName: String,
  models: List<String>,
  selectedModel: String,
  onSelectModel: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val filteredModels =
    remember(models, searchQuery) {
      val clean = searchQuery.trim()
      if (clean.isEmpty()) models
      else models.filter { it.contains(clean, ignoreCase = true) }
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = StudioCard,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Select Model",
          style = MaterialTheme.typography.titleMedium,
          color = StudioCyan,
        )
        Text(
          text = "$providerName • ${models.size} available",
          style = MaterialTheme.typography.labelSmall,
          color = StudioTextSecondary,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          singleLine = true,
          placeholder = { Text("Search ${models.size} models...") },
          leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = StudioTextSecondary, modifier = Modifier.size(18.dp))
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = StudioTextSecondary, modifier = Modifier.size(18.dp))
              }
            }
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
          modifier = Modifier.fillMaxWidth().testTag("model_search_input"),
        )

        if (filteredModels.isEmpty()) {
          Surface(
            color = StudioCardDeep,
            border = BorderStroke(1.dp, StudioOutline),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No models match \"$searchQuery\".",
              style = MaterialTheme.typography.bodySmall,
              color = StudioTextMuted,
              modifier = Modifier.padding(14.dp),
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(filteredModels, key = { it }) { modelName ->
              val isSelected = modelName == selectedModel.trim()
              Surface(
                color = if (isSelected) StudioCyanDim else StudioCardDeep,
                border = BorderStroke(1.dp, if (isSelected) StudioCyan else StudioOutline),
                shape = RoundedCornerShape(10.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectModel(modelName) }
                    .testTag("detected_model_item_$modelName"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = if (isSelected) StudioCyan else StudioTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                  )
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = "Selected model",
                      tint = StudioCyan,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = StudioTextSecondary)
      }
    },
  )
}

@Composable
private fun GodPromptFileCard(
  config: StudioConfig,
  previewText: String,
  onImportFile: () -> Unit,
  onClearFile: () -> Unit,
) {
  val isLoaded = config.hasGodPromptFile

  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth().testTag("god_prompt_file_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.Description, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(18.dp))
          Text(
            text = "GOD PROMPT FILE (.TXT / .CSV)",
            style = StudioSectionLabelStyle,
            color = StudioCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Surface(
          color = if (isLoaded) StudioGreenDim else StudioCardElevated,
          border = BorderStroke(1.dp, if (isLoaded) StudioGreen.copy(alpha = 0.5f) else StudioOutline),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("god_prompt_status_badge"),
        ) {
          Text(
            text = if (isLoaded) "Loaded" else "Not Set",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLoaded) StudioGreen else StudioTextMuted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      if (isLoaded) {
        Surface(
          color = StudioCardDeep,
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = config.godPromptFileName,
              style = MaterialTheme.typography.titleSmall,
              color = StudioTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = buildString {
                append("${String.format(Locale.US, "%,d", config.godPromptCharCount)} chars")
                append(" • ${String.format(Locale.US, "%,d", config.godPromptWordCount)} words")
                if (config.godPromptUpdatedAt > 0L) {
                  val dateStr =
                    SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(config.godPromptUpdatedAt))
                  append(" • $dateStr")
                }
              },
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = StudioGreen,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            if (previewText.isNotBlank()) {
              Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      } else {
        Surface(
          color = StudioCardDeep,
          border = BorderStroke(1.dp, StudioOutline),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Import a master .txt or .csv system prompt file. Saved internally in filesDir/god_prompt_master.txt with zero UI lag.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextMuted,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilledTonalButton(
          onClick = onImportFile,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = StudioCyanDim,
              contentColor = StudioCyan,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_god_prompt_button"),
        ) {
          Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = "Import .TXT / .CSV",
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (isLoaded) {
          OutlinedButton(
            onClick = onClearFile,
            border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.55f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("clear_god_prompt_button"),
          ) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioRed)
            Spacer(Modifier.width(4.dp))
            Text(
              text = "Clear",
              color = StudioRed,
              maxLines = 1,
              softWrap = false,
            )
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
