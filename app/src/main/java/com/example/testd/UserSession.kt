package com.example.testd

import android.content.Context

data class UserInfo(
    val studentId: String,
    val name: String,
    val username: String,
    val avatarFilename: String,
    val avatarUrl: String,
    val balance: Double,
    val stars: Int
)

object UserSession {
    private const val PREFS_NAME = "user_session"
    private const val KEY_STUDENT_ID = "student_id"
    private const val KEY_NAME = "name"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR_FILENAME = "avatar_filename"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_BALANCE = "balance"
    private const val KEY_STARS = "stars"
    private const val KEY_LOGGED_OUT = "logged_out"

    fun save(
        context: Context,
        studentId: String,
        name: String,
        username: String,
        avatarFilename: String,
        avatarUrl: String,
        balance: Double,
        stars: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_STUDENT_ID, studentId)
            .putString(KEY_NAME, name)
            .putString(KEY_USERNAME, username)
            .putString(KEY_AVATAR_FILENAME, avatarFilename)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .putFloat(KEY_BALANCE, balance.toFloat())
            .putInt(KEY_STARS, stars)
            .putBoolean(KEY_LOGGED_OUT, false)
            .apply()
    }

    fun get(context: Context): UserInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val studentId = prefs.getString(KEY_STUDENT_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val username = prefs.getString(KEY_USERNAME, "") ?: ""
        val avatarFilename = prefs.getString(KEY_AVATAR_FILENAME, "") ?: ""
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: ""
        val balance = prefs.getFloat(KEY_BALANCE, 0f).toDouble()
        val stars = prefs.getInt(KEY_STARS, 0)
        return UserInfo(studentId, name, username, avatarFilename, avatarUrl, balance, stars)
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
