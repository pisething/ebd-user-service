package com.pisethjavaschool.userservice.businessowner.registration;

import com.pisethjavaschool.platform.events.registration.BusinessOwnerRegistrationRequested;
import com.pisethjavaschool.platform.events.registration.RegistrationEventTopics;
import com.pisethjavaschool.platform.outbox.R2dbcOutboxWriter;
import com.pisethjavaschool.userservice.businessowner.dto.BusinessOwnerRegistrationRequest;
import com.pisethjavaschool.userservice.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class BusinessOwnerRegistrationOutboxService {

    private final BusinessOwnerRegistrationStore registrationStore;
    private final BusinessOwnerRegistrationEventFactory eventFactory;
    private final R2dbcOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public Mono<Void> recordRequested(UserResponse user, BusinessOwnerRegistrationRequest request) {
        return Mono.defer(() -> registrationStore.createPendingIfAbsent(user.id())
                .flatMap(registrationId -> outboxWriter.appendGenerated(
                            RegistrationEventTopics.REGISTRATION_REQUESTED,
                            registrationId.toString(),
                            eventId -> {
                                BusinessOwnerRegistrationRequested event = eventFactory.create(
                                        eventId, registrationId, user, request);
                                return objectMapper.writeValueAsString(event);
                            }).then()));
    }
}