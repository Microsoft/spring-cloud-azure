/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.cloud.keyvault.config;

import com.microsoft.azure.spring.cloud.keyvault.config.auth.Credentials;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

/**
 * Configuration properties for the Azure Key Vault integration with Spring Cloud Config.
 */
@Getter
@Setter
@ConfigurationProperties(KeyVaultConfigProperties.CONFIG_PREFIX)
@Validated
public class KeyVaultConfigProperties {
    public static final String CONFIG_PREFIX = "spring.cloud.azure.keyvault.config";
    public static final String ENABLED = CONFIG_PREFIX + ".enabled";

    @NestedConfigurationProperty
    @NotNull
    private Credentials credentials;

    private boolean enabled = true;

    private boolean failFast = true;

    private String name;

    private String activeProfile;
}
