package io.github.leonardodvf.deliverytracker.freight.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ConflictingFreightRatesException extends ErrorResponseException {

    public ConflictingFreightRatesException() {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Multiple freight tariffs match the destination and weight"), null);
    }
}
