package pl.misticklite

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class OrderStore(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun load(): HomeState = try {
        val json = prefs.getString("state_v1", null)?.let(::JSONObject)
        if (json == null) HomeState() else HomeState(
            readArray(json.optJSONArray("order")),
            readArray(json.optJSONArray("hidden")).toSet()
        )
    } catch (_: Exception) {
        HomeState()
    }

    /** Call on the single IO executor: writes stay ordered and never block DPAD. */
    fun save(state: HomeState): Boolean = prefs.edit().putString("state_v1", JSONObject().apply {
        put("order", JSONArray(state.order.orEmpty()))
        put("hidden", JSONArray(state.hidden.sorted()))
    }.toString()).commit()

    private fun readArray(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optString(it)?.takeIf(String::isNotBlank) }.distinct()
}
