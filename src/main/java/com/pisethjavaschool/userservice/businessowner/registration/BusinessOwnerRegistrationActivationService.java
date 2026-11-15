package com.pisethjavaschool.userservice.businessowner.registration;

import com.pisethjavaschool.userservice.authentication.keycloak.KeycloakUserClient;
import com.pisethjavaschool.userservice.user.enums.AccountStatus;
import com.pisethjavaschool.userservice.user.enums.RegistrationStatus;
import com.pisethjavaschool.userservice.user.repository.UserAccountRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Completes a provisioned registration without enabling Keycloak before the
 * durable user-account state is ready. Repeated calls are safe after failures.
 */
@Service
@RequiredArgsConstructor
public class BusinessOwnerRegistrationActivationService {

    private final BusinessOwnerRegistrationStore registrationStore;
    private final UserAccountRepository accountRepository;
    private final KeycloakUserClient keycloakUserClient;

    public Mono<Void> activate(UUID registrationId, UUID platformUserId) {
        return prepare(registrationId, platformUserId)
                .flatMap(ready -> ready ? enableIdentity(platformUserId)
                        .then(Mono.defer(() -> complete(registrationId, platformUserId))) : Mono.empty());
    }

    public Mono<Boolean> prepare(UUID registrationId, UUID platformUserId) {
        return registrationStore.findStatusForUser(registrationId, platformUserId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Registration not found")))
                .flatMap(status -> {
                    if (status == BusinessOwnerRegistrationStatus.COMPLETED) {
                        return Mono.just(false);
                    }
                    if (status != BusinessOwnerRegistrationStatus.ROLE_ASSIGNED
                            && status != BusinessOwnerRegistrationStatus.ACTIVATION_PENDING) {
                        return Mono.error(new IllegalStateException("Owner role has not been assigned"));
                    }
                    return accountRepository.findById(platformUserId)
                            .switchIfEmpty(Mono.error(new IllegalStateException("Registration account not found")))
                            .flatMap(account -> {
                                if (account.getAccountStatus() == AccountStatus.DELETED) {
                                    return Mono.error(new IllegalStateException("Deleted account cannot be activated"));
                                }
                                return Mono.just(account);
                            })
                            .then(Mono.defer(() -> registrationStore.transitionForUser(registrationId, platformUserId,
                                    BusinessOwnerRegistrationStatus.ROLE_ASSIGNED,
                                    BusinessOwnerRegistrationStatus.ACTIVATION_PENDING)))
                            .flatMap(updated -> updated
                                    ? Mono.just(true)
                                    : registrationStore.findStatusForUser(registrationId, platformUserId)
                                            .filter(current -> current == BusinessOwnerRegistrationStatus.ACTIVATION_PENDING)
                                            .hasElement()
                                            .flatMap(pending -> pending ? Mono.just(true)
                                                    : Mono.error(new IllegalStateException(
                                                            "Activation state changed unexpectedly"))));
                });
    }

    private Mono<Void> enableIdentity(UUID platformUserId) {
        return accountRepository.findById(platformUserId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Registration account not found")))
                .flatMap(account -> keycloakUserClient.updateUser(account.getKeycloakUserId(),
                        account.getEmail(), null, null, true));
    }

    public Mono<Void> complete(UUID registrationId, UUID platformUserId) {
        return accountRepository.findById(platformUserId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Registration account not found")))
                .flatMap(account -> {
                    if (account.getAccountStatus() == AccountStatus.DELETED) {
                        return Mono.error(new IllegalStateException("Deleted account cannot be activated"));
                    }
                    account.setRegistrationStatus(RegistrationStatus.COMPLETED);
                    account.setAccountStatus(AccountStatus.ACTIVE);
                    return accountRepository.save(account);
                })
                .then(Mono.defer(() -> registrationStore.transitionForUser(registrationId, platformUserId,
                        BusinessOwnerRegistrationStatus.ACTIVATION_PENDING,
                        BusinessOwnerRegistrationStatus.COMPLETED)))
                .flatMap(updated -> updated ? Mono.<Void>empty()
                        : registrationStore.findStatusForUser(registrationId, platformUserId)
                                .filter(status -> status == BusinessOwnerRegistrationStatus.COMPLETED)
                                .switchIfEmpty(Mono.error(new IllegalStateException("Activation state changed unexpectedly")))
                                .then());
    }
}