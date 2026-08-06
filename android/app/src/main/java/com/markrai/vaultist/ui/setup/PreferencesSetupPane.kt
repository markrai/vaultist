package com.markrai.vaultist.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.markrai.vaultist.domain.DateTimeInsertFormat
import com.markrai.vaultist.ui.theme.Spacing

@Composable
fun PreferencesSetupPane(
    relativeModifiedDates: Boolean,
    onRelativeModifiedDatesChange: (Boolean) -> Unit,
    dateTimeInsertFormat: DateTimeInsertFormat,
    onDateTimeInsertFormatChange: (DateTimeInsertFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Browse", style = MaterialTheme.typography.subtitle1)
        SettingsSwitchRow(
            title = "Relative dates",
            subtitle = "Show modified times as \"3 days ago\" instead of calendar dates.",
            checked = relativeModifiedDates,
            onCheckedChange = onRelativeModifiedDatesChange,
            modifier = Modifier.testTag("relative_modified_dates_row"),
            switchModifier = Modifier.testTag("relative_modified_dates_toggle"),
        )

        Text("Note editing", style = MaterialTheme.typography.subtitle1)
        Text(
            "Format used when inserting a date and time from the edit toolbar.",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DateTimeInsertFormat.entries.forEach { format ->
                SettingsRadioRow(
                    title = format.label,
                    subtitle = format.previewSample(),
                    selected = dateTimeInsertFormat == format,
                    onClick = { onDateTimeInsertFormatChange(format) },
                    modifier = Modifier.testTag("datetime_format_${format.id}"),
                )
            }
        }
    }
}
