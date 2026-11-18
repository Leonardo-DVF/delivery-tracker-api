package io.github.leonardodvf.deliverytracker.shipment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ShipmentNotFoundException extends ErrorResponseException {

    public ShipmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "Shipment not found"), null);
    }
}
