/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.integration;

import com.microsoft.azure.spring.integration.core.AbstractInboundChannelAdapter;
import com.microsoft.azure.spring.integration.core.Checkpointer;
import com.microsoft.azure.spring.integration.eventhub.inbound.CheckpointMode;
import org.junit.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.util.Assert;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public abstract class InboundChannelAdapterTest<D, K, A extends AbstractInboundChannelAdapter<D, K>> {

    protected final String[] payloads = {"payload1", "payload2"};
    protected final String destination = "ServiceBusQueue";
    protected final String consumerGroup = "group";
    protected Class<K> clazz;

    protected Checkpointer<K> checkpointer;

    protected List<D> messages = new LinkedList<>();

    protected A adapter;

    @Test
    public void testCheckpointRecordMode() throws InterruptedException {
        sendAndReceive(CheckpointMode.RECORD);
        verify(this.checkpointer, times(this.messages.size())).checkpoint(isA(clazz));
    }

    private void sendAndReceive(CheckpointMode checkpointMode) throws InterruptedException {
        DirectChannel channel = new DirectChannel();
        channel.setBeanName("output");
        this.adapter.setCheckpointMode(checkpointMode);
        this.adapter.doStart();
        this.adapter.setOutputChannel(channel);

        final CountDownLatch latch = new CountDownLatch(this.messages.size());
        final List<String> receivedMessages = new CopyOnWriteArrayList<>();
        channel.subscribe(message -> {
            try {
                receivedMessages.add(new String((byte[]) message.getPayload()));
            } finally {
                latch.countDown();
            }

        });

        this.messages.stream().forEach(this.adapter::receiveMessage);
        Assert.isTrue(latch.await(5L, TimeUnit.SECONDS), "Failed to receive message");

        for (int i = 0; i < receivedMessages.size(); i++) {
            assertEquals(receivedMessages.get(i), payloads[i]);
        }
    }
}

