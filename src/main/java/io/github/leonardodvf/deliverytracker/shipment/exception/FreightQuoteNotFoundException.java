package io.github.leonardodvf.deliverytracker.shipment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class FreightQuoteNotFoundException extends ErrorResponseException {

    public FreightQuoteNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Freight quote not found"), null);
    }
}
