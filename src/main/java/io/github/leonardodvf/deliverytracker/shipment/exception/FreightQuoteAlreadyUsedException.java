package io.github.leonardodvf.deliverytracker.shipment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class FreightQuoteAlreadyUsedException extends ErrorResponseException {

    public FreightQuoteAlreadyUsedException() {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Freight quote has already been used for a shipment"), null);
    }
}
