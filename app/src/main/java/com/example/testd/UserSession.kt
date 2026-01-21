package com.example.testd

import android.content.Context

data class UserInfo(
    val studentId: String,
    val name: String,
    val username: String
)

object UserSession {
    private const val PREFS_NAME = "user_session"
    private const val KEY_STUDENT_ID = "student_id"
    private const val KEY_NAME = "name"
    private const val KEY_USERNAME = "username"
    private const val KEY_LOGGED_OUT = "logged_out"

    fun save(context: Context, studentId: String, name: String, username: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_STUDENT_ID, studentId)
            .putString(KEY_NAME, name)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_LOGGED_OUT, false)
            .apply()
    }

    fun get(context: Context): UserInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val studentId = prefs.getString(KEY_STUDENT_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        return UserInfo(studentId, name, username)
    }

    fun isLoggedOut(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOGGED_OUT, false)
    }

    fun markLoggedOut(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOGGED_OUT, true).apply()
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
