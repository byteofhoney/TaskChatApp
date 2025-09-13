package com.task.taskchatapp.data

import com.google.firebase.Timestamp

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val type: String = "", // "mentor" or "intern"
    val createdAt: Timestamp? = null
)