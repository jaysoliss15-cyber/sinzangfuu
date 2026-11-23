package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.PreviewPlayerTarget
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCyan
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewDialog(
  target: PreviewPlayerTarget,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val exoPlayer =
    remember(target.filePath) {
      ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(target.filePath))))
        repeatMode = Player.REPEAT_MODE_ONE
        playWhenReady = true
        prepare()
      }
    }

  DisposableEffect(exoPlayer) {
    onDispose {
      exoPlayer.release()
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.95f),
      shape = RoundedCornerShape(18.dp),
      color = StudioCard,
      tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = target.title,
            style = MaterialTheme.typography.titleMedium,
            color = StudioCyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_video_preview_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close video preview",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(16f / 9f)
              .clip(RoundedCornerShape(12.dp))
              .background(StudioBackground)
        ) {
          AndroidView(
            factory = { ctx ->
              PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams =
                  ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                  )
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
