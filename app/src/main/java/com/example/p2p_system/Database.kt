package com.example.p2p_system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class User(
    val username: String,
    val password: String
)

enum class TransactionType { PAY, RECEIVE }

data class TransactionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val fromUser: String,
    val toUser: String,
    val amount: Double, // always 100.0 in history
    val note: String? = null
)

object Database {
    // Pre-existing users for testing purposes
    private val users = mutableListOf(
        User(username = "test1", password = "1234"),
        User(username = "test2", password = "1234")
    )

    private val balances = mutableMapOf(
        // default balances for testing purposes
        "test1" to 1000.0,
        "test2" to 1000.0
    )

    private val favorites = mutableMapOf(
        "test1" to listOf("test2"), // test1's favorite list
        "test2" to listOf("test1")  // test2's favorite list
    )

    // Transaction history (only $100 records are kept)
    private val records = mutableListOf<TransactionRecord>()
    private val _recordsFlow = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val recordsFlow: StateFlow<List<TransactionRecord>> = _recordsFlow.asStateFlow()

    init {
        // Seed two default $100 records: one Pay and one Receive for the test users
        val now = System.currentTimeMillis()
        records.add(
            TransactionRecord(
                timestamp = now - 24 * 60 * 60 * 1000L,
                fromUser = "test1",
                toUser = "test2",
                amount = 100.0,
                note = "Seed: test1 paid test2"
            )
        )
        records.add(
            TransactionRecord(
                timestamp = now - 60 * 60 * 1000L,
                fromUser = "test2",
                toUser = "test1",
                amount = 100.0,
                note = "Seed: test2 paid test1"
            )
        )
        publish()
    }

    private fun publish() {
        _recordsFlow.value = records.sortedByDescending { it.timestamp }
    }

    fun register(username: String, password: String): Boolean {
        if (users.any { it.username == username }) return false
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

    // Only $100 transactions are logged in history; ignore other amounts.
    fun transfer(fromUser: String, toUser: String, amount: Double): Boolean {
        val fromBalance = balances[fromUser] ?: return false
        val toBalance = balances[toUser] ?: return false

        if (fromBalance >= amount) {
            balances[fromUser] = fromBalance - amount
            balances[toUser] = toBalance + amount

            // Always store as $100 in history (current system only transacts 100)
            records.add(
                TransactionRecord(
                    timestamp = System.currentTimeMillis(),
                    fromUser = fromUser,
                    toUser = toUser,
                    amount = 100.0,
                    note = null
                )
            )
            publish()
            return true
        }
        return false
    }

    fun getUserRecords(username: String): List<TransactionRecord> {
        return records
            .asSequence()
            .filter { it.fromUser == username || it.toUser == username }
            .sortedByDescending { it.timestamp }
            .toList()
    }
}
