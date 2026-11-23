package com.example.data

import java.util.Locale

enum class LlmProvider(
  val id: String,
  val displayName: String,
  val defaultBaseUrl: String,
  val defaultModel: String,
  val isBaseUrlEditable: Boolean = false,
) {
  GEMINI(
    id = "gemini",
    displayName = "Google Gemini",
    defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
    defaultModel = "gemini-2.5-flash",
  ),
  OPENROUTER(
    id = "openrouter",
    displayName = "OpenRouter",
    defaultBaseUrl = "https://openrouter.ai/api/v1",
    defaultModel = "google/gemini-2.5-flash",
  ),
  OPENAI(
    id = "openai",
    displayName = "OpenAI",
    defaultBaseUrl = "https://api.openai.com/v1",
    defaultModel = "gpt-4o-mini",
  ),
  GROQ(
    id = "groq",
    displayName = "Groq",
    defaultBaseUrl = "https://api.groq.com/openai/v1",
    defaultModel = "llama-3.3-70b-versatile",
  ),
  DEEPSEEK(
    id = "deepseek",
    displayName = "DeepSeek",
    defaultBaseUrl = "https://api.deepseek.com/v1",
    defaultModel = "deepseek-chat",
  ),
  CUSTOM(
    id = "custom",
    displayName = "Custom (OpenAI-Compatible)",
    defaultBaseUrl = "",
    defaultModel = "",
    isBaseUrlEditable = true,
  );

  companion object {
    fun fromId(raw: String?): LlmProvider {
      val clean = raw?.trim()?.lowercase(Locale.ROOT)
      return entries.firstOrNull {
        it.id == clean || it.name.equals(raw?.trim(), ignoreCase = true)
      } ?: GEMINI
    }
  }
}

enum class AppTab {
  STUDIO,
  MEDIA_HUB,
  API_SETTINGS,
}

enum class RenderFilterMode(
  val displayName: String,
  val slug: String,
  val ffmpegFilter: String,
) {
  STANDARD_HORROR(
    displayName = "Standard Horror",
    slug = "standard_horror",
    ffmpegFilter =
      "eq=saturation=0.6,colorbalance=bs=0.2:bm=0.2:bh=0.2,vignette=PI/4,noise=alls=8:allf=t",
  ),
  NIGHT_BOOST(
    displayName = "Night Boost",
    slug = "night_boost",
    ffmpegFilter =
      "eq=brightness=0.15:gamma=1.3:contrast=1.1:saturation=0.5,colorbalance=bs=0.2:bm=0.2:bh=0.2,noise=alls=8:allf=t",
  ),
  ANALOG_VHS(
    displayName = "Analog VHS",
    slug = "analog_vhs",
    ffmpegFilter =
      "eq=contrast=1.3:saturation=0.4:gamma=0.8,colorchannelmixer=rr=1.2:bb=1.2:gg=0.8,noise=alls=15:allf=t,vignette=PI/3",
  );

  companion object {
    fun fromSlug(slug: String?): RenderFilterMode =
      entries.firstOrNull { it.slug == slug } ?: STANDARD_HORROR
  }
}

enum class StudioRunStatus(val label: String) {
  READY("● READY"),
  BUSY("● RUNNING"),
  DONE("● DONE"),
  CANCELLED("● CANCELLED"),
  ERROR("● ERROR"),
}

