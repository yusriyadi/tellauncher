package id.tellolabs.launcher.ui.home

import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.TextClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import id.tellolabs.launcher.R

@Composable
internal fun HomeScreen(
    showSeconds: Boolean,
    showDrawer: Boolean,
    doubleTapEnabled: Boolean,
    onAlarm: () -> Unit,
    onCalendar: () -> Unit,
    onDrawer: () -> Unit,
    onSettings: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val drawerLabel = stringResource(R.string.open_drawer)
    val settingsLabel = stringResource(R.string.settings)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnAlarm by rememberUpdatedState(onAlarm)
    val currentOnCalendar by rememberUpdatedState(onCalendar)
    val currentOnSettings by rememberUpdatedState(onSettings)
    val tapTargets = remember { HomeTapTargets() }
    val alarmLabel = stringResource(R.string.open_alarm)
    val calendarLabel = stringResource(R.string.open_calendar)
    val clockColor = MaterialTheme.colorScheme.primary.toArgb()
    val dateColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val locale = LocalConfiguration.current.locales[0]
    val format12 = remember(locale, showSeconds) {
        DateFormat.getBestDateTimePattern(locale, if (showSeconds) "hms" else "hm")
    }
    val format24 = remember(locale, showSeconds) {
        DateFormat.getBestDateTimePattern(locale, if (showSeconds) "Hms" else "Hm")
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { tapTargets.homeOffset = it.positionInRoot() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(drawerLabel) { onDrawer(); true },
                    CustomAccessibilityAction(settingsLabel) { onSettings(); true },
                )
                contentDescription = drawerLabel
            }
            .pointerInput(doubleTapEnabled) {
                detectTapGestures(
                    onLongPress = { currentOnSettings() },
                    // One recognizer delays a single tap until double-tap detection finishes.
                    onDoubleTap = if (doubleTapEnabled) ({ currentOnDoubleTap() }) else null,
                    onTap = { position ->
                        val rootPosition = position + tapTargets.homeOffset
                        when {
                            tapTargets.clock.contains(rootPosition) -> currentOnAlarm()
                            tapTargets.date.contains(rootPosition) -> currentOnCalendar()
                        }
                    },
                )
            }
            .pointerInput(swipeThreshold) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = { if (drag < -swipeThreshold) onDrawer() },
                    onDragCancel = { drag = 0f },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AndroidView(
                    factory = { context ->
                        TextClock(context).apply {
                            textSize = 28f
                            setTextColor(clockColor)
                            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                            gravity = Gravity.CENTER
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                    },
                    update = { clock ->
                        clock.setTextColor(clockColor)
                        if (clock.format12Hour?.toString() != format12) clock.format12Hour = format12
                        if (clock.format24Hour?.toString() != format24) clock.format24Hour = format24
                    },
                    modifier = Modifier.wrapContentSize()
                        .onGloballyPositioned { tapTargets.clock = it.boundsInRoot() }
                        .semantics {
                            onClick(label = alarmLabel) { currentOnAlarm(); true }
                        },
                )
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    factory = { context ->
                        TextClock(context).apply {
                            // TextClock localizes weekday names and updates at midnight/time-zone changes.
                            format12Hour = "EEEE, dd-MM-yyyy"
                            format24Hour = "EEEE, dd-MM-yyyy"
                            textSize = 14f
                            setTextColor(dateColor)
                            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                            gravity = Gravity.CENTER
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                    },
                    update = { date -> date.setTextColor(dateColor) },
                    modifier = Modifier.wrapContentSize()
                        .onGloballyPositioned { tapTargets.date = it.boundsInRoot() }
                        .semantics {
                            onClick(label = calendarLabel) { currentOnCalendar(); true }
                        },
                )
            }
            if (showDrawer) {
                IconButton(
                    onClick = onDrawer,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).size(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_app_drawer), contentDescription = drawerLabel,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

private class HomeTapTargets {
    var homeOffset: Offset = Offset.Zero
    var clock: Rect = Rect.Zero
    var date: Rect = Rect.Zero
}
