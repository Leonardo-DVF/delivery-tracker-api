package io.github.leonardodvf.deliverytracker.webhook.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The response never says which check failed; the reason is kept for server-side logs only. */
@Getter
public class InvalidWebhookSignatureException extends ErrorResponseException {

    private final String reason;

    public InvalidWebhookSignatureException(String reason) {
        super(HttpStatus.UNAUTHORIZED, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Invalid or missing webhook signature"), null);
        this.reason = reason;
    }
}
