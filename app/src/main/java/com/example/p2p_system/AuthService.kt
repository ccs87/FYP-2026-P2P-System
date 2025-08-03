package com.example.p2p_system

data class User(
    val username: String,
    val password: String,
    val email: String = ""
)

object AuthService {
    private val users = mutableListOf<User>()

    fun register(username: String, password: String, email: String): Boolean {
        if (users.any { it.username == username }) {
            return false // Username already exists
        }
        users.add(User(username, password, email))
        return true
    }

    fun login(username: String, password: String): Boolean {
        return users.any { it.username == username && it.password == password }
    }
}