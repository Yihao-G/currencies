package de.salomax.currencies.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.salomax.currencies.repository.ExchangeRatesRepository
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class CurrencyWidgetWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val start = System.currentTimeMillis()
        val repository = ExchangeRatesRepository(ctx)

        // Fetch latest rates
        repository.getExchangeRates()

        // Ensure minimum update time for UI feedback
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 1000) {
            delay((1000 - elapsed).milliseconds)
        }

        // Update all widgets
        CurrencyWidgetProvider.updateAllWidgets(ctx)

        return Result.success()
    }
}
