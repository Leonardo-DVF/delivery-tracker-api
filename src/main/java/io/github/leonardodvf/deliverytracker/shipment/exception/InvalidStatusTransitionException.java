package io.github.leonardodvf.deliverytracker.shipment.exception;

import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class InvalidStatusTransitionException extends ErrorResponseException {

    public InvalidStatusTransitionException(ShipmentStatus current, ShipmentStatus requested) {
        super(HttpStatus.CONFLICT, problem(current, requested), null);
    }

    private static ProblemDetail problem(ShipmentStatus current, ShipmentStatus requested) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Cannot change shipment status from " + current + " to " + requested);
        problem.setProperty("currentStatus", current);
        problem.setProperty("requestedStatus", requested);
        return problem;
    }
}
