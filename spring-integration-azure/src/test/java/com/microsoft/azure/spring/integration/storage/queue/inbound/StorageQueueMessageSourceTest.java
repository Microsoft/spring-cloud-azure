/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.integration.storage.queue.inbound;

import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.spring.integration.storage.queue.StorageQueueOperation;
import com.microsoft.azure.spring.integration.storage.queue.StorageQueueRuntimeException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class StorageQueueMessageSourceTest {

    @Mock
    private StorageQueueOperation mockOperation;
    private Message<?> message =
            new GenericMessage<>("testPayload", ImmutableMap.of("key1", "value1", "key2", "value2"));

    private String destination = "test-destination";
    private StorageQueueMessageSource messageSource;
    private CompletableFuture<Message<?>> future = new CompletableFuture<>();

    @Before
    public void setup() {
        messageSource = new StorageQueueMessageSource(destination, mockOperation);
    }

    @Test
    public void testDoReceiveWhenHaveNoMessage() {
        future.complete(null);
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        assertNull(messageSource.doReceive());
    }

    @Test(expected = StorageQueueRuntimeException.class)
    public void testReceiveFailure() {
        future.completeExceptionally(new StorageQueueRuntimeException("Failed to receive message."));
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        messageSource.doReceive();
    }

    @Test
    public void testDoReceiveSuccess() {
        future.complete(message);
        when(this.mockOperation.receiveAsync(eq(destination)))
                .thenReturn(future);
        Message<?> receivedMessage =  (Message<?>) messageSource.doReceive();
        assertEquals(message, receivedMessage);
    }
}
