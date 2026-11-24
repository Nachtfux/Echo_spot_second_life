package ai.pocket.heyclaude.command

import ai.pocket.heyclaude.Constants
import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Station lookup in the free, open radio-browser.info directory (no account, no key).
 * Only the spoken station name is sent.
 */
object RadioBrowser {
    data class Station(val name: String, val url: String, val favicon: String?, val votes: Int)

    private const val API = "https://de1.api.radio-browser.info/json/stations/search"

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9äöüß]"), "")

    /**
     * Best station for [spokenName] (numbers already as digits, see [CommandParser.stationQuery]),
     * or null. [strict]: name must match exactly (used for "spiel X" / "play X"). Stations in
     * [preferredCountry] are searched first, then worldwide.
     */
    fun find(spokenName: String, strict: Boolean, preferredCountry: String?): Station? {
        val candidates = linkedSetOf(spokenName, spokenName.replace(" ", ""))
        for (query in candidates) {
            for (country in listOf(preferredCountry, null).distinct()) {
                val list = search(query, country)
                val want = norm(query)
                val exact = list.filter { norm(it.name) == want }.maxByOrNull { it.votes }
                if (exact != null && (!strict || exact.votes >= 20)) return exact
                if (!strict) {
                    list.filter { norm(it.name).startsWith(want) }.maxByOrNull { it.votes }?.let { return it }
                    list.maxByOrNull { it.votes }?.let { return it }
                }
            }
        }
        return null
    }

    private fun search(name: String, country: String?): List<Station> {
        val url = buildString {
            append(API).append("?name=").append(URLEncoder.encode(name, "UTF-8"))
            append("&hidebroken=true&order=votes&reverse=true&limit=15")
            if (country != null) append("&countrycode=").append(country)
        }
        return try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 8000
            con.readTimeout = 8000
            con.setRequestProperty("User-Agent", "HeyClaudeSpot/1.0")
            val body = con.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            con.disconnect()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val stream = o.optString("url_resolved").ifEmpty { o.optString("url") }
                if (!stream.startsWith("http")) null
                else Station(o.optString("name").trim(), stream, o.optString("favicon").ifEmpty { null }, o.optInt("votes"))
            }
        } catch (e: Exception) {
            Log.w(Constants.TAG, "radio-browser search failed: $e")
            emptyList()
        }
    }
}
