package com.example.p2p_system

import android.util.Log

object ErrorHandler {

    fun handleException(exception: Exception): String {
        Log.e("ErrorHandler", "Exception caught: ${exception.message}", exception)
        return when (exception) {
            is IllegalArgumentException -> "Invalid input provided."
            is IllegalStateException -> "Illegal state encountered."
            is NullPointerException -> "Unexpected null value."
            is IndexOutOfBoundsException -> "Index out of bounds."
            is UnsupportedOperationException -> "Operation not supported."
            else -> "An unexpected error occurred."
        }
    }

    fun validateLogin(username: String, password: String): String? {
        return when {
            username.isEmpty() || password.isEmpty() -> "Please enter username and password."
            !Database.login(username, password) -> "Invalid credentials. Please try again."
            else -> null
        }
    }

    fun validateRegistration(username: String, password: String, confirmPassword: String): String? {
        return when {
            username.isEmpty() || password.isEmpty() -> "Please fill all fields."
            password != confirmPassword -> "Passwords don't match."
            !Database.register(username, password) -> "Username already taken."
            else -> null
        }
    }

    fun validateTransfer(fromUser: String, toUser: String, amount: String): String? {
        val parsedAmount = amount.toDoubleOrNull()
        return when {
            toUser.isEmpty() -> "Recipient username cannot be empty."
            parsedAmount == null || parsedAmount <= 0 -> "Invalid transfer amount."
            !Database.transfer(fromUser, toUser, parsedAmount) -> "Transfer failed. Check balance or recipient username."
            else -> null
        }
    }
}
