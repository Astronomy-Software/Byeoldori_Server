package com.project.byeoldori.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    // 400 Bad Request - 잘못된 요청
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이고, 영문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."),
    CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "새 비밀번호가 이전 비밀번호와 동일합니다."),
    INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "부모 댓글이 다른 게시글에 속해있습니다."),
    CANNOT_REPLY_TO_REPLY(HttpStatus.BAD_REQUEST, "대댓글에는 답글을 작성할 수 없습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    INVALID_COMMENT_FOR_POST(HttpStatus.BAD_REQUEST, "해당 게시글에 속한 댓글이 아닙니다."),
    CANNOT_LIKE_DELETED_COMMENT(HttpStatus.BAD_REQUEST, "삭제된 댓글에는 좋아요를 누를 수 없습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 빠를 수 없습니다."),
    MAX_IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "업로드 가능한 최대 이미지 수를 초과했습니다."),

    // 401 Unauthorized - 인증 실패
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다. 다시 로그인해주세요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "토큰이 유효하지 않습니다."),
    GOOGLE_ID_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 Google ID 토큰입니다."),

    // 403 Forbidden - 권한 없음
    FORBIDDEN(HttpStatus.FORBIDDEN, "요청에 대한 권한이 없습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증 후 로그인 가능합니다."),

    // 404 Not Found - 리소스 없음
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ACCOUNT_INFO_MISMATCH(HttpStatus.NOT_FOUND, "입력하신 정보와 일치하는 계정을 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    SITE_NOT_FOUND(HttpStatus.NOT_FOUND, "관측지를 찾을 수 없습니다."),
    SAVED_SITE_NOT_FOUND(HttpStatus.NOT_FOUND, "즐겨찾기 목록에 없는 항목입니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "계획/기록을 찾을 수 없습니다."),

    // 409 Conflict - 리소스 충돌
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    SITE_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 관측지 이름입니다."),
    ACCOUNT_ALREADY_EXISTS_WITH_DIFFERENT_PROVIDER(HttpStatus.CONFLICT, "이미 다른 방식으로 가입된 이메일입니다."),
    LOGIN_METHOD_MISMATCH(HttpStatus.CONFLICT, "소셜 로그인 계정입니다. 해당 소셜 로그인을 이용해주세요."),

    // 413 파일 용량 업로드 초과
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 파일이 너무 큽니다. (최대 10MB)"),

    // 418 I'm a Teapot - 서비스 지역 벗어남 (특수 케이스)
    OUT_OF_SERVICE_AREA(HttpStatus.I_AM_A_TEAPOT, "서비스 지역을 벗어났습니다."),

    // 500 Internal Server Error - 서버 내부 오류
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 전송에 실패했습니다.")
}