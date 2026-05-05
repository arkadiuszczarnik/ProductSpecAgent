package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Service

@Service
class UserStorage(private val objectStore: ObjectStore) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun userKey(userId: String) = "users/$userId.json"
    private fun emailKey(email: String) = "users/_index/email-${email.lowercase()}.json"

    @Serializable
    private data class EmailIndex(val userId: String)

    fun saveUser(user: User) {
        val normalized = user.copy(email = user.email.lowercase())
        objectStore.put(
            userKey(normalized.id),
            json.encodeToString(normalized).toByteArray(),
            "application/json"
        )
        objectStore.put(
            emailKey(normalized.email),
            json.encodeToString(EmailIndex(normalized.id)).toByteArray(),
            "application/json"
        )
    }

    fun loadUser(userId: String): User? {
        val bytes = objectStore.get(userKey(userId)) ?: return null
        return json.decodeFromString<User>(bytes.toString(Charsets.UTF_8))
    }

    fun findByEmail(email: String): User? {
        val bytes = objectStore.get(emailKey(email)) ?: return null
        val index = json.decodeFromString<EmailIndex>(bytes.toString(Charsets.UTF_8))
        return loadUser(index.userId)
    }

    fun emailExists(email: String): Boolean =
        objectStore.exists(emailKey(email))
}
