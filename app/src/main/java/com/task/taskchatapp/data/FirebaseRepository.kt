package com.task.taskchatapp.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // --- AUTH ---
    suspend fun loginUser(email: String, password: String): Boolean {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            true
        } catch (e: Exception) {
            Log.e("FR_REPO", "loginUser error: ${e.message}")
            false
        }
    }

    suspend fun registerUser(email: String, password: String): Boolean {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            true
        } catch (e: Exception) {
            Log.e("FR_REPO", "registerUser error: ${e.message}")
            false
        }
    }

    fun currentUserId(): String? = auth.currentUser?.uid

    fun registerOrLogin(
        name: String,
        email: String,
        password: String,
        role: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { res ->
                val uid = res.user?.uid ?: return@addOnSuccessListener
                val map = mapOf(
                    "name" to name,
                    "email" to email,
                    "type" to role,
                    "createdAt" to Timestamp.now()
                )
                db.collection("users").document(uid)
                    .set(map, SetOptions.merge())
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e ->
                        Log.e("FR_REPO", "registerOrLogin set user doc failed: ${e.message}")
                        onResult(false, e.message)
                    }
            }
            .addOnFailureListener { err ->
                Log.d("FR_REPO", "createUser failed, trying signIn: ${err.message}")
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                        val map = mapOf("name" to name, "email" to email, "type" to role)
                        db.collection("users").document(uid)
                            .set(map, SetOptions.merge())
                            .addOnSuccessListener { onResult(true, null) }
                            .addOnFailureListener { e ->
                                Log.e("FR_REPO", "signIn set user doc failed: ${e.message}")
                                onResult(false, e.message)
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("FR_REPO", "signIn failed: ${e.message}")
                        onResult(false, e.message)
                    }
            }
    }

    fun getCurrentUserDoc(onResult: (User?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.d("FR_REPO", "getCurrentUserDoc: no auth user")
            return onResult(null)
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val user = snap.toObject(User::class.java)?.copy(id = uid)
                Log.d("FR_REPO", "getCurrentUserDoc -> ${user?.id} type=${user?.type}")
                onResult(user)
            }
            .addOnFailureListener { e ->
                Log.e("FR_REPO", "getCurrentUserDoc failed: ${e.message}")
                onResult(null)
            }
    }

    // One-time fetch (keeps for backwards compatibility)
    fun fetchOppositeUsers(myRole: String, onResult: (List<User>) -> Unit) {
        val target = if (myRole == "mentor") "intern" else "mentor"
        Log.d("FR_REPO", "fetchOppositeUsers: myRole=$myRole target=$target")
        db.collection("users").whereEqualTo("type", target).get()
            .addOnSuccessListener { qs ->
                val list = qs.documents.mapNotNull { it.toObject(User::class.java)?.copy(id = it.id) }
                Log.d("FR_REPO", "fetchOppositeUsers result=${list.size}")
                onResult(list)
            }
            .addOnFailureListener { e ->
                Log.e("FR_REPO", "fetchOppositeUsers error: ${e.message}")
                onResult(emptyList())
            }
    }

    // NEW: real-time listener for opposite users — use this so lists update automatically
    fun listenOppositeUsers(myRole: String, onChange: (List<User>) -> Unit): ListenerRegistration {
        val target = if (myRole == "mentor") "intern" else "mentor"
        Log.d("FR_REPO", "listenOppositeUsers: myRole=$myRole target=$target")
        return db.collection("users")
            .whereEqualTo("type", target)
            .addSnapshotListener { qs, error ->
                if (error != null) {
                    Log.e("FR_REPO", "listenOppositeUsers error: ${error.message}")
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val list = qs?.documents?.mapNotNull { d ->
                    d.toObject(User::class.java)?.copy(id = d.id)
                } ?: emptyList()
                Log.d("FR_REPO", "listenOppositeUsers update size=${list.size}")
                onChange(list)
            }
    }

    // CHAT helpers (unchanged)
    fun getChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    fun createOrGetChat(me: User, other: User, onResult: (String?, String?) -> Unit) {
        val cid = getChatId(me.id, other.id)
        val ref = db.collection("chats").document(cid)
        ref.get().addOnSuccessListener { ds ->
            if (ds.exists()) {
                onResult(cid, null)
            } else {
                val chat = mapOf(
                    "participants" to listOf(me.id, other.id),
                    "participantNames" to listOf(me.name, other.name),
                    "lastMessage" to "",
                    "lastMessageTime" to Timestamp.now(),
                    "createdAt" to Timestamp.now()
                )
                ref.set(chat)
                    .addOnSuccessListener { onResult(cid, null) }
                    .addOnFailureListener { e ->
                        Log.e("FR_REPO", "createOrGetChat set failed: ${e.message}")
                        onResult(null, e.message)
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("FR_REPO", "createOrGetChat get failed: ${e.message}")
            onResult(null, e.message)
        }
    }

    fun listenMyChats(uid: String, onChange: (List<Chat>) -> Unit): ListenerRegistration {
        return db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { qs, error ->
                if (error != null) {
                    Log.e("FR_REPO", "listenMyChats error: ${error.message}")
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val list = qs?.documents?.mapNotNull { d -> d.toObject(Chat::class.java)?.copy(id = d.id) } ?: emptyList()
                onChange(list.sortedByDescending { it.lastMessageTime?.seconds ?: 0 })
            }
    }

    fun listenMessages(chatId: String, onChange: (List<Message>) -> Unit): ListenerRegistration {
        return db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { qs, error ->
                if (error != null) {
                    Log.e("FR_REPO", "listenMessages error: ${error.message}")
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val list = qs?.documents?.mapNotNull { d -> d.toObject(Message::class.java)?.copy(id = d.id) } ?: emptyList()
                onChange(list)
            }
    }

    fun sendMessage(chatId: String, text: String, sender: User, onDone: (Boolean) -> Unit) {
        val msgRef = db.collection("chats").document(chatId).collection("messages").document()
        val msg = mapOf(
            "id" to msgRef.id,
            "text" to text,
            "senderId" to sender.id,
            "senderName" to sender.name,
            "timestamp" to Timestamp.now()
        )
        msgRef.set(msg).addOnSuccessListener {
            db.collection("chats").document(chatId)
                .set(mapOf("lastMessage" to text, "lastMessageTime" to Timestamp.now()), SetOptions.merge())
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }.addOnFailureListener { onDone(false) }
    }

    fun currentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            id = firebaseUser.uid,
            name = firebaseUser.displayName ?: firebaseUser.email ?: "Unknown"
        )
    }
}
