package com.cerebrozen.app.ui.offline

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cerebrozen.app.R
import com.cerebrozen.app.ui.screens.SectionCard
import com.cerebrozen.app.ui.screens.SubPage
import com.cerebrozen.app.ui.theme.Cyan
import com.cerebrozen.app.ui.theme.Ok
import com.cerebrozen.app.ui.theme.Periwinkle
import com.cerebrozen.app.ui.theme.TextMuted
import com.cerebrozen.app.ui.theme.TextMuted2
import com.cerebrozen.app.ui.theme.TextPrimary
import com.cerebrozen.app.ui.theme.TextSoft

@Composable
fun CbtIOfflineScreen(onBack: () -> Unit) = OfflineProgramScreen(OfflineToolContent.cbtI, onBack)

@Composable
fun MbctOfflineScreen(onBack: () -> Unit) = OfflineProgramScreen(OfflineToolContent.mbct, onBack)

@Composable
private fun OfflineProgramScreen(program: OfflineProgram, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("offline_tools", Context.MODE_PRIVATE) }
    val prefix = "program_${program.id.name}_"
    var completed by remember {
        mutableStateOf(program.modules.indices.filterTo(mutableSetOf()) { prefs.getBoolean("$prefix$it", false) })
    }
    var expanded by remember { mutableIntStateOf(0) }
    val accent = if (program.id == OfflineProgramId.CbtI) Cyan else Periwinkle

    SubPage(stringResource(program.eyebrowRes), stringResource(program.titleRes), onBack) {
        Text(stringResource(program.subtitleRes), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        SectionCard(quiet = true) {
            Text(stringResource(R.string.offline_disclaimer), color = TextMuted2, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.offline_program_progress, completed.size, program.modules.size),
            style = MaterialTheme.typography.titleSmall, color = accent,
        )
        Text(stringResource(R.string.offline_modules), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        program.modules.forEachIndexed { index, module ->
            val done = index in completed
            SectionCard(onClick = { expanded = index }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        null, tint = if (done) Ok else TextMuted2,
                    )
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${stringResource(module.titleRes)}", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        if (expanded != index) Text(stringResource(module.bodyRes), color = TextMuted, maxLines = 2)
                    }
                }
                if (expanded == index) {
                    Text(stringResource(module.bodyRes), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                    Text(stringResource(R.string.offline_practice), style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(stringResource(module.practiceRes), style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            val next = completed.toMutableSet()
                            if (done) next.remove(index) else next.add(index)
                            completed = next
                            prefs.edit().putBoolean("$prefix$index", !done).apply()
                        }) {
                            Text(stringResource(if (done) R.string.offline_mark_undone else R.string.offline_mark_done), color = accent)
                        }
                    }
                }
            }
        }
    }
}
