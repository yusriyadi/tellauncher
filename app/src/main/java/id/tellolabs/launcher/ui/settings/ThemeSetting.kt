package id.tellolabs.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tellolabs.launcher.R
import id.tellolabs.launcher.ui.theme.ThemeMode

@Composable
internal fun ThemeSetting(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }
            .heightIn(min = 56.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.theme), fontSize = 16.sp)
                Text(stringResource(mode.label), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { option ->
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = mode == option },
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { RadioButton(selected = mode == option, onClick = null) },
                    onClick = { expanded = false; onChange(option) },
                )
            }
        }
    }
}
