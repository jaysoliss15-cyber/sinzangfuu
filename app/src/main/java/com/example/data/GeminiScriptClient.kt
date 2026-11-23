package com.example.data

import android.util.Log
import com.example.domain.ScriptValidator
import com.example.domain.StrictCleanDialogueSanitizer
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import java.io.IOException
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

  suspend fun runAutoPilotAndAudit(
    topicOrDraft: String,
    partNumber: Int,
    totalParts: Int = 1,
    generateFromTopic: Boolean,
    geminiApiKey: String = "",
    geminiModel: String = StudioConfig.DEFAULT_GEMINI_MODEL,
    godPrompt: String = "",
    priorPartTail: String = "",
    onProgress: (percent: Int, step: String) -> Unit = { _, _ -> },
    onLog: (String, LogLevel) -> Unit = { _, _ -> },
  ): GeminiScriptAuditOutcome {
    val cleanInput = topicOrDraft.trim()
    require(cleanInput.isNotBlank()) {
      "Enter an episode topic/prompt or paste a draft script first."
    }

    val effectiveModel = geminiModel.trim().ifEmpty { StudioConfig.DEFAULT_GEMINI_MODEL }
    val cleanApiKey = geminiApiKey.trim()
    val cleanGodPrompt = godPrompt.trim()

    // Step 1: Generate draft (if Auto-Pilot generation requested) or start from pasted draft
    var candidate =
      if (generateFromTopic) {
        onProgress(15, "Gemini Auto-Pilot: Writing Part $partNumber/$totalParts narration...")
        onLog(
          "Gemini Auto-Pilot: Generating Part $partNumber/$totalParts script draft (${if (cleanApiKey.isNotEmpty()) "API Key: $effectiveModel" else "Firebase AI Logic"})...",
          LogLevel.INFO,
        )
        callWithTransientRetry("Auto-Pilot Draft", onLog) {
          invokeModel(
            prompt = buildAutoPilotPrompt(cleanInput, partNumber, totalParts, priorPartTail),
            geminiApiKey = cleanApiKey,
            geminiModel = effectiveModel,
            systemInstruction = cleanGodPrompt,
          )
        }
      } else {
        cleanInput
      }

    // Step 2: Mandatory Self-Audit Pass
    currentCoroutineContext().ensureActive()
    onProgress(45, "Gemini Self-Audit: Auditing Part $partNumber cues, digits & cleanliness...")
    onLog("Gemini Self-Audit: Enforcing spoken narration rules & cue syntax...", LogLevel.INFO)
    candidate =
      callWithTransientRetry("Self-Audit Pass", onLog) {
        invokeModel(
          prompt = buildSelfAuditPrompt(candidate),
          geminiApiKey = cleanApiKey,
          geminiModel = effectiveModel,
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
      onProgress(pct, "Gemini Repair Pass $repairPasses/$MAX_REPAIR_PASSES...")
      onLog(
        "Targeted repair pass $repairPasses/$MAX_REPAIR_PASSES triggered (${qa.errors.size} error(s), ${qa.warnings.size} warning(s)).",
        LogLevel.WARNING,
      )

      val repaired =
        callWithTransientRetry("Repair Pass $repairPasses", onLog) {
          invokeModel(
            prompt = buildRepairPrompt(sanitized.ifBlank { candidate }, qa),
            geminiApiKey = cleanApiKey,
            geminiModel = effectiveModel,
            systemInstruction = cleanGodPrompt,
          )
        }
      sanitized = StrictCleanDialogueSanitizer.sanitize(repaired)
      qa = ScriptValidator.validateScript(sanitized)
    }

    if (!qa.isValid || qa.hasFatalDigits) {
      val reason = qa.errors.firstOrNull() ?: "Script failed validation after $repairPasses repair pass(es)."
      onLog("Gemini Audit blocked unsafe script: $reason", LogLevel.ERROR)
      throw IllegalStateException(
        "Script QA blocked output before TTS ($reason). Fix manually or retry Auto-Pilot."
      )
    }

    onProgress(100, "Part $partNumber Self-Audit complete (${qa.charCount} chars).")
    onLog(
      "Part $partNumber Gemini Self-Audit passed: ${qa.charCount} spoken chars, ${qa.sfxCount} SFX, ${qa.silenceCount} silence drop(s), $repairPasses repair pass(es).",
      LogLevel.SUCCESS,
    )
    return GeminiScriptAuditOutcome(
      cleanScript = sanitized,
      qaResult = qa,
      repairPassesUsed = repairPasses,
    )
  }

  private suspend fun invokeModel(
    prompt: String,
    geminiApiKey: String,
    geminiModel: String,
    systemInstruction: String,
  ): String {
    currentCoroutineContext().ensureActive()
    if (generateRawOverride != null) {
      return withTimeout(60_000L) { generateRawOverride.invoke(prompt) }
    }

    // If user configured a personal Gemini API key in API & Settings, use Google AI REST endpoint directly
    if (geminiApiKey.isNotBlank()) {
      return invokeRestWithUserApiKey(
        prompt = prompt,
        apiKey = geminiApiKey,
        modelName = geminiModel,
        systemInstruction = systemInstruction,
      )
    }

    // Otherwise use provisioned Firebase AI Logic (requires no API key; uses App Check)
    val firebaseSafeModel = resolveFirebaseAiModel(geminiModel)
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
      val response = withTimeout(60_000L) { model.generateContent(prompt) }
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

  private suspend fun invokeRestWithUserApiKey(
    prompt: String,
    apiKey: String,
    modelName: String,
    systemInstruction: String,
  ): String =
    withTimeout(60_000L) {
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
        val request =
          Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
          val respText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val detail = extractRestError(respText)
            throw IllegalStateException("Gemini HTTP ${response.code}: $detail")
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

  private fun extractRestError(rawBody: String): String {
    if (rawBody.isBlank()) return "Empty response body"
    return runCatching {
      JSONObject(rawBody).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        ?: rawBody.take(240)
    }.getOrElse { rawBody.take(240) }
  }

  private fun resolveFirebaseAiModel(configuredModel: String): String {
    val clean = configuredModel.trim()
    if (clean.isBlank()) return FIREBASE_DEFAULT_MODEL
    val lower = clean.lowercase()
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
    if (error is IOException) return true
    val msg = (error.message ?: "").lowercase()
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

  companion object {
    private const val TAG = "GeminiScriptClient"
    private const val MAX_REPAIR_PASSES = 2
    private const val FIREBASE_DEFAULT_MODEL = "gemini-3.8-flash"
    val RETRY_BACKOFFS_MS = listOf(5_000L, 15_000L, 30_000L)
  }
}
