package com.pisethjavaschool.userservice.businessowner.facade.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pisethjavaschool.platform.accesscontrol.client.AccessControlClient;
import com.pisethjavaschool.platform.accesscontrol.client.enums.AccessRoleCode;
import com.pisethjavaschool.userservice.businessowner.dto.BusinessOwnerRegistrationRequest;
import com.pisethjavaschool.userservice.businessowner.enums.BusinessType;
import com.pisethjavaschool.userservice.businessowner.facade.BusinessOwnerRegistrationFacade;
import com.pisethjavaschool.userservice.businessowner.mapper.BusinessOwnerRegistrationMapper;
import com.pisethjavaschool.userservice.common.client.BusinessOwnerClient;
import com.pisethjavaschool.userservice.user.dto.UserResponse;
import com.pisethjavaschool.userservice.user.service.UserCommandService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class BusinessOwnerRegistrationFacadeImpl implements BusinessOwnerRegistrationFacade {
    private final UserCommandService userCommandService;  //CQRS
    private final BusinessOwnerRegistrationMapper mapper;
    private final BusinessOwnerClient businessOwnerClient;
    private final AccessControlClient accessControlClient;

    @Override
    @Transactional
    public Mono<UserResponse> registerBusinessOwner(BusinessOwnerRegistrationRequest request) {
        return userCommandService.create(mapper.toCreateUserRequest(request))
        		.flatMap(user -> businessOwnerClient.createOwnerProfile(user.id(), request)
                        .flatMap(organizationId -> accessControlClient.assignRole(
                                user.id(),
                                ownerRole(request.businessType()),
                                organizationId))
                        .thenReturn(user));
        
    }
    private AccessRoleCode ownerRole(BusinessType businessType) {
        return switch (businessType) {
            case RESTAURANT -> AccessRoleCode.RESTAURANT_OWNER;
            case KTV -> AccessRoleCode.KTV_OWNER;
        };
    }
}
