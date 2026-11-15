package com.pisethjavaschool.userservice.user.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pisethjavaschool.platform.security.PlatformAuditorContext;
import com.pisethjavaschool.userservice.authentication.keycloak.KeycloakUserClient;
import com.pisethjavaschool.userservice.common.exception.DuplicateUserException;
import com.pisethjavaschool.userservice.common.exception.NotFoundException;
import com.pisethjavaschool.userservice.common.service.PasswordPolicy;
import com.pisethjavaschool.userservice.common.service.UsernameResolver;
import com.pisethjavaschool.userservice.user.dto.CreateUserRequest;
import com.pisethjavaschool.userservice.user.dto.UpdateUserRequest;
import com.pisethjavaschool.userservice.user.dto.UserResponse;
import com.pisethjavaschool.userservice.user.entity.UserAccount;
import com.pisethjavaschool.userservice.user.entity.UserProfile;
import com.pisethjavaschool.userservice.user.enums.AccountStatus;
import com.pisethjavaschool.userservice.user.enums.RegistrationStatus;
import com.pisethjavaschool.userservice.user.mapper.UserMapper;
import com.pisethjavaschool.userservice.user.repository.UserAccountRepository;
import com.pisethjavaschool.userservice.user.repository.UserProfileRepository;
import com.pisethjavaschool.userservice.user.service.UserCommandService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {
    private final UserAccountRepository accountRepository;
    private final UserProfileRepository profileRepository;
    private final KeycloakUserClient keycloakUserClient;
    private final UserMapper mapper;
    private final UsernameResolver usernameResolver;
    private final PasswordPolicy passwordPolicy;

    @Override
    @Transactional
    public Mono<UserResponse> create(CreateUserRequest request) {
        return createUser(request, AccountStatus.ACTIVE, RegistrationStatus.COMPLETED);
    }

    @Override
    @Transactional
    public Mono<UserResponse> createPendingRegistration(CreateUserRequest request) {
        return createUser(request, AccountStatus.INACTIVE, RegistrationStatus.PENDING_VERIFICATION);
    }

    private Mono<UserResponse> createUser(
            CreateUserRequest request,
            AccountStatus accountStatus,
            RegistrationStatus registrationStatus) {
        String username = usernameResolver.resolve(
                request.email(), request.countryCode(), request.phoneNumber());
        Mono<UserResponse> registration = passwordPolicy.validate(request.password())
                .then(validateUniqueUsername(username))
                .then(createIdentity(request, username, accountStatus, registrationStatus))
                .flatMap(account -> createProfile(account, request)
                        .map(profile -> mapper.toResponse(account, profile)));
        return PlatformAuditorContext.withAuditorId(registration, PlatformAuditorContext.REGISTRATION_SYSTEM_AUDITOR);
    }

    @Override
    @Transactional
    public Mono<UserResponse> update(UUID id, UpdateUserRequest request) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("User account not found.")))
                .flatMap(account -> updateAccount(account, request))
                .flatMap(account -> updateProfile(account, request));
    }

    @Override
    @Transactional
    public Mono<Void> delete(UUID id) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("User account not found.")))
                .flatMap(account -> {
                    account.setAccountStatus(AccountStatus.DELETED);
                    return keycloakUserClient.disableUser(account.getKeycloakUserId())
                            .then(accountRepository.save(account))
                            .then();
                });
    }

    @Override
    public Mono<UserResponse> activate(UUID id) {
        return changeStatus(id, AccountStatus.ACTIVE);
    }

    @Override
    public Mono<UserResponse> deactivate(UUID id) {
        return changeStatus(id, AccountStatus.INACTIVE);
    }

    @Override
    public Mono<Void> resetPassword(UUID id, String newPassword) {
        return passwordPolicy.validate(newPassword)
                .then(accountRepository.findById(id))
                .switchIfEmpty(Mono.error(new NotFoundException("User account not found.")))
                .flatMap(account -> keycloakUserClient.resetPassword(account.getKeycloakUserId(), newPassword));
    }

    private Mono<UserAccount> updateAccount(UserAccount account, UpdateUserRequest request) {
        String username = usernameResolver.resolve(request.email(), request.countryCode(), request.phoneNumber());
        mapper.updateAccount(request, username, account);
        boolean enabled = account.getAccountStatus() == AccountStatus.ACTIVE
                && account.getRegistrationStatus() == RegistrationStatus.COMPLETED;
        return keycloakUserClient.updateUser(account.getKeycloakUserId(), account.getEmail(), request.firstName(), request.lastName(), enabled)
                .then(accountRepository.save(account));
    }

    private Mono<UserResponse> updateProfile(UserAccount account, UpdateUserRequest request) {
        return profileRepository.findByUserAccountId(account.getId())
                .switchIfEmpty(Mono.error(new NotFoundException("User profile not found.")))
                .flatMap(profile -> {
                    mapper.updateProfile(request, profile);
                    return profileRepository.save(profile);
                })
                .map(profile -> mapper.toResponse(account, profile));
    }

    private Mono<UserResponse> changeStatus(UUID id, AccountStatus status) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("User account not found.")))
                .flatMap(account -> {
                    if (status == AccountStatus.ACTIVE
                            && account.getRegistrationStatus() != RegistrationStatus.COMPLETED) {
                        return Mono.error(new InvalidRegistrationStateException(
                                account.getRegistrationStatus()));
                    }
                    boolean enabled = status == AccountStatus.ACTIVE;
                    return keycloakUserClient.updateUser(account.getKeycloakUserId(), account.getEmail(), null, null, enabled)
                            .then(Mono.defer(() -> {
                                account.setAccountStatus(status);
                                return accountRepository.save(account);
                            }));
                })
                .flatMap(account -> profileRepository.findByUserAccountId(account.getId())
                        .map(profile -> mapper.toResponse(account, profile)));
    }
    private Mono<UserAccount> createIdentity(
            CreateUserRequest request,
            String username,
            AccountStatus accountStatus,
            RegistrationStatus registrationStatus) {
        boolean enabled = accountStatus == AccountStatus.ACTIVE;
        return keycloakUserClient.createUser(username, request.email(), request.firstName(),
                        request.lastName(), request.password(), enabled)
                .flatMap(keycloakUserId -> {
                    UserAccount account = mapper.toAccount(
                            request, username, keycloakUserId, accountStatus, registrationStatus);
                    return accountRepository.save(account)
                            .flatMap(saved -> keycloakUserClient
                                    .setPlatformUserId(keycloakUserId, saved.getId())
                                    .thenReturn(saved));
                });
    }

    private Mono<UserProfile> createProfile(UserAccount account, CreateUserRequest request) {
        UserProfile profile = mapper.toProfile(request, account.getId());
        return profileRepository.save(profile);
    }

    private Mono<Void> validateUniqueUsername(String username) {
        return accountRepository.existsByUsernameIgnoreCase(username)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new DuplicateUserException("User already exists."));
                    }
                    return Mono.empty();
                });
    }
}