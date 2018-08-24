/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.integration.core;

import java.util.concurrent.CompletableFuture;

/**
 * A callback to perform checkpoint.
 *
 * @param <T> checkpoint key type parameter
 * @author Warren Zhu
 */
public interface Checkpointer<T> {

    /**
     * Checkpoint to last record processed. Please check result to detect failure
     */
    CompletableFuture<Void> checkpoint();

    /**
     * Checkpoint based on provided message key. Please check result to detect failure
     */
    CompletableFuture<Void> checkpoint(T t);
}
