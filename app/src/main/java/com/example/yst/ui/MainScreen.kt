package com.example.yst.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yst.R
import com.example.yst.detection.DetectionPhase
import com.example.yst.detection.SoundClip
import com.example.yst.ui.theme.VoiceTriggerTheme

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startMonitoring()
        } else {
            viewModel.onPermissionDenied()
        }
    }
    val openAudioFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.importUris(uris)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? Activity
                val rotating = activity?.isChangingConfigurations == true
                if (!rotating) {
                    viewModel.onAppBackgrounded()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { notice ->
            val message = when (notice) {
                UserNotice.ImportFailed -> context.getString(R.string.import_failed)
                UserNotice.PermissionDenied -> context.getString(R.string.permission_denied)
                UserNotice.CaptureFailed -> context.getString(R.string.capture_failed)
                UserNotice.CaptureInterrupted -> context.getString(R.string.capture_interrupted)
                UserNotice.BackgroundCaptureUnavailable -> context.getString(R.string.capture_background_stopped)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    MainScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onThresholdChanged = viewModel::onThresholdChanged,
        onSpeechDurationChanged = viewModel::onSpeechDurationChanged,
        onSilenceTimeoutChanged = viewModel::onSilenceTimeoutChanged,
        onMinIntervalChanged = viewModel::onMinIntervalChanged,
        onMaxIntervalChanged = viewModel::onMaxIntervalChanged,
        onMonitoringClicked = {
            if (state.monitoringActive) {
                viewModel.stopMonitoring()
            } else if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startMonitoring()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onAddFilesClicked = { openAudioFiles.launch(arrayOf("audio/*")) },
        onRemoveSound = viewModel::removeSound,
    )
}

@Composable
private fun MainScreenContent(
    state: MainUiState,
    snackbarHostState: SnackbarHostState,
    onThresholdChanged: (Float) -> Unit,
    onSpeechDurationChanged: (Float) -> Unit,
    onSilenceTimeoutChanged: (Float) -> Unit,
    onMinIntervalChanged: (Float) -> Unit,
    onMaxIntervalChanged: (Float) -> Unit,
    onMonitoringClicked: () -> Unit,
    onAddFilesClicked: () -> Unit,
    onRemoveSound: (String) -> Unit,
) {
    val status = monitoringStatus(state.monitoringActive, state.phase)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusCard(status = status, phase = state.phase)
                MicrophoneCard(
                    levelDbFs = state.levelDbFs,
                    thresholdDbFs = state.settings.volumeThresholdDbFs,
                )
                SettingSlider(
                    title = stringResource(R.string.section_threshold),
                    valueLabel = stringResource(
                        R.string.threshold_value,
                        state.settings.volumeThresholdDbFs,
                    ),
                    value = state.settings.volumeThresholdDbFs,
                    valueRange = MainViewModel.MIN_DB..MainViewModel.MAX_DB,
                    onValueChange = onThresholdChanged,
                )
                Button(
                    onClick = onMonitoringClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (state.monitoringActive) {
                                R.string.stop_monitoring
                            } else {
                                R.string.start_monitoring
                            },
                        ),
                    )
                }
                ResponseCard(state = state)
                SoundCollectionCard(
                    sounds = state.sounds,
                    onAddFilesClicked = onAddFilesClicked,
                    onRemoveSound = onRemoveSound,
                )
                SettingsCard(
                    state = state,
                    onSpeechDurationChanged = onSpeechDurationChanged,
                    onSilenceTimeoutChanged = onSilenceTimeoutChanged,
                    onMinIntervalChanged = onMinIntervalChanged,
                    onMaxIntervalChanged = onMaxIntervalChanged,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: MonitoringStatus, phase: DetectionPhase) {
    val label = when (status) {
        MonitoringStatus.Stopped -> stringResource(R.string.status_stopped)
        MonitoringStatus.Listening -> stringResource(R.string.status_listening)
        MonitoringStatus.LoudSpeechDetected -> stringResource(R.string.status_loud_speech)
    }
    val detail = when (status) {
        MonitoringStatus.Stopped -> stringResource(R.string.status_stopped_detail)
        MonitoringStatus.Listening -> stringResource(R.string.status_listening_detail)
        MonitoringStatus.LoudSpeechDetected -> stringResource(R.string.status_loud_detail)
    }
    SectionCard {
        SectionTitle(stringResource(R.string.section_status))
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor(status),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.detector_phase, phase.name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MicrophoneCard(levelDbFs: Float?, thresholdDbFs: Float) {
    SectionCard {
        SectionTitle(stringResource(R.string.section_microphone))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = levelDbFs?.let { "%.0f".format(it) }
                    ?: stringResource(R.string.level_unavailable),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.level_unit),
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LevelMeter(levelDbFs = levelDbFs, thresholdDbFs = thresholdDbFs)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.meter_scale_min), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(R.string.threshold_marker, thresholdDbFs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(stringResource(R.string.meter_scale_max), style = MaterialTheme.typography.labelMedium)
        }
        if (levelDbFs == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.level_unavailable_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelMeter(levelDbFs: Float?, thresholdDbFs: Float) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val levelColor = MaterialTheme.colorScheme.primary
    val thresholdColor = MaterialTheme.colorScheme.secondary
    val levelFraction = levelDbFs?.let { meterFraction(it) }
    val thresholdFraction = meterFraction(thresholdDbFs)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (levelFraction != null) {
            drawRoundRect(
                color = levelColor,
                size = Size(size.width * levelFraction, size.height),
                cornerRadius = radius,
            )
        }
        val markerX = size.width * thresholdFraction
        drawLine(
            color = thresholdColor,
            start = Offset(markerX, 0f),
            end = Offset(markerX, size.height),
            strokeWidth = 4.dp.toPx(),
        )
    }
}

@Composable
private fun ResponseCard(state: MainUiState) {
    val minSeconds = (state.settings.minResponseIntervalMs / 1_000L).toInt()
    val maxSeconds = (state.settings.maxResponseIntervalMs / 1_000L).toInt()
    SectionCard {
        SectionTitle(stringResource(R.string.section_response))
        Text(stringResource(R.string.last_played_label), style = MaterialTheme.typography.titleMedium)
        Text(
            text = state.lastPlayedSound ?: stringResource(R.string.last_played_none),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.lastPlayedSound == null) {
            Text(
                text = stringResource(R.string.playback_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(stringResource(R.string.next_interval_label), style = MaterialTheme.typography.titleMedium)
        Text(
            text = state.nextResponseIntervalMs?.let {
                stringResource(R.string.next_interval_value, it / 1_000f)
            } ?: stringResource(R.string.next_interval_unscheduled),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.nextResponseIntervalMs == null) {
            Text(
                text = stringResource(R.string.next_interval_detail, minSeconds, maxSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SoundCollectionCard(
    sounds: List<SoundClip>,
    onAddFilesClicked: () -> Unit,
    onRemoveSound: (String) -> Unit,
) {
    SectionCard {
        SectionTitle(stringResource(R.string.section_sounds))
        if (sounds.isEmpty()) {
            Text(
                text = stringResource(R.string.sounds_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sounds.forEach { clip ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = clip.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { onRemoveSound(clip.id) }) {
                        Text(stringResource(R.string.remove_sound))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddFilesClicked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_files))
        }
    }
}

@Composable
private fun SettingsCard(
    state: MainUiState,
    onSpeechDurationChanged: (Float) -> Unit,
    onSilenceTimeoutChanged: (Float) -> Unit,
    onMinIntervalChanged: (Float) -> Unit,
    onMaxIntervalChanged: (Float) -> Unit,
) {
    SectionCard {
        SectionTitle(stringResource(R.string.section_settings))
        SettingSlider(
            title = stringResource(R.string.speech_duration),
            valueLabel = stringResource(
                R.string.duration_ms,
                state.settings.requiredLoudSpeechMs.toInt(),
            ),
            value = state.settings.requiredLoudSpeechMs.toFloat(),
            valueRange = MainViewModel.MIN_SPEECH_MS..MainViewModel.MAX_SPEECH_MS,
            onValueChange = onSpeechDurationChanged,
        )
        SettingSlider(
            title = stringResource(R.string.silence_timeout),
            valueLabel = stringResource(
                R.string.duration_ms,
                state.settings.silenceTimeoutMs.toInt(),
            ),
            value = state.settings.silenceTimeoutMs.toFloat(),
            valueRange = MainViewModel.MIN_SILENCE_MS..MainViewModel.MAX_SILENCE_MS,
            onValueChange = onSilenceTimeoutChanged,
        )
        SettingSlider(
            title = stringResource(R.string.response_interval_min),
            valueLabel = stringResource(
                R.string.duration_seconds,
                (state.settings.minResponseIntervalMs / 1_000L).toInt(),
            ),
            value = state.settings.minResponseIntervalMs / 1_000f,
            valueRange = MainViewModel.MIN_INTERVAL_S..MainViewModel.MAX_INTERVAL_S,
            onValueChange = onMinIntervalChanged,
        )
        SettingSlider(
            title = stringResource(R.string.response_interval_max),
            valueLabel = stringResource(
                R.string.duration_seconds,
                (state.settings.maxResponseIntervalMs / 1_000L).toInt(),
            ),
            value = state.settings.maxResponseIntervalMs / 1_000f,
            valueRange = MainViewModel.MIN_INTERVAL_S..MainViewModel.MAX_INTERVAL_S,
            onValueChange = onMaxIntervalChanged,
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun statusColor(status: MonitoringStatus): Color {
    return when (status) {
        MonitoringStatus.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        MonitoringStatus.Listening -> MaterialTheme.colorScheme.secondary
        MonitoringStatus.LoudSpeechDetected -> MaterialTheme.colorScheme.primary
    }
}

private fun meterFraction(decibels: Float): Float {
    return ((decibels - MainViewModel.MIN_DB) / (MainViewModel.MAX_DB - MainViewModel.MIN_DB))
        .coerceIn(0f, 1f)
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1200)
@Composable
private fun MainScreenPreview() {
    VoiceTriggerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MainScreenContent(
                state = MainUiState(),
                snackbarHostState = SnackbarHostState(),
                onThresholdChanged = {},
                onSpeechDurationChanged = {},
                onSilenceTimeoutChanged = {},
                onMinIntervalChanged = {},
                onMaxIntervalChanged = {},
                onMonitoringClicked = {},
                onAddFilesClicked = {},
                onRemoveSound = {},
            )
        }
    }
}
