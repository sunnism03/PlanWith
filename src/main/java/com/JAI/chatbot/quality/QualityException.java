package com.JAI.chatbot.quality;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class QualityException extends ResponseStatusException {
    private final QualityError code;
    private final String field;
    public QualityException(QualityError code, String field) {
        super(switch (code) {
            case API_CONFIGURATION, API_UNAVAILABLE, API_TIMEOUT, API_RATE_LIMIT, CALL_BUDGET_EXCEEDED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        }, code + ": " + field + " — 입력 내용을 확인하고 날짜·시간을 구체적으로 지정해 주세요");
        this.code = code; this.field = field;
    }
    public QualityError code() { return code; }
    public String field() { return field; }
    public boolean repairable() { return code == QualityError.INVALID_DATE_RANGE || code == QualityError.SEMANTIC_MISMATCH; }
}
