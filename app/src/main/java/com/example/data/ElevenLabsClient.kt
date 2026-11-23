package com.example.data

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ElevenLabsClient {
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(240, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun fetchSubscriptionInfo(apiKey: String): SubscriptionInfo =
    withContext(Dispatchers.IO) {
      val trimmedKey = apiKey.trim()
      if (trimmedKey.isEmpty()) {
        return@withContext SubscriptionInfo(errorMessage = "API Key is empty.")
      }

      val request =
        Request.Builder()
          .url("$BASE_URL/user/subscription")
          .addHeader("xi-api-key", trimmedKey)
          .get()
          .build()

      try {
        client.newCall(request).execute().use { response ->
          val bodyString = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext SubscriptionInfo(
              errorMessage = "HTTP ${response.code}: ${extractErrorMessage(bodyString)}"
            )
          }
          val json = JSONObject(bodyString)
          val resetUnix =
            if (json.has("next_character_count_reset_unix") &&
              !json.isNull("next_character_count_reset_unix")
            ) {
              json.optLong("next_character_count_reset_unix").takeIf { it > 0L }
            } else {
              null
            }
          val nowStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US).format(Date())
          SubscriptionInfo(
            tier = json.optString("tier", "unknown"),
            status = json.optString("status", "unknown"),
            characterCount = json.optLong("character_count", 0L),
            characterLimit = json.optLong("character_limit", 0L),
            nextResetUnixSeconds = resetUnix,
            checkedAtFormatted = nowStr,
          )
        }
      } catch (e: Exception) {
        SubscriptionInfo(errorMessage = e.message ?: "Network error checking subscription.")
      }
    }

  suspend fun synthesizeTts(
    apiKey: String,
    voiceId: String,
    modelId: String,
    text: String,
    outFile: File,
  ) =
    withContext(Dispatchers.IO) {
      val trimmedKey = apiKey.trim()
      require(trimmedKey.isNotEmpty()) { "ElevenLabs API Key is not configured." }
      val effectiveVoiceId = voiceId.trim().ifEmpty { StudioConfig.DEFAULT_VOICE_ID }
      val effectiveModelId = modelId.trim().ifEmpty { StudioConfig.DEFAULT_MODEL_ID }

      val payload =
        JSONObject()
          .put("text", text)
          .put("model_id", effectiveModelId)
          .put(
            "voice_settings",
            JSONObject().put("stability", 0.5).put("similarity_boost", 0.75),
          )
          .toString()

      val request =
        Request.Builder()
          .url("$BASE_URL/text-to-speech/$effectiveVoiceId?output_format=mp3_44100_128")
          .addHeader("xi-api-key", trimmedKey)
          .addHeader("Accept", "audio/mpeg")
          .post(payload.toRequestBody(jsonMediaType))
          .build()

      executeBinaryDownload(request, outFile)
    }

  suspend fun generateSfx(
    apiKey: String,
    sfxModelId: String,
    prompt: String,
    outFile: File,
  ) =
    withContext(Dispatchers.IO) {
      val trimmedKey = apiKey.trim()
      require(trimmedKey.isNotEmpty()) { "ElevenLabs API Key is not configured." }
      val effectiveSfxModel = sfxModelId.trim().ifEmpty { StudioConfig.DEFAULT_SFX_MODEL_ID }

      val payload =
        JSONObject()
          .put("text", prompt)
          .put("model_id", effectiveSfxModel)
          .put("prompt_influence", 0.55)
          .put("loop", false)
          .toString()

      val request =
        Request.Builder()
          .url("$BASE_URL/sound-generation?output_format=mp3_44100_128")
          .addHeader("xi-api-key", trimmedKey)
          .addHeader("Accept", "audio/mpeg")
          .post(payload.toRequestBody(jsonMediaType))
          .build()

      executeBinaryDownload(request, outFile)
    }

  private fun executeBinaryDownload(request: Request, outFile: File) {
    outFile.parentFile?.mkdirs()
    val tmp = File(outFile.parentFile, ".tmp_${outFile.name}_${System.currentTimeMillis()}")
    try {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val errBody = response.body?.string().orEmpty()
          throw IllegalStateException(
            "ElevenLabs API error (${response.code}): ${extractErrorMessage(errBody)}"
          )
        }
        val body = response.body ?: throw IllegalStateException("Empty ElevenLabs response body.")
        body.byteStream().use { input ->
          FileOutputStream(tmp).use { output -> input.copyTo(output, bufferSize = 32768) }
        }
      }
      if (tmp.length() == 0L) {
        throw IllegalStateException("ElevenLabs returned an empty audio file.")
      }
      if (outFile.exists()) outFile.delete()
      if (!tmp.renameTo(outFile)) {
        tmp.copyTo(outFile, overwrite = true)
        tmp.delete()
      }
    } catch (e: Exception) {
      tmp.delete()
      throw e
    }
  }

  private fun extractErrorMessage(body: String): String {
    if (body.isBlank()) return "Empty error response"
    return runCatching {
      val obj = JSONObject(body)
      when {
        obj.has("detail") -> {
          val d = obj.get("detail")
          if (d is JSONObject) d.optString("message", d.toString()) else d.toString()
        }
        obj.has("message") -> obj.optString("message")
        else -> body.take(240)
      }
    }.getOrElse { body.take(240) }
  }

  companion object {
    const val BASE_URL = "https://api.elevenlabs.io/v1"
  }
}
