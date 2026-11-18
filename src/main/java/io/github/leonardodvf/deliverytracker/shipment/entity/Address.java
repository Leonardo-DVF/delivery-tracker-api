package io.github.leonardodvf.deliverytracker.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Column(nullable = false, length = 100)
    private String contactName;
    @Column(nullable = false, length = 8)
    private String postalCode;
    @Column(nullable = false, length = 150)
    private String street;
    @Column(nullable = false, length = 20)
    private String number;
    @Column(length = 100)
    private String complement;
    @Column(nullable = false, length = 100)
    private String neighborhood;
    @Column(nullable = false, length = 100)
    private String city;
    @Column(nullable = false, length = 2)
    private String state;

    public Address(String contactName, String postalCode, String street, String number,
            String complement, String neighborhood, String city, String state) {
        this.contactName = contactName.strip();
        this.postalCode = postalCode;
        this.street = street.strip();
        this.number = number.strip();
        this.complement = complement == null ? null : complement.strip();
        this.neighborhood = neighborhood.strip();
        this.city = city.strip();
        this.state = state;
    }
}
