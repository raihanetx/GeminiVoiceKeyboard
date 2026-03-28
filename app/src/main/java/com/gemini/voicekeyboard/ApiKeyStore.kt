package com.gemini.voicekeyboard

import android.content.Context
import android.content.SharedPreferences

object ApiKeyStore {
    private const val PREFS_NAME = "gemini_prefs"
    private const val KEY_API = "gemini_api_key"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key).apply()
    }

    fun get(context: Context): String =
        prefs(context).getString(KEY_API, "") ?: ""
}
