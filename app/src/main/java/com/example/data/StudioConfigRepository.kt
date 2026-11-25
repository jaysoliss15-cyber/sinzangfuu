package com.example.data

import android.content.Context
import android.content.SharedPreferences

class StudioConfigRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("missing_page_studio_config", Context.MODE_PRIVATE)

  fun load(): StudioConfig {
    val autoClean =
      if (!prefs.getBoolean(KEY_AUTO_CLEAN_MIGRATED_V2, false)) {
        prefs.edit()
          .putBoolean(KEY_AUTO_CLEAN, true)
          .putBoolean(KEY_AUTO_CLEAN_MIGRATED_V2, true)
          .apply()
        true
      } else {
        prefs.getBoolean(KEY_AUTO_CLEAN, true)
      }

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
      autoCleanAfterExport = autoClean,
      transitionOriginalName = prefs.getString(KEY_TRANSITION_NAME, "") ?: "",
      bgmOriginalName = prefs.getString(KEY_BGM_NAME, "") ?: "",
      outroOriginalName = prefs.getString(KEY_OUTRO_NAME, "") ?: "",
    )
  }

  fun save(config: StudioConfig) {
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
      .putBoolean(KEY_AUTO_CLEAN_MIGRATED_V2, true)
      .putString(KEY_TRANSITION_NAME, config.transitionOriginalName)
      .putString(KEY_BGM_NAME, config.bgmOriginalName)
      .putString(KEY_OUTRO_NAME, config.outroOriginalName)
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
    private const val KEY_AUTO_CLEAN_MIGRATED_V2 = "auto_clean_migrated_v2"
    private const val KEY_TRANSITION_NAME = "transition_original_name"
    private const val KEY_BGM_NAME = "bgm_original_name"
    private const val KEY_OUTRO_NAME = "outro_original_name"
  }
}
