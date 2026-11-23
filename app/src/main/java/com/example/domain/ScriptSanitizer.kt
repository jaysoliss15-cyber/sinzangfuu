package com.example.domain

/**
 * Strict Clean Dialogue Extractor: removes markdown wrappers, production/source/technical blocks,
 * chapter titles, outro video directives, AI conversational chatter, and non-cue brackets while
 * preserving ONLY pure spoken narration/dialogue and valid inline cues ([SFX: ...],
 * [DEAD SILENCE DROP: ...s]).
 */
object StrictCleanDialogueSanitizer {
  private val validCueRegex =
    Regex(
      """\[SFX:\s*[^\]]+\]|\[DEAD SILENCE DROP:\s*\d+(?:\.\d+)?\s*s?\]""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

  private val markdownFenceLineRegex = Regex("""^\s*```[a-zA-Z0-9_-]*\s*$""")
  private val markdownHorizontalRuleRegex = Regex("""^\s*([-*_]){3,}\s*$""")

  // Multi-line section blocks that often appear after or around LLM-generated narration
  private val sectionBlockHeaderRegex =
    Regex(
      """(?im)^\s*(?:\*\*|#{1,6}\s*)?\[?(?:PRODUCTION\s+NOTES|SOURCE\s+PACK|AUDIO\s*/?\s*TECHNICAL\s+NOTES|TECHNICAL\s+NOTES|AUDIO\s+NOTES|DIRECTOR(?:'S)?\s+NOTES|SOURCES|REFERENCES|CITATIONS)(?:\s*[:\]]|\b).*$"""
    )

  // Single-line bracketed directives that are NOT audio SFX/silence cues
  private val nonCueBracketBlockRegex =
    Regex(
      """\[\s*(?:PRODUCTION\s+NOTES|SOURCE\s+PACK|AUDIO\s*/?\s*TECHNICAL\s+NOTES|TECHNICAL\s+NOTES|CHAPTER\s+TITLE|OUTRO\s+VIDEO|OUTRO|INTRO|TITLE|SCENE|VISUAL|B-ROLL|CAMERA|PART\s+\d+|END\s+OF\s+PART|NARRATOR|SCRIPT)[^\]]*\]""",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

  // Conversational AI preamble or trailer lines
  private val chatterLinePatterns =
    listOf(
      Regex("""^(?:sure|certainly|of course|absolutely|here(?:'s| is)(?: the)?|below is|i(?:'ve| have) (?:written|created|generated|revised|cleaned|audited|repaired)|please find|note:|notes:|word count:|character count:|char count:).*$""", RegexOption.IGNORE_CASE),
      Regex("""^(?:let me know if|would you like|feel free to|hope this helps|if you(?:'d| would) like|shall i proceed|want me to).*$""", RegexOption.IGNORE_CASE),
      Regex("""^(?:\*\*|#+\s*)(?:chapter\s+title|production\s+notes|source\s+pack|audio\s*/?\s*technical\s+notes|outro\s+video|part\s+\d+).*$""", RegexOption.IGNORE_CASE),
    )

  private val multiNewlineRegex = Regex("""\n{3,}""")

  fun sanitize(rawInput: String): String {
    if (rawInput.isBlank()) return ""

    var working = rawInput.replace("\r\n", "\n").replace('\r', '\n')

    // Step 1: Protect valid [SFX: ...] and [DEAD SILENCE DROP: ...s] cues with unique tokens
    val preservedCues = mutableListOf<String>()
    working =
      validCueRegex.replace(working) { match ->
        val token = "___STUDIO_CUE_TOKEN_${preservedCues.size}___"
        preservedCues += normalizeValidCue(match.value)
        token
      }

    // Step 2: Strip markdown code fences (```text, ```, etc.)
    working =
      working
        .lines()
        .filterNot { line -> markdownFenceLineRegex.matches(line) || markdownHorizontalRuleRegex.matches(line) }
        .joinToString("\n")

    // Step 3: Remove inline bracketed metadata blocks ([PRODUCTION NOTES...], [SOURCE PACK...],
    // [AUDIO / TECHNICAL NOTES...], [CHAPTER TITLE: ...], [OUTRO VIDEO: ...], etc.)
    working = nonCueBracketBlockRegex.replace(working, "")

    // Step 4: Remove trailing/embedded section blocks (e.g. "PRODUCTION NOTES:" or "[SOURCE PACK]"
    // followed by bullets/metadata until end or next clear paragraph break)
    working = stripMetadataSections(working)

    // Step 5: Filter out conversational intro/outro chatter lines and stray empty brackets
    val filteredLines =
      working
        .lines()
        .map { line -> cleanLineFormatting(line) }
        .filterNot { line -> isChatterLine(line) }

    working = filteredLines.joinToString("\n")

    // Collapse trailing/leading empty lines inside working text
    working = multiNewlineRegex.replace(working, "\n\n").trim()

    // Step 6: Restore preserved valid cues
    preservedCues.forEachIndexed { idx, cueText ->
      working = working.replace("___STUDIO_CUE_TOKEN_${idx}___", cueText)
    }

    return multiNewlineRegex.replace(working, "\n\n").trim()
  }

  fun hasContaminatedDirectives(text: String): Boolean {
    if (text.isBlank()) return false
    if (text.contains("```")) return true
    if (nonCueBracketBlockRegex.containsMatchIn(text)) return true
    return text.lines().any { line ->
      sectionBlockHeaderRegex.matches(line) || isChatterLine(line.trim())
    }
  }

