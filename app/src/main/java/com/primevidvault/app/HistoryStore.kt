package com.primevidvault.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val id: String,
    val title: String,
    val cover: String,
    val type: String,
    val date: Long
)

/** Download history, stored in SharedPreferences.
 *  Entries persist until the user deletes them manually — nothing is
 *  auto-removed. */
object HistoryStore {
    private const val PREFS = "prime_vid_vault"
    private const val KEY_HISTORY = "history"

    fun load(context: Context): MutableList<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                HistoryItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    cover = o.optString("cover"),
                    type = o.optString("type"),
                    date = o.optLong("date")
                )
            )
        }
        return list
    }

    fun add(context: Context, item: HistoryItem) {
        val list = load(context)
        list.add(0, item)
        // No auto-delete: history stays until the user removes it manually.
        save(context, list)
    }

    fun remove(context: Context, id: String) {
        val list = load(context).filter { it.id != id }.toMutableList()
        save(context, list)
    }

    fun clear(context: Context) {
        save(context, mutableListOf())
    }

    private fun save(context: Context, list: List<HistoryItem>) {
        val arr = JSONArray()
        for (item in list) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("title", item.title)
            o.put("cover", item.cover)
            o.put("type", item.type)
            o.put("date", item.date)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

}
