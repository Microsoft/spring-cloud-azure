/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.cloud.keyvault;

import org.springframework.core.env.EnumerablePropertySource;

/**
 * Property which has `keyVault:` prefix will consult this class
 *
 * Depend on {@link KeyVaultOperation} to list and get secret as property by always fetching from cache
 *
 * @author Warren Zhu
 */
public class KeyVaultPropertySource extends EnumerablePropertySource<KeyVaultOperation> {

    private static final String KEY_VALUE_PREFIX = "keyVault:";
    private final KeyVaultOperation operation;
    private final String keyVaultName;

    public KeyVaultPropertySource(KeyVaultOperation operation, String keyVaultName) {
        super("KeyVault", operation);
        this.operation = operation;
        this.keyVaultName = keyVaultName;
    }

    public String[] getPropertyNames() {
        return this.operation.listSecrets(this.keyVaultName).toArray(new String[0]);
    }

    public Object getProperty(String name) {
        if (name.startsWith(KEY_VALUE_PREFIX)) {
            return this.operation.getSecret(this.keyVaultName, name.substring(KEY_VALUE_PREFIX.length()));
        }

        return null;
    }
}
