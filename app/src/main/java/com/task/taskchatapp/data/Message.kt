package com.task.taskchatapp.data

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Timestamp? = null
)