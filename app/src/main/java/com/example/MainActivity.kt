package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppTab
import com.example.data.StudioRunStatus
import com.example.ui.ApiSettingsScreen
import com.example.ui.MediaHubScreen
import com.example.ui.StudioScreen
import com.example.ui.StudioViewModel
import com.example.ui.components.VideoPreviewDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MissingPageStudioTheme
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberDim
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanDim
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioGreenDim
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioRedDim
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    setContent {
      MissingPageStudioTheme {
        MissingPageStudioApp(
          onMoveTaskToBack = { moveTaskToBack(true) },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingPageStudioApp(
  onMoveTaskToBack: () -> Unit,
  viewModel: StudioViewModel = viewModel(),
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val config by viewModel.config.collectAsStateWithLifecycle()
  val partStatuses by viewModel.partStatuses.collectAsStateWithLifecycle()
  val mediaHubState by viewModel.mediaHubState.collectAsStateWithLifecycle()
  val dialogueText by viewModel.dialogueText.collectAsStateWithLifecycle()
  val qaResult by viewModel.qaResult.collectAsStateWithLifecycle()
  val runStatus by viewModel.runStatus.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val logs by viewModel.logs.collectAsStateWithLifecycle()
  val playingAudioPart by viewModel.playingAudioPart.collectAsStateWithLifecycle()
  val subscriptionInfo by viewModel.subscriptionInfo.collectAsStateWithLifecycle()
  val isCheckingSubscription by viewModel.isCheckingSubscription.collectAsStateWithLifecycle()
  val previewTarget by viewModel.previewTarget.collectAsStateWithLifecycle()
  val lowStorageWarning by viewModel.lowStorageWarning.collectAsStateWithLifecycle()
  val snackbarEvent by viewModel.snackbarEvent.collectAsStateWithLifecycle()

  var activeTab by rememberSaveable { mutableStateOf(AppTab.STUDIO) }
  val snackbarHostState = remember { SnackbarHostState() }

  // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // Auto-Paste inspection on ON_RESUME
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.checkAutoPasteOnResume()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Back button safety: minimize via moveTaskToBack(true) when download/render is active!
  BackHandler(enabled = runStatus == StudioRunStatus.BUSY || activeTab != AppTab.STUDIO) {
    if (runStatus == StudioRunStatus.BUSY) {
      onMoveTaskToBack()
    } else if (activeTab != AppTab.STUDIO) {
      activeTab = AppTab.STUDIO
    }
  }

  // SAF Document Launchers (Zero File Explorer)
  var pendingPartForPicker by remember { mutableStateOf(1) }

  val partVideoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importPartVideo(pendingPartForPicker, uri)
    }

  val partAudioPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importPartAudio(pendingPartForPicker, uri)
    }

  val txtScriptPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importDialogueTxt(uri)
    }

  val stockLoopsMultiPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) viewModel.addStockLoops(uris)
    }

  val transitionPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.setTransitionBroll(uri)
    }

  val bgmPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.setBgm(uri)
    }

  val outroPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.setOutro(uri)
    }

  // Snackbar with Undo support
  LaunchedEffect(snackbarEvent) {
    val event = snackbarEvent ?: return@LaunchedEffect
    val (message, actionLabel) = event
    val result =
      snackbarHostState.showSnackbar(
        message = message,
        actionLabel = actionLabel,
        duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
      )
    if (result == SnackbarResult.ActionPerformed && actionLabel == "Undo") {
      viewModel.undoDialogueClear()
    }
    viewModel.clearSnackbar()
  }

  if (previewTarget != null) {
    VideoPreviewDialog(
      target = previewTarget!!,
      onDismiss = { viewModel.closePreviewPlayer() },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = StudioBackground,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = stringResource(R.string.studio_header_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = StudioCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              StatusPill(runStatus)
            }
          },
          colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = StudioBackground,
            ),
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar(
            windowInsets = WindowInsets.navigationBars,
            containerColor = StudioCard,
          ) {
            val navColors =
              NavigationBarItemDefaults.colors(
                selectedIconColor = StudioCyan,
                selectedTextColor = StudioCyan,
                indicatorColor = StudioCyanDim,
                unselectedIconColor = StudioTextSecondary,
                unselectedTextColor = StudioTextSecondary,
              )
            NavigationBarItem(
              selected = activeTab == AppTab.STUDIO,
              onClick = { activeTab = AppTab.STUDIO },
              colors = navColors,
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.STUDIO) Icons.Filled.MovieCreation
                    else Icons.Outlined.MovieCreation,
                  contentDescription = stringResource(R.string.nav_studio),
                )
              },
              label = { Text(stringResource(R.string.nav_studio)) },
              modifier = Modifier.testTag("nav_studio_tab"),
            )
            NavigationBarItem(
              selected = activeTab == AppTab.MEDIA_HUB,
              onClick = { activeTab = AppTab.MEDIA_HUB },
              colors = navColors,
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.MEDIA_HUB) Icons.Filled.FolderSpecial
                    else Icons.Outlined.FolderSpecial,
                  contentDescription = stringResource(R.string.nav_media_hub),
                )
              },
              label = { Text(stringResource(R.string.nav_media_hub)) },
              modifier = Modifier.testTag("nav_media_hub_tab"),
            )
            NavigationBarItem(
              selected = activeTab == AppTab.API_SETTINGS,
              onClick = { activeTab = AppTab.API_SETTINGS },
              colors = navColors,
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.API_SETTINGS) Icons.Filled.Settings
                    else Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.nav_api_settings),
                )
              },
              label = { Text(stringResource(R.string.nav_api_settings)) },
              modifier = Modifier.testTag("nav_api_settings_tab"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(containerColor = StudioCard) {
            NavigationRailItem(
              selected = activeTab == AppTab.STUDIO,
              onClick = { activeTab = AppTab.STUDIO },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.STUDIO) Icons.Filled.MovieCreation
                    else Icons.Outlined.MovieCreation,
                  contentDescription = stringResource(R.string.nav_studio),
                )
              },
              label = { Text(stringResource(R.string.nav_studio)) },
              modifier = Modifier.testTag("rail_studio_tab"),
            )
            NavigationRailItem(
              selected = activeTab == AppTab.MEDIA_HUB,
              onClick = { activeTab = AppTab.MEDIA_HUB },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.MEDIA_HUB) Icons.Filled.FolderSpecial
                    else Icons.Outlined.FolderSpecial,
                  contentDescription = stringResource(R.string.nav_media_hub),
                )
              },
              label = { Text(stringResource(R.string.nav_media_hub)) },
              modifier = Modifier.testTag("rail_media_hub_tab"),
            )
            NavigationRailItem(
              selected = activeTab == AppTab.API_SETTINGS,
              onClick = { activeTab = AppTab.API_SETTINGS },
              icon = {
                Icon(
                  imageVector =
                    if (activeTab == AppTab.API_SETTINGS) Icons.Filled.Settings
                    else Icons.Outlined.Settings,
                  contentDescription = stringResource(R.string.nav_api_settings),
                )
              },
              label = { Text(stringResource(R.string.nav_api_settings)) },
              modifier = Modifier.testTag("rail_api_settings_tab"),
            )
          }
        }

        when (activeTab) {
          AppTab.STUDIO -> {
            StudioScreen(
              config = config,
              partStatuses = partStatuses,
              dialogueText = dialogueText,
              qaResult = qaResult,
              progress = progress,
              logs = logs,
              playingAudioPart = playingAudioPart,
              lowStorageWarning = lowStorageWarning,
              onUpdatePartFolders = { viewModel.updatePartFolders(it) },
              onSelectPart = { viewModel.selectPart(it) },
              onPickPartVideo = { partNum ->
                pendingPartForPicker = partNum
                partVideoPicker.launch(arrayOf("video/mp4", "video/*"))
              },
              onPickPartAudio = { partNum ->
                pendingPartForPicker = partNum
                partAudioPicker.launch(arrayOf("audio/*"))
              },
              onLoadTxt = { txtScriptPicker.launch(arrayOf("text/plain", "text/*")) },
              onTogglePlayAudio = { viewModel.togglePlayPartAudio(it) },
              onClearPart = { viewModel.clearPart(it) },
              onDialogueChange = { viewModel.onDialogueChanged(it) },
              onClearDialogue = { viewModel.clearDialogue() },
              onPasteDialogue = { viewModel.pasteFromClipboard() },
              onSanitizeDialogue = { viewModel.sanitizeDialogue() },
              onRunGeminiAutoPilot = { viewModel.runGeminiAutoPilot(it) },
              onRunGeminiSelfAudit = { viewModel.runGeminiSelfAudit() },
              onAutoPasteToggle = { viewModel.setAutoPasteEnabled(it) },
              onAutoNextToggle = { viewModel.setAutoNextPartEnabled(it) },
              onDownloadVoice = { viewModel.startDownloadVoice() },
              onFilterSelected = { viewModel.setFilterMode(it) },
              onPreviewFilter = { viewModel.startFilterPreview() },
              onTurboToggle = { viewModel.setTurboModeEnabled(it) },
              onSmartResumeToggle = { viewModel.setSmartResumeEnabled(it) },
              onSaveTransitions = { viewModel.saveTransitionsAfter(it) },
              onAutoCleanToggle = { viewModel.setAutoCleanAfterExport(it) },
              onClearWorkspaceTotalZero = { viewModel.clearWorkspaceTotalZero() },
              onStartSmartRender = { viewModel.startSmartRender(ignoreLowStorage = false) },
              onConfirmLowStorageRender = { viewModel.startSmartRender(ignoreLowStorage = true) },
              onDismissLowStorageWarning = { viewModel.dismissLowStorageWarning() },
              onCancelActiveTask = { viewModel.cancelActiveTask() },
              onClearLogs = { viewModel.clearLogs() },
              modifier = Modifier.weight(1f),
            )
          }

          AppTab.MEDIA_HUB -> {
            MediaHubScreen(
              state = mediaHubState,
              isBusy = progress.isRunning,
              onAddStockLoops = { stockLoopsMultiPicker.launch(arrayOf("video/*")) },
              onDeleteStockLoop = { viewModel.deleteStockLoop(it) },
              onPickTransition = { transitionPicker.launch(arrayOf("video/mp4", "video/*")) },
              onRemoveTransition = { viewModel.removeTransitionBroll() },
              onPickBgm = { bgmPicker.launch(arrayOf("audio/*")) },
              onRemoveBgm = { viewModel.removeBgm() },
              onPickOutro = { outroPicker.launch(arrayOf("video/mp4", "video/*")) },
              onRemoveOutro = { viewModel.removeOutro() },
              onCleanTempCache = { viewModel.cleanTempCache() },
              onPlayFinishedVideo = { viewModel.playFinishedVideo(it.name, it.filePath) },
              onSaveFinishedToGallery = { viewModel.saveFinishedVideoToGallery(it.filePath) },
              onShareFinishedVideo = { video ->
                val intent = viewModel.createShareIntent(video.filePath)
                context.startActivity(Intent.createChooser(intent, "Share ${video.name}"))
              },
              onDeleteFinishedVideo = { viewModel.deleteFinishedVideo(it.filePath) },
              modifier = Modifier.weight(1f),
            )
          }

          AppTab.API_SETTINGS -> {
            ApiSettingsScreen(
              config = config,
              subscriptionInfo = subscriptionInfo,
              isCheckingSubscription = isCheckingSubscription,
              onSaveSettings = { key, voice, model, sfxModel ->
                viewModel.saveApiSettings(key, voice, model, sfxModel)
              },
              onCheckCredits = { viewModel.checkSubscriptionCredits() },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatusPill(status: StudioRunStatus) {
  val (bg, fg) =
    when (status) {
      StudioRunStatus.READY -> StudioCardElevated to StudioTextPrimary
      StudioRunStatus.BUSY -> StudioAmberDim to StudioAmber
      StudioRunStatus.DONE -> StudioGreenDim to StudioGreen
      StudioRunStatus.CANCELLED -> StudioAmberDim to StudioAmber
      StudioRunStatus.ERROR -> StudioRedDim to StudioRed
    }
  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.testTag("studio_status_pill"),
  ) {
    Text(
      text = status.label,
      style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
      color = fg,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}