  private fun normalizeValidCue(rawCue: String): String {
    val trimmed = rawCue.trim()
    val silenceMatch =
      Regex("""^\[DEAD SILENCE DROP:\s*(\d+(?:\.\d+)?)\s*s?\]$""", RegexOption.IGNORE_CASE)
        .matchEntire(trimmed)
    if (silenceMatch != null) {
      return "[DEAD SILENCE DROP: ${silenceMatch.groupValues[1]}s]"
    }
    val sfxMatch =
      Regex("""^\[SFX:\s*(.*?)\]$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .matchEntire(trimmed)
    if (sfxMatch != null) {
      return "[SFX: ${sfxMatch.groupValues[1].trim().replace(Regex("""\s+"""), " ")}]"
    }
    return trimmed
  }

  private fun stripMetadataSections(text: String): String {
    val lines = text.lines()
    val output = mutableListOf<String>()
    var insideMetadataBlock = false

    for (rawLine in lines) {
      val trimmed = rawLine.trim()
      if (!insideMetadataBlock) {
        if (sectionBlockHeaderRegex.matches(trimmed)) {
          insideMetadataBlock = true
          continue
        }
        output += rawLine
      } else {
        // Stay inside metadata block while empty, bulleted, numbered, or key:value lines continue;
        // if another metadata header appears, keep skipping. Exit if we reach normal narration
        // prose after a blank line that does not look like a bullet or note.
        if (trimmed.isEmpty() || trimmed == "]" || trimmed == "[/PRODUCTION NOTES]" || trimmed == "[/SOURCE PACK]") {
          continue
        }
        if (sectionBlockHeaderRegex.matches(trimmed) || isLikelyMetadataLine(trimmed)) {
          continue
        }
        // Check if we're in a clearly terminated section and hit real dialogue prose
        if (output.lastOrNull()?.isBlank() == true && trimmed.length > 60 && !trimmed.contains(":")) {
          insideMetadataBlock = false
          output += rawLine
        }
      }
    }
    return output.joinToString("\n")
  }

  private fun isLikelyMetadataLine(trimmed: String): Boolean {
    if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")) return true
    if (trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.contains("___STUDIO_CUE_TOKEN_")) return true
    if (Regex("""^[A-Za-z][A-Za-z0-9 /\-_]{1,32}:\s+.*$""").matches(trimmed)) return true
    if (Regex("""^\d+[.)]\s+.*$""").matches(trimmed)) return true
    return false
  }

  private fun cleanLineFormatting(line: String): String {
    val trimmed = line.trimEnd()
    // Strip markdown heading markers like "## " if a line got prefixed, without touching "..."
    return trimmed.replace(Regex("""^\s{0,3}#{1,6}\s+"""), "")
  }

  private fun isChatterLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.contains("___STUDIO_CUE_TOKEN_")) return false
    return chatterLinePatterns.any { it.matches(trimmed) }
  }
}
