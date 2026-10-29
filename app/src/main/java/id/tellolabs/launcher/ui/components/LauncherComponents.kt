package id.tellolabs.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import id.tellolabs.launcher.R
import id.tellolabs.launcher.model.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AppIcon(app: LauncherApp, modifier: Modifier) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, app, pixels) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getActivityIcon(app.component).toBitmap(pixels, pixels).asImageBitmap()
            } catch (_: Exception) { null }
        }
    }
    val image = bitmap
    if (image != null) Image(image, contentDescription = null, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)))
}

@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val clearLabel = stringResource(R.string.clear_search)
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_apps), fontSize = 15.sp) },
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQuery("") }, enabled = enabled) {
                Text("×", fontSize = 24.sp, modifier = Modifier.semantics {
                    contentDescription = clearLabel
                })
            } }
        } else null,
    )
}

@Composable
internal fun PageHeader(
    title: Int,
    actions: @Composable RowScope.() -> Unit = {},
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val backLabel = stringResource(R.string.back)
        IconButton(onClick = onBack) {
            Text("‹", fontSize = 32.sp, modifier = Modifier.semantics { contentDescription = backLabel })
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(title), fontSize = 20.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
internal fun ToggleSetting(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled,
            role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(20.dp))
        }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingLink(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 56.dp).padding(vertical = 8.dp)) {
        Text(title, fontSize = 16.sp)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun EmptyMessage(message: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}
