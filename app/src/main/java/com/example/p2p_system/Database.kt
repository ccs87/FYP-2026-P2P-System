package com.example.p2p_system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class User(
    val username: String,
    val password: String,
    val isAdmin: Boolean = false
)

enum class UserStatus { ACTIVE, DEACTIVATED }

enum class TransactionType { PAY, RECEIVE }

data class TransactionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val fromUser: String,
    val toUser: String,
    val amount: Double,
    val note: String? = null
)

data class UserSnapshot(
    val username: String,
    val balance: Double,
    val status: UserStatus,
    val isAdmin: Boolean
)

object Database {
    private val users = mutableListOf(
        User(username = "test1", password = "1234"),
        User(username = "test2", password = "1234"),
        User(username = "admin1", password = "1234", isAdmin = true)
    )

    private val balances = mutableMapOf(
        "test1" to 1000.0,
        "test2" to 1000.0,
        "admin1" to 0.0
    )

    private val statuses = mutableMapOf(
        "test1" to UserStatus.ACTIVE,
        "test2" to UserStatus.ACTIVE,
        "admin1" to UserStatus.ACTIVE
    )

    private val favorites = mutableMapOf(
        "test1" to listOf("test2"),
        "test2" to listOf("test1"),
        "admin1" to emptyList()
    )

    private val records = mutableListOf<TransactionRecord>()
    private val _recordsFlow = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val recordsFlow: StateFlow<List<TransactionRecord>> = _recordsFlow.asStateFlow()

    init {
        val now = System.currentTimeMillis()
        records.add(
            TransactionRecord(
                timestamp = now - 24 * 60 * 60 * 1000L,
                fromUser = "test1",
                toUser = "test2",
                amount = 100.0,
                note = "test1 paid test2"
            )
        )
        records.add(
            TransactionRecord(
                timestamp = now - 60 * 60 * 1000L,
                fromUser = "test2",
                toUser = "test1",
                amount = 100.0,
                note = "test2 paid test1"
            )
        )
        publish()
    }

    private fun publish() {
        _recordsFlow.value = records.sortedByDescending { it.timestamp }
    }

    fun isAdmin(username: String): Boolean {
        return users.firstOrNull { it.username == username }?.isAdmin == true
    }

    fun getStatus(username: String): UserStatus {
        return statuses[username] ?: UserStatus.ACTIVE
    }

    fun setStatus(username: String, status: UserStatus): Boolean {
        if (users.none { it.username == username }) return false
        statuses[username] = status
        return true
    }

    fun getAllUserSnapshots(): List<UserSnapshot> {
        return users
            .asSequence()
            .sortedBy { it.username }
            .map { u ->
                UserSnapshot(
                    username = u.username,
                    balance = balances[u.username] ?: 0.0,
                    status = statuses[u.username] ?: UserStatus.ACTIVE,
                    isAdmin = u.isAdmin
                )
            }
            .toList()
    }

    fun register(username: String, password: String): Boolean {
        if (users.any { it.username == username }) return false
        users.add(User(username, password, isAdmin = false))
        balances[username] = 1000.0
        favorites[username] = emptyList()
        statuses[username] = UserStatus.ACTIVE
        return true
    }

    fun login(username: String, password: String): Boolean {
        val user = users.firstOrNull { it.username == username } ?: return false
        if (user.password != password) return false
        // New: deactivated users cannot login (admin can reactivate them)
        if ((statuses[username] ?: UserStatus.ACTIVE) == UserStatus.DEACTIVATED) return false
        return true
    }

    fun getBalance(username: String): Double? {
        return balances[username]
    }

    fun transfer(fromUser: String, toUser: String, amount: Double): Boolean {
        if (isAdmin(fromUser) || isAdmin(toUser)) return false

        if (getStatus(fromUser) != UserStatus.ACTIVE) return false
        if (getStatus(toUser) != UserStatus.ACTIVE) return false

        val fromBalance = balances[fromUser] ?: return false
        val toBalance = balances[toUser] ?: return false

        if (fromBalance >= amount) {
            balances[fromUser] = fromBalance - amount
            balances[toUser] = toBalance + amount

            records.add(
                TransactionRecord(
                    timestamp = System.currentTimeMillis(),
                    fromUser = fromUser,
                    toUser = toUser,
                    amount = amount,
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