enum class LogLevel {
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

data class LogEntry(
  val timestamp: String,
  val message: String,
  val level: LogLevel,
)

data class StudioConfig(
  val apiKey: String = "",
  val voiceId: String = DEFAULT_VOICE_ID,
  val modelId: String = DEFAULT_MODEL_ID,
  val sfxModelId: String = DEFAULT_SFX_MODEL_ID,
  val partCount: Int = 10,
  val selectedPart: Int = 1,
  val autoPasteEnabled: Boolean = false,
  val lastAutoPastedHash: String = "",
  val autoNextPartEnabled: Boolean = true,
  val filterMode: RenderFilterMode = RenderFilterMode.STANDARD_HORROR,
  val turboModeEnabled: Boolean = true,
  val smartResumeEnabled: Boolean = true,
  val transitionsAfter: String = "2,4,6",
  val autoCleanAfterExport: Boolean = false,
  val transitionOriginalName: String = "",
  val bgmOriginalName: String = "",
  val outroOriginalName: String = "",
  val llmProvider: LlmProvider = LlmProvider.GEMINI,
  val llmBaseUrl: String = LlmProvider.GEMINI.defaultBaseUrl,
  val llmApiKey: String = "",
  val llmModel: String = DEFAULT_GEMINI_MODEL,
  val godPromptFileName: String = "",
  val godPromptCharCount: Int = 0,
  val godPromptWordCount: Int = 0,
  val godPromptUpdatedAt: Long = 0L,
  val autoPilotFullAuto: Boolean = false,
) {
  val geminiApiKey: String
    get() = llmApiKey

  val geminiModel: String
    get() = llmModel

  val hasGodPromptFile: Boolean
    get() = godPromptFileName.isNotBlank() && godPromptCharCount > 0

  companion object {
    const val DEFAULT_VOICE_ID = "N2lVS1w4EtoT3dr4eOWO"
    const val DEFAULT_MODEL_ID = "eleven_v4"
    const val DEFAULT_SFX_MODEL_ID = "eleven_text_to_sound_v2"
    const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
  }
}

data class ModelDetectionState(
  val isLoading: Boolean = false,
  val models: List<String> = emptyList(),
  val errorMessage: String? = null,
  val detectedForProvider: LlmProvider? = null,
)

data class GodPromptImportResult(
  val fileName: String,
  val charCount: Int,
  val wordCount: Int,
  val updatedAt: Long,
  val previewText: String,
)

data class PartStatus(
  val partNumber: Int,
  val hasAudio: Boolean,
  val audioFileName: String? = null,
  val hasCustomVideo: Boolean,
  val videoFileName: String? = null,
  val hasTranscript: Boolean = false,
)

data class MediaFileInfo(
  val name: String,
  val filePath: String,
  val sizeBytes: Long,
  val durationSeconds: Double? = null,
)

data class AssetSlotStatus(
  val isLoaded: Boolean,
  val displayName: String,
  val filePath: String? = null,
  val durationSeconds: Double? = null,
  val sizeBytes: Long = 0L,
)

data class MediaHubState(
  val stockLoops: List<MediaFileInfo> = emptyList(),
  val transitionAsset: AssetSlotStatus = AssetSlotStatus(false, "Not Set"),
  val bgmAsset: AssetSlotStatus = AssetSlotStatus(false, "Not Set"),
  val outroAsset: AssetSlotStatus = AssetSlotStatus(false, "Not Set"),
  val tempCacheBytes: Long = 0L,
  val finishedVideos: List<MediaFileInfo> = emptyList(),
)

sealed interface ScriptEvent {
  data class Speech(val text: String) : ScriptEvent
  data class Sfx(val prompt: String) : ScriptEvent
  data class Silence(val durationSeconds: Double) : ScriptEvent
}

data class ScriptQaResult(
  val events: List<ScriptEvent>,
  val cleanedTranscript: String,
  val charCount: Int,
  val errors: List<String>,
  val warnings: List<String>,
  val hasFatalDigits: Boolean,
  val sfxCount: Int,
  val silenceCount: Int,
) {
  val isValid: Boolean
    get() = errors.isEmpty() && !hasFatalDigits
}

data class SubscriptionInfo(
  val tier: String = "",
  val status: String = "",
  val characterCount: Long = 0L,
  val characterLimit: Long = 0L,
  val nextResetUnixSeconds: Long? = null,
  val checkedAtFormatted: String = "",
  val errorMessage: String? = null,
) {
  val remainingCharacters: Long
    get() = (characterLimit - characterCount).coerceAtLeast(0L)
}

data class PipelineProgress(
  val isRunning: Boolean = false,
  val taskTitle: String = "",
  val progressPercent: Int = 0,
  val stepText: String = "",
  val etaText: String = "",
  val elapsedText: String = "",
)

data class PreviewPlayerTarget(
  val title: String,
  val filePath: String,
)

data class LowStorageWarning(
  val freeGb: Double,
)
