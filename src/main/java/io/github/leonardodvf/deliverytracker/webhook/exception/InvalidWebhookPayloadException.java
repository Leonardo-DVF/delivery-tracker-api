package io.github.leonardodvf.deliverytracker.webhook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.List;

public class InvalidWebhookPayloadException extends ErrorResponseException {

    public record FieldError(String field, String message) {
    }

    private InvalidWebhookPayloadException(ProblemDetail problem) {
        super(HttpStatus.BAD_REQUEST, problem, null);
    }

    public static InvalidWebhookPayloadException malformed() {
        return new InvalidWebhookPayloadException(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Malformed webhook payload"));
    }

    public static InvalidWebhookPayloadException invalid(List<FieldError> errors) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Webhook payload validation failed");
        problem.setProperty("errors", errors);
        return new InvalidWebhookPayloadException(problem);
    }
}
