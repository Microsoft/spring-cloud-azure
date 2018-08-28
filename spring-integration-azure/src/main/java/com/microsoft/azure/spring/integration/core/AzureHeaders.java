/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.integration.core;

/**
 * Azure internal headers for Spring Messaging messages.
 *
 * @author Warren Zhu
 */
public class AzureHeaders {
    private static final String PREFIX = "azure_";

    public static final String PARTITION_ID = PREFIX + "partition_id";

    public static final String PARTITION_KEY = PREFIX + "partition_key";

    public static final String NAME = PREFIX + "name";

    /**
     * The {@value CHECKPOINTER} header for checkpoint the specific message.
     */
    public static final String CHECKPOINTER = PREFIX + "checkpointer";
}
