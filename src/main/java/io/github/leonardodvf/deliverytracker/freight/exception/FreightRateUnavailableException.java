package io.github.leonardodvf.deliverytracker.freight.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class FreightRateUnavailableException extends ErrorResponseException {

    public FreightRateUnavailableException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "No fictional tariff available for the destination and weight"), null);
    }
}
