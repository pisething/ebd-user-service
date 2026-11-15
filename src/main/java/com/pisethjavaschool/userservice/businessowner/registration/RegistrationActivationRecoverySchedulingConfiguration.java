package com.pisethjavaschool.userservice.businessowner.registration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "registration.activation.recovery", name = "enabled", havingValue = "true")
public class RegistrationActivationRecoverySchedulingConfiguration {
}