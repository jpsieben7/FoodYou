package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.home.shared.HomeState
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsCardsLayout
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun MealsCards(
    homeState: HomeState,
    onAdd: (epochDay: Long, mealId: Long) -> Unit,
    onQuickAdd: (epochDay: Long, mealId: Long) -> Unit,
    onEditEntry: (foodEntryId: Long?, manualEntryId: Long?) -> Unit,
    onLongClick: (mealId: Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val viewModel: MealsCardsViewModel = koinViewModel()
    val diaryMeals = viewModel.diaryMeals.collectAsStateWithLifecycle().value
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val copyMealDialogState by viewModel.copyMealDialogState.collectAsStateWithLifecycle()

    LaunchedEffect(homeState.selectedDate, viewModel) { viewModel.setDate(homeState.selectedDate) }

    copyMealDialogState?.let { state ->
        CopyMealDialog(
            state = state,
            onDismiss = viewModel::onDismissCopyMealDialog,
            onSourceDateSelected = viewModel::onCopyMealSourceDateSelected,
            onChangeSourceDate = viewModel::onCopyMealChangeSourceDate,
            onConfirm = viewModel::onConfirmCopyMeal,
        )
    }

    when (layout) {
        MealsCardsLayout.Horizontal ->
            HorizontalMealsCards(
                meals = diaryMeals,
                targetDate = homeState.selectedDate,
                onAdd = { mealId -> onAdd(homeState.selectedDate.toEpochDays(), mealId) },
                onQuickAdd = { mealId -> onQuickAdd(homeState.selectedDate.toEpochDays(), mealId) },
                onCopyMeal = viewModel::onCopyMealClick,
                onEditEntry = { model ->
                    val foodEntry = model as? FoodMealEntryModel
                    val manualEntry = model as? ManualMealEntryModel
                    onEditEntry(foodEntry?.id?.value, manualEntry?.id?.value)
                },
                onDeleteEntry = viewModel::onDeleteEntry,
                onLongClick = onLongClick,
                shimmer = homeState.shimmer,
                contentPadding = contentPadding,
                modifier = modifier,
            )

        MealsCardsLayout.Vertical ->
            VerticalMealsCards(
                meals = diaryMeals,
                targetDate = homeState.selectedDate,
                onAdd = { mealId -> onAdd(homeState.selectedDate.toEpochDays(), mealId) },
                onQuickAdd = { mealId -> onQuickAdd(homeState.selectedDate.toEpochDays(), mealId) },
                onCopyMeal = viewModel::onCopyMealClick,
                onEditEntry = { model ->
                    val foodEntry = model as? FoodMealEntryModel
                    val manualEntry = model as? ManualMealEntryModel
                    onEditEntry(foodEntry?.id?.value, manualEntry?.id?.value)
                },
                onDeleteEntry = viewModel::onDeleteEntry,
                onLongClick = onLongClick,
                shimmer = homeState.shimmer,
                contentPadding = contentPadding,
                modifier = modifier,
            )
    }
}
