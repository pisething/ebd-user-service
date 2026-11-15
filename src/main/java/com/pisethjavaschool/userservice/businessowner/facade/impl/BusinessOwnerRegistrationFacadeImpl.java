package com.pisethjavaschool.userservice.businessowner.facade.impl;

import com.pisethjavaschool.userservice.businessowner.dto.BusinessOwnerRegistrationRequest;
import com.pisethjavaschool.userservice.businessowner.facade.BusinessOwnerRegistrationFacade;
import com.pisethjavaschool.userservice.businessowner.registration.BusinessOwnerRegistrationInitiationService;
import com.pisethjavaschool.userservice.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BusinessOwnerRegistrationFacadeImpl implements BusinessOwnerRegistrationFacade {

    private final BusinessOwnerRegistrationInitiationService initiationService;

    @Override
    public Mono<UserResponse> registerBusinessOwner(BusinessOwnerRegistrationRequest request) {
        return initiationService.initiate(request);
    }
}