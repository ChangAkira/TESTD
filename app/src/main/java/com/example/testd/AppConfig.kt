package com.example.testd

import okhttp3.OkHttpClient

object AppConfig {
    const val BASE_URL = "http://192.168.110.58:5000"
    val httpClient = OkHttpClient()
}
