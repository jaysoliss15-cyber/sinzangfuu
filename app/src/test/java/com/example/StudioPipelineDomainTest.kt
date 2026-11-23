package com.example

import com.example.data.GeminiScriptClient
import com.example.data.ScriptEvent
import com.example.data.VideoRenderPipeline
import com.example.domain.NaturalSort
import com.example.domain.ScriptValidator
import com.example.domain.StrictCleanDialogueSanitizer
import com.example.domain.TransitionParser
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

  @Test
  fun `parses ffmpeg stderr time stamps into total seconds`() {
    val line = "frame=  420 fps= 48 q=21.0 size=   10240kB time=01:02:03.45 bitrate=2412.1kbits/s speed=2.0x"
    val seconds = VideoRenderPipeline.parseFfmpegTimeSeconds(line)
    assertEquals(3723.45, seconds ?: -1.0, 0.001)
    assertNull(VideoRenderPipeline.parseFfmpegTimeSeconds("Stream #0:0 -> #0:0 (copy)"))
  }

  @Test
  fun `monotonic progress tracker never regresses and computes ETA after 3 percent`() {
    var fakeClockMs = 1_000L
    val emitted = mutableListOf<Triple<Int, String, String>>()
    val tracker =
      VideoRenderPipeline.MonotonicProgressTracker(
        startTimeMs = 1_000L,
        clockMs = { fakeClockMs },
      ) { pct, step, eta ->
        emitted += Triple(pct, step, eta)
      }

    fakeClockMs = 2_000L
    tracker.report(2.0, "Step A")
    assertEquals("Calculating...", emitted.last().third)

    fakeClockMs = 11_000L
    tracker.report(10.0, "Step B")
    assertEquals(10, emitted.last().first)
    assertTrue(emitted.last().third != "Calculating...")

    // Attempt backward regression and same-pct rapid update within 250ms -> ignored
    fakeClockMs = 11_050L
    val countBefore = emitted.size
    tracker.report(5.0, "Step B")
    assertEquals(10, tracker.currentPercent)
    assertEquals(countBefore, emitted.size)
  }

  @Test
  fun `strict clean dialogue sanitizer strips notes, fences, tags and chatter while preserving valid cues`() {
    val dirty =
      """
      Sure, here is your cleaned horror narration script:
      ```markdown
      [CHAPTER TITLE: THE ARCHIVE BASEMENT]
      The tape hissed in the dark.
      [SFX: distant heavy footsteps echoing down concrete stairs]
      Nobody breathed for three seconds.
      [DEAD SILENCE DROP: 2.5s]
      Then the lock clicked open.
      ...
      ...
      [OUTRO VIDEO: fade to black]
      ```

      [PRODUCTION NOTES]
      - Tone: sinister documentary
      - Pacing: slow burn

      [SOURCE PACK]
      - Case File Alpha

      [AUDIO / TECHNICAL NOTES]
      - Peak at minus three dB

      Let me know if you'd like any adjustments!
      """.trimIndent()

    assertTrue(StrictCleanDialogueSanitizer.hasContaminatedDirectives(dirty))
    assertFalse(ScriptValidator.validateScript(dirty).isValid)

    val cleaned = StrictCleanDialogueSanitizer.sanitize(dirty)
    assertFalse(StrictCleanDialogueSanitizer.hasContaminatedDirectives(cleaned))
    assertFalse(cleaned.contains("```"))
    assertFalse(cleaned.contains("PRODUCTION NOTES"))
    assertFalse(cleaned.contains("SOURCE PACK"))
    assertFalse(cleaned.contains("TECHNICAL NOTES"))
    assertFalse(cleaned.contains("CHAPTER TITLE"))
    assertFalse(cleaned.contains("OUTRO VIDEO"))
    assertFalse(cleaned.contains("Let me know"))
    assertTrue(cleaned.contains("[SFX: distant heavy footsteps echoing down concrete stairs]"))
    assertTrue(cleaned.contains("[DEAD SILENCE DROP: 2.5s]"))

    val qa = ScriptValidator.validateScript(cleaned)
    assertTrue(qa.isValid)
    assertEquals(1, qa.sfxCount)
    assertEquals(1, qa.silenceCount)
  }

  @Test
  fun `gemini script client retries transient errors with 5s 15s 30s backoff and repairs digits`() =
    runTest {
      val observedDelays = mutableListOf<Long>()
      var callCount = 0

      val client =
        GeminiScriptClient(
          generateRawOverride = { prompt ->
            callCount++
            when (callCount) {
              1 -> throw IOException("HTTP 429 rate limit exceeded")
              2 -> throw IllegalStateException("HTTP 503 service unavailable")
              3 -> throw IOException("Temporary network reset")
              // 4th call completes Self-Audit (still has a stray digit requiring 1 targeted repair)
              4 ->
                """
                ```
                In 1994 the corridor went cold.
                [SFX: flickering fluorescent tube]
                ...
                ...
                ```
                """.trimIndent()
              // 5th call is targeted Repair Pass 1
              else -> {
                assertTrue(prompt.contains("FATAL ERROR"))
                """
                In nineteen ninety-four the corridor went cold.
                [SFX: flickering fluorescent tube]
                ...
                ...
                """.trimIndent()
              }
            }
          },
          delayFn = { ms -> observedDelays += ms },
        )

      val outcome =
        client.runAutoPilotAndAudit(
          topicOrDraft = "The hallway went dark.",
          partNumber = 1,
          generateFromTopic = false,
        )

      assertEquals(listOf(5_000L, 15_000L, 30_000L), observedDelays)
      assertEquals(1, outcome.repairPassesUsed)
      assertTrue(outcome.qaResult.isValid)
      assertFalse(outcome.qaResult.hasFatalDigits)
      assertTrue(outcome.cleanScript.contains("nineteen ninety-four"))
    }

  @Test
  fun `gemini script client loads system instruction from godPromptFile on IO`() =
    runTest {
      val tmpFile = java.io.File.createTempFile("god_prompt_test", ".txt")
      try {
        tmpFile.writeText("Follow dark documentary tone strictly.")
        val logMessages = mutableListOf<String>()
        val client =
          GeminiScriptClient(
            godPromptFile = tmpFile,
            generateRawOverride = { _ ->
              """
              The hallway tape continued spinning in silence.
              [SFX: subtle magnetic tape hiss]
              ...
              ...
              """.trimIndent()
            },
          )

        val outcome =
          client.runAutoPilotAndAudit(
            topicOrDraft = "The hallway tape continued spinning.",
            partNumber = 1,
            generateFromTopic = true,
            onLog = { msg, _ -> logMessages += msg },
          )

        assertTrue(outcome.qaResult.isValid)
        assertTrue(
          logMessages.any {
            it.contains("Loaded master God Prompt (38 chars) from ${tmpFile.name}")
          }
        )
      } finally {
        tmpFile.delete()
      }
    }

  @Test
  fun `normalizes provider base urls cleanly`() {
    assertEquals(
      "https://api.openai.com/v1",
      GeminiScriptClient.normalizeBaseUrl(
        com.example.data.LlmProvider.CUSTOM,
        "https://api.openai.com/v1/chat/completions/",
      ),
    )
    assertEquals(
      "https://openrouter.ai/api/v1",
      GeminiScriptClient.normalizeBaseUrl(
        com.example.data.LlmProvider.OPENROUTER,
        "https://openrouter.ai/api/v1/models",
      ),
    )
  }
}
