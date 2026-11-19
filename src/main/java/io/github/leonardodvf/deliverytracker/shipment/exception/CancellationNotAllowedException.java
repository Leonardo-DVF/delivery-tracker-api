package io.github.leonardodvf.deliverytracker.shipment.exception;

import io.github.leonardodvf.deliverytracker.shipment.entity.ShipmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The state machine allows the cancellation, but customer policy does not (only before pickup). */
public class CancellationNotAllowedException extends ErrorResponseException {

    public CancellationNotAllowedException(ShipmentStatus current) {
        super(HttpStatus.CONFLICT, problem(current), null);
    }

    private static ProblemDetail problem(ShipmentStatus current) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Customers can cancel only shipments that have not been picked up; contact an operator");
        problem.setProperty("currentStatus", current);
        return problem;
    }
}
