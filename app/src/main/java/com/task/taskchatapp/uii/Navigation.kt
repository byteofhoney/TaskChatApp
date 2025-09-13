package com.task.taskchatapp.uii

import androidx.compose.runtime.*
import com.task.taskchatapp.viewmodel.ChatViewModel

@Composable
fun AppNavigation(viewModel: ChatViewModel) {
    var screen by remember { mutableStateOf("login") }
    var chatId by remember { mutableStateOf("") }

    val currentUser by viewModel.currentUser.collectAsState()

    // Auto-navigate if user is already logged in
    LaunchedEffect(currentUser) {
        if (currentUser != null && screen == "login") {
            screen = "messages"
        }
    }

    when (screen) {
        "login" -> {
            LoginScreen(viewModel) {
                screen = "messages"
            }
        }
        "messages" -> {
            MessagesListScreen(viewModel) { selectedChatId ->
                chatId = selectedChatId
                screen = "chat"
            }
        }
        "chat" -> {
            ChatScreen(
                viewModel = viewModel,
                chatId = chatId,
                onBack = {
                    viewModel.clearMessages()
                    screen = "messages"
                }
            )
        }
    }
}

