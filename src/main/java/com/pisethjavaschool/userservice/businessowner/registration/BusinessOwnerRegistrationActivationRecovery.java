package com.pisethjavaschool.userservice.businessowner.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "registration.activation.recovery", name = "enabled", havingValue = "true")
public class BusinessOwnerRegistrationActivationRecovery {

    private final BusinessOwnerRegistrationStore registrationStore;
    private final BusinessOwnerRegistrationActivationService activationService;

    @Scheduled(fixedDelayString = "${registration.activation.recovery.interval-ms:30000}")
    public void recover() {
        registrationStore.findActivationPending(100)
                .concatMap(candidate -> activationService.activate(
                                candidate.registrationId(), candidate.platformUserId())
                        .onErrorResume(error -> {
                            log.warn("Activation retry failed for registration {}",
                                    candidate.registrationId(), error);
                            return Mono.empty();
                        }))
                .then()
                .subscribe(null, error -> log.error("Unable to scan pending registrations", error));
    }
}