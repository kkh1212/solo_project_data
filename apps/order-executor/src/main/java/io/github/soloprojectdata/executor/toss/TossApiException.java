package io.github.soloprojectdata.executor.toss;

/**
 * 응답 원문이나 자격증명을 포함하지 않는 Toss API 실패다.
 */
public final class TossApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    TossApiException(String message, int httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    TossApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.errorCode = "transport-error";
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }
}
