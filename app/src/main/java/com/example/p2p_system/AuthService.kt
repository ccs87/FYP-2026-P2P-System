package com.example.p2p_system

data class User(
    val username: String,
    val password: String
)

object AuthService {
    //Pre existing users for testing purposes
    private val users = mutableListOf(
        User(username = "test1", password = "1234"),
        User(username = "test2", password = "1234")
    )

    private val balances = mutableMapOf(
        //default balances for testing purposes
        "test1" to 1000.0,
        "test2" to 1000.0
    )

    private val favorites = mutableMapOf(
        "test1" to listOf("test2"), // test1's favorite list
        "test2" to listOf("test1")  // test2's favorite list
    )

    fun register(username: String, password: String): Boolean {
        if (users.any { it.username == username }) {
            return false
        }
        users.add(User(username, password))
        balances[username] = 100.0
        favorites[username] = emptyList()
        return true
    }

    fun login(username: String, password: String): Boolean {
        return users.any { it.username == username && it.password == password }
    }

    fun getBalance(username: String): Double? {
        return balances[username]
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