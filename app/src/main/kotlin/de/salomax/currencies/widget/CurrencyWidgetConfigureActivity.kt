package de.salomax.currencies.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.model.Rate
import de.salomax.currencies.repository.Database
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.view.main.spinner.SearchableSpinner

class CurrencyWidgetConfigureActivity : BaseActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var spinnerFrom: SearchableSpinner
    private lateinit var spinnerTo: SearchableSpinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Find widget ID from intent
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_widget_configure)
        setTitle(R.string.widget_configure_title)

        spinnerFrom = findViewById(R.id.spinner_from)
        spinnerTo = findViewById(R.id.spinner_to)

        // Populate spinners with currencies
        val currencies = Currency.entries.filter { it.iso4217Alpha() != "BTC" }
        val rates = currencies.map { Rate(it, 1.0f) }
        spinnerFrom.setRates(rates)
        spinnerTo.setRates(rates)

        // Set default selections (USD and EUR)
        spinnerFrom.setSelection(Currency.USD)
        spinnerTo.setSelection(Currency.EUR)

        // Load existing config if any
        val db = Database(this)
        val (base, target) = db.getWidgetConfig(widgetId)
        if (base != null) {
            spinnerFrom.setSelection(Currency.fromString(base))
        }
        if (target != null) {
            spinnerTo.setSelection(Currency.fromString(target))
        }

        findViewById<View>(R.id.btn_save).setOnClickListener {
            saveConfiguration()
        }

        findViewById<View>(R.id.btn_cancel).setOnClickListener {
            cancelConfiguration()
        }
    }

    private fun saveConfiguration() {
        val baseCurrency = (spinnerFrom.selectedItem as Rate?)?.currency?.iso4217Alpha()
        val targetCurrency = (spinnerTo.selectedItem as Rate?)?.currency?.iso4217Alpha()

        if (baseCurrency != null && targetCurrency != null) {
            val db = Database(this)
            db.saveWidgetConfig(widgetId, baseCurrency, targetCurrency)

            // Update widget
            CurrencyWidgetProvider.updateAllWidgets(this)

            // Return success
            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            setResult(RESULT_OK, resultValue)
        }
        finish()
    }

    private fun cancelConfiguration() {
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_CANCELED, resultValue)
        finish()
    }
}
