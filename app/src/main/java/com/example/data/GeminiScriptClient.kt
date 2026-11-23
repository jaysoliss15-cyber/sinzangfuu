package com.example.data

import android.util.Log
import com.example.domain.ScriptValidator
import com.example.domain.StrictCleanDialogueSanitizer
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GeminiScriptAuditOutcome(
  val cleanScript: String,
  val qaResult: ScriptQaResult,
  val repairPassesUsed: Int,
)

class GeminiScriptClient(
  private val godPromptFile: File? = null,
  private val generateRawOverride: (suspend (String) -> String)? = null,
  private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) },
) {
  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(90, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()
  }

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun fetchAvailableModels(
    provider: LlmProvider,
    baseUrl: String,
    apiKey: String,
  ): List<String> {
    val cleanKey = apiKey.trim()
    val cleanBase = normalizeBaseUrl(provider, baseUrl)

    return try {
      withTimeout(MODEL_FETCH_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
          currentCoroutineContext().ensureActive()
          when (provider) {
            LlmProvider.GEMINI -> {
              if (cleanKey.isEmpty()) {
                throw IllegalArgumentException(
                  "Enter your Google Gemini API key first to auto-detect available models."
                )
              }
              val effectiveBase =
                cleanBase.ifBlank { LlmProvider.GEMINI.defaultBaseUrl }
              val encodedKey = URLEncoder.encode(cleanKey, "UTF-8")
              val requestUrl = "$effectiveBase/models?key=$encodedKey"
              val request = Request.Builder().url(requestUrl).get().build()
              httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                  throw HttpStatusException(
                    code = response.code,
                    message =
                      formatHttpError(
                        provider = provider,
                        code = response.code,
                        url = "$effectiveBase/models",
                        rawBody = body,
                      ),
                  )
                }
                parseGeminiModelsJson(body)
              }
            }

            LlmProvider.OPENROUTER,
            LlmProvider.OPENAI,
            LlmProvider.GROQ,
            LlmProvider.DEEPSEEK,
            LlmProvider.CUSTOM -> {
              if (!cleanBase.startsWith("http://") && !cleanBase.startsWith("https://")) {
                throw IllegalArgumentException(
                  "Enter a valid HTTP/HTTPS Base URL (e.g. https://api.openai.com/v1)."
                )
              }
              if (provider != LlmProvider.CUSTOM && cleanKey.isEmpty()) {
                throw IllegalArgumentException(
                  "Enter your ${provider.displayName} API key first to detect models."
                )
              }
              val requestUrl = "$cleanBase/models"
              val reqBuilder =
                Request.Builder()
                  .url(requestUrl)
                  .get()
                  .addHeader("Accept", "application/json")
              if (cleanKey.isNotEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer $cleanKey")
              }
              if (provider == LlmProvider.OPENROUTER) {
                reqBuilder
                  .addHeader("HTTP-Referer", OPENROUTER_REFERER)
                  .addHeader("X-Title", OPENROUTER_APP_TITLE)
              }
              httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                  throw HttpStatusException(
                    code = response.code,
                    message =
                      formatHttpError(
                        provider = provider,
                        code = response.code,
                        url = requestUrl,
                        rawBody = body,
                      ),
                  )
                }
                parseOpenAiModelsJson(body)
              }
            }
          }
        }
      }
    } catch (e: TimeoutCancellationException) {
      throw IllegalStateException(
        "Timed out fetching models from ${provider.displayName}. Check network or Base URL.",
        e,
      )
    } catch (e: SocketTimeoutException) {
      throw IllegalStateException(
        "Network timeout fetching models from ${provider.displayName}. Check network or Base URL.",
        e,
      )
    } catch (e: UnknownHostException) {
      throw IllegalStateException(
        "Could not resolve ${provider.displayName} host. Check internet connection and Base URL.",
        e,
      )
    } catch (e: CancellationException) {
      throw e
    }
  }

  suspend fun runAutoPilotAndAudit(
    topicOrDraft: String,
    partNumber: Int,
    totalParts: Int = 1,
    generateFromTopic: Boolean,
    provider: LlmProvider = LlmProvider.GEMINI,
    baseUrl: String = provider.defaultBaseUrl,
    apiKey: String = "",
    model: String = provider.defaultModel,
    godPrompt: String = "",
    priorPartTail: String = "",
    onProgress: (percent: Int, step: String) -> Unit = { _, _ -> },
    onLog: (String, LogLevel) -> Unit = { _, _ -> },
  ): GeminiScriptAuditOutcome {
    val cleanInput = topicOrDraft.trim()
    require(cleanInput.isNotBlank()) {
      "Enter an episode topic/prompt or paste a draft script first."
    }

    val effectiveModel =
      model.trim().ifEmpty { provider.defaultModel }
    require(effectiveModel.isNotBlank()) {
      "Enter or select a Model ID for ${provider.displayName} in API & Settings."
    }

    val cleanApiKey = apiKey.trim()
    val cleanBaseUrl = normalizeBaseUrl(provider, baseUrl)
    val cleanGodPrompt = resolveGodPromptText(godPrompt, onLog)
    val engineLabel =
      if (provider == LlmProvider.GEMINI && cleanApiKey.isEmpty()) {
        "Firebase AI Logic"
      } else {
        "${provider.displayName} ($effectiveModel)"
      }

    // Step 1: Generate draft (if Auto-Pilot generation requested) or start from pasted draft
    var candidate =
      if (generateFromTopic) {
        onProgress(15, "${provider.displayName} Auto-Pilot: Writing Part $partNumber/$totalParts narration...")
        onLog(
          "Auto-Pilot: Generating Part $partNumber/$totalParts script draft via $engineLabel...",
          LogLevel.INFO,
        )
        callWithTransientRetry("Auto-Pilot Draft", onLog) {
          invokeModel(
            prompt = buildAutoPilotPrompt(cleanInput, partNumber, totalParts, priorPartTail),
            provider = provider,
            baseUrl = cleanBaseUrl,
            apiKey = cleanApiKey,
            modelName = effectiveModel,
            systemInstruction = cleanGodPrompt,
          )
        }
      } else {
        cleanInput
      }

    // Step 2: Mandatory Self-Audit Pass
    currentCoroutineContext().ensureActive()
    onProgress(45, "${provider.displayName} Self-Audit: Auditing Part $partNumber cues, digits & cleanliness...")
    onLog("Self-Audit ($engineLabel): Enforcing spoken narration rules & cue syntax...", LogLevel.INFO)
    candidate =
      callWithTransientRetry("Self-Audit Pass", onLog) {
        invokeModel(
          prompt = buildSelfAuditPrompt(candidate),
          provider = provider,
          baseUrl = cleanBaseUrl,
          apiKey = cleanApiKey,
          modelName = effectiveModel,
          systemInstruction = cleanGodPrompt,
        )
      }

    // Step 3: Strict Clean Dialogue Extractor + ScriptValidator
    var sanitized = StrictCleanDialogueSanitizer.sanitize(candidate)
    var qa = ScriptValidator.validateScript(sanitized)
    var repairPasses = 0

    // Step 4: Up to 2 targeted repair passes if errors, fatal digits, or ending warnings exist
    while (needsRepair(qa) && repairPasses < MAX_REPAIR_PASSES) {
      currentCoroutineContext().ensureActive()
      repairPasses++
      val pct = 60 + (repairPasses * 15)
      onProgress(pct, "QA Repair Pass $repairPasses/$MAX_REPAIR_PASSES ($effectiveModel)...")
      onLog(
        "Targeted repair pass $repairPasses/$MAX_REPAIR_PASSES triggered (${qa.errors.size} error(s), ${qa.warnings.size} warning(s)).",
        LogLevel.WARNING,
      )

      val repaired =
        callWithTransientRetry("Repair Pass $repairPasses", onLog) {
          invokeModel(
            prompt = buildRepairPrompt(sanitized.ifBlank { candidate }, qa),
            provider = provider,
            baseUrl = cleanBaseUrl,
            apiKey = cleanApiKey,
            modelName = effectiveModel,
            systemInstruction = cleanGodPrompt,
          )
        }
      sanitized = StrictCleanDialogueSanitizer.sanitize(repaired)
      qa = ScriptValidator.validateScript(sanitized)
    }

    if (!qa.isValid || qa.hasFatalDigits) {
      val reason = qa.errors.firstOrNull() ?: "Script failed validation after $repairPasses repair pass(es)."
      onLog("Script Audit blocked unsafe script: $reason", LogLevel.ERROR)
      throw IllegalStateException(
        "Script QA blocked output before TTS ($reason). Fix manually or retry Auto-Pilot."
      )
    }

    onProgress(100, "Part $partNumber Self-Audit complete (${qa.charCount} chars).")
    onLog(
      "Part $partNumber Self-Audit passed: ${qa.charCount} spoken chars, ${qa.sfxCount} SFX, ${qa.silenceCount} silence drop(s), $repairPasses repair pass(es).",
      LogLevel.SUCCESS,
    )
    return GeminiScriptAuditOutcome(
      cleanScript = sanitized,
      qaResult = qa,
      repairPassesUsed = repairPasses,
    )
  }

  private suspend fun resolveGodPromptText(
    explicitOverride: String,
    onLog: (String, LogLevel) -> Unit,
  ): String {
    val direct = explicitOverride.trim()
    if (direct.isNotEmpty()) return direct
    val file = godPromptFile ?: return ""
    val loaded =
      withContext(Dispatchers.IO) {
        if (file.exists() && file.isFile && file.length() > 0L) {
          runCatching { file.readText(Charsets.UTF_8).trim() }.getOrDefault("")
        } else {
          ""
        }
      }
    if (loaded.isNotEmpty()) {
      onLog("Loaded master God Prompt (${loaded.length} chars) from ${file.name}.", LogLevel.INFO)
    }
    return loaded
  }

  private suspend fun invokeModel(
    prompt: String,
    provider: LlmProvider,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    systemInstruction: String,
  ): String {
    currentCoroutineContext().ensureActive()
    if (generateRawOverride != null) {
      return withTimeout(REQUEST_TIMEOUT_MS) { generateRawOverride.invoke(prompt) }
    }

    return when (provider) {
      LlmProvider.GEMINI -> {
        if (apiKey.isNotBlank()) {
          invokeGeminiRestWithUserApiKey(
            prompt = prompt,
            baseUrl = baseUrl.ifBlank { LlmProvider.GEMINI.defaultBaseUrl },
            apiKey = apiKey,
            modelName = modelName,
            systemInstruction = systemInstruction,
          )
        } else {
          invokeFirebaseAiLogic(
            prompt = prompt,
            configuredModel = modelName,
            systemInstruction = systemInstruction,
          )
        }
      }

      LlmProvider.OPENROUTER,
      LlmProvider.OPENAI,
      LlmProvider.GROQ,
      LlmProvider.DEEPSEEK,
      LlmProvider.CUSTOM -> {
        invokeOpenAiCompatibleChat(
          prompt = prompt,
          provider = provider,
          baseUrl = baseUrl,
          apiKey = apiKey,
          modelName = modelName,
          systemInstruction = systemInstruction,
        )
      }
    }
  }

  private suspend fun invokeFirebaseAiLogic(
    prompt: String,
    configuredModel: String,
    systemInstruction: String,
  ): String {
    val firebaseSafeModel = resolveFirebaseAiModel(configuredModel)
    val model =
      if (systemInstruction.isNotBlank()) {
        Firebase.ai.generativeModel(
          modelName = firebaseSafeModel,
          systemInstruction = content { text(systemInstruction) },
        )
      } else {
        Firebase.ai.generativeModel(modelName = firebaseSafeModel)
      }

    return try {
      val response = withTimeout(REQUEST_TIMEOUT_MS) { model.generateContent(prompt) }
      val text = response.text?.trim()
      if (text.isNullOrBlank()) {
        throw IllegalStateException("Gemini returned an empty response.")
      }
      text
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini AI Logic call timed out after 60s", e)
      throw e
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Firebase AI generation failed", e)
      throw e
    }
  }

  private suspend fun invokeGeminiRestWithUserApiKey(
    prompt: String,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    systemInstruction: String,
  ): String =
    withTimeout(REQUEST_TIMEOUT_MS) {
      withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val bodyObj =
          JSONObject().apply {
            if (systemInstruction.isNotBlank()) {
              put(
                "system_instruction",
                JSONObject().put(
                  "parts",
                  JSONArray().put(JSONObject().put("text", systemInstruction)),
                ),
              )
            }
            put(
              "contents",
              JSONArray().put(
                JSONObject()
                  .put("role", "user")
                  .put("parts", JSONArray().put(JSONObject().put("text", prompt))),
              ),
            )
          }

        val cleanModel = modelName.removePrefix("models/").trim()
        val requestUrl = "${baseUrl.trimEnd('/')}/models/$cleanModel:generateContent"
        val request =
          Request.Builder()
            .url(requestUrl)
            .addHeader("x-goog-api-key", apiKey)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
          val respText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw HttpStatusException(
              code = response.code,
              message =
                formatHttpError(
                  provider = LlmProvider.GEMINI,
                  code = response.code,
                  url = requestUrl,
                  rawBody = respText,
                ),
            )
          }
          val root = JSONObject(respText)
          val candidates = root.optJSONArray("candidates")
          val firstCandidate = candidates?.optJSONObject(0)
          val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
          val generated =
            buildString {
              if (parts != null) {
                for (i in 0 until parts.length()) {
                  append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
                }
              }
            }.trim()
          if (generated.isBlank()) {
            throw IllegalStateException("Gemini REST API returned an empty candidate response.")
          }
          generated
        }
      }
    }

  private suspend fun invokeOpenAiCompatibleChat(
    prompt: String,
    provider: LlmProvider,
    baseUrl: String,
    apiKey: String,
    modelName: String,
    systemInstruction: String,
  ): String {
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      throw IllegalArgumentException(
        "Invalid Base URL for ${provider.displayName}. Must start with http:// or https://."
      )
    }
    if (provider != LlmProvider.CUSTOM && apiKey.isBlank()) {
      throw IllegalArgumentException(
        "Missing ${provider.displayName} API Key. Enter it in API & Settings."
      )
    }

    return withTimeout(REQUEST_TIMEOUT_MS) {
      withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val messages =
          JSONArray().apply {
            if (systemInstruction.isNotBlank()) {
              put(
                JSONObject()
                  .put("role", "system")
                  .put("content", systemInstruction),
              )
            }
            put(
              JSONObject()
                .put("role", "user")
                .put("content", prompt),
            )
          }

        val bodyObj =
          JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
          }

        val requestUrl = "${baseUrl.trimEnd('/')}/chat/completions"
        val reqBuilder =
          Request.Builder()
            .url(requestUrl)
            .addHeader("Accept", "application/json")
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
        if (apiKey.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        if (provider == LlmProvider.OPENROUTER) {
          reqBuilder
            .addHeader("HTTP-Referer", OPENROUTER_REFERER)
            .addHeader("X-Title", OPENROUTER_APP_TITLE)
        }

        httpClient.newCall(reqBuilder.build()).execute().use { response ->
          val respText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw HttpStatusException(
              code = response.code,
              message =
                formatHttpError(
                  provider = provider,
                  code = response.code,
                  url = requestUrl,
                  rawBody = respText,
                ),
            )
          }

          val content = parseOpenAiChatCompletionContent(respText)
          if (content.isBlank()) {
            throw IllegalStateException("${provider.displayName} returned an empty response.")
          }
          content
        }
      }
    }
  }

  private fun formatHttpError(
    provider: LlmProvider,
    code: Int,
    url: String,
    rawBody: String,
  ): String {
    val detail = extractRestError(rawBody)
    return when (code) {
      401,
      403 ->
        "Authentication failed (HTTP $code) for ${provider.displayName}: verify your API key. ($detail)"
      404 ->
        "Endpoint or model not found (HTTP 404) at $url. Verify Base URL and Model ID. ($detail)"
      429 ->
        "Rate limit / quota exceeded (HTTP 429) for ${provider.displayName}. ($detail)"
      else ->
        "${provider.displayName} HTTP $code: $detail"
    }
  }

  private fun extractRestError(rawBody: String): String {
    if (rawBody.isBlank()) return "Empty response body"
    return runCatching {
      val json = JSONObject(rawBody)
      val errObj = json.optJSONObject("error")
      val nestedMsg = errObj?.optString("message")?.takeIf { it.isNotBlank() }
      nestedMsg
        ?: json.optString("message").takeIf { it.isNotBlank() }
        ?: rawBody.take(240)
    }.getOrElse { rawBody.take(240) }
  }

  private fun resolveFirebaseAiModel(configuredModel: String): String {
    val clean = configuredModel.trim()
    if (clean.isBlank()) return FIREBASE_DEFAULT_MODEL
    val lower = clean.lowercase(Locale.ROOT)
    return if (
      lower.startsWith("gemini-1.") ||
        lower.startsWith("gemini-2.") ||
        lower == "gemini-pro"
    ) {
      FIREBASE_DEFAULT_MODEL
    } else {
      clean
    }
  }

  private suspend fun <T> callWithTransientRetry(
    label: String,
    onLog: (String, LogLevel) -> Unit,
    block: suspend () -> T,
  ): T {
    var lastError: Throwable? = null
    for (attempt in 0..RETRY_BACKOFFS_MS.size) {
      currentCoroutineContext().ensureActive()
      try {
        return block()
      } catch (e: TimeoutCancellationException) {
        lastError = e
        if (attempt >= RETRY_BACKOFFS_MS.size) break
        val waitMs = RETRY_BACKOFFS_MS[attempt]
        onLog(
          "$label timed out; retrying in ${waitMs / 1000L}s (attempt ${attempt + 1}/${RETRY_BACKOFFS_MS.size})...",
          LogLevel.WARNING,
        )
        delayFn(waitMs)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        if (!isTransientError(e) || attempt >= RETRY_BACKOFFS_MS.size) {
          throw e
        }
        val waitMs = RETRY_BACKOFFS_MS[attempt]
        onLog(
          "$label transient error (${e.message ?: e.javaClass.simpleName}); retrying in ${waitMs / 1000L}s (attempt ${attempt + 1}/${RETRY_BACKOFFS_MS.size})...",
          LogLevel.WARNING,
        )
        delayFn(waitMs)
      }
    }
    throw lastError ?: IllegalStateException("$label failed after retries.")
  }

  private fun isTransientError(error: Throwable): Boolean {
    if (error is HttpStatusException) {
      return error.code == 429 || error.code in 500..599
    }
    if (error is IOException) return true
    val msg = (error.message ?: "").lowercase(Locale.ROOT)
    if (msg.contains("http 401") || msg.contains("http 403") || msg.contains("http 404") || msg.contains("http 400")) {
      return false
    }
    return msg.contains("429") ||
      msg.contains("500") ||
      msg.contains("502") ||
      msg.contains("503") ||
      msg.contains("504") ||
      msg.contains("5xx") ||
      msg.contains("resource_exhausted") ||
      msg.contains("rate limit") ||
      msg.contains("quota") ||
      msg.contains("unavailable") ||
      msg.contains("internal") ||
      msg.contains("deadline_exceeded") ||
      msg.contains("timed out") ||
      msg.contains("timeout") ||
      msg.contains("network")
  }

  private fun needsRepair(qa: ScriptQaResult): Boolean {
    if (!qa.isValid || qa.hasFatalDigits) return true
    return qa.warnings.any { it.contains("Ending check", ignoreCase = true) }
  }

  private fun buildAutoPilotPrompt(
    topicOrOutline: String,
    partNumber: Int,
    totalParts: Int,
    priorPartTail: String,
  ): String {
    val continuityBlock =
      if (priorPartTail.isNotBlank() && partNumber > 1) {
        "\nCONTINUITY FROM PREVIOUS PART (continue seamlessly without repeating):\n${priorPartTail.takeLast(500)}\n"
      } else {
        ""
      }

    return """
      You are writing dark documentary horror narration for Part $partNumber of $totalParts for "The Missing Page".
      Episode Topic / Prompt:
      $topicOrOutline
      $continuityBlock
      STRICT OUTPUT RULES (FOLLOW EVERY RULE):
      1. Output ONLY pure spoken narration text and valid inline cues:
         - [SFX: vivid short sound description]
         - [DEAD SILENCE DROP: 2.5s] (duration strictly between 0.1 and 30.0 seconds)
      2. NEVER include numerical digits (0-9) anywhere in spoken narration. Spell out EVERY number, year, date, time, and quantity in words (e.g. "nineteen ninety-four", "two thirty in the morning", "twelve"). Only digits allowed are inside [DEAD SILENCE DROP: Xs].
      3. Target 4,000 to 5,000 spoken characters.
      4. End the script with exactly two separate lines containing "..." and "...".
      5. NEVER include markdown code fences (```), [PRODUCTION NOTES], [SOURCE PACK], [AUDIO / TECHNICAL NOTES], [CHAPTER TITLE: ...], [OUTRO VIDEO: ...], headings, bullet lists, or conversational chatter.
    """.trimIndent()
  }

  private fun buildSelfAuditPrompt(draft: String): String =
    """
    Perform a strict Self-Audit on the horror documentary script below and output ONLY the cleaned, corrected spoken narration script plus valid inline cues.

    CHECKLIST:
    1. Strip any markdown fences, [PRODUCTION NOTES...], [SOURCE PACK...], [AUDIO / TECHNICAL NOTES...], [CHAPTER TITLE: ...], [OUTRO VIDEO: ...], headings, and conversational intro/outro chatter.
    2. Convert EVERY numerical digit (0-9) in spoken narration into spelled-out English words (e.g., 1987 -> nineteen eighty-seven, 3 -> three). Do not change digits inside [DEAD SILENCE DROP: Xs].
    3. Keep ONLY valid inline cues formatted strictly as [SFX: ...] or [DEAD SILENCE DROP: X.Xs] (0.1 to 30.0s).
    4. Ensure the very last two non-empty lines are literally "..." on its own line followed by "..." on its own line.

    DRAFT SCRIPT:
    $draft
    """.trimIndent()

  private fun buildRepairPrompt(currentScript: String, qa: ScriptQaResult): String {
    val issues =
      buildString {
        qa.errors.forEach { appendLine("- ERROR: $it") }
        qa.warnings.forEach { appendLine("- WARNING: $it") }
      }.trim()

    return """
      Your previous script failed automated QA checks. Fix EVERY listed issue and return ONLY the repaired spoken narration script with valid [SFX: ...] / [DEAD SILENCE DROP: Xs] cues (no markdown fences, no commentary, no notes).

      QA ISSUES TO FIX:
      $issues

      RULES:
      - Zero digits (0-9) in spoken text — spell out every single number in words.
      - Ensure all [SFX: ...] prompts are non-empty and all [DEAD SILENCE DROP: Xs] values are between 0.1 and 30.0 seconds.
      - End the script with two separate final lines:
        ...
        ...

      SCRIPT TO REPAIR:
      $currentScript
    """.trimIndent()
  }

  private class HttpStatusException(val code: Int, message: String) : IllegalStateException(message)

  companion object {
    private const val TAG = "GeminiScriptClient"
    private const val MAX_REPAIR_PASSES = 2
    private const val REQUEST_TIMEOUT_MS = 60_000L
    private const val MODEL_FETCH_TIMEOUT_MS = 30_000L
    private const val FIREBASE_DEFAULT_MODEL = "gemini-3.8-flash"
    private const val OPENROUTER_REFERER = "https://themissingpage.studio"
    private const val OPENROUTER_APP_TITLE = "The Missing Page - S-Tier Studio"
    val RETRY_BACKOFFS_MS = listOf(5_000L, 15_000L, 30_000L)

    fun normalizeBaseUrl(provider: LlmProvider, rawBaseUrl: String): String {
      val candidate = rawBaseUrl.trim().ifBlank { provider.defaultBaseUrl }.trimEnd('/')
      if (candidate.isEmpty()) return ""
      return when {
        candidate.endsWith("/chat/completions", ignoreCase = true) ->
          candidate.dropLast("/chat/completions".length).trimEnd('/')
        candidate.endsWith("/models", ignoreCase = true) ->
          candidate.dropLast("/models".length).trimEnd('/')
        else -> candidate
      }
    }

    fun parseGeminiModelsJson(jsonBody: String): List<String> {
      val root = JSONObject(jsonBody)
      val modelsArray =
        root.optJSONArray("models")
          ?: throw IllegalStateException("Unexpected Gemini /models response format.")
      val result = LinkedHashSet<String>()
      for (i in 0 until modelsArray.length()) {
        val item = modelsArray.optJSONObject(i) ?: continue
        val methods = item.optJSONArray("supportedGenerationMethods")
        var supportsGenerateContent = false
        if (methods != null) {
          for (j in 0 until methods.length()) {
            if (methods.optString(j) == "generateContent") {
              supportsGenerateContent = true
              break
            }
          }
        }
        if (!supportsGenerateContent) continue
        val cleanName = item.optString("name", "").trim().removePrefix("models/").trim()
        if (cleanName.isNotEmpty()) {
          result += cleanName
        }
      }
      return result.sorted()
    }

    fun parseOpenAiModelsJson(jsonBody: String): List<String> {
      val root = JSONObject(jsonBody)
      val dataArray =
        root.optJSONArray("data")
          ?: throw IllegalStateException("Unexpected /models response format (missing 'data' array).")
      val result = LinkedHashSet<String>()
      for (i in 0 until dataArray.length()) {
        val item = dataArray.optJSONObject(i) ?: continue
        val id = item.optString("id", "").trim()
        if (id.isNotEmpty()) {
          result += id
        }
      }
      return result.sorted()
    }

    fun parseOpenAiChatCompletionContent(jsonBody: String): String {
      val root = JSONObject(jsonBody)
      val choices = root.optJSONArray("choices") ?: return ""
      val firstChoice = choices.optJSONObject(0) ?: return ""
      val message = firstChoice.optJSONObject("message") ?: return ""
      val rawContent: Any? = message.opt("content")
      return if (rawContent is String) {
        rawContent.trim()
      } else if (rawContent is JSONArray) {
        buildString {
          for (i in 0 until rawContent.length()) {
            val partObj = rawContent.optJSONObject(i)
            if (partObj != null) {
              append(partObj.optString("text", ""))
            } else {
              append(rawContent.optString(i, ""))
            }
          }
        }.trim()
      } else {
        ""
      }
    }
  }
}
