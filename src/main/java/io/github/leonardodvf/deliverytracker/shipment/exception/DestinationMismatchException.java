package io.github.leonardodvf.deliverytracker.shipment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class DestinationMismatchException extends ErrorResponseException {

    public DestinationMismatchException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "Destination postal code does not match the freight quote"), null);
    }
}
