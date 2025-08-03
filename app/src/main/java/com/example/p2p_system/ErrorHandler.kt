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

    fun handleAuthError(errorMessage: String): String {
        return when (errorMessage) {
            "Username already exists" -> "This username is already taken. Please choose another."
            "Invalid credentials" -> "The username or password is incorrect."
            "Empty fields" -> "Please fill in all required fields."
            else -> "An unknown authentication error occurred."
        }
    }
}