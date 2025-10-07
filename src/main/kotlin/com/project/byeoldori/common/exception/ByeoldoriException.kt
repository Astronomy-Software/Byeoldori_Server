package com.project.byeoldori.common.exception

abstract class ByeoldoriException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)