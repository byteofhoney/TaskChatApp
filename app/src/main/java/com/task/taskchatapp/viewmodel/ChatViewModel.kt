package com.task.taskchatapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.task.taskchatapp.data.Chat
import com.task.taskchatapp.data.FirebaseRepository
import com.task.taskchatapp.data.Message
import com.task.taskchatapp.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel : ViewModel() {
    private val repo = FirebaseRepository()

    // Messages
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    // Current user
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    // Users list (mentors/interns)
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    // Chats list
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    // UI States
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var messagesListener: ListenerRegistration? = null
    private var chatsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    init {
        loadCurrentUser()
    }

    // --- AUTH helpers kept as you had them (adapt if you use suspend login/register)
    fun registerOrLogin(
        name: String,
        email: String,
        password: String,
        role: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        _isLoading.value = true
        repo.registerOrLogin(name, email, password, role) { success, error ->
            _isLoading.value = false
            if (success) {
                loadCurrentUser()
                onResult(true, null)
            } else {
                onResult(false, error)
            }
        }
    }

    // Force reload of current user
    private fun loadCurrentUser() {
        repo.getCurrentUserDoc { user ->
            _currentUser.value = user
            Log.d("VM", "loadCurrentUser -> id=${user?.id} type=${user?.type}")
            if (user != null) {
                startUsersListener(user.type)
                startChatsListener(user.id)
            } else {
                // clear lists if no user
                _users.value = emptyList()
                _chats.value = emptyList()
            }
        }
    }

    // Start a real-time listener for opposite-role users
    private fun startUsersListener(myRole: String) {
        usersListener?.remove()
        usersListener = repo.listenOppositeUsers(myRole) { list ->
            Log.d("VM", "startUsersListener got ${list.size} users for role=$myRole")
            _users.value = list
        }
    }

    private fun startChatsListener(myUid: String) {
        chatsListener?.remove()
        chatsListener = repo.listenMyChats(myUid) { chatList ->
            _chats.value = chatList
        }
    }

    // create or get chat
    fun createOrGetChat(otherUser: User, onResult: (String?) -> Unit) {
        val me = _currentUser.value
        if (me == null) {
            onResult(null)
            return
        }
        repo.createOrGetChat(me, otherUser) { chatId, error ->
            if (chatId != null) onResult(chatId) else {
                _errorMessage.value = error
                onResult(null)
            }
        }
    }

    // messaging
    fun sendMessage(chatId: String, text: String) {
        val sender = _currentUser.value ?: return
        repo.sendMessage(chatId, text, sender) { success ->
            if (!success) _errorMessage.value = "Failed to send message"
        }
    }

    fun observeMessages(chatId: String) {
        messagesListener?.remove()
        messagesListener = repo.listenMessages(chatId) { msgs ->
            _messages.value = msgs
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        messagesListener?.remove()
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        chatsListener?.remove()
        usersListener?.remove()
    }
}
