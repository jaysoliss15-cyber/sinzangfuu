package com.example

import com.example.data.ScriptEvent
import com.example.domain.NaturalSort
import com.example.domain.ScriptValidator
import com.example.domain.TransitionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPipelineDomainTest {

  @Test
  fun `parses inline SFX and dead silence cues in order`() {
    val raw =
      """
      The corridor went quiet.
      [SFX: heavy iron door creaking open slowly]
      Nobody moved for three seconds.
      [DEAD SILENCE DROP: 2.5s]
      ...
      ...
      """.trimIndent()

    val qa = ScriptValidator.validateScript(raw)
    assertTrue(qa.isValid)
    assertEquals(5, qa.events.size)
    assertTrue(qa.events[0] is ScriptEvent.Speech)
    assertEquals(
      ScriptEvent.Sfx("heavy iron door creaking open slowly"),
      qa.events[1],
    )
    assertTrue(qa.events[2] is ScriptEvent.Speech)
    assertEquals(ScriptEvent.Silence(2.5), qa.events[3])
    assertTrue(qa.events[4] is ScriptEvent.Speech)
    assertEquals(1, qa.sfxCount)
    assertEquals(1, qa.silenceCount)
  }

  @Test
  fun `detects fatal numerical digits in spoken narration while allowing digits inside cues`() {
    val validWithCueDigit =
      """
      Twenty-five witnesses vanished before dawn.
      [DEAD SILENCE DROP: 3.0s]
      ...
      ...
      """.trimIndent()
    val okResult = ScriptValidator.validateScript(validWithCueDigit)
    assertFalse(okResult.hasFatalDigits)
    assertTrue(okResult.isValid)

    val invalidWithSpokenDigit =
      """
      25 witnesses vanished before dawn.
      ...
      ...
      """.trimIndent()
    val badResult = ScriptValidator.validateScript(invalidWithSpokenDigit)
    assertTrue(badResult.hasFatalDigits)
    assertFalse(badResult.isValid)
  }

  @Test
  fun `chunkSpeechSegment splits long speech cleanly with zero word overlap`() {
    val sentences =
      (1..45).map { idx ->
        "Sentence number ${idx.toString().padStart(2, 'a')} describes the eerie archive hallway in detail."
      }
    val longText = sentences.joinToString(" ")
    val chunks = ScriptValidator.chunkSpeechSegment(longText, maxChars = 1000, softMin = 900)

    assertTrue(chunks.size >= 3)
    assertTrue(chunks.all { it.length <= 1000 })
    // Rejoining chunks with single space must exactly equal original text (zero overlap, zero loss)
    assertEquals(longText, chunks.joinToString(" "))
  }

  @Test
  fun `natural sort orders part 2 before part 10`() {
    val input = listOf("part 10", "part 2", "part 1", "part 20", "part 3")
    val sorted = input.sortedWith(NaturalSort.comparator)
    assertEquals(listOf("part 1", "part 2", "part 3", "part 10", "part 20"), sorted)
  }

  @Test
  fun `transition parser handles ALL, 1, and comma lists`() {
    assertEquals(setOf(1, 2, 3, 4), TransitionParser.parseTransitionAfterParts("ALL", 5))
    assertEquals(setOf(1, 2, 3, 4), TransitionParser.parseTransitionAfterParts("1", 5))
    assertEquals(setOf(2, 4), TransitionParser.parseTransitionAfterParts("2, 4, 6", 5))
    assertEquals(emptySet<Int>(), TransitionParser.parseTransitionAfterParts("2,4", 1))
  }
}
