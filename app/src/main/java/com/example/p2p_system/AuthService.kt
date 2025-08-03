package com.example.p2p_system

data class User(
    val username: String,
    val password: String
)

object AuthService {
    private val users = mutableListOf(
        User(username = "test1", password = "1234"), // Pre-existing account
        User(username = "test2", password = "1234") // Second account
    )

    private val balances = mutableMapOf(
        "test1" to 100.0, // Default balance for test1
        "test2" to 100.0  // Default balance for test2
    )

    private val favorites = mutableMapOf(
        "test1" to listOf("test2"), // test1's favorite list
        "test2" to listOf("test1")  // test2's favorite list
    )

    fun register(username: String, password: String): Boolean {
        if (users.any { it.username == username }) {
            return false // Username already exists
        }
        users.add(User(username, password))
        balances[username] = 100.0 // Set default balance
        favorites[username] = emptyList() // Initialize empty favorite list
        return true
    }

    fun login(username: String, password: String): Boolean {
        return users.any { it.username == username && it.password == password }
    }

    fun getBalance(username: String): Double? {
        return balances[username]
    }

    fun getFavorites(username: String): List<String> {
        return favorites[username] ?: emptyList()
    }

    fun transfer(fromUser: String, toUser: String, amount: Double): Boolean {
        val fromBalance = balances[fromUser] ?: return false
        val toBalance = balances[toUser] ?: return false

        if (fromBalance >= amount) {
            balances[fromUser] = fromBalance - amount
            balances[toUser] = toBalance + amount
            return true
        }
        return false
    }
}