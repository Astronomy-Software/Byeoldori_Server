package com.project.byeoldori.user.utils

import java.security.MessageDigest

object TokenHasher {
    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}