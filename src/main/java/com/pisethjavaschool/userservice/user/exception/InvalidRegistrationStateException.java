package com.pisethjavaschool.userservice.user.exception;

import com.pisethjavaschool.userservice.user.enums.RegistrationStatus;
import com.pisethjavaschool.platform.exception.ConflictException;

public class InvalidRegistrationStateException extends ConflictException {
    public InvalidRegistrationStateException(RegistrationStatus status) {
        super("INVALID_REGISTRATION_STATE", "Cannot complete registration from status: " + status);
    }
}