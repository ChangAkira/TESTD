package com.example.testd.tasks

data class TaskItem(
    val id: Int,
    val title: String,
    val description: String,
    val startLocation: String,
    val targetLocation: String,
    val reward: Double,
    val status: String
)
