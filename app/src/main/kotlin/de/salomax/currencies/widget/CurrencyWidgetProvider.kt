package de.salomax.currencies.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.*
import de.salomax.currencies.R
import de.salomax.currencies.repository.Database
import de.salomax.currencies.view.main.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class CurrencyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scheduleUpdate(context)
        updateAllWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateAllWidgets(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val db = Database(context)
        appWidgetIds.forEach { widgetId ->
            db.deleteWidgetConfig(widgetId)
        }
        if (db.getAllWidgetIds().isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK)
        }
    }

    override fun onEnabled(context: Context) {
        schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK)
    }

    private fun scheduleUpdate(context: Context) {
        val updateRequest = OneTimeWorkRequest.Builder(CurrencyWidgetWorker::class.java)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(updateRequest)
    }

    private fun schedulePeriodicUpdate(context: Context) {
        val periodicWork = PeriodicWorkRequest.Builder(
            CurrencyWidgetWorker::class.java,
            3, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    companion object {
        private const val WIDGET_UPDATE_WORK = "currency_widget_periodic_update"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CurrencyWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val db = Database(context)
                ids.forEach { widgetId ->
                    updateWidget(context, manager, db, widgetId)
                }
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, db: Database, widgetId: Int) {
            val (baseCurrencyCode, targetCurrencyCode) = db.getWidgetConfig(widgetId)

            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val layoutId = if (minHeight < 100) R.layout.widget_currency_short else R.layout.widget_currency

            if (baseCurrencyCode == null || targetCurrencyCode == null) {
                val views = RemoteViews(context.packageName, layoutId)
                views.setTextViewText(R.id.text_from, context.getString(R.string.widget_not_configured))
                views.setTextViewText(R.id.text_to, "")
                appWidgetManager.updateAppWidget(widgetId, views)
                return
            }

            val views = RemoteViews(context.packageName, layoutId)

            // Get exchange rates from cache
            val prefsRates = context.getSharedPreferences("rates", Context.MODE_PRIVATE)
            val baseRate = prefsRates.getFloat(baseCurrencyCode, -1f)
            val targetRate = prefsRates.getFloat(targetCurrencyCode, -1f)
            val dateStr = prefsRates.getString("_date", null)
            val timestamp = prefsRates.getLong("_timestamp", -1L)

            // Get currency symbols
            val baseCurrency = de.salomax.currencies.model.Currency.fromString(baseCurrencyCode)
            val targetCurrency = de.salomax.currencies.model.Currency.fromString(targetCurrencyCode)

            val baseSymbol = baseCurrency?.symbol() ?: baseCurrencyCode
            val targetSymbol = targetCurrency?.symbol() ?: targetCurrencyCode

            // Set flags
            baseCurrency?.let { currency ->
                try {
                    val field = currency.javaClass.getDeclaredField("flag")
                    field.isAccessible = true
                    val flagResId = field.get(currency) as? Int ?: R.drawable.flag_unknown
                    val roundedBitmap = getRoundedFlag(context, flagResId)
                    if (roundedBitmap != null) {
                        views.setImageViewBitmap(R.id.image_flag_from, roundedBitmap)
                    } else {
                        views.setImageViewResource(R.id.image_flag_from, flagResId)
                    }
                    views.setViewVisibility(R.id.image_flag_from, android.view.View.VISIBLE)
                } catch (e: Exception) {
                    views.setViewVisibility(R.id.image_flag_from, android.view.View.GONE)
                }
            } ?: views.setViewVisibility(R.id.image_flag_from, android.view.View.GONE)

            targetCurrency?.let { currency ->
                try {
                    val field = currency.javaClass.getDeclaredField("flag")
                    field.isAccessible = true
                    val flagResId = field.get(currency) as? Int ?: R.drawable.flag_unknown
                    val roundedBitmap = getRoundedFlag(context, flagResId)
                    if (roundedBitmap != null) {
                        views.setImageViewBitmap(R.id.image_flag_to, roundedBitmap)
                    } else {
                        views.setImageViewResource(R.id.image_flag_to, flagResId)
                    }
                    views.setViewVisibility(R.id.image_flag_to, android.view.View.VISIBLE)
                } catch (e: Exception) {
                    views.setViewVisibility(R.id.image_flag_to, android.view.View.GONE)
                }
            } ?: views.setViewVisibility(R.id.image_flag_to, android.view.View.GONE)

            views.setTextViewText(R.id.text_from, "$baseCurrencyCode ${baseSymbol}1")
            if (baseRate > 0 && targetRate > 0) {
                val rate = targetRate / baseRate
                val formattedRate = String.format("%.2f", rate)
                views.setTextViewText(R.id.text_to, "$targetCurrencyCode $targetSymbol$formattedRate")
            } else {
                views.setTextViewText(R.id.text_to, context.getString(R.string.widget_error_no_data))
            }

            // Show last update date
            if (timestamp != -1L) {
                try {
                    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                    val formatted = dateTime.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
                    views.setTextViewText(R.id.text_updated, context.getString(R.string.widget_updated, formatted))
                } catch (e: Exception) {
                    views.setTextViewText(R.id.text_updated, context.getString(R.string.widget_updated_never))
                }
            } else if (dateStr != null) {
                try {
                    val date = LocalDate.parse(dateStr)
                    val formatted = date.format(DateTimeFormatter.ofPattern("MMM dd"))
                    views.setTextViewText(R.id.text_updated, context.getString(R.string.widget_updated, formatted))
                } catch (e: Exception) {
                    views.setTextViewText(R.id.text_updated, context.getString(R.string.widget_updated_never))
                }
            } else {
                views.setTextViewText(R.id.text_updated, context.getString(R.string.widget_updated_never))
            }

            // Set up tap intent to open main activity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("base_currency", baseCurrencyCode)
                putExtra("target_currency", targetCurrencyCode)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val openAppPendingIntent = PendingIntent.getActivity(context, widgetId, openAppIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun getRoundedFlag(context: Context, resId: Int): Bitmap? {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return null
            val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics).toInt()
            val height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 17f, context.resources.displayMetrics).toInt()
            val cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)

            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val path = Path().apply {
                addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            canvas.clipPath(path)
            drawable.draw(canvas)

            return bitmap
        }
    }
}
