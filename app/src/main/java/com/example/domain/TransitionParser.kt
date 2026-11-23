package com.example.domain

object TransitionParser {
  /**
   * Parses the "Transitions After" setting string:
   * - "1" or "ALL" (case-insensitive) -> insert transition after every part except the last.
   * - Comma-separated 1-based part numbers (e.g. "2,4,6") -> insert transition after each
   *   valid index strictly less than [totalParts].
   */
  fun parseTransitionAfterParts(setting: String, totalParts: Int): Set<Int> {
    if (totalParts <= 1) return emptySet()
    val trimmed = setting.trim()
    if (trimmed.isEmpty()) return emptySet()

    if (trimmed.equals("ALL", ignoreCase = true) || trimmed == "1") {
      return (1 until totalParts).toSet()
    }

    val result = linkedSetOf<Int>()
    for (token in trimmed.split(',')) {
      val partIdx = token.trim().toIntOrNull() ?: continue
      if (partIdx in 1 until totalParts) {
        result.add(partIdx)
      }
    }
    return result
  }
}
