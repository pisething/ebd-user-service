package com.pisethjavaschool.userservice.businessowner.registration;

import com.pisethjavaschool.platform.security.PlatformAuditorContext;
import com.pisethjavaschool.platform.events.registration.OwnerRoleAssigned;
import com.pisethjavaschool.platform.events.registration.PropertyOwnerProvisioned;
import com.pisethjavaschool.platform.events.registration.RegistrationEventTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class BusinessOwnerRegistrationProgressKafkaListener {

    private final BusinessOwnerRegistrationProgressService progressService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = RegistrationEventTopics.OWNER_PROVISIONED,
            groupId = "user-service-registration-owner-progress-v1")
    public void onOwnerProvisioned(String payload) {
        PropertyOwnerProvisioned event = objectMapper.readValue(payload, PropertyOwnerProvisioned.class);
        PlatformAuditorContext.withAuditorId(
                progressService.ownerProvisioned(event),
                PlatformAuditorContext.REGISTRATION_SYSTEM_AUDITOR).block();
    }

    @KafkaListener(topics = RegistrationEventTopics.ROLE_ASSIGNED,
            groupId = "user-service-registration-role-progress-v1")
    public void onRoleAssigned(String payload) {
        OwnerRoleAssigned event = objectMapper.readValue(payload, OwnerRoleAssigned.class);
        PlatformAuditorContext.withAuditorId(
                progressService.roleAssigned(event),
                PlatformAuditorContext.REGISTRATION_SYSTEM_AUDITOR).block();
    }
}