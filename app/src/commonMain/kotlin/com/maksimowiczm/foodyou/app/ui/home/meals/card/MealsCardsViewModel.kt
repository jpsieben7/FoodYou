package com.maksimowiczm.foodyou.app.ui.home.meals.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryMeal
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.MealsPreferences
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CopyMealFromDateUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.ObserveDiaryMealsUseCase
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

internal class MealsCardsViewModel(
    private val observeDiaryMealsUseCase: ObserveDiaryMealsUseCase,
    private val foodEntryRepository: FoodDiaryEntryRepository,
    private val manualEntryRepository: ManualDiaryEntryRepository,
    private val copyMealFromDateUseCase: CopyMealFromDateUseCase,
    mealsPreferencesRepository: UserPreferencesRepository<MealsPreferences>,
) : ViewModel() {
    private val dateState = MutableStateFlow<LocalDate?>(null)

    private val _copyMealDialogState = MutableStateFlow<CopyMealDialogState?>(null)
    val copyMealDialogState: StateFlow<CopyMealDialogState?> = _copyMealDialogState.asStateFlow()

    val diaryMeals: StateFlow<List<MealModel>?> =
        dateState
            .filterNotNull()
            .flatMapLatest { date -> observeDiaryMealsUseCase.observe(date) }
            .map { list -> list.map { it.toMealModel() } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(60_000),
                initialValue = null,
            )

    private val _layout = mealsPreferencesRepository.observe().map { it.layout }
    val layout =
        _layout.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = runBlocking { _layout.first() },
        )

    fun setDate(date: LocalDate) {
        viewModelScope.launch { dateState.value = date }
    }

    fun onDeleteEntry(model: MealEntryModel) {
        viewModelScope.launch {
            when (model) {
                is FoodMealEntryModel -> foodEntryRepository.delete(model.id)
                is ManualMealEntryModel -> manualEntryRepository.delete(model.id)
            }
        }
    }

    fun onCopyMealClick(meal: MealModel, targetDate: LocalDate) {
        _copyMealDialogState.value =
            CopyMealDialogState(
                mealId = meal.id,
                mealName = meal.name,
                targetDate = targetDate,
                step = CopyMealDialogStep.PickDate,
            )
    }

    fun onDismissCopyMealDialog() {
        _copyMealDialogState.value = null
    }

    fun onCopyMealSourceDateSelected(sourceDate: LocalDate) {
        val dialogState = _copyMealDialogState.value ?: return

        viewModelScope.launch {
            val entries = loadMealEntries(mealId = dialogState.mealId, date = sourceDate)
            _copyMealDialogState.value =
                dialogState.copy(
                    step =
                        CopyMealDialogStep.Preview(
                            sourceDate = sourceDate,
                            entries = entries,
                            isCopying = false,
                        ),
                )
        }
    }

    fun onCopyMealChangeSourceDate() {
        _copyMealDialogState.update { state ->
            state?.copy(step = CopyMealDialogStep.PickDate)
        }
    }

    fun onConfirmCopyMeal() {
        val dialogState = _copyMealDialogState.value ?: return
        val preview = dialogState.step as? CopyMealDialogStep.Preview ?: return
        if (preview.entries.isEmpty() || preview.isCopying) return

        viewModelScope.launch {
            _copyMealDialogState.value = dialogState.copy(step = preview.copy(isCopying = true))

            copyMealFromDateUseCase.copy(
                mealId = dialogState.mealId,
                sourceDate = preview.sourceDate,
                targetDate = dialogState.targetDate,
            )

            _copyMealDialogState.value = null
        }
    }

    private suspend fun loadMealEntries(mealId: Long, date: LocalDate): List<MealEntryModel> {
        val foodEntries = foodEntryRepository.observeAll(mealId = mealId, date = date).first()
        val manualEntries = manualEntryRepository.observeAll(mealId = mealId, date = date).first()
        return (foodEntries + manualEntries).sortedBy { it.name }.map { it.toMealEntryModel() }
    }
}

private fun DiaryMeal.toMealModel(): MealModel =
    MealModel(
        id = meal.id,
        name = meal.name,
        from = meal.from,
        to = meal.to,
        isAllDay = meal.from == meal.to,
        foods = entries.map { it.toMealEntryModel() },
        energy = nutritionFacts.energy.value?.roundToInt() ?: 0,
        proteins = nutritionFacts.proteins.value ?: 0.0,
        carbohydrates = nutritionFacts.carbohydrates.value ?: 0.0,
        fats = nutritionFacts.fats.value ?: 0.0,
    )

private fun DiaryEntry.toMealEntryModel(): MealEntryModel =
    when (this) {
        is FoodDiaryEntry ->
            FoodMealEntryModel(
                id = id,
                name = food.name,
                energy = nutritionFacts.energy.value?.roundToInt(),
                proteins = nutritionFacts.proteins.value,
                carbohydrates = nutritionFacts.carbohydrates.value,
                fats = nutritionFacts.fats.value,
                measurement = measurement,
                weight = weight,
                isLiquid = food.isLiquid,
                isRecipe = food is DiaryFoodRecipe,
                totalWeight = food.totalWeight,
                servingWeight = food.servingWeight,
            )

        is ManualDiaryEntry ->
            ManualMealEntryModel(
                id = id,
                name = name,
                energy = nutritionFacts.energy.value?.roundToInt(),
                proteins = nutritionFacts.proteins.value,
                carbohydrates = nutritionFacts.carbohydrates.value,
                fats = nutritionFacts.fats.value,
            )
    }
