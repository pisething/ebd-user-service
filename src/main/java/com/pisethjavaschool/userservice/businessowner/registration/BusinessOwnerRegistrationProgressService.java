package com.pisethjavaschool.userservice.businessowner.registration;

import com.pisethjavaschool.platform.events.registration.OwnerRoleAssigned;
import com.pisethjavaschool.platform.events.registration.PropertyOwnerProvisioned;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BusinessOwnerRegistrationProgressService {

    private final BusinessOwnerRegistrationStore registrationStore;
    private final BusinessOwnerRegistrationActivationService activationService;

    @Transactional
    public Mono<Void> ownerProvisioned(PropertyOwnerProvisioned event) {
        if (event == null || event.eventId() == null || event.registrationId() == null
                || event.platformUserId() == null || event.propertyOwnerId() == null) {
            return Mono.error(new IllegalArgumentException("Owner provisioning event is incomplete"));
        }
        return advance(event.registrationId(), event.platformUserId(),
                BusinessOwnerRegistrationStatus.PENDING,
                BusinessOwnerRegistrationStatus.OWNER_PROVISIONED);
    }

    public Mono<Void> roleAssigned(OwnerRoleAssigned event) {
        if (event == null || event.eventId() == null || event.registrationId() == null
                || event.platformUserId() == null) {
            return Mono.error(new IllegalArgumentException("Role assignment event is incomplete"));
        }
        return advance(event.registrationId(), event.platformUserId(),
                BusinessOwnerRegistrationStatus.OWNER_PROVISIONED,
                BusinessOwnerRegistrationStatus.ROLE_ASSIGNED)
                .then(Mono.defer(() -> activationService.activate(event.registrationId(), event.platformUserId())));
    }

    private Mono<Void> advance(UUID registrationId, UUID platformUserId,
            BusinessOwnerRegistrationStatus expected, BusinessOwnerRegistrationStatus next) {
        return registrationStore.transitionForUser(registrationId, platformUserId, expected, next)
                .flatMap(updated -> {
                    if (updated) {
                        return Mono.<Void>empty();
                    }
                    return registrationStore.findStatusForUser(registrationId, platformUserId)
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "Registration not found for the specified user")))
                            .flatMap(current -> {
                                if (current == next || isLaterStage(current, next)) {
                                    return Mono.<Void>empty();
                                }
                                return Mono.error(new IllegalStateException(
                                        "Registration event arrived before its prerequisite: " + current));
                            });
                });
    }

    private boolean isLaterStage(BusinessOwnerRegistrationStatus current,
            BusinessOwnerRegistrationStatus next) {
        return switch (next) {
            case OWNER_PROVISIONED -> current == BusinessOwnerRegistrationStatus.ROLE_ASSIGNED
                    || current == BusinessOwnerRegistrationStatus.ACTIVATION_PENDING
                    || current == BusinessOwnerRegistrationStatus.COMPLETED;
            case ROLE_ASSIGNED -> current == BusinessOwnerRegistrationStatus.ACTIVATION_PENDING
                    || current == BusinessOwnerRegistrationStatus.COMPLETED;
            default -> false;
        };
    }
}
