package io.github.leonardodvf.deliverytracker.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class EmailAlreadyRegisteredException extends ErrorResponseException {

    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Email already registered"), null);
    }
}
