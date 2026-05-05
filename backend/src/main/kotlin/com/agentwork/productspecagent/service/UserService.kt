package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.User
import com.agentwork.productspecagent.storage.UserStorage
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.UUID

@Service
class UserService(
    private val storage: UserStorage,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun register(email: String, password: String): User {
        val normalizedEmail = email.trim().lowercase()
        validateEmail(normalizedEmail)
        validatePassword(password)
        if (storage.emailExists(normalizedEmail)) throw EmailAlreadyExistsException()

        val user = User(
            id = UUID.randomUUID().toString(),
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(password)!!,
            createdAt = clock.instant().toString(),
        )
        storage.saveUser(user)
        return user
    }

    fun authenticate(email: String, password: String): User {
        val user = storage.findByEmail(email.trim().lowercase()) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(password, user.passwordHash)) throw InvalidCredentialsException()
        return user
    }

    private fun validateEmail(email: String) {
        if (!emailRegex.matches(email)) throw InvalidEmailException("Ungültiges Email-Format")
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) throw WeakPasswordException("Passwort muss mindestens 8 Zeichen haben")
        if (password.length > 128) throw WeakPasswordException("Passwort darf maximal 128 Zeichen haben")
    }
}
