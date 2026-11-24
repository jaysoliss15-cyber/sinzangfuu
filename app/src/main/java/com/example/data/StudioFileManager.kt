package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.domain.NaturalSort
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class StudioFileManager(private val context: Context) {
  val episodeWorkspaceDir: File = File(context.filesDir, "episode_workspace")
  val workspaceDir: File = File(context.filesDir, "workspace")
  val stockLoopsDir: File = File(context.filesDir, "stock_loops")
  val assetsDir: File = File(context.filesDir, "assets")
  val transitionsDir: File = File(assetsDir, "transitions")
  val transitionFile: File = File(transitionsDir, "BAGONG_PAHiNA_6S.mp4")
  val outroFile: File = File(assetsDir, "outro_vanity_card.mp4")
  val bgmFile: File = File(assetsDir, "the_dread.mp3")
  val outputDir: File = File(context.filesDir, "output")
  val godPromptFile: File = File(context.filesDir, "god_prompt_master.txt")

  init {
    ensureCoreDirectories()
  }

  fun ensureCoreDirectories() {
    episodeWorkspaceDir.mkdirs()
    workspaceDir.mkdirs()
    stockLoopsDir.mkdirs()
    transitionsDir.mkdirs()
    outputDir.mkdirs()
  }

  fun getPartDir(partNumber: Int): File = File(episodeWorkspaceDir, "part $partNumber")

  fun ensurePartFolders(partCount: Int) {
    ensureCoreDirectories()
    val safeCount = partCount.coerceIn(1, 30)
    for (i in 1..safeCount) {
      getPartDir(i).mkdirs()
    }
    episodeWorkspaceDir.listFiles()?.forEach { child ->
      if (child.isDirectory && child.name.startsWith("part ")) {
        val num = child.name.removePrefix("part ").trim().toIntOrNull()
        if (num == null || num > safeCount || num < 1) {
          child.deleteRecursively()
        }
      }
    }
  }

  fun listNaturalPartDirs(): List<File> {
    val dirs =
      episodeWorkspaceDir
        .listFiles()
        ?.filter { it.isDirectory && it.name.lowercase(Locale.ROOT).startsWith("part ") }
        ?: emptyList()
    return NaturalSort.sortedNaturally(dirs) { it.name }
  }

  fun getPartStatuses(partCount: Int): List<PartStatus> {
    ensurePartFolders(partCount)
    return (1..partCount.coerceIn(1, 30)).map { partNum ->
      val dir = getPartDir(partNum)
      val audio = findPartAudioFile(partNum, dir)
      val video = findPartCustomVideoFile(partNum, dir)
      val transcript = File(dir, "${partNum}_transcript.txt")
      PartStatus(
        partNumber = partNum,
        hasAudio = audio != null && audio.length() > 0L,
        audioFileName = audio?.name,
        hasCustomVideo = video != null && video.length() > 0L,
        videoFileName = video?.name,
        hasTranscript = transcript.exists() && transcript.length() > 0L,
      )
    }
  }

  /**
   * Audio lookup priority per part:
   * X_mix.wav -> X.wav -> X_mix.mp3 -> X.mp3 -> first naturally sorted .mp3/.wav excluding
   * _new, _voice_new, _raw stems.
   */
  fun findPartAudioFile(partNum: Int, partDir: File = getPartDir(partNum)): File? {
    if (!partDir.exists()) return null
    val priorityNames =
      listOf("${partNum}_mix.wav", "$partNum.wav", "${partNum}_mix.mp3", "$partNum.mp3")
    for (name in priorityNames) {
      val candidate = File(partDir, name)
      if (candidate.exists() && candidate.isFile && candidate.length() > 0L) {
        return candidate
      }
    }
    val excludedSuffixes = listOf("_new", "_voice_new", "_raw")
    val candidates =
      partDir
        .listFiles()
        ?.filter { file ->
          file.isFile &&
            file.length() > 0L &&
            file.extension.lowercase(Locale.ROOT) in setOf("mp3", "wav") &&
            excludedSuffixes.none { suffix ->
              file.nameWithoutExtension.lowercase(Locale.ROOT).endsWith(suffix)
            }
        }
        ?: emptyList()
    return NaturalSort.sortedNaturally(candidates) { it.name }.firstOrNull()
  }

  fun findPartCustomVideoFile(partNum: Int, partDir: File = getPartDir(partNum)): File? {
    if (!partDir.exists()) return null
    val exact = File(partDir, "$partNum.mp4")
    if (exact.exists() && exact.isFile && exact.length() > 0L) return exact
    val others =
      partDir
        .listFiles()
        ?.filter {
          it.isFile && it.length() > 0L && it.extension.equals("mp4", ignoreCase = true)
        }
        ?: emptyList()
    return NaturalSort.sortedNaturally(others) { it.name }.firstOrNull()
  }

  fun listStockLoops(): List<File> {
    val files =
      stockLoopsDir
        .listFiles()
        ?.filter { it.isFile && it.length() > 0L && it.extension.equals("mp4", ignoreCase = true) }
        ?: emptyList()
    return NaturalSort.sortedNaturally(files) { it.name }
  }

  fun pickVideoForPart(partNum: Int, partDir: File = getPartDir(partNum)): File? {
    findPartCustomVideoFile(partNum, partDir)?.let { return it }
    val stocks = listStockLoops()
    return if (stocks.isNotEmpty()) stocks.random() else null
  }

  fun copyPartVideoOverride(partNum: Int, uri: Uri): File {
    val partDir = getPartDir(partNum).apply { mkdirs() }
    partDir.listFiles()?.forEach { file ->
      if (file.isFile && file.extension.equals("mp4", ignoreCase = true)) {
        file.delete()
      }
    }
    val dest = File(partDir, "$partNum.mp4")
    copyUriToFileAtomic(uri, dest)
    return dest
  }

  fun copyPartAudioOverride(partNum: Int, uri: Uri): File {
    val partDir = getPartDir(partNum).apply { mkdirs() }
    val rawName = queryDisplayName(uri) ?: "audio.mp3"
    val ext =
      rawName.substringAfterLast('.', "").lowercase(Locale.ROOT).let {
        if (it == "wav" || it == "mp3") it else "mp3"
      }
    clearPartAudioFiles(partNum)
    val dest = File(partDir, "${partNum}_mix.$ext")
    copyUriToFileAtomic(uri, dest)
    return dest
  }

  fun readUtf8Text(uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
      it.readText()
    } ?: throw IllegalStateException("Unable to read selected text file.")

  fun importGodPromptFile(uri: Uri): GodPromptImportResult {
    val displayName = queryDisplayName(uri)?.trim().orEmpty().ifBlank { "god_prompt.txt" }
    val mime = context.contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
    val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val allowedExt = ext in setOf("txt", "csv")
    val allowedMime =
      mime == "text/plain" ||
        mime == "text/csv" ||
        mime == "text/comma-separated-values" ||
        mime == "application/csv" ||
        mime == "application/vnd.ms-excel"
    if (!allowedExt && !allowedMime) {
      throw IllegalArgumentException("Unsupported file \"$displayName\". Please pick a .txt or .csv file.")
    }

    val rawContent = readUtf8Text(uri).replace("\r\n", "\n").replace('\r', '\n').trim()
    if (rawContent.isEmpty()) {
      throw IllegalArgumentException("Selected file \"$displayName\" is empty.")
    }

    val tmp = File(context.filesDir, ".tmp_god_prompt_master.txt")
    try {
      tmp.writeText(rawContent, Charsets.UTF_8)
      if (godPromptFile.exists()) godPromptFile.delete()
      if (!tmp.renameTo(godPromptFile)) {
        tmp.copyTo(godPromptFile, overwrite = true)
        tmp.delete()
      }
    } catch (e: Exception) {
      tmp.delete()
      throw e
    }

    val charCount = rawContent.length
    val wordCount = countWordsFast(rawContent)
    val updatedAt = System.currentTimeMillis()
    val preview = buildShortPreview(rawContent)

    return GodPromptImportResult(
      fileName = displayName,
      charCount = charCount,
      wordCount = wordCount,
      updatedAt = updatedAt,
      previewText = preview,
    )
  }

  fun readGodPromptPreview(maxChars: Int = 260): String {
    if (!godPromptFile.exists() || godPromptFile.length() == 0L) return ""
    return runCatching {
      godPromptFile.bufferedReader(Charsets.UTF_8).use { reader ->
        val buffer = CharArray(maxChars)
        val readCount = reader.read(buffer, 0, maxChars)
        if (readCount <= 0) "" else buildShortPreview(String(buffer, 0, readCount))
      }
    }.getOrDefault("")
  }

  fun clearGodPromptFile() {
    if (godPromptFile.exists()) {
      godPromptFile.delete()
    }
  }

  private fun buildShortPreview(text: String): String {
    val lines =
      text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(4)
        .toList()
    return lines.joinToString("\n").take(260)
  }

  private fun countWordsFast(text: String): Int {
    var count = 0
    var inWord = false
    for (ch in text) {
      if (ch.isWhitespace()) {
        inWord = false
      } else if (!inWord) {
        count++
        inWord = true
      }
    }
    return count
  }

  fun clearPartAudioFiles(
    partNum: Int,
    excludeFile: File? = null,
  ) {
    val partDir = getPartDir(partNum)
    if (!partDir.exists()) return
    val excludeCanonical = excludeFile?.absolutePath
    partDir.listFiles()?.forEach { file ->
      if (excludeCanonical != null && file.absolutePath == excludeCanonical) {
        return@forEach
      }
      if (
        file.isFile && file.extension.lowercase(Locale.ROOT) in setOf("wav", "mp3")
      ) {
        file.delete()
      } else if (file.isDirectory && file.name == "_audio_generation_work") {
        file.deleteRecursively()
      }
    }
  }

  fun clearPart(partNum: Int) {
    val partDir = getPartDir(partNum)
    if (!partDir.exists()) return
    partDir.listFiles()?.forEach { it.deleteRecursively() }
  }

  fun clearWorkspaceTotalZero(partCount: Int) {
    episodeWorkspaceDir.deleteRecursively()
    workspaceDir.deleteRecursively()
    ensurePartFolders(partCount)
  }

  fun cleanTempWorkspaceOnly() {
    workspaceDir.deleteRecursively()
    workspaceDir.mkdirs()
  }

  fun addStockLoop(uri: Uri): File {
    stockLoopsDir.mkdirs()
    val orig = queryDisplayName(uri) ?: "stock_${System.currentTimeMillis()}.mp4"
    val safeName = sanitizeFilename(orig, "mp4")
    var target = File(stockLoopsDir, safeName)
    if (target.exists()) {
      val base = target.nameWithoutExtension
      val ts = System.currentTimeMillis() % 100000
      target = File(stockLoopsDir, "${base}_$ts.mp4")
    }
    copyUriToFileAtomic(uri, target)
    return target
  }

  fun setTransitionAsset(uri: Uri): String {
    transitionsDir.mkdirs()
    copyUriToFileAtomic(uri, transitionFile)
    return queryDisplayName(uri) ?: transitionFile.name
  }

  fun removeTransitionAsset() {
    transitionFile.delete()
  }

  fun setBgmAsset(uri: Uri): String {
    assetsDir.mkdirs()
    copyUriToFileAtomic(uri, bgmFile)
    return queryDisplayName(uri) ?: bgmFile.name
  }

  fun removeBgmAsset() {
    bgmFile.delete()
  }

  fun setOutroAsset(uri: Uri): String {
    assetsDir.mkdirs()
    copyUriToFileAtomic(uri, outroFile)
    return queryDisplayName(uri) ?: outroFile.name
  }

  fun removeOutroAsset() {
    outroFile.delete()
  }

  fun getWorkspaceSizeBytes(): Long = directorySizeBytes(workspaceDir)

  fun listFinishedOutputs(): List<File> {
    val files =
      outputDir
        .listFiles()
        ?.filter { it.isFile && it.length() > 0L && it.extension.equals("mp4", ignoreCase = true) }
        ?: emptyList()
    return files.sortedByDescending { it.lastModified() }
  }

  fun getFreeStorageGb(): Double {
    val stat = StatFs(context.filesDir.absolutePath)
    return stat.availableBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
  }

  fun saveVideoToGallery(videoFile: File): Uri {
    require(videoFile.exists() && videoFile.length() > 0L) { "Output video file not found." }
    val resolver = context.contentResolver
    val displayName = videoFile.name
    val values =
      ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(
            MediaStore.Video.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_MOVIES}/TheMissingPage",
          )
          put(MediaStore.Video.Media.IS_PENDING, 1)
        }
      }

    val uri =
      resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Unable to create MediaStore entry in Movies/TheMissingPage.")

    try {
      resolver.openOutputStream(uri)?.use { outStream ->
        FileInputStream(videoFile).use { inStream -> inStream.copyTo(outStream, bufferSize = 65536) }
      } ?: throw IllegalStateException("Unable to open MediaStore output stream.")

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val publish = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        resolver.update(uri, publish, null, null)
      }
      return uri
    } catch (e: Exception) {
      runCatching { resolver.delete(uri, null, null) }
      throw e
    }
  }

  fun createShareIntent(videoFile: File): Intent {
    val contentUri =
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
    return Intent(Intent.ACTION_SEND).apply {
      type = "video/mp4"
      putExtra(Intent.EXTRA_STREAM, contentUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  fun queryDisplayName(uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) return cursor.getString(idx)
        }
      }
    return uri.lastPathSegment?.substringAfterLast('/')
  }

  private fun copyUriToFileAtomic(uri: Uri, destination: File) {
    destination.parentFile?.mkdirs()
    val tmp = File(destination.parentFile, ".tmp_${destination.name}_${System.currentTimeMillis()}")
    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tmp).use { output -> input.copyTo(output, bufferSize = 65536) }
      } ?: throw IllegalStateException("Cannot open selected document URI.")
      if (destination.exists()) {
        destination.delete()
      }
      if (!tmp.renameTo(destination)) {
        tmp.copyTo(destination, overwrite = true)
        tmp.delete()
      }
    } catch (e: Exception) {
      tmp.delete()
      throw e
    }
  }

  private fun sanitizeFilename(rawName: String, requiredExt: String): String {
    val base =
      rawName
        .substringBeforeLast('.')
        .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        .ifBlank { "media_${System.currentTimeMillis()}" }
    return "$base.$requiredExt"
  }

  private fun directorySizeBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    var total = 0L
    dir.listFiles()?.forEach { child -> total += directorySizeBytes(child) }
    return total
  }
}
