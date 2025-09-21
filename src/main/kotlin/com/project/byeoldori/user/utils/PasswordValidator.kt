package com.project.byeoldori.user.utils

object PasswordValidator {
    private val regex = Regex("^(?=.*[A-Za-z])(?=.*\\d|.*\\W).{6,}$")
    fun isValid(password: String) = regex.matches(password)
}