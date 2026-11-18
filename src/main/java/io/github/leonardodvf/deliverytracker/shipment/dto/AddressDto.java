package io.github.leonardodvf.deliverytracker.shipment.dto;

import io.github.leonardodvf.deliverytracker.shipment.entity.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record AddressDto(
        @NotBlank @Size(max = 100) String contactName,
        @NotNull @Pattern(regexp = "[0-9]{8}") String postalCode,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 20) String number,
        @Size(max = 100) String complement,
        @NotBlank @Size(max = 100) String neighborhood,
        @NotBlank @Size(max = 100) String city,
        @NotNull @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String state) {

    public Address toAddress() {
        return new Address(contactName, postalCode, street, number, complement, neighborhood, city, state);
    }

    public static AddressDto from(Address address) {
        return new AddressDto(address.getContactName(), address.getPostalCode(), address.getStreet(),
                address.getNumber(), address.getComplement(), address.getNeighborhood(), address.getCity(), address.getState());
    }
}
