package com.project.byeoldori.user.utils

object PasswordValidator {
    private val regex = Regex("^(?=.*[A-Za-z])(?=.*\\d).{6,}$")

    fun isValid(password: String): Boolean {
        return regex.matches(password)
    }
}