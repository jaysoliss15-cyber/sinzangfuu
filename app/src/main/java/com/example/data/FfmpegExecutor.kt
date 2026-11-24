package com.example.data

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class CommandExecutionResult(
  val exitCode: Int,
  val output: String,
  val wasCancelled: Boolean = false,
)

class FfmpegExecutor(private val context: Context) {
  private val activeProcess = AtomicReference<Process?>(null)
  private val cancelRequested = AtomicBoolean(false)

  val ffmpegBinary: File
    get() = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

  val ffprobeBinary: File
    get() = File(context.applicationInfo.nativeLibraryDir, "libffprobe.so")

  fun resetCancellationFlag() {
    cancelRequested.set(false)
  }

  fun cancelCurrentProcess() {
    cancelRequested.set(true)
    val process = activeProcess.getAndSet(null)
    if (process != null) {
      runCatching { process.destroyForcibly() }
    }
  }

  fun areBinariesAvailable(): Boolean =
    ffmpegBinary.exists() && ffmpegBinary.canExecute() && ffprobeBinary.exists() && ffprobeBinary.canExecute()

  suspend fun runFfmpeg(
    args: List<String>,
    onOutputLine: ((String) -> Unit)? = null,
  ): CommandExecutionResult {
    val finalArgs = if (args.firstOrNull() == "-y") args else listOf("-y") + args
    return executeBinary(ffmpegBinary, finalArgs, onOutputLine)
  }

  suspend fun probeDurationSeconds(file: File): Double? {
    if (!file.exists() || !file.isFile || file.length() == 0L) return null
    val result =
      executeBinary(
        binary = ffprobeBinary,
        args =
          listOf(
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            file.absolutePath,
          ),
      )
    if (result.exitCode != 0) return null
    return result.output.trim().lines().firstOrNull()?.toDoubleOrNull()?.takeIf { it > 0.0 }
  }

  suspend fun hasAudioStream(file: File): Boolean {
    if (!file.exists() || !file.isFile || file.length() == 0L) return false
    val result =
      executeBinary(
        binary = ffprobeBinary,
        args =
          listOf(
            "-v",
            "error",
            "-select_streams",
            "a:0",
            "-show_entries",
            "stream=codec_type",
            "-of",
            "csv=p=0",
            file.absolutePath,
          ),
      )
    return result.exitCode == 0 && result.output.trim().contains("audio", ignoreCase = true)
  }

  private suspend fun executeBinary(
    binary: File,
    args: List<String>,
    onOutputLine: ((String) -> Unit)? = null,
  ): CommandExecutionResult =
    withContext(Dispatchers.IO) {
      currentCoroutineContext().ensureActive()
      if (cancelRequested.get()) {
        throw CancellationException("Cancelled before starting ${binary.name}")
      }
      if (!binary.exists()) {
        return@withContext CommandExecutionResult(
          exitCode = -1,
          output =
            "Native binary missing: ${binary.absolutePath}. Ensure arm64-v8a static binaries are packaged.",
        )
      }
      if (!binary.canExecute()) {
        binary.setExecutable(true, true)
      }

      val command = listOf(binary.absolutePath) + args
      val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
      val process = processBuilder.start()
      activeProcess.set(process)
      if (cancelRequested.get()) {
        runCatching { process.destroyForcibly() }
        activeProcess.compareAndSet(process, null)
        throw CancellationException("Process cancelled by user")
      }

      val outputBuffer = StringBuilder()
      try {
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
          while (true) {
            if (cancelRequested.get()) {
              runCatching { process.destroyForcibly() }
              throw CancellationException("Process cancelled by user")
            }
            currentCoroutineContext().ensureActive()
            val line = reader.readLine() ?: break
            outputBuffer.appendLine(line)
            onOutputLine?.invoke(line)
          }
        }
        val exitCode = process.waitFor()
        if (cancelRequested.get()) {
          throw CancellationException("Process cancelled by user")
        }
        CommandExecutionResult(exitCode = exitCode, output = outputBuffer.toString())
      } catch (e: CancellationException) {
        runCatching { process.destroyForcibly() }
        throw e
      } finally {
        activeProcess.compareAndSet(process, null)
      }
    }

  companion object {
    fun formatConcatEntry(file: File): String {
      val escapedPath = file.absolutePath.replace("'", "'\\''")
      return "file '$escapedPath'"
    }

    fun buildConcatManifest(files: List<File>): String {
      require(files.isNotEmpty()) { "FFmpeg concat manifest requires at least one input file." }
      files.forEach { file ->
        require(file.exists() && file.isFile && file.length() > 0L) {
          "FFmpeg concat input missing or empty: ${file.absolutePath}"
        }
      }
      return files.joinToString(separator = "\n", postfix = "\n") { formatConcatEntry(it) }
    }

    fun lastStderrLines(output: String, maxLines: Int = 10): String {
      val tail =
        output
          .lineSequence()
          .map { it.trimEnd() }
          .filter { it.isNotBlank() }
          .toList()
          .takeLast(maxLines.coerceAtLeast(1))
      return if (tail.isEmpty()) "no stderr output" else tail.joinToString("\n")
    }
  }
}
