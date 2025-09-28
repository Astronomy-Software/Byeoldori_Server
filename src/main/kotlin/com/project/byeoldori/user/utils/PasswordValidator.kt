package com.project.byeoldori.user.utils

object PasswordValidator {
    private val regex = Regex("^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#\$%^&*()-_=+]).{8,64}\$")
    fun isValid(password: String) = regex.matches(password)
}