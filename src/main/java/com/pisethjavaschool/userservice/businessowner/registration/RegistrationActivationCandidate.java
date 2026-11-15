package com.pisethjavaschool.userservice.businessowner.registration;

import java.util.UUID;

public record RegistrationActivationCandidate(UUID registrationId, UUID platformUserId) {
}

