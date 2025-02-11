package com.minimalisticapps.priceconverter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.minimalisticapps.priceconverter.bitpay.BitpayApiRatesPlugin
import com.minimalisticapps.priceconverter.blockchaininfo.BlockchainInfoApiRatesPlugin
import com.minimalisticapps.priceconverter.coingecko.CoingeckoApiRatesPlugin
import com.minimalisticapps.priceconverter.ratesapiplugin.BITCOIN_PRECISION
import com.minimalisticapps.priceconverter.ratesapiplugin.BITCOIN_RATE_PRECISION_INTERNAL
import com.minimalisticapps.priceconverter.ratesapiplugin.Callback
import com.minimalisticapps.priceconverter.ratesapiplugin.RatesApiPlugin
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*


// Because fuel prices are often denominated in 3 decimal places
const val FIAT_SHITCOIN_PRECISION = 3

const val VIRTUAL_BTC_CURRENCY_INDEX = -2 // Not using -1 as it is returned by indexOf()

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private var availableCurrencies = arrayOf<String>()

    private var parentLinearLayout: LinearLayout? = null

    private var spinner: ProgressBar? = null
    private var updatedAgoText: TextView? = null
    private var spinnerCounter: Int = 0

    private var currencies: MutableList<String> = mutableListOf()
    private var currencyViews: MutableList<View> = mutableListOf()

    lateinit var timerHandler: Handler

    private var ratesUpdatedAt: Date? = null
    private var ratesBasedInBTC: MutableMap<String, BigDecimal> = mutableMapOf()

    private var ratePlugins: Map<String, RatesApiPlugin> =
        mapOf(
            "Coingecko" to CoingeckoApiRatesPlugin(),
            "Bitpay" to BitpayApiRatesPlugin(),
            "BlockchainInfo" to BlockchainInfoApiRatesPlugin()
        )

    private fun updateUpdatedAgoText() {
        val updatedAgoText = findViewById<TextView>(R.id.last_updated_ago)
        val now = Calendar.getInstance().time
        updatedAgoText.text = timeToTimeAgo(ratesUpdatedAt, now)

        if (isDiffLongerThat1hours(ratesUpdatedAt, now)) {
            updatedAgoText.setTextColor(Color.RED)
        } else {
            updatedAgoText.setTextColor(Color.BLACK)
        }
    }

    private val updateTextTask = object : Runnable {
        override fun run() {
            if (ratesUpdatedAt != null) {
                updateUpdatedAgoText()
            }

            timerHandler.postDelayed(this, 1000)
        }
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return networkCapabilities != null &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    fun deleteBtc(view: View) {
        Toast.makeText(
            this@MainActivity,
            resources.getString(R.string.delete_btc),
            Toast.LENGTH_SHORT
        )
            .show()
    }

    private fun updateUiWithRates() {
        for (currencyFromRates in ratesBasedInBTC.keys) {

            for (i in this.currencies.indices) {
                val currencyFromState = this.currencies[i]
                if (currencyFromRates == currencyFromState) {
                    val rateText =
                        currencyViews[i].findViewById<TextView>(R.id.rate)
                    val formattedBtcPrice =
                        formatBtcRate(ratesBasedInBTC[currencyFromRates])
                    rateText.text = "1 $currencyFromRates = $formattedBtcPrice BTC"

                    val editText =
                        currencyViews[i].findViewById<TextView>(R.id.number_edit_text)

                    editText.isEnabled = true
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        timerHandler.removeCallbacks(updateTextTask)
    }

    override fun onResume() {
        super.onResume()
        timerHandler.post(updateTextTask)
    }

    private fun loadRates() {
        val rates: MutableMap<String, MutableList<BigDecimal>> = mutableMapOf()

        for (pluginEntry in ratePlugins) {
            startSpinner()
            pluginEntry.value.call("", object : Callback {
                override fun onSuccess(data: Map<String, BigDecimal>) {
                    ratesUpdatedAt = Calendar.getInstance().time

                    data.forEach {
                        if (rates[it.key] == null) {
                            rates[it.key] = mutableListOf()
                        }
                        rates[it.key]?.add(it.value)
                    }

                    Log.v(
                        TAG,
                        "Prices updated by ${pluginEntry.key}, "
                                + "new values: $rates"
                    )

                    rates.entries.forEach {
                        ratesBasedInBTC[it.key] =
                            it.value.reduce { acc, bigDecimal -> acc.plus(bigDecimal) }
                                .divide(
                                    BigDecimal(it.value.size),
                                    BITCOIN_RATE_PRECISION_INTERNAL,
                                    RoundingMode.HALF_UP
                                )
                    }

                    saveRates()
                    updateUiWithRates()
                    stopSpinner()
                }

                override fun onFailure(t: Throwable) {
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to load rates from ${pluginEntry.key}: ${t.message}",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    stopSpinner()
                }
            })
        }
    }

    private fun startSpinner() {
        updatedAgoText?.visibility = View.INVISIBLE
        spinnerCounter++
        spinner?.visibility = View.VISIBLE
    }

    private fun stopSpinner() {
        spinnerCounter--
        if (spinnerCounter == 0) {
            spinner?.visibility = View.INVISIBLE
            updateUpdatedAgoText()
            updatedAgoText?.visibility = View.VISIBLE
        }
    }

    private fun setupBtcSpecialField() {
        val btcEdit = findViewById<EditText>(R.id.btc_number_edit_text)

        btcEdit.filters = arrayOf<InputFilter>(BitcoinInputFilter())

        btcEdit.setSelectAllOnFocus(true)

        btcEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!btcEdit.isFocused) {
                    return
                }
                val numberString = s.toString()
                val value = parseBigDecimalFromString(numberString) ?: return
                recalculatePrices(VIRTUAL_BTC_CURRENCY_INDEX, value)
            }

            override fun afterTextChanged(s: Editable?) {
                var numberString = s.toString().replace(",", "")

                if (numberString.startsWith(".")) {
                    numberString = "0$numberString"
                }

                val value = parseBigDecimalFromString(numberString) ?: return

                if (numberString != value.toPlainString()) {
                    return
                }

                val formatted = formatBtc(value)

                btcEdit.removeTextChangedListener(this)
                btcEdit.setText(formatted)
                btcEdit.setSelection(btcEdit.text.length)
                btcEdit.addTextChangedListener(this)
            }
        })
    }

    private fun loadAppCurrenciesState() {
        val currencies = this.getPreferences(Context.MODE_PRIVATE)
            .getString(getString(R.string.currencies_key), "")

        if (currencies != null && currencies != "") {
            val currenciesSplit = currencies.split(",")

            for (currencyIndex in currenciesSplit.indices) {
                val currency = currenciesSplit[currencyIndex]
                addCurrencyToStateAndAddUiViewForIt(currencyIndex, currency)
            }
        }
    }

    private fun loadAppRatesState() {
        val rates = this.getPreferences(Context.MODE_PRIVATE)
            .getString(getString(R.string.rates_key), "")

        if (rates != null && rates != "") {
            val ratesPairs = rates.split(",")

            for (ratePair in ratesPairs) {
                val data = ratePair.split(":")
                ratesBasedInBTC[data[0]] = BigDecimal(data[1])

            }
        }

        val updatedAt = this.getPreferences(Context.MODE_PRIVATE)
            .getString(getString(R.string.rates_updated_at_key), null)

        if (updatedAt != null) {
            ratesUpdatedAt = getDateFromTimestampInMillis(updatedAt.toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        availableCurrencies = resources.getStringArray(R.array.shitcoins)

        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        parentLinearLayout = findViewById(R.id.parent_linear_layout)
        spinner = findViewById(R.id.progressBar1)
        updatedAgoText = findViewById(R.id.last_updated_ago)

        timerHandler = Handler(Looper.getMainLooper())

        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.refreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            loadRates()
            swipeRefreshLayout.isRefreshing = false
        }

        loadAppCurrenciesState()
        loadAppRatesState()
        setupBtcSpecialField()

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view -> this.onAdd(view) }

        if (isNetworkConnected()) {
            loadRates()
        } else {
            AlertDialog.Builder(this).setTitle("No Internet Connection")
                .setMessage("Please check your internet connection and try again")
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .setIcon(android.R.drawable.ic_dialog_alert).show()
        }
    }

    private fun getPrecision(currency: String): Int {
        return if (currency !== "BTC") FIAT_SHITCOIN_PRECISION else BITCOIN_PRECISION
    }

    private fun updateCurrenciesByChangeOnIndex(
        targetCurrencyIndex: Int,
        targetCurrency: String,
        sourceRate: BigDecimal,
        sourceCurrencyValue: BigDecimal
    ) {
        val currencyView = this.currencyViews[targetCurrencyIndex]
        val editField = currencyView.findViewById<EditText>(R.id.number_edit_text)
        val targetRate = this.ratesBasedInBTC.get(targetCurrency) ?: return

        // Cannot use .equals()
        // https://stackoverflow.com/a/10950967
        if (targetRate.compareTo(BigDecimal.ZERO) == 0) {
            return
        }

        val newValue =
            sourceCurrencyValue.multiply(sourceRate)
                .divide(targetRate, getPrecision(targetCurrency), RoundingMode.HALF_UP)

        editField.text =
            Editable.Factory.getInstance().newEditable(newValue.toPlainString())
    }

    private fun recalculatePrices(sourceIndex: Int, sourceCurrencyValue: BigDecimal) {
        val sourceCurrency =
            if (sourceIndex == VIRTUAL_BTC_CURRENCY_INDEX) "BTC" else this.currencies[sourceIndex]

        Log.v(
            TAG,
            "recalculating prices for $sourceCurrency at index "
                    + "$sourceIndex at value ${sourceCurrencyValue.toPlainString()}"
        )

        val sourceRate =
            (if (sourceCurrency == "BTC") BigDecimal(1) else this.ratesBasedInBTC.get(sourceCurrency))
                ?: return

        for (targetCurrencyIndex in this.currencies.indices) {
            val targetCurrency = this.currencies[targetCurrencyIndex]
            if (sourceIndex != targetCurrencyIndex) {
                updateCurrenciesByChangeOnIndex(
                    targetCurrencyIndex,
                    targetCurrency,
                    sourceRate,
                    sourceCurrencyValue
                )
            }
        }

        if (sourceCurrency != "BTC") {
            val btcEditField = findViewById<EditText>(R.id.btc_number_edit_text)
            val newBtcValue = sourceCurrencyValue.multiply(sourceRate)
                .setScale(BITCOIN_PRECISION, RoundingMode.HALF_UP)
            btcEditField.text =
                Editable.Factory.getInstance().newEditable(newBtcValue.toPlainString())
        }
    }

    private fun addCurrencyToStateAndAddUiViewForIt(index: Int, currency: String) {
        val inflater =
            this.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val rowView: View = inflater.inflate(R.layout.field, null)
        val spinner = rowView.findViewById<Spinner>(R.id.type_spinner)
        spinner.setSelection(availableCurrencies.indexOf(currency))
        val rateText = rowView.findViewById<TextView>(R.id.rate)
        val rate = ratesBasedInBTC[currency]
        val formattedBtcPrice = formatBtcRate(rate)
        rateText.text = "1 $currency = $formattedBtcPrice BTC"

        val numberEditText = rowView.findViewById<EditText>(R.id.number_edit_text)
        numberEditText.isEnabled = rate != null
        numberEditText.filters =
            arrayOf<InputFilter>(DecimalDigitsInputFilter(getPrecision(currency)))

        numberEditText.setSelectAllOnFocus(true);

        numberEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!numberEditText.isFocused) {
                    return
                }

                val value = parseBigDecimalFromString(s.toString()) ?: return
                recalculatePrices(index, value)
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (view == null || parent == null) {
                    return
                }

                val currencyIndex = currencyViews.indexOf(parent.parent as View)
                if (currencyIndex == -1) {
                    return
                }
                val selectedCurrency = availableCurrencies.get(position)

                val rateTextToUpdate =
                    (parent.parent as View).findViewById<TextView>(R.id.rate)

                val selectedCurrencyRate = ratesBasedInBTC[selectedCurrency]
                val selectedFormattedBtcPrice = formatBtcRate(selectedCurrencyRate)
                rateTextToUpdate.text = "1 $selectedCurrency = $selectedFormattedBtcPrice BTC"

                val editText =
                    (parent.parent as View).findViewById<EditText>(R.id.number_edit_text)
                editText.isEnabled = selectedCurrencyRate != null
                if (!editText.isEnabled) {
                    editText.setText("")
                }

                currencies[currencyIndex] = selectedCurrency
                saveCurrencies()

                // After all new data saved, recalculate prices
                val focused = editText.requestFocus() // set focus to prevent infinite loop
                if (focused) {
                    val value = parseBigDecimalFromString(editText.text.toString())
                    if (value != null) {
                        recalculatePrices(currencyIndex, value)
                    }
                }
            }
        }

        parentLinearLayout?.addView(rowView, parentLinearLayout!!.childCount)

        currencies.add(index, currency)
        this.currencyViews.add(index, rowView)

        // after new row is added we need to recalculate
        val btcEdit = findViewById<EditText>(R.id.btc_number_edit_text)
        val value = parseBigDecimalFromString(btcEdit.text.toString())
        if (value != null) {
            recalculatePrices(VIRTUAL_BTC_CURRENCY_INDEX, value)
        }
    }

    fun onAdd(view: View) {
        val remainingCurrencies = availableCurrencies.subtract(currencies)
        if (remainingCurrencies.isEmpty()) {
            return
        }
        val currency = remainingCurrencies.first()

        addCurrencyToStateAndAddUiViewForIt(currencies.size, currency)
        saveCurrencies()
    }

    fun onDelete(view: View) {
        val i = currencyViews.indexOf(view.parent as View)
        parentLinearLayout?.removeView(view.parent as View)

        currencies.removeAt(i)
        currencyViews.removeAt(i)

        saveCurrencies()
    }

    private fun saveCurrencies() {
        val _this = this

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            val data = _this.currencies.joinToString(",")
            Log.v(TAG, "saveCurrencies $data")

            putString(getString(R.string.currencies_key), data)
            apply()
            commit()
        }
    }

    private fun saveRates() {
        val _this = this

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            val rawPairs: List<String> = _this.ratesBasedInBTC.entries.fold(
                listOf(),
                { acc, it -> acc + arrayOf(it.key + ":" + it.value) })

            val data = rawPairs.joinToString(",")

            Log.v(TAG, "saveRates $data")

            putString(getString(R.string.rates_key), data)
            putString(getString(R.string.rates_updated_at_key), ratesUpdatedAt?.time.toString())
            apply()
            commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun startAboutActivity(item: android.view.MenuItem) {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }
}