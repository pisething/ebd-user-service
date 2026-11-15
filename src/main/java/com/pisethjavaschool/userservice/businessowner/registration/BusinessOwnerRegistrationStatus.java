package com.pisethjavaschool.userservice.businessowner.registration;

public enum BusinessOwnerRegistrationStatus {
    PENDING,
    IDENTITY_PROVISIONING,
    IDENTITY_READY,
    OWNER_PROVISIONED,
    ROLE_ASSIGNED,
    ACTIVATION_PENDING,
    COMPLETED,
    FAILED
}