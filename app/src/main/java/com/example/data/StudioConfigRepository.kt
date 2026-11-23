package com.example.data

import android.content.Context
import android.content.SharedPreferences

class StudioConfigRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("missing_page_studio_config", Context.MODE_PRIVATE)

  fun load(): StudioConfig {
    val provider = LlmProvider.fromId(prefs.getString(KEY_LLM_PROVIDER, null))
    val storedBaseUrl = prefs.getString(KEY_LLM_BASE_URL, null)?.trim().orEmpty()
    val effectiveBaseUrl =
      if (provider.isBaseUrlEditable) {
        storedBaseUrl
      } else {
        storedBaseUrl.ifEmpty { provider.defaultBaseUrl }
      }

    // Migrate cleanly from legacy gemini_api_key / gemini_model when llm_* keys are absent
    val storedApiKey = prefs.getString(KEY_LLM_API_KEY, null)
    val effectiveApiKey =
      when {
        storedApiKey != null -> storedApiKey
        else -> prefs.getString(KEY_LEGACY_GEMINI_API_KEY, "") ?: ""
      }

    val storedModel = prefs.getString(KEY_LLM_MODEL, null)?.trim().orEmpty()
    val legacyModel = prefs.getString(KEY_LEGACY_GEMINI_MODEL, null)?.trim().orEmpty()
    val effectiveModel =
      storedModel
        .ifEmpty { legacyModel }
        .ifEmpty { provider.defaultModel.ifEmpty { StudioConfig.DEFAULT_GEMINI_MODEL } }

    return StudioConfig(
      apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
      voiceId =
        prefs.getString(KEY_VOICE_ID, StudioConfig.DEFAULT_VOICE_ID)
          ?: StudioConfig.DEFAULT_VOICE_ID,
      modelId =
        prefs.getString(KEY_MODEL_ID, StudioConfig.DEFAULT_MODEL_ID)
          ?: StudioConfig.DEFAULT_MODEL_ID,
      sfxModelId =
        prefs.getString(KEY_SFX_MODEL_ID, StudioConfig.DEFAULT_SFX_MODEL_ID)
          ?: StudioConfig.DEFAULT_SFX_MODEL_ID,
      partCount = prefs.getInt(KEY_PART_COUNT, 10).coerceIn(1, 30),
      selectedPart = prefs.getInt(KEY_SELECTED_PART, 1).coerceIn(1, 30),
      autoPasteEnabled = prefs.getBoolean(KEY_AUTO_PASTE, false),
      lastAutoPastedHash = prefs.getString(KEY_LAST_PASTE_HASH, "") ?: "",
      autoNextPartEnabled = prefs.getBoolean(KEY_AUTO_NEXT, true),
      filterMode = RenderFilterMode.fromSlug(prefs.getString(KEY_FILTER_MODE, null)),
      turboModeEnabled = prefs.getBoolean(KEY_TURBO_MODE, true),
      smartResumeEnabled = prefs.getBoolean(KEY_SMART_RESUME, true),
      transitionsAfter = prefs.getString(KEY_TRANSITIONS_AFTER, "2,4,6") ?: "2,4,6",
      autoCleanAfterExport = prefs.getBoolean(KEY_AUTO_CLEAN, false),
      transitionOriginalName = prefs.getString(KEY_TRANSITION_NAME, "") ?: "",
      bgmOriginalName = prefs.getString(KEY_BGM_NAME, "") ?: "",
      outroOriginalName = prefs.getString(KEY_OUTRO_NAME, "") ?: "",
      llmProvider = provider,
      llmBaseUrl = effectiveBaseUrl,
      llmApiKey = effectiveApiKey,
      llmModel = effectiveModel,
      godPromptFileName = prefs.getString(KEY_GOD_PROMPT_FILE_NAME, "") ?: "",
      godPromptCharCount = prefs.getInt(KEY_GOD_PROMPT_CHAR_COUNT, 0).coerceAtLeast(0),
      godPromptWordCount = prefs.getInt(KEY_GOD_PROMPT_WORD_COUNT, 0).coerceAtLeast(0),
      godPromptUpdatedAt = prefs.getLong(KEY_GOD_PROMPT_UPDATED_AT, 0L).coerceAtLeast(0L),
      autoPilotFullAuto = prefs.getBoolean(KEY_AUTO_PILOT_FULL_AUTO, false),
    )
  }

  fun save(config: StudioConfig) {
    val provider = config.llmProvider
    val savedBaseUrl =
      if (provider.isBaseUrlEditable) {
        config.llmBaseUrl.trim()
      } else {
        config.llmBaseUrl.trim().ifEmpty { provider.defaultBaseUrl }
      }
    val savedModel =
      config.llmModel.trim().ifEmpty { provider.defaultModel }

    prefs
      .edit()
      .putString(KEY_API_KEY, config.apiKey)
      .putString(KEY_VOICE_ID, config.voiceId)
      .putString(KEY_MODEL_ID, config.modelId)
      .putString(KEY_SFX_MODEL_ID, config.sfxModelId)
      .putInt(KEY_PART_COUNT, config.partCount.coerceIn(1, 30))
      .putInt(KEY_SELECTED_PART, config.selectedPart.coerceIn(1, config.partCount.coerceIn(1, 30)))
      .putBoolean(KEY_AUTO_PASTE, config.autoPasteEnabled)
      .putString(KEY_LAST_PASTE_HASH, config.lastAutoPastedHash)
      .putBoolean(KEY_AUTO_NEXT, config.autoNextPartEnabled)
      .putString(KEY_FILTER_MODE, config.filterMode.slug)
      .putBoolean(KEY_TURBO_MODE, config.turboModeEnabled)
      .putBoolean(KEY_SMART_RESUME, config.smartResumeEnabled)
      .putString(KEY_TRANSITIONS_AFTER, config.transitionsAfter)
      .putBoolean(KEY_AUTO_CLEAN, config.autoCleanAfterExport)
      .putString(KEY_TRANSITION_NAME, config.transitionOriginalName)
      .putString(KEY_BGM_NAME, config.bgmOriginalName)
      .putString(KEY_OUTRO_NAME, config.outroOriginalName)
      .putString(KEY_LLM_PROVIDER, provider.id)
      .putString(KEY_LLM_BASE_URL, savedBaseUrl)
      .putString(KEY_LLM_API_KEY, config.llmApiKey.trim())
      .putString(KEY_LLM_MODEL, savedModel)
      .putString(KEY_LEGACY_GEMINI_API_KEY, config.llmApiKey.trim())
      .putString(KEY_LEGACY_GEMINI_MODEL, savedModel)
      .putString(KEY_GOD_PROMPT_FILE_NAME, config.godPromptFileName)
      .putInt(KEY_GOD_PROMPT_CHAR_COUNT, config.godPromptCharCount.coerceAtLeast(0))
      .putInt(KEY_GOD_PROMPT_WORD_COUNT, config.godPromptWordCount.coerceAtLeast(0))
      .putLong(KEY_GOD_PROMPT_UPDATED_AT, config.godPromptUpdatedAt.coerceAtLeast(0L))
      .putBoolean(KEY_AUTO_PILOT_FULL_AUTO, config.autoPilotFullAuto)
      .remove(KEY_LEGACY_GOD_PROMPT)
      .apply()
  }

  companion object {
    private const val KEY_API_KEY = "api_key"
    private const val KEY_VOICE_ID = "voice_id"
    private const val KEY_MODEL_ID = "model_id"
    private const val KEY_SFX_MODEL_ID = "sfx_model_id"
    private const val KEY_PART_COUNT = "part_count"
    private const val KEY_SELECTED_PART = "selected_part"
    private const val KEY_AUTO_PASTE = "auto_paste_enabled"
    private const val KEY_LAST_PASTE_HASH = "last_auto_pasted_hash"
    private const val KEY_AUTO_NEXT = "auto_next_part_enabled"
    private const val KEY_FILTER_MODE = "filter_mode"
    private const val KEY_TURBO_MODE = "turbo_mode_enabled"
    private const val KEY_SMART_RESUME = "smart_resume_enabled"
    private const val KEY_TRANSITIONS_AFTER = "transitions_after"
    private const val KEY_AUTO_CLEAN = "auto_clean_after_export"
    private const val KEY_TRANSITION_NAME = "transition_original_name"
    private const val KEY_BGM_NAME = "bgm_original_name"
    private const val KEY_OUTRO_NAME = "outro_original_name"
    private const val KEY_LLM_PROVIDER = "llm_provider"
    private const val KEY_LLM_BASE_URL = "llm_base_url"
    private const val KEY_LLM_API_KEY = "llm_api_key"
    private const val KEY_LLM_MODEL = "llm_model"
    private const val KEY_LEGACY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_LEGACY_GEMINI_MODEL = "gemini_model"
    private const val KEY_GOD_PROMPT_FILE_NAME = "god_prompt_file_name"
    private const val KEY_GOD_PROMPT_CHAR_COUNT = "god_prompt_char_count"
    private const val KEY_GOD_PROMPT_WORD_COUNT = "god_prompt_word_count"
    private const val KEY_GOD_PROMPT_UPDATED_AT = "god_prompt_updated_at"
    private const val KEY_LEGACY_GOD_PROMPT = "script_writer_god_prompt"
    private const val KEY_AUTO_PILOT_FULL_AUTO = "auto_pilot_full_auto"
  }
}
