package com.sparsh.sentinel.ingest.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "customer", schema = "sentinel")
public class Customer {

    /** Peer group assigned at onboarding. */
    public enum Segment { RETAIL, BUSINESS, CORPORATE }

    public enum RiskRating { LOW, MEDIUM, HIGH }

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "full_name", nullable = false, length = 256)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Segment segment;

    @Column(nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", nullable = false, length = 8)
    private RiskRating riskRating;

    @Column(name = "onboarded_on", nullable = false)
    private LocalDate onboardedOn;

    protected Customer() {
        // for JPA
    }

    public Customer(String id, String fullName, Segment segment, String country,
                    RiskRating riskRating, LocalDate onboardedOn) {
        this.id = id;
        this.fullName = fullName;
        this.segment = segment;
        this.country = country;
        this.riskRating = riskRating;
        this.onboardedOn = onboardedOn;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Segment getSegment() {
        return segment;
    }

    public String getCountry() {
        return country;
    }

    public RiskRating getRiskRating() {
        return riskRating;
    }

    public LocalDate getOnboardedOn() {
        return onboardedOn;
    }
}
