package com.maksimowiczm.foodyou.fooddiary.domain.usecase

import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

class CopyMealFromDateUseCase(
    private val foodEntryRepository: FoodDiaryEntryRepository,
    private val manualEntryRepository: ManualDiaryEntryRepository,
    private val transactionProvider: TransactionProvider,
    private val dateProvider: DateProvider,
) {
    suspend fun copy(
        mealId: Long,
        sourceDate: LocalDate,
        targetDate: LocalDate,
    ): Result<Int, Nothing> {
        val foodEntries = foodEntryRepository.observeAll(mealId = mealId, date = sourceDate).first()
        val manualEntries =
            manualEntryRepository.observeAll(mealId = mealId, date = sourceDate).first()

        if (foodEntries.isEmpty() && manualEntries.isEmpty()) {
            return Ok(0)
        }

        val now = dateProvider.now()

        return transactionProvider.withTransaction {
            var count = 0

            foodEntries.forEach { entry ->
                foodEntryRepository.insert(
                    measurement = entry.measurement,
                    mealId = mealId,
                    date = targetDate,
                    food = entry.food,
                    createdAt = now,
                )
                count++
            }

            manualEntries.forEach { entry ->
                manualEntryRepository.insert(
                    name = entry.name,
                    mealId = mealId,
                    date = targetDate,
                    nutritionFacts = entry.nutritionFacts,
                    createdAt = now,
                )
                count++
            }

            Ok(count)
        }
    }
}
