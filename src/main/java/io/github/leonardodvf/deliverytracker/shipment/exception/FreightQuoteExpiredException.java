package io.github.leonardodvf.deliverytracker.shipment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class FreightQuoteExpiredException extends ErrorResponseException {

    public FreightQuoteExpiredException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "Freight quote has expired; request a new quote"), null);
    }
}
