package com.project.byeoldori.common.exception

class InvalidInputException(message: String = ErrorCode.INVALID_INPUT_VALUE.message) : ByeoldoriException(ErrorCode.INVALID_INPUT_VALUE, message)
class UnauthorizedException(message: String = ErrorCode.UNAUTHORIZED.message) : ByeoldoriException(ErrorCode.UNAUTHORIZED, message)
class ForbiddenException(message: String = ErrorCode.FORBIDDEN.message) : ByeoldoriException(ErrorCode.FORBIDDEN, message)
class NotFoundException(errorCode: ErrorCode, message: String = errorCode.message) : ByeoldoriException(errorCode, message)
class ConflictException(errorCode: ErrorCode, message: String = errorCode.message) : ByeoldoriException(errorCode, message)
class InternalServerException(message: String = ErrorCode.INTERNAL_SERVER_ERROR.message) : ByeoldoriException(ErrorCode.INTERNAL_SERVER_ERROR, message)