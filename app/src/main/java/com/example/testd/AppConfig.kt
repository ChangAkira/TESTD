package com.example.testd

import okhttp3.OkHttpClient

object AppConfig {
    const val BASE_URL = "http://39.96.193.209:5000"
    val httpClient = OkHttpClient()
}
