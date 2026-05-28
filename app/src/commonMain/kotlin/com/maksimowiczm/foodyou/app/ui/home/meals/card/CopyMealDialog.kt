package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.common.extension.minus
import foodyou.app.generated.resources.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class CopyMealDialogState(
    val mealId: Long,
    val mealName: String,
    val targetDate: LocalDate,
    val step: CopyMealDialogStep,
)

@Immutable
internal sealed interface CopyMealDialogStep {
    data object PickDate : CopyMealDialogStep

    data class Preview(
        val sourceDate: LocalDate,
        val entries: List<MealEntryModel>,
        val isCopying: Boolean,
    ) : CopyMealDialogStep
}

@Composable
internal fun CopyMealDialog(
    state: CopyMealDialogState,
    onDismiss: () -> Unit,
    onSourceDateSelected: (LocalDate) -> Unit,
    onChangeSourceDate: () -> Unit,
    onConfirm: () -> Unit,
) {
    when (val step = state.step) {
        CopyMealDialogStep.PickDate ->
            CopyMealDatePickerDialog(
                mealName = state.mealName,
                targetDate = state.targetDate,
                onDismiss = onDismiss,
                onSourceDateSelected = onSourceDateSelected,
            )

        is CopyMealDialogStep.Preview ->
            CopyMealPreviewDialog(
                mealName = state.mealName,
                targetDate = state.targetDate,
                sourceDate = step.sourceDate,
                entries = step.entries,
                isCopying = step.isCopying,
                onDismiss = onDismiss,
                onChangeSourceDate = onChangeSourceDate,
                onConfirm = onConfirm,
            )
    }
}

@Composable
private fun CopyMealDatePickerDialog(
    mealName: String,
    targetDate: LocalDate,
    onDismiss: () -> Unit,
    onSourceDateSelected: (LocalDate) -> Unit,
) {
    val initialDate = targetDate.minus(1.days)
    val maxDateMillis =
        targetDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds().takeIf { it >= 0 } ?: 0
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                initialDate.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds().takeIf { it >= 0 }
                    ?: 0,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis <= maxDateMillis
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date =
                            Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC)
                                .date
                        onSourceDateSelected(date)
                    }
                },
                enabled = datePickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(Res.string.positive_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DatePickerDefaults.DatePickerTitle(displayMode = datePickerState.displayMode)
                    Text(
                        text = stringResource(Res.string.headline_copy_meal),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = mealName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.description_copy_meal_pick_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun CopyMealPreviewDialog(
    mealName: String,
    targetDate: LocalDate,
    sourceDate: LocalDate,
    entries: List<MealEntryModel>,
    isCopying: Boolean,
    onDismiss: () -> Unit,
    onChangeSourceDate: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dateFormatter = LocalDateFormatter.current
    val canConfirm = entries.isNotEmpty() && !isCopying

    AlertDialog(
        onDismissRequest = { if (!isCopying) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.headline_copy_meal))
                Text(
                    text = mealName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text =
                        stringResource(
                            Res.string.description_copy_meal_preview,
                            dateFormatter.formatDate(targetDate),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = dateFormatter.formatDate(sourceDate),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.description_copy_meal_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(entries, key = { entry -> entry.previewKey }) { entry ->
                            MealFoodListItem(
                                entry = entry,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text(stringResource(Res.string.action_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onChangeSourceDate, enabled = !isCopying) {
                Text(stringResource(Res.string.action_choose_other_date))
            }
        },
    )
}

private val MealEntryModel.previewKey: String
    get() =
        when (this) {
            is FoodMealEntryModel -> "food-${id.value}"
            is ManualMealEntryModel -> "manual-${id.value}"
        }
