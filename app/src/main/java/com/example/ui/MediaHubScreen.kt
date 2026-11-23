package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AssetSlotStatus
import com.example.data.MediaFileInfo
import com.example.data.MediaHubState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardDeep
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanDim
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioGreenDim
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaHubScreen(
  state: MediaHubState,
  isBusy: Boolean,
  onAddStockLoops: () -> Unit,
  onDeleteStockLoop: (String) -> Unit,
  onPickTransition: () -> Unit,
  onRemoveTransition: () -> Unit,
  onPickBgm: () -> Unit,
  onRemoveBgm: () -> Unit,
  onPickOutro: () -> Unit,
  onRemoveOutro: () -> Unit,
  onCleanTempCache: () -> Unit,
  onPlayFinishedVideo: (MediaFileInfo) -> Unit,
  onSaveFinishedToGallery: (MediaFileInfo) -> Unit,
  onShareFinishedVideo: (MediaFileInfo) -> Unit,
  onDeleteFinishedVideo: (MediaFileInfo) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.widthIn(max = 820.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 1. Stock Loops Pool
      Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "STOCK LOOPS POOL (stock_loops/)",
                style = MaterialTheme.typography.titleSmall,
                color = StudioCyan,
              )
              Text(
                text = "${state.stockLoops.size} fallback video clip(s)",
                style = MaterialTheme.typography.bodySmall,
                color = StudioTextSecondary,
              )
            }
            FilledTonalButton(
              onClick = onAddStockLoops,
              enabled = !isBusy,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = StudioCyanDim,
                  contentColor = StudioCyan,
                ),
              modifier = Modifier.heightIn(min = 48.dp).testTag("add_stock_loops_button"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Add Stock MP4(s)")
            }
          }

          if (state.stockLoops.isEmpty()) {
            Surface(
              color = StudioCardDeep,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = StudioTextMuted)
                Text(
                  text = "No stock loops loaded yet. Tap + Add Stock MP4(s) to import fallback looping clips.",
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextMuted,
                )
              }
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              state.stockLoops.forEach { clip ->
                Surface(
                  color = StudioCardElevated,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = clip.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${formatMb(clip.sizeBytes)} • ${formatDuration(clip.durationSeconds)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = StudioTextSecondary,
                      )
                    }
                    IconButton(
                      onClick = { onDeleteStockLoop(clip.filePath) },
                      enabled = !isBusy,
                      modifier = Modifier.testTag("delete_stock_${clip.name}"),
                    ) {
                      Icon(Icons.Filled.Delete, contentDescription = "Delete ${clip.name}", tint = StudioRed)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 2. Global Assets: Transition, BGM, Outro
      GlobalAssetSlotCard(
        title = "NEXT PAGE TRANSITION B-ROLL",
        subtitle = "assets/transitions/BAGONG_PAHiNA_6S.mp4 (Clean, zero filter/overlay)",
        status = state.transitionAsset,
        showDuration = true,
        pickLabel = "Pick / Replace MP4",
        isBusy = isBusy,
        pickTestTag = "pick_transition_button",
        removeTestTag = "remove_transition_button",
        onPick = onPickTransition,
        onRemove = onRemoveTransition,
      )

      GlobalAssetSlotCard(
        title = "BACKGROUND MUSIC / BGM",
        subtitle = "assets/the_dread.mp3 (Auto-looped & faded under voice)",
        status = state.bgmAsset,
        showDuration = false,
        pickLabel = "Pick / Replace Audio",
        isBusy = isBusy,
        pickTestTag = "pick_bgm_button",
        removeTestTag = "remove_bgm_button",
        onPick = onPickBgm,
        onRemove = onRemoveBgm,
      )

      GlobalAssetSlotCard(
        title = "OUTRO VANITY CARD",
        subtitle = "assets/outro_vanity_card.mp4 (Optional ending video)",
        status = state.outroAsset,
        showDuration = true,
        pickLabel = "Pick / Replace MP4",
        isBusy = isBusy,
        pickTestTag = "pick_outro_button",
        removeTestTag = "remove_outro_button",
        onPick = onPickOutro,
        onRemove = onRemoveOutro,
      )

      // 3. Temp Cache Manager
      Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
              text = "TEMP CACHE MANAGER (workspace/)",
              style = MaterialTheme.typography.titleSmall,
              color = StudioCyan,
            )
            Text(
              text = "Current cache: ${formatMb(state.tempCacheBytes)}",
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
              color = if (state.tempCacheBytes > 0L) StudioAmber else StudioTextSecondary,
            )
          }
          OutlinedButton(
            onClick = onCleanTempCache,
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.7f)),
            modifier = Modifier.heightIn(min = 48.dp).testTag("clean_temp_cache_button"),
          ) {
            Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("🧹 Clean Temp Cache", color = StudioAmber)
          }
        }
      }

      // 4. Finished Videos (output/)
      Card(
        colors = CardDefaults.cardColors(containerColor = StudioCard),
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "FINISHED VIDEOS (output/)",
            style = MaterialTheme.typography.titleSmall,
            color = StudioCyan,
          )

          if (state.finishedVideos.isEmpty()) {
            Text(
              text = "No finished renders yet. Run 1-Click Smart Render in Studio.",
              style = MaterialTheme.typography.bodySmall,
              color = StudioTextMuted,
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              state.finishedVideos.forEach { video ->
                Surface(
                  color = StudioCardElevated,
                  shape = RoundedCornerShape(12.dp),
                  border = BorderStroke(1.dp, StudioGreen.copy(alpha = 0.4f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Filled.Movie, contentDescription = null, tint = StudioGreen)
                      Spacer(Modifier.width(8.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = video.name,
                          style = MaterialTheme.typography.titleSmall,
                          color = StudioTextPrimary,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = "${formatDuration(video.durationSeconds)} • ${formatMb(video.sizeBytes)}",
                          style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                          color = StudioGreen,
                        )
                      }
                    }

                    HorizontalDivider(color = StudioOutline.copy(alpha = 0.5f))

                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      FilledTonalButton(
                        onClick = { onPlayFinishedVideo(video) },
                        colors =
                          ButtonDefaults.filledTonalButtonColors(
                            containerColor = StudioCyanDim,
                            contentColor = StudioCyan,
                          ),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("play_output_${video.name}"),
                      ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                      }

                      FilledTonalButton(
                        onClick = { onSaveFinishedToGallery(video) },
                        colors =
                          ButtonDefaults.filledTonalButtonColors(
                            containerColor = StudioGreenDim,
                            contentColor = StudioGreen,
                          ),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("save_gallery_${video.name}"),
                      ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save to Gallery")
                      }

                      OutlinedButton(
                        onClick = { onShareFinishedVideo(video) },
                        border = BorderStroke(1.dp, StudioOutline),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("share_output_${video.name}"),
                      ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioTextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("Share", color = StudioTextPrimary)
                      }

                      OutlinedButton(
                        onClick = { onDeleteFinishedVideo(video) },
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.6f)),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("delete_output_${video.name}"),
                      ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = StudioRed)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GlobalAssetSlotCard(
  title: String,
  subtitle: String,
  status: AssetSlotStatus,
  showDuration: Boolean,
  pickLabel: String,
  isBusy: Boolean,
  pickTestTag: String,
  removeTestTag: String,
  onPick: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = StudioCard),
    border = BorderStroke(1.dp, StudioOutline),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = StudioCyan)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StudioTextSecondary)

      val statusLabel =
        if (status.isLoaded) {
          if (showDuration && status.durationSeconds != null) {
            "Loaded: ${status.displayName} (${String.format(Locale.US, "%.1fs", status.durationSeconds)})"
          } else {
            "Loaded: ${status.displayName}"
          }
        } else {
          "Not Set"
        }

      Surface(
        color = if (status.isLoaded) StudioGreenDim.copy(alpha = 0.6f) else StudioCardDeep,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = if (pickLabel.contains("Audio")) Icons.Filled.AudioFile else Icons.Filled.Movie,
            contentDescription = null,
            tint = if (status.isLoaded) StudioGreen else StudioTextMuted,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (status.isLoaded) FontWeight.SemiBold else FontWeight.Normal,
            color = if (status.isLoaded) StudioGreen else StudioTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
          onClick = onPick,
          enabled = !isBusy,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = StudioCyanDim,
              contentColor = StudioCyan,
            ),
          modifier = Modifier.heightIn(min = 48.dp).testTag(pickTestTag),
        ) {
          Text(pickLabel)
        }

        if (status.isLoaded) {
          OutlinedButton(
            onClick = onRemove,
            enabled = !isBusy,
            border = BorderStroke(1.dp, StudioRed.copy(alpha = 0.6f)),
            modifier = Modifier.heightIn(min = 48.dp).testTag(removeTestTag),
          ) {
            Text("Remove", color = StudioRed)
          }
        }
      }
    }
  }
}

private fun formatMb(bytes: Long): String =
  String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))

private fun formatDuration(seconds: Double?): String {
  if (seconds == null || seconds <= 0.0) return "--:--"
  val total = seconds.toInt()
  val mins = total / 60
  val secs = total % 60
  return String.format(Locale.US, "%d:%02d", mins, secs)
}
