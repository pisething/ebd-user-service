package com.pisethjavaschool.userservice.businessowner.registration;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.pisethjavaschool.platform.events.registration.BusinessOwnerRegistrationRequested;
import com.pisethjavaschool.userservice.businessowner.dto.BusinessOwnerRegistrationRequest;
import com.pisethjavaschool.userservice.user.dto.UserResponse;

import lombok.RequiredArgsConstructor;

/** Maps an already-created, disabled identity to a credential-free integration event. */
@Component
@RequiredArgsConstructor
public class BusinessOwnerRegistrationEventFactory {

    public BusinessOwnerRegistrationRequested create(
            UUID eventId,
            UUID registrationId,
            UserResponse user,
            BusinessOwnerRegistrationRequest request) {
        return new BusinessOwnerRegistrationRequested(
                eventId,
                registrationId,
                user.id(),
                Instant.now(),
                request.ownerType().name(),
                request.businessType().name(),
                request.firstName().trim() + " " + request.lastName().trim(),
                request.businessName(),
                null,
                null,
                request.countryCode() + request.phoneNumber(),
                request.email(),
                request.telegram());
    }
}