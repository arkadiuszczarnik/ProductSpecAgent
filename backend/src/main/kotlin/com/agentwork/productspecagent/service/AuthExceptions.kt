package com.agentwork.productspecagent.service

class InvalidCredentialsException : RuntimeException("Login fehlgeschlagen")
class EmailAlreadyExistsException : RuntimeException("Email bereits registriert")
class WeakPasswordException(message: String) : RuntimeException(message)
class InvalidEmailException(message: String) : RuntimeException(message)
