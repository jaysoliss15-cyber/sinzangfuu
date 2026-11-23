package com.example.domain

import com.example.data.ScriptEvent
import com.example.data.ScriptQaResult

object ScriptValidator {
  private val cueRegex =
    Regex(
      """\[SFX:\s*(.*?)\]|\[DEAD SILENCE DROP:\s*(\d+(?:\.\d+)?)\s*s?\]""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

  private val malformedTagRegex =
    Regex("""\[(?:SFX|DEAD\s+SILENCE\s+DROP)\s*:?""", RegexOption.IGNORE_CASE)

  private val digitRegex = Regex("""\d""")
  private val multiSpaceRegex = Regex("""[^\S\n]+""")
  private val multiNewlineRegex = Regex("""\n{3,}""")

  fun parseScriptEvents(rawScript: String): List<ScriptEvent> {
    if (rawScript.isEmpty()) return emptyList()
    val events = mutableListOf<ScriptEvent>()
    var cursor = 0

    for (match in cueRegex.findAll(rawScript)) {
      if (match.range.first > cursor) {
        val speechSlice = rawScript.substring(cursor, match.range.first)
        if (speechSlice.isNotBlank()) {
          events += ScriptEvent.Speech(speechSlice.trim())
        }
      }

      val sfxGroup = match.groups[1]
      val silenceGroup = match.groups[2]
      if (sfxGroup != null) {
        events += ScriptEvent.Sfx(sfxGroup.value.trim())
      } else if (silenceGroup != null) {
        val seconds = silenceGroup.value.toDoubleOrNull() ?: -1.0
        events += ScriptEvent.Silence(seconds)
      }
      cursor = match.range.last + 1
    }

    if (cursor < rawScript.length) {
      val tail = rawScript.substring(cursor)
      if (tail.isNotBlank()) {
        events += ScriptEvent.Speech(tail.trim())
      }
    }

    return events
  }

  /**
   * Zero-overlap sequential chunker: splits any speech segment > [maxChars] into chunks of
   * target size [softMin]..[maxChars] strictly at paragraph (\n) or sentence (., !, ?) boundaries
   * whenever possible, guaranteeing zero overlap so no words are ever repeated.
   */
  fun chunkSpeechSegment(
    text: String,
    maxChars: Int = 1000,
    softMin: Int = 900,
  ): List<String> {
    val clean = text.trim()
    if (clean.isEmpty()) return emptyList()
    if (clean.length <= maxChars) return listOf(clean)

    val chunks = mutableListOf<String>()
    var start = 0
    val len = clean.length

    while (start < len) {
      while (start < len && clean[start].isWhitespace()) {
        start++
      }
      if (start >= len) break

      val remaining = len - start
      if (remaining <= maxChars) {
        val tail = clean.substring(start).trim()
        if (tail.isNotEmpty()) chunks += tail
        break
      }

      val hardEnd = start + maxChars
      val preferredMin = (start + softMin).coerceAtMost(hardEnd - 1)

      val splitPos =
        findSplitBoundary(clean, start, preferredMin, hardEnd)
          ?: findSplitBoundary(clean, start, start + 1, hardEnd)
          ?: findLastWhitespace(clean, start, hardEnd)
          ?: hardEnd

      val safeEnd = splitPos.coerceIn(start + 1, hardEnd)
      val piece = clean.substring(start, safeEnd).trim()
      if (piece.isNotEmpty()) {
        chunks += piece
      }
      start = safeEnd
    }

    return chunks
  }

  private fun findSplitBoundary(
    text: String,
    start: Int,
    minSplitIndex: Int,
    maxSplitIndex: Int,
  ): Int? {
    // 1. Prefer paragraph break (\n) ending inside [minSplitIndex, maxSplitIndex]
    for (idx in maxSplitIndex downTo minSplitIndex) {
      if (idx > start && text[idx - 1] == '\n') {
        return idx
      }
    }

    // 2. Otherwise prefer end of sentence (., !, ?) optionally followed by closing quote/paren
    for (idx in maxSplitIndex downTo minSplitIndex) {
      val punctPos = idx - 1
      if (punctPos < start) break
      val ch = text[punctPos]
      if (ch == '.' || ch == '!' || ch == '?') {
        val next = text.getOrNull(idx)
        if (next == null || next.isWhitespace() || next == '"' || next == '\'' || next == ')') {
          return idx
        }
      }
    }
    return null
  }

  private fun findLastWhitespace(text: String, start: Int, hardEnd: Int): Int? {
    for (idx in hardEnd downTo (start + 1)) {
      if (text[idx - 1].isWhitespace()) return idx
    }
    return null
  }

  fun validateScript(rawScript: String): ScriptQaResult {
    val events = parseScriptEvents(rawScript)
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    var sfxCount = 0
    var silenceCount = 0
    var hasMalformedTag = false
    var hasDigitsInSpeech = false
    val speechBuilder = StringBuilder()

    for (event in events) {
      when (event) {
        is ScriptEvent.Sfx -> {
          sfxCount++
          if (event.prompt.isBlank()) {
            errors += "Empty [SFX: ] prompt detected. Provide sound description or remove tag."
          } else if (event.prompt.length > 450) {
            warnings += "SFX prompt exceeds 450 chars (${event.prompt.length} chars)."
          }
        }
        is ScriptEvent.Silence -> {
          silenceCount++
          if (event.durationSeconds < 0.1 || event.durationSeconds > 30.0) {
            errors +=
              "Invalid [DEAD SILENCE DROP: ${event.durationSeconds}s] — must be between 0.1 and 30.0 seconds."
          }
        }
        is ScriptEvent.Speech -> {
          if (malformedTagRegex.containsMatchIn(event.text)) {
            hasMalformedTag = true
          }
          if (digitRegex.containsMatchIn(event.text)) {
            hasDigitsInSpeech = true
          }
          if (speechBuilder.isNotEmpty()) {
            speechBuilder.append('\n')
          }
          speechBuilder.append(event.text)
        }
      }
    }

    if (hasMalformedTag) {
      errors +=
        "Malformed or unclosed [SFX: ...] or [DEAD SILENCE DROP: ...] tag detected outside valid cues."
    }
    if (StrictCleanDialogueSanitizer.hasContaminatedDirectives(rawScript)) {
      errors +=
        "Contaminated markdown fences, notes, or non-cue directives detected. Run Sanitize or Gemini Self-Audit first."
    }

    val cleanedTranscript = normalizeTranscript(speechBuilder.toString())
    val charCount = cleanedTranscript.length

    if (cleanedTranscript.isBlank()) {
      errors += "No spoken narration exists after stripping cues."
    }

    if (hasDigitsInSpeech) {
      errors +=
        "FATAL ERROR: Numerical digit (0-9) found in spoken narration. Spell out all numbers (e.g. 'twenty-five')."
    }

    if (charCount > 0 && (charCount < 4000 || charCount > 5000)) {
      warnings += "Total narration length is $charCount chars (recommended 4,000–5,000)."
    }

    val nonEmptyLines =
      cleanedTranscript
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (
      nonEmptyLines.size < 2 ||
        nonEmptyLines[nonEmptyLines.lastIndex] != "..." ||
        nonEmptyLines[nonEmptyLines.lastIndex - 1] != "..."
    ) {
      warnings += "Ending check: last two non-empty lines are not \"...\" and \"...\"."
    }

    return ScriptQaResult(
      events = events,
      cleanedTranscript = cleanedTranscript,
      charCount = charCount,
      errors = errors,
      warnings = warnings,
      hasFatalDigits = hasDigitsInSpeech,
      sfxCount = sfxCount,
      silenceCount = silenceCount,
    )
  }

  private fun normalizeTranscript(rawSpeech: String): String {
    if (rawSpeech.isBlank()) return ""
    val normalizedLines =
      rawSpeech
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { line -> multiSpaceRegex.replace(line.trim(), " ") }
        .joinToString("\n")
    return multiNewlineRegex.replace(normalizedLines, "\n\n").trim()
  }
}
