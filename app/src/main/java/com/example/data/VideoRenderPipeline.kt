package com.example.data

import com.example.domain.TransitionParser
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class VideoRenderPipeline(
  private val fileManager: StudioFileManager,
  private val ffmpegExecutor: FfmpegExecutor,
) {
  internal class MonotonicProgressTracker(
    private val startTimeMs: Long,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val onProgress: (percent: Int, step: String, eta: String) -> Unit,
  ) {
    private var lastReportedPct = 0
    private var lastEmitTimeMs = 0L
    private var lastEmittedStep = ""

    val currentPercent: Int
      get() = lastReportedPct

    fun report(
      targetPercent: Double,
      step: String,
      force: Boolean = false,
    ) {
      val clampedPct = targetPercent.roundToInt().coerceIn(lastReportedPct, 100)
      val now = clockMs()
      val pctChanged = clampedPct > lastReportedPct
      val stepChanged = step != lastEmittedStep
      val intervalElapsed = (now - lastEmitTimeMs) >= 250L

      if (!force && !pctChanged && !(intervalElapsed && stepChanged)) {
        return
      }

      lastReportedPct = clampedPct
      lastEmitTimeMs = now
      lastEmittedStep = step

      val eta = computeDynamicEta(now, clampedPct)
      onProgress(clampedPct, step, eta)
    }

    fun reportStageProgress(
      stageStartPct: Double,
      stageEndPct: Double,
      fraction: Double,
      step: String,
      force: Boolean = false,
    ) {
      val clampedFraction = fraction.coerceIn(0.0, 1.0)
      val target = stageStartPct + (stageEndPct - stageStartPct) * clampedFraction
      report(target, step, force = force)
    }

    private fun computeDynamicEta(nowMs: Long, pct: Int): String {
      if (pct >= 100) return "Done"
      if (pct < 3) return "Calculating..."
      val elapsedSec = (nowMs - startTimeMs) / 1000.0
      if (elapsedSec < 0.5) return "Calculating..."
      val remainingSec = (elapsedSec * (100 - pct) / pct.toDouble()).toInt().coerceAtLeast(1)
      return formatSecondsStatic(remainingSec.toDouble())
    }
  }

  suspend fun renderFilterPreview(
    partNumber: Int,
    filterMode: RenderFilterMode,
    onProgress: (Int, String, String) -> Unit,
    onLog: (String, LogLevel) -> Unit,
  ): File {
    fileManager.ensureCoreDirectories()
    val partDir = fileManager.getPartDir(partNumber)
    val sourceVideo =
      fileManager.findPartCustomVideoFile(partNumber, partDir)
        ?: fileManager.listStockLoops().firstOrNull()
        ?: throw IllegalStateException(
          "No video available for preview. Add a Stock MP4 in Media Hub or pick a Part MP4."
        )

    val tracker = MonotonicProgressTracker(System.currentTimeMillis(), onProgress = onProgress)
    tracker.report(5.0, "Rendering 3s ${filterMode.displayName} preview...", force = true)
    onLog("Generating 3s filter preview (${filterMode.displayName}) from ${sourceVideo.name}...", LogLevel.INFO)

    val previewOut = File(fileManager.workspaceDir, "filter_preview.mp4")
    val previewTmp = File(fileManager.workspaceDir, ".tmp_filter_preview.mp4")
    try {
      val vf =
        "scale=1920:1080:force_original_aspect_ratio=decrease," +
          "pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24,format=yuv420p," +
          filterMode.ffmpegFilter

      val res =
        ffmpegExecutor.runFfmpeg(
          listOf(
            "-t",
            "3",
            "-i",
            sourceVideo.absolutePath,
            "-vf",
            vf,
            "-c:v",
            "libx264",
            "-preset",
            "ultrafast",
            "-crf",
            "23",
            "-pix_fmt",
            "yuv420p",
            "-r",
            "24",
            "-an",
            previewTmp.absolutePath,
          )
        ) { line ->
          parseFfmpegTimeSeconds(line)?.let { sec ->
            tracker.reportStageProgress(
              5.0,
              98.0,
              sec / 3.0,
              "Rendering 3s ${filterMode.displayName} preview...",
            )
          }
        }
      if (res.exitCode != 0 || !previewTmp.exists() || previewTmp.length() == 0L) {
        throw IllegalStateException("3s preview render failed: ${res.output.takeLast(300)}")
      }
      atomicReplace(previewTmp, previewOut)
      tracker.report(100.0, "3s filter preview ready.", force = true)
      onLog("3s ${filterMode.displayName} preview ready.", LogLevel.SUCCESS)
      return previewOut
    } catch (e: CancellationException) {
      previewTmp.delete()
      throw e
    } catch (e: Exception) {
      previewTmp.delete()
      throw e
    }
  }

  suspend fun renderFullDocumentary(
    config: StudioConfig,
    onProgress: (percent: Int, step: String, eta: String) -> Unit,
    onLog: (String, LogLevel) -> Unit,
  ): File {
    fileManager.ensureCoreDirectories()
    val startTimeMs = System.currentTimeMillis()
    val tracker = MonotonicProgressTracker(startTimeMs, onProgress = onProgress)
    val workspaceDir = fileManager.workspaceDir
    val partDirs = fileManager.listNaturalPartDirs()

    data class ActivePartSpec(
      val partDir: File,
      val partNumber: Int,
      val partKey: String,
      val audioFile: File,
      val videoFile: File,
    )

    val activeParts = mutableListOf<ActivePartSpec>()
    for (dir in partDirs) {
      val partNum = dir.name.removePrefix("part ").trim().toIntOrNull() ?: continue
      val audio = fileManager.findPartAudioFile(partNum, dir) ?: continue
      val video =
        fileManager.pickVideoForPart(partNum, dir)
          ?: throw IllegalStateException(
            "No video found for part $partNum and Stock Loops pool is empty. Add Stock MP4(s) in Media Hub."
          )
      activeParts +=
        ActivePartSpec(
          partDir = dir,
          partNumber = partNum,
          partKey = "part_$partNum",
          audioFile = audio,
          videoFile = video,
        )
    }

    if (activeParts.isEmpty()) {
      throw IllegalStateException("No parts have audio loaded. Download Voice or pick audio first.")
    }

    onLog(
      "Starting Smart Render for ${activeParts.size} active part(s) | Filter: ${config.filterMode.displayName} | Turbo: ${config.turboModeEnabled} | SmartResume: ${config.smartResumeEnabled}",
      LogLevel.INFO,
    )

    val transitionAfterIndices =
      TransitionParser.parseTransitionAfterParts(config.transitionsAfter, activeParts.size)
    val hasTransitionAsset = fileManager.transitionFile.exists() && fileManager.transitionFile.length() > 0L
    if (transitionAfterIndices.isNotEmpty() && !hasTransitionAsset) {
      onLog(
        "Transition B-roll not set in Media Hub; skipping Next Page transitions.",
        LogLevel.WARNING,
      )
    }

    val renderedPartFiles = mutableListOf<File>()
    val partAudioDurations = mutableListOf<Double>()
    val gradedFileByVideoPath = mutableMapOf<String, File>()
    var activeTempFile: File? = null

    try {
      // =========================================================================
      // Stage 1 (0% - 10%): Boomerang + Pre-graded Loop Prep
      // =========================================================================
      val distinctVideos = activeParts.map { it.videoFile }.distinctBy { it.absolutePath }
      val prepSpan = 10.0 / distinctVideos.size.coerceAtLeast(1)

      distinctVideos.forEachIndexed { vIdx, sourceVideo ->
        currentCoroutineContext().ensureActive()
        val slotStart = vIdx * prepSpan
        val slotEnd = (vIdx + 1) * prepSpan
        val videoStem = sanitizeStem(sourceVideo.nameWithoutExtension)
        val boomerangFile = File(workspaceDir, "boomerang_${videoStem}.mp4")
        val gradedFile = File(workspaceDir, "graded_${videoStem}_${config.filterMode.slug}.mp4")

        val boomCached = boomerangFile.exists() && boomerangFile.length() > 0L
        val gradeCached = gradedFile.exists() && gradedFile.length() > 0L

        if (boomCached && gradeCached) {
          tracker.report(
            slotEnd,
            "Loop cache ready: ${sourceVideo.name}",
            force = true,
          )
        } else {
          val sourceDur = ffmpegExecutor.probeDurationSeconds(sourceVideo) ?: 20.0
          val cappedDur = sourceDur.coerceAtMost(20.0).coerceAtLeast(0.5)

          if (!boomCached) {
            val preScaleStart = slotStart
            val preScaleEnd = slotStart + prepSpan * 0.35
            val boomStart = preScaleEnd
            val boomEnd = slotStart + prepSpan * 0.70

            tracker.report(
              preScaleStart,
              "Pre-scaling loop (${sourceVideo.name})...",
              force = true,
            )

            val preScaledTmp = File(workspaceDir, ".tmp_prescale_${videoStem}.mp4")
            val boomerangTmp = File(workspaceDir, ".tmp_boomerang_${videoStem}.mp4")
            activeTempFile = preScaledTmp

            val preScaleRes =
              ffmpegExecutor.runFfmpeg(
                listOf(
                  "-t",
                  "20",
                  "-i",
                  sourceVideo.absolutePath,
                  "-vf",
                  "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24,format=yuv420p",
                  "-c:v",
                  "libx264",
                  "-preset",
                  "ultrafast",
                  "-crf",
                  "20",
                  "-pix_fmt",
                  "yuv420p",
                  "-r",
                  "24",
                  "-an",
                  preScaledTmp.absolutePath,
                )
              ) { line ->
                parseFfmpegTimeSeconds(line)?.let { sec ->
                  tracker.reportStageProgress(
                    preScaleStart,
                    preScaleEnd,
                    sec / cappedDur,
                    "Pre-scaling loop (${sourceVideo.name})...",
                  )
                }
              }
            if (preScaleRes.exitCode != 0 || !preScaledTmp.exists() || preScaledTmp.length() == 0L) {
              throw IllegalStateException(
                "Pre-scale failed for ${sourceVideo.name}: ${preScaleRes.output.takeLast(300)}"
              )
            }

            tracker.report(
              boomStart,
              "Building RAM-safe boomerang (${sourceVideo.name})...",
              force = true,
            )
            activeTempFile = boomerangTmp
            val expectedBoomDur = (cappedDur * 2.0).coerceAtLeast(1.0)
            val boomRes =
              ffmpegExecutor.runFfmpeg(
                listOf(
                  "-i",
                  preScaledTmp.absolutePath,
                  "-filter_complex",
                  "[0:v]reverse[r];[0:v][r]concat=n=2:v=1:a=0[v]",
                  "-map",
                  "[v]",
                  "-c:v",
                  "libx264",
                  "-preset",
                  "ultrafast",
                  "-crf",
                  "23",
                  "-profile:v",
                  "high",
                  "-level",
                  "4.1",
                  "-g",
                  "24",
                  "-keyint_min",
                  "24",
                  "-sc_threshold",
                  "0",
                  "-pix_fmt",
                  "yuv420p",
                  "-r",
                  "24",
                  "-video_track_timescale",
                  "90000",
                  boomerangTmp.absolutePath,
                )
              ) { line ->
                parseFfmpegTimeSeconds(line)?.let { sec ->
                  tracker.reportStageProgress(
                    boomStart,
                    boomEnd,
                    sec / expectedBoomDur,
                    "Building RAM-safe boomerang (${sourceVideo.name})...",
                  )
                }
              }
            preScaledTmp.delete()
            if (boomRes.exitCode != 0 || !boomerangTmp.exists() || boomerangTmp.length() == 0L) {
              throw IllegalStateException(
                "Boomerang generation failed for ${sourceVideo.name}: ${boomRes.output.takeLast(300)}"
              )
            }
            atomicReplace(boomerangTmp, boomerangFile)
            activeTempFile = null
            onLog("Cached RAM-safe boomerang: ${boomerangFile.name}", LogLevel.INFO)
          }

          if (!gradeCached) {
            val gradeStart = slotStart + prepSpan * 0.70
            tracker.report(
              gradeStart,
              "Pre-grading loop (${config.filterMode.displayName})...",
              force = true,
            )
            val gradedTmp = File(workspaceDir, ".tmp_graded_${videoStem}_${config.filterMode.slug}.mp4")
            activeTempFile = gradedTmp
            val boomDur =
              (ffmpegExecutor.probeDurationSeconds(boomerangFile) ?: (cappedDur * 2.0)).coerceAtLeast(0.5)

            val gradeRes =
              ffmpegExecutor.runFfmpeg(
                listOf(
                  "-i",
                  boomerangFile.absolutePath,
                  "-vf",
                  "${config.filterMode.ffmpegFilter},fps=24,format=yuv420p",
                  "-c:v",
                  "libx264",
                  "-preset",
                  "fast",
                  "-crf",
                  "21",
                  "-profile:v",
                  "high",
                  "-level",
                  "4.1",
                  "-g",
                  "24",
                  "-keyint_min",
                  "24",
                  "-sc_threshold",
                  "0",
                  "-pix_fmt",
                  "yuv420p",
                  "-r",
                  "24",
                  "-video_track_timescale",
                  "90000",
                  "-an",
                  gradedTmp.absolutePath,
                )
              ) { line ->
                parseFfmpegTimeSeconds(line)?.let { sec ->
                  tracker.reportStageProgress(
                    gradeStart,
                    slotEnd,
                    sec / boomDur,
                    "Pre-grading loop (${config.filterMode.displayName})...",
                  )
                }
              }
            if (gradeRes.exitCode != 0 || !gradedTmp.exists() || gradedTmp.length() == 0L) {
              throw IllegalStateException(
                "Pre-grading failed for ${boomerangFile.name}: ${gradeRes.output.takeLast(300)}"
              )
            }
            atomicReplace(gradedTmp, gradedFile)
            activeTempFile = null
            onLog("Cached pre-graded loop: ${gradedFile.name}", LogLevel.INFO)
          } else {
            tracker.report(slotEnd, "Pre-graded loop ready: ${gradedFile.name}", force = true)
          }
        }
        gradedFileByVideoPath[sourceVideo.absolutePath] = gradedFile
      }
      tracker.report(10.0, "Loop caches ready.", force = true)

      // =========================================================================
      // Stage 2 (10% - 75%): Per-Part Broadcast Audio + Part Rendering
      // =========================================================================
      val partSpan = 65.0 / activeParts.size
      activeParts.forEachIndexed { idx, spec ->
        currentCoroutineContext().ensureActive()
        val partStart = 10.0 + idx * partSpan
        val audioEnd = partStart + partSpan * 0.10
        val partEnd = 10.0 + (idx + 1) * partSpan

        tracker.report(
          partStart,
          "Part ${spec.partNumber}: Preparing broadcast audio...",
          force = true,
        )
        val voiceRawWav = File(workspaceDir, "${spec.partKey}_voice_raw.wav")
        val tmpVoiceRaw = File(workspaceDir, ".tmp_${spec.partKey}_voice_raw.wav")
        activeTempFile = tmpVoiceRaw

        val rawAudioDur = (ffmpegExecutor.probeDurationSeconds(spec.audioFile) ?: 1.0).coerceAtLeast(0.5)
        val isMixAudio = spec.audioFile.nameWithoutExtension.lowercase(Locale.ROOT).endsWith("_mix")
        val audioFilter =
          if (isMixAudio) "volume=1.5,alimiter=limit=0.98"
          else "volume=1.5,dynaudnorm=p=0.95,alimiter=limit=0.95"

        val audioRes =
          ffmpegExecutor.runFfmpeg(
            listOf(
              "-i",
              spec.audioFile.absolutePath,
              "-af",
              audioFilter,
              "-ar",
              "44100",
              "-ac",
              "2",
              "-c:a",
              "pcm_s16le",
              tmpVoiceRaw.absolutePath,
            )
          ) { line ->
            parseFfmpegTimeSeconds(line)?.let { sec ->
              tracker.reportStageProgress(
                partStart,
                audioEnd,
                sec / rawAudioDur,
                "Part ${spec.partNumber}: Preparing broadcast audio...",
              )
            }
          }
        if (audioRes.exitCode != 0 || !tmpVoiceRaw.exists() || tmpVoiceRaw.length() == 0L) {
          throw IllegalStateException(
            "Broadcast audio normalization failed on Part ${spec.partNumber}: ${audioRes.output.takeLast(300)}"
          )
        }
        atomicReplace(tmpVoiceRaw, voiceRawWav)
        activeTempFile = null

        val audioDuration =
          ffmpegExecutor.probeDurationSeconds(voiceRawWav)
            ?: throw IllegalStateException("Could not probe audio duration for Part ${spec.partNumber}.")
        partAudioDurations += audioDuration

        val gradedFile =
          gradedFileByVideoPath[spec.videoFile.absolutePath]
            ?: throw IllegalStateException("Missing pre-graded loop for ${spec.videoFile.name}.")

        val renderedPartFile = File(workspaceDir, "${spec.partKey}_rendered.mp4")
        val sigFile = File(workspaceDir, "${spec.partKey}_rendered.sig")
        val signature =
          "${spec.audioFile.lastModified()}|${spec.audioFile.length()}|${spec.videoFile.name}|${config.filterMode.slug}|${config.turboModeEnabled}"

        val canResume =
          config.smartResumeEnabled &&
            renderedPartFile.exists() &&
            renderedPartFile.length() > 0L &&
            sigFile.exists() &&
            sigFile.readText().trim() == signature

        if (canResume) {
          onLog("Smart Resume: Skipped Part ${spec.partNumber} (signature matched).", LogLevel.SUCCESS)
          tracker.report(partEnd, "Part ${spec.partNumber}: Smart Resume skipped.", force = true)
        } else {
          val modeLabel = if (config.turboModeEnabled) "Turbo" else "Full Fade"
          val stepLabel = "Part ${spec.partNumber}: Rendering ${formatSeconds(audioDuration)} ($modeLabel)..."
          tracker.report(audioEnd, stepLabel, force = true)

          val partTmp = File(workspaceDir, ".tmp_${spec.partKey}_rendered.mp4")
          activeTempFile = partTmp
          val durStr = formatDuration(audioDuration)

          val partArgs =
            if (config.turboModeEnabled) {
              listOf(
                "-stream_loop",
                "-1",
                "-i",
                gradedFile.absolutePath,
                "-i",
                voiceRawWav.absolutePath,
                "-map",
                "0:v",
                "-map",
                "1:a",
                "-t",
                durStr,
                "-c:v",
                "copy",
                "-c:a",
                "aac",
                "-b:a",
                "320k",
                "-ac",
                "2",
                "-ar",
                "44100",
                "-shortest",
                partTmp.absolutePath,
              )
            } else {
              val fadeOutStart = max(0.0, audioDuration - 1.2)
              val fadeFilter =
                "fade=t=in:st=0:d=1.0,fade=t=out:st=${formatDuration(fadeOutStart)}:d=1.0,fps=24,format=yuv420p"
              listOf(
                "-stream_loop",
                "-1",
                "-i",
                gradedFile.absolutePath,
                "-i",
                voiceRawWav.absolutePath,
                "-vf",
                fadeFilter,
                "-map",
                "0:v",
                "-map",
                "1:a",
                "-t",
                durStr,
                "-threads",
                "4",
                "-c:v",
                "libx264",
                "-preset",
                "fast",
                "-crf",
                "21",
                "-profile:v",
                "high",
                "-level",
                "4.1",
                "-g",
                "24",
                "-keyint_min",
                "24",
                "-sc_threshold",
                "0",
                "-pix_fmt",
                "yuv420p",
                "-r",
                "24",
                "-video_track_timescale",
                "90000",
                "-c:a",
                "aac",
                "-b:a",
                "320k",
                "-ac",
                "2",
                "-ar",
                "44100",
                "-shortest",
                partTmp.absolutePath,
              )
            }

          val safePartDur = audioDuration.coerceAtLeast(0.5)
          val partRes =
            ffmpegExecutor.runFfmpeg(partArgs) { line ->
              parseFfmpegTimeSeconds(line)?.let { sec ->
                tracker.reportStageProgress(audioEnd, partEnd, sec / safePartDur, stepLabel)
              }
            }
          if (partRes.exitCode != 0 || !partTmp.exists() || partTmp.length() == 0L) {
            throw IllegalStateException(
              "Part ${spec.partNumber} render failed: ${partRes.output.takeLast(300)}"
            )
          }
          atomicReplace(partTmp, renderedPartFile)
          sigFile.writeText(signature, Charsets.UTF_8)
          activeTempFile = null
          tracker.report(partEnd, "Part ${spec.partNumber}: Rendered.", force = true)
          onLog("Rendered Part ${spec.partNumber}: ${renderedPartFile.name}", LogLevel.SUCCESS)
        }
        renderedPartFiles += renderedPartFile
      }
      tracker.report(75.0, "All parts rendered.", force = true)

      // =========================================================================
      // Stage 3 (75% - 82%): Transition & Outro Standardization
      // =========================================================================
      currentCoroutineContext().ensureActive()
      val needsTransition = transitionAfterIndices.isNotEmpty() && hasTransitionAsset
      val needsOutro = fileManager.outroFile.exists() && fileManager.outroFile.length() > 0L
      val prepTaskCount = (if (needsTransition) 1 else 0) + (if (needsOutro) 1 else 0)
      val prepSlotSpan = if (prepTaskCount > 0) 7.0 / prepTaskCount else 0.0
      var nextPrepStart = 75.0

      var stdTransitionFile: File? = null
      var transDur = 0.0
      if (needsTransition) {
        val slotStart = nextPrepStart
        val slotEnd = slotStart + prepSlotSpan
        nextPrepStart = slotEnd

        val rawTransition = fileManager.transitionFile
        val targetStdTransition = File(workspaceDir, "BAGONG_PAHiNA_6S_standard.mp4")
        val tmpStdTransition = File(workspaceDir, ".tmp_BAGONG_PAHiNA_6S_standard.mp4")
        activeTempFile = tmpStdTransition

        transDur = ffmpegExecutor.probeDurationSeconds(rawTransition) ?: 6.0
        val hasAudio = ffmpegExecutor.hasAudioStream(rawTransition)
        val durStr = formatDuration(transDur)
        val scaleVf =
          "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24,format=yuv420p"

        tracker.report(slotStart, "Standardizing Next Page transition B-roll...", force = true)

        val transArgs =
          if (hasAudio) {
            listOf(
              "-i",
              rawTransition.absolutePath,
              "-t",
              durStr,
              "-vf",
              scaleVf,
              "-af",
              "aresample=44100,apad",
              "-c:v",
              "libx264",
              "-preset",
              "fast",
              "-crf",
              "20",
              "-profile:v",
              "high",
              "-level",
              "4.1",
              "-g",
              "24",
              "-keyint_min",
              "24",
              "-sc_threshold",
              "0",
              "-pix_fmt",
              "yuv420p",
              "-r",
              "24",
              "-video_track_timescale",
              "90000",
              "-c:a",
              "aac",
              "-b:a",
              "320k",
              "-ac",
              "2",
              "-ar",
              "44100",
              "-shortest",
              tmpStdTransition.absolutePath,
            )
          } else {
            listOf(
              "-i",
              rawTransition.absolutePath,
              "-f",
              "lavfi",
              "-i",
              "anullsrc=channel_layout=stereo:sample_rate=44100",
              "-t",
              durStr,
              "-map",
              "0:v",
              "-map",
              "1:a",
              "-vf",
              scaleVf,
              "-c:v",
              "libx264",
              "-preset",
              "fast",
              "-crf",
              "20",
              "-profile:v",
              "high",
              "-level",
              "4.1",
              "-g",
              "24",
              "-keyint_min",
              "24",
              "-sc_threshold",
              "0",
              "-pix_fmt",
              "yuv420p",
              "-r",
              "24",
              "-video_track_timescale",
              "90000",
              "-c:a",
              "aac",
              "-b:a",
              "320k",
              "-ac",
              "2",
              "-ar",
              "44100",
              "-shortest",
              tmpStdTransition.absolutePath,
            )
          }

        val safeTransDur = transDur.coerceAtLeast(0.5)
        val transRes =
          ffmpegExecutor.runFfmpeg(transArgs) { line ->
            parseFfmpegTimeSeconds(line)?.let { sec ->
              tracker.reportStageProgress(
                slotStart,
                slotEnd,
                sec / safeTransDur,
                "Standardizing Next Page transition B-roll...",
              )
            }
          }
        if (transRes.exitCode != 0 || !tmpStdTransition.exists() || tmpStdTransition.length() == 0L) {
          throw IllegalStateException("Transition standardization failed: ${transRes.output.takeLast(300)}")
        }
        atomicReplace(tmpStdTransition, targetStdTransition)
        activeTempFile = null
        stdTransitionFile = targetStdTransition
        onLog("Standardized clean Next Page B-roll (${formatSeconds(transDur)}).", LogLevel.INFO)
      }

      var stdOutroFile: File? = null
      var outroDuration = 0.0
      if (needsOutro) {
        val slotStart = nextPrepStart
        val slotEnd = slotStart + prepSlotSpan
        val rawOutro = fileManager.outroFile
        val targetOutro = File(workspaceDir, "outro_standard.mp4")
        val tmpOutro = File(workspaceDir, ".tmp_outro_standard.mp4")
        activeTempFile = tmpOutro

        outroDuration = ffmpegExecutor.probeDurationSeconds(rawOutro) ?: 5.0
        val hasAudio = ffmpegExecutor.hasAudioStream(rawOutro)
        val scaleVf =
          "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=24,format=yuv420p"

        tracker.report(slotStart, "Standardizing Outro vanity card...", force = true)

        val outroArgs =
          if (hasAudio) {
            listOf(
              "-i",
              rawOutro.absolutePath,
              "-vf",
              scaleVf,
              "-af",
              "volume=0.3,aresample=44100,apad",
              "-c:v",
              "libx264",
              "-preset",
              "fast",
              "-crf",
              "20",
              "-profile:v",
              "high",
              "-level",
              "4.1",
              "-g",
              "24",
              "-keyint_min",
              "24",
              "-sc_threshold",
              "0",
              "-pix_fmt",
              "yuv420p",
              "-r",
              "24",
              "-video_track_timescale",
              "90000",
              "-c:a",
              "aac",
              "-b:a",
              "320k",
              "-ac",
              "2",
              "-ar",
              "44100",
              "-shortest",
              tmpOutro.absolutePath,
            )
          } else {
            listOf(
              "-i",
              rawOutro.absolutePath,
              "-f",
              "lavfi",
              "-i",
              "anullsrc=channel_layout=stereo:sample_rate=44100",
              "-t",
              formatDuration(if (outroDuration > 0.0) outroDuration else 5.0),
              "-map",
              "0:v",
              "-map",
              "1:a",
              "-vf",
              scaleVf,
              "-c:v",
              "libx264",
              "-preset",
              "fast",
              "-crf",
              "20",
              "-profile:v",
              "high",
              "-level",
              "4.1",
              "-g",
              "24",
              "-keyint_min",
              "24",
              "-sc_threshold",
              "0",
              "-pix_fmt",
              "yuv420p",
              "-r",
              "24",
              "-video_track_timescale",
              "90000",
              "-c:a",
              "aac",
              "-b:a",
              "320k",
              "-ac",
              "2",
              "-ar",
              "44100",
              "-shortest",
              tmpOutro.absolutePath,
            )
          }

        val safeOutroDur = outroDuration.coerceAtLeast(0.5)
        val outroRes =
          ffmpegExecutor.runFfmpeg(outroArgs) { line ->
            parseFfmpegTimeSeconds(line)?.let { sec ->
              tracker.reportStageProgress(
                slotStart,
                slotEnd,
                sec / safeOutroDur,
                "Standardizing Outro vanity card...",
              )
            }
          }
        if (outroRes.exitCode != 0 || !tmpOutro.exists() || tmpOutro.length() == 0L) {
          throw IllegalStateException("Outro standardization failed: ${outroRes.output.takeLast(300)}")
        }
        atomicReplace(tmpOutro, targetOutro)
        activeTempFile = null
        stdOutroFile = targetOutro
        outroDuration = ffmpegExecutor.probeDurationSeconds(targetOutro) ?: outroDuration
        onLog("Standardized outro card (${formatSeconds(outroDuration)}).", LogLevel.INFO)
      }
      tracker.report(82.0, "Transitions & outro standardized.", force = true)

      // =========================================================================
      // Stage 4 (82% - 88%): Master Concat (MASTER_NO_BGM.mp4)
      // =========================================================================
      currentCoroutineContext().ensureActive()
      tracker.report(82.0, "Concatenating master video stream...", force = true)

      val concatEntries = mutableListOf<File>()
      var insertedTransitions = 0
      renderedPartFiles.forEachIndexed { index, partFile ->
        concatEntries += partFile
        val partIdx1Based = index + 1
        if (stdTransitionFile != null && partIdx1Based in transitionAfterIndices) {
          concatEntries += stdTransitionFile
          insertedTransitions++
          onLog("Inserted clean transition after Part ${activeParts[index].partNumber}.", LogLevel.INFO)
        }
      }
      if (stdOutroFile != null) {
        concatEntries += stdOutroFile
      }

      val masterListFile = File(workspaceDir, "master_video_list.txt")
      masterListFile.writeText(
        concatEntries.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" } + "\n",
        Charsets.UTF_8,
      )

      val masterNoBgm = File(workspaceDir, "MASTER_NO_BGM.mp4")
      val tmpMasterNoBgm = File(workspaceDir, ".tmp_MASTER_NO_BGM.mp4")
      activeTempFile = tmpMasterNoBgm

      val totalEstimatedDuration =
        (partAudioDurations.sum() + (insertedTransitions * transDur) + outroDuration).coerceAtLeast(1.0)

      val concatRes =
        ffmpegExecutor.runFfmpeg(
          listOf(
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            masterListFile.absolutePath,
            "-c",
            "copy",
            tmpMasterNoBgm.absolutePath,
          )
        ) { line ->
          parseFfmpegTimeSeconds(line)?.let { sec ->
            tracker.reportStageProgress(
              82.0,
              88.0,
              sec / totalEstimatedDuration,
              "Concatenating master video stream...",
            )
          }
        }
      if (concatRes.exitCode != 0 || !tmpMasterNoBgm.exists() || tmpMasterNoBgm.length() == 0L) {
        throw IllegalStateException("Master concat failed: ${concatRes.output.takeLast(300)}")
      }
      atomicReplace(tmpMasterNoBgm, masterNoBgm)
      activeTempFile = null
      tracker.report(88.0, "Master concat complete.", force = true)

      val masterDuration =
        ffmpegExecutor.probeDurationSeconds(masterNoBgm)
          ?: throw IllegalStateException("Failed to probe MASTER_NO_BGM.mp4 duration.")

      // =========================================================================
      // Stage 5 (88% - 97%): Final BGM Mix & Metadata Wipe
      // =========================================================================
      currentCoroutineContext().ensureActive()
      tracker.report(88.0, "Mixing BGM & stripping metadata...", force = true)

      val finalOutput = File(fileManager.outputDir, "FINAL_DOCUMENTARY_STUDIO_MIX.mp4")
      val tmpFinalOutput = File(fileManager.outputDir, ".tmp_FINAL_DOCUMENTARY_STUDIO_MIX.mp4")
      activeTempFile = tmpFinalOutput

      val finalArgs =
        if (fileManager.bgmFile.exists() && fileManager.bgmFile.length() > 0L) {
          val fadeStart =
            if (outroDuration > 0.0) max(0.0, masterDuration - outroDuration - 2.0)
            else max(0.0, masterDuration - 5.0)
          val filterComplex =
            "[1:a]volume=0.10,afade=t=out:st=${formatDuration(fadeStart)}:d=3[bgm];" +
              "[0:a]volume=1.0[main_audio];" +
              "[main_audio][bgm]amix=inputs=2:duration=first:dropout_transition=2,volume=2.0[a_mix]"
          listOf(
            "-i",
            masterNoBgm.absolutePath,
            "-stream_loop",
            "-1",
            "-i",
            fileManager.bgmFile.absolutePath,
            "-filter_complex",
            filterComplex,
            "-map",
            "0:v",
            "-map",
            "[a_mix]",
            "-t",
            formatDuration(masterDuration),
            "-map_metadata",
            "-1",
            "-fflags",
            "+bitexact",
            "-c:v",
            "copy",
            "-c:a",
            "aac",
            "-b:a",
            "320k",
            tmpFinalOutput.absolutePath,
          )
        } else {
          listOf(
            "-i",
            masterNoBgm.absolutePath,
            "-map_metadata",
            "-1",
            "-fflags",
            "+bitexact",
            "-c",
            "copy",
            tmpFinalOutput.absolutePath,
          )
        }

      val safeMasterDur = masterDuration.coerceAtLeast(1.0)
      val finalRes =
        ffmpegExecutor.runFfmpeg(finalArgs) { line ->
          parseFfmpegTimeSeconds(line)?.let { sec ->
            tracker.reportStageProgress(
              88.0,
              97.0,
              sec / safeMasterDur,
              "Mixing BGM & stripping metadata...",
            )
          }
        }
      if (finalRes.exitCode != 0 || !tmpFinalOutput.exists() || tmpFinalOutput.length() == 0L) {
        throw IllegalStateException("Final BGM mix / metadata wipe failed: ${finalRes.output.takeLast(300)}")
      }
      atomicReplace(tmpFinalOutput, finalOutput)
      activeTempFile = null

      // =========================================================================
      // Stage 6 (97% - 100%): Gallery Export & Optional Auto-Clean
      // =========================================================================
      currentCoroutineContext().ensureActive()
      tracker.report(97.0, "Exporting to Movies/TheMissingPage/...", force = true)
      runCatching { fileManager.saveVideoToGallery(finalOutput) }
        .onSuccess {
          onLog("Auto-exported to Gallery: Movies/TheMissingPage/${finalOutput.name}", LogLevel.SUCCESS)
        }
        .onFailure { err ->
          onLog("Gallery auto-export warning: ${err.message}", LogLevel.WARNING)
        }

      if (config.autoCleanAfterExport) {
        tracker.report(99.0, "Auto-cleaning temp workspace/...", force = true)
        fileManager.cleanTempWorkspaceOnly()
        onLog("Auto-cleaned temp workspace/ after export.", LogLevel.INFO)
      }

      tracker.report(100.0, "Render complete (${formatSeconds(masterDuration)})", force = true)
      onLog(
        "FINISHED: ${finalOutput.name} (${formatSeconds(masterDuration)}, ${String.format(Locale.US, "%.1f", finalOutput.length() / (1024.0 * 1024.0))} MB)",
        LogLevel.SUCCESS,
      )
      return finalOutput
    } catch (e: CancellationException) {
      activeTempFile?.delete()
      throw e
    } catch (e: Exception) {
      activeTempFile?.delete()
      throw e
    }
  }

  private fun sanitizeStem(raw: String): String =
    raw.replace(Regex("""[^A-Za-z0-9_-]"""), "_").ifBlank { "loop" }

  private fun formatDuration(seconds: Double): String =
    String.format(Locale.US, "%.3f", seconds.coerceAtLeast(0.0))

  private fun atomicReplace(tmpFile: File, targetFile: File) {
    if (targetFile.exists()) targetFile.delete()
    if (!tmpFile.renameTo(targetFile)) {
      tmpFile.copyTo(targetFile, overwrite = true)
      tmpFile.delete()
    }
  }

  private fun formatSeconds(sec: Double): String = formatSecondsStatic(sec)

  companion object {
    private val ffmpegTimeRegex = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")

    fun parseFfmpegTimeSeconds(line: String): Double? {
      val match = ffmpegTimeRegex.find(line) ?: return null
      val hours = match.groupValues[1].toDoubleOrNull() ?: return null
      val minutes = match.groupValues[2].toDoubleOrNull() ?: return null
      val seconds = match.groupValues[3].toDoubleOrNull() ?: return null
      return (hours * 3600.0) + (minutes * 60.0) + seconds
    }

    internal fun formatSecondsStatic(sec: Double): String {
      val total = sec.toInt().coerceAtLeast(0)
      val mins = total / 60
      val rem = total % 60
      return if (mins > 0) "${mins}m ${rem}s" else "${rem}s"
    }
  }
}
