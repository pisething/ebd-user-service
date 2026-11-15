package com.pisethjavaschool.userservice.businessowner.registration;

import java.util.UUID;
import reactor.core.publisher.Flux;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class BusinessOwnerRegistrationStore {

    private final DatabaseClient databaseClient;

    public Mono<UUID> createPendingIfAbsent(UUID platformUserId) {
        return databaseClient.sql("""
                        INSERT INTO business_owner_registration (platform_user_id, status)
                        VALUES (:platformUserId, :status)
                        ON CONFLICT (platform_user_id) DO NOTHING
                        RETURNING id
                        """)
                .bind("platformUserId", platformUserId)
                .bind("status", BusinessOwnerRegistrationStatus.PENDING.name())
                .map((row, metadata) -> row.get("id", UUID.class))
                .one();
    }

    public Mono<BusinessOwnerRegistrationStatus> findStatus(UUID registrationId) {
        return databaseClient.sql("""
                        SELECT status
                        FROM business_owner_registration
                        WHERE id = :registrationId
                        """)
                .bind("registrationId", registrationId)
                .map((row, metadata) -> BusinessOwnerRegistrationStatus.valueOf(
                        row.get("status", String.class)))
                .one();
    }

    public Mono<UUID> findRegistrationIdByPlatformUserId(UUID platformUserId) {
        return databaseClient.sql("""
                        SELECT id
                        FROM business_owner_registration
                        WHERE platform_user_id = :platformUserId
                        """)
                .bind("platformUserId", platformUserId)
                .map((row, metadata) -> row.get("id", UUID.class))
                .one();
    }

    public Mono<BusinessOwnerRegistrationStatus> findStatusForUser(
            UUID registrationId, UUID platformUserId) {
        return databaseClient.sql("""
                        SELECT status
                        FROM business_owner_registration
                        WHERE id = :registrationId AND platform_user_id = :platformUserId
                        """)
                .bind("registrationId", registrationId)
                .bind("platformUserId", platformUserId)
                .map((row, metadata) -> BusinessOwnerRegistrationStatus.valueOf(
                        row.get("status", String.class)))
                .one();
    }

    public Flux<RegistrationActivationCandidate> findActivationPending(int limit) {
        return databaseClient.sql("""
                        SELECT id, platform_user_id
                        FROM business_owner_registration
                        WHERE status = :status
                        ORDER BY updated_at, id
                        LIMIT :limit
                        """)
                .bind("status", BusinessOwnerRegistrationStatus.ACTIVATION_PENDING.name())
                .bind("limit", limit)
                .map((row, metadata) -> new RegistrationActivationCandidate(
                        row.get("id", UUID.class), row.get("platform_user_id", UUID.class)))
                .all();
    }

    public Mono<Boolean> transitionForUser(
            UUID registrationId,
            UUID platformUserId,
            BusinessOwnerRegistrationStatus expected,
            BusinessOwnerRegistrationStatus next) {
        return databaseClient.sql("""
                        UPDATE business_owner_registration
                        SET status = :next, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :registrationId
                          AND platform_user_id = :platformUserId
                          AND status = :expected
                        """)
                .bind("registrationId", registrationId)
                .bind("platformUserId", platformUserId)
                .bind("expected", expected.name())
                .bind("next", next.name())
                .fetch()
                .rowsUpdated()
                .map(updated -> updated == 1);
    }

    public Mono<Boolean> transition(
            UUID registrationId,
            BusinessOwnerRegistrationStatus expected,
            BusinessOwnerRegistrationStatus next
    ) {
        return databaseClient.sql("""
                        UPDATE business_owner_registration
                        SET status = :next, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :registrationId AND status = :expected
                        """)
                .bind("registrationId", registrationId)
                .bind("expected", expected.name())
                .bind("next", next.name())
                .fetch()
                .rowsUpdated()
                .map(updated -> updated == 1);
    }
}