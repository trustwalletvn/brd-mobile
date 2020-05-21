/**
 * BreadWallet
 *
 * Created by Jade Byfield <jade@breadwallet.com> on 9/13/2018.
 * Copyright (c) 2018 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.breadwallet.tools.util

import android.content.Context
import com.breadwallet.BuildConfig
import com.breadwallet.R
import com.breadwallet.logger.logError
import com.breadwallet.model.TokenItem
import com.breadwallet.tools.manager.BRReportsManager
import com.platform.APIClient.BRResponse
import com.platform.APIClient.Companion.getBaseURL
import com.platform.APIClient.Companion.getInstance
import com.platform.util.getBooleanOrDefault
import com.platform.util.getJSONArrayOrNull
import com.platform.util.getJSONObjectOrNull
import com.platform.util.getStringOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale

object TokenUtil {
    private const val ENDPOINT_CURRENCIES = "/currencies"
    private const val ENDPOINT_CURRENCIES_SALE_ADDRESS = "/currencies?saleAddress="
    private const val FIELD_CODE = "code"
    private const val FIELD_NAME = "name"
    private const val FIELD_SCALE = "scale"
    private const val FIELD_CONTRACT_ADDRESS = "contract_address"
    private const val FIELD_IS_SUPPORTED = "is_supported"
    private const val FIELD_SALE_ADDRESS = "sale_address"
    private const val FIELD_CONTRACT_INITIAL_VALUE = "contract_initial_value"
    private const val FIELD_COLORS = "colors"
    private const val FIELD_CURRENCY_ID = "currency_id"
    private const val FIELD_TYPE = "type"
    private const val FIELD_ALTERNATE_NAMES = "alternate_names"
    private const val FIELD_CRYPTOCOMPARE = "cryptocompare"
    private const val ICON_DIRECTORY_NAME_WHITE_NO_BACKGROUND = "white-no-bg"
    private const val ICON_DIRECTORY_NAME_WHITE_SQUARE_BACKGROUND = "white-square-bg"
    private const val ICON_FILE_NAME_FORMAT = "%s.png"
    private const val START_COLOR_INDEX = 0
    private const val END_COLOR_INDEX = 1
    private const val TOKENS_FILENAME = "tokens.json"
    private const val ETHEREUM = "ethereum"
    private const val ETHEREUM_TESTNET = "ropsten"
    private const val TESTNET = "testnet"
    private const val MAINNET = "mainnet"

    private lateinit var context: Context
    private var tokenItems: List<TokenItem> = ArrayList()
    private var tokenMap: Map<String, TokenItem> = HashMap()
    private val initLock = Mutex(locked = true)

    suspend fun waitUntilInitialized() = initLock.withLock { Unit }

    /**
     * When the app first starts, fetch our local copy of tokens.json from the resource folder
     *
     * @param context The Context of the caller
     */
    fun initialize(context: Context, forceLoad: Boolean) {
        this.context = context
        val tokensFile = File(context.filesDir, TOKENS_FILENAME)
        if (!tokensFile.exists() || forceLoad) {
            try {
                val tokens = context.resources
                    .openRawResource(R.raw.tokens)
                    .reader()
                    .use { it.readText() }

                // Copy the APK tokens.json to a file on internal storage
                saveTokenListToFile(context, tokens)
                loadTokens(parseJsonToTokenList(tokens))
                initLock.unlock()
            } catch (e: IOException) {
                BRReportsManager.error("Failed to read res/raw/tokens.json", e)
            }
        } else {
            fetchTokensFromServer()
        }
    }

    /**
     * This method can either fetch the list of supported tokens, or fetch a specific token by saleAddress
     * Request the list of tokens we support from the /currencies endpoint
     *
     * @param tokenUrl The URL of the endpoint to get the token metadata from.
     */
    private fun fetchTokensFromServer(tokenUrl: String): BRResponse {
        val request = Request.Builder()
            .get()
            .url(tokenUrl)
            .header(BRConstants.HEADER_CONTENT_TYPE, BRConstants.CONTENT_TYPE_JSON_CHARSET_UTF8)
            .header(BRConstants.HEADER_ACCEPT, BRConstants.CONTENT_TYPE_JSON)
            .build()
        return getInstance(context).sendRequest(request, true)
    }

    /**
     * This method fetches a specific token by saleAddress
     *
     * @param saleAddress Optional sale address value if we are looking for a specific token response.
     */
    fun getTokenItem(saleAddress: String): TokenItem? {
        val url = getBaseURL() + ENDPOINT_CURRENCIES_SALE_ADDRESS + saleAddress
        val response = fetchTokensFromServer(url)
        if (response.isSuccessful && response.bodyText.isNotEmpty()) {
            val tokenItems = parseJsonToTokenList(response.bodyText)
            // The response in this case should contain exactly 1 token item.
            return tokenItems.singleOrNull()
        }
        return null
    }

    @Synchronized
    fun getTokenItems(): List<TokenItem> {
        if (tokenItems.isEmpty()) {
            loadTokens(getTokensFromFile())
        }
        return tokenItems
    }

    /**
     * Return a TokenItem with the given currency code or null if non TokenItem has the currency code.
     *
     * @param currencyCode The currency code of the token we are looking.
     * @return The TokenItem with the given currency code or null.
     */
    fun getTokenItemByCurrencyCode(currencyCode: String): TokenItem? {
        return tokenMap[currencyCode.toLowerCase()]
    }

    private fun fetchTokensFromServer() {
        val response = fetchTokensFromServer(getBaseURL() + ENDPOINT_CURRENCIES)
        if (response.isSuccessful && response.bodyText.isNotEmpty()) {
            // Synchronize on the class object since getTokenItems is static and also synchronizes
            // on the class object rather than on an instance of the class.
            synchronized(TokenItem::class.java) {
                val responseBody = response.bodyText

                // Check if the response from the server is valid JSON before trying to save & parse.
                if (Utils.isValidJSON(responseBody)) {
                    saveTokenListToFile(context, responseBody)
                    loadTokens(parseJsonToTokenList(responseBody))
                }
            }
        } else {
            logError("failed to fetch tokens: ${response.code}")
        }
    }

    private fun parseJsonToTokenList(jsonString: String): ArrayList<TokenItem> {
        val tokenJsonArray = try {
            JSONArray(jsonString)
        } catch (e: JSONException) {
            BRReportsManager.error("Failed to parse Token list JSON.", e)
            JSONArray()
        }
        return List(tokenJsonArray.length()) { i ->
            try {
                tokenJsonArray.getJSONObject(i).asTokenItem()
            } catch (e: JSONException) {
                BRReportsManager.error("Failed to parse Token JSON.", e)
                null
            }
        }.filterNotNull().run(::ArrayList)
    }

    private fun saveTokenListToFile(context: Context, jsonResponse: String) {
        try {
            File(context.filesDir.absolutePath, TOKENS_FILENAME).writeText(jsonResponse)
        } catch (e: IOException) {
            BRReportsManager.error("Failed to write tokens.json file", e)
        }
    }

    private fun getTokensFromFile(): List<TokenItem> = try {
        val file = File(context.filesDir.path, TOKENS_FILENAME)
        parseJsonToTokenList(file.readText())
    } catch (e: IOException) {
        BRReportsManager.error("Failed to read tokens.json file", e)
        tokenItems
    }

    fun getTokenIconPath(currencyCode: String, withBackground: Boolean): String? {
        val bundleResource = ServerBundlesHelper
            .getExtractedPath(context, ServerBundlesHelper.getBundle(ServerBundlesHelper.Type.TOKEN), null)
        val iconFileName = ICON_FILE_NAME_FORMAT.format(currencyCode)
        val iconDirectoryName = if (withBackground) {
            ICON_DIRECTORY_NAME_WHITE_SQUARE_BACKGROUND
        } else {
            ICON_DIRECTORY_NAME_WHITE_NO_BACKGROUND
        }
        return File(bundleResource).listFiles()
            ?.filter { it.name.equals(iconDirectoryName, true) }
            ?.flatMap { it.listFiles()?.asList() ?: emptyList() }
            ?.firstOrNull { it.name.equals(iconFileName, true) }
            ?.absolutePath
    }

    fun getTokenStartColor(currencyCode: String): String? {
        val tokenItem = tokenMap[currencyCode.toLowerCase(Locale.ROOT)]
        return if (tokenItem != null && !tokenItem.startColor.isNullOrBlank()) {
            tokenItem.startColor
        } else {
            context.getString(R.color.wallet_delisted_token_background)
        }
    }

    fun getTokenEndColor(currencyCode: String): String? {
        val tokenItem = tokenMap[currencyCode.toLowerCase(Locale.ROOT)]
        return if (tokenItem != null && !tokenItem.endColor.isNullOrBlank()) {
            tokenItem.endColor
        } else {
            context.getString(R.color.wallet_delisted_token_background)
        }
    }

    fun isTokenSupported(symbol: String): Boolean {
        return tokenMap[symbol.toLowerCase(Locale.ROOT)]?.isSupported ?: true
    }

    /**
     * Returns the currency code to be used when fetching the exchange rate for the token with the
     * given currency code.
     *
     * @param currencyCode the currency code
     * @return
     */
    fun getExchangeRateCode(currencyCode: String): String {
        val code = currencyCode.toLowerCase(Locale.ROOT)
        return tokenMap[code]?.exchangeRateCurrencyCode ?: currencyCode
    }

    private fun loadTokens(tokenItems: List<TokenItem>) {
        this.tokenItems = tokenItems
        tokenMap = tokenItems.associateBy { item ->
            item.symbol.toLowerCase(Locale.ROOT)
        }
    }

    fun getTokenItemForCurrencyId(currencyId: String): TokenItem? {
        return tokenItems.find { it.currencyId.equals(currencyId, true) }
    }

    private fun JSONObject.asTokenItem(): TokenItem? = try {
        val (startColor, endColor) = getJSONArrayOrNull(FIELD_COLORS)?.run {
            getStringOrNull(START_COLOR_INDEX) to getStringOrNull(END_COLOR_INDEX)
        } ?: null to null
        val currencyId = getString(FIELD_CURRENCY_ID).run {
            if (BuildConfig.BITCOIN_TESTNET) {
                replace(
                    MAINNET,
                    if (contains(ETHEREUM)) ETHEREUM_TESTNET else TESTNET
                )
            } else this
        }

        TokenItem(
            address = getStringOrNull(FIELD_CONTRACT_ADDRESS),
            symbol = getString(FIELD_CODE),
            name = getString(FIELD_NAME),
            image = null,
            isSupported = getBooleanOrDefault(FIELD_IS_SUPPORTED, true),
            currencyId = currencyId,
            type = getString(FIELD_TYPE),
            startColor = startColor,
            endColor = endColor,
            cryptocompareAlias = getJSONObjectOrNull(FIELD_ALTERNATE_NAMES)
                ?.getStringOrNull(FIELD_CRYPTOCOMPARE)
        )
    } catch (e: JSONException) {
        BRReportsManager.error("Token JSON: $this")
        BRReportsManager.error("Failed to create TokenItem from JSON.", e)
        null
    }
}