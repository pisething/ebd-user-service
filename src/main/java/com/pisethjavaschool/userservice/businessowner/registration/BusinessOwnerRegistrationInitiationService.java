package com.pisethjavaschool.userservice.businessowner.registration;

import org.springframework.stereotype.Service;

import com.pisethjavaschool.userservice.businessowner.dto.BusinessOwnerRegistrationRequest;
import com.pisethjavaschool.userservice.businessowner.mapper.BusinessOwnerRegistrationMapper;
import com.pisethjavaschool.userservice.user.dto.UserResponse;
import com.pisethjavaschool.userservice.user.service.UserCommandService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Staging service for asynchronous registration. It is not exposed by the HTTP
 * facade until identity/outbox crash recovery and request idempotency are ready.
 */
@Service
@RequiredArgsConstructor
public class BusinessOwnerRegistrationInitiationService {

    private final UserCommandService userCommandService;
    private final BusinessOwnerRegistrationMapper mapper;
    private final BusinessOwnerRegistrationOutboxService outboxService;

    public Mono<UserResponse> initiate(BusinessOwnerRegistrationRequest request) {
        return Mono.defer(() -> userCommandService.createPendingRegistration(
                        mapper.toCreateUserRequest(request))
                .flatMap(user -> outboxService.recordRequested(user, request)
                        .thenReturn(user)));
    }
}