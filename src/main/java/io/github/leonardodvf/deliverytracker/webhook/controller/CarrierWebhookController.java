package io.github.leonardodvf.deliverytracker.webhook.controller;

import io.github.leonardodvf.deliverytracker.webhook.dto.CarrierEventPayload;
import io.github.leonardodvf.deliverytracker.webhook.dto.WebhookReceipt;
import io.github.leonardodvf.deliverytracker.webhook.service.CarrierWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Carrier webhooks")
public class CarrierWebhookController {

    public static final String TIMESTAMP_HEADER = "X-Carrier-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Carrier-Signature";

    private final CarrierWebhookService webhooks;

    /**
     * The body is taken as raw bytes because the HMAC covers the exact bytes sent; parsing first and
     * re-serializing could change whitespace or field order and break the signature.
     */
    @PostMapping(path = "/carrier", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Receive a carrier tracking event",
            description = "No bearer token: authenticated by an HMAC-SHA256 signature of `<timestamp>.<raw body>` "
                    + "with the shared secret. Redelivered event ids return DUPLICATE; stale statuses return IGNORED; "
                    + "a status whose predecessor has not arrived yet returns 409 so the carrier retries later.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CarrierEventPayload.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event APPLIED, DUPLICATE or IGNORED; stop retrying"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/InvalidWebhookSignature"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/ShipmentNotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/InvalidStatusTransition")
    })
    public WebhookReceipt receive(
            @Parameter(description = "Unix time in seconds when the request was signed", required = true, example = "1763553600")
            @RequestHeader(name = TIMESTAMP_HEADER, required = false) String timestamp,
            @Parameter(description = "`sha256=` followed by the hex HMAC", required = true)
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] body) {
        return webhooks.receive(timestamp, signature, body);
    }
}
