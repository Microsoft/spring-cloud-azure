/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.eventhub.stream.binder;

import com.microsoft.azure.eventhub.stream.binder.properties.EventHubConsumerProperties;
import com.microsoft.azure.eventhub.stream.binder.properties.EventHubExtendedBindingProperties;
import com.microsoft.azure.eventhub.stream.binder.properties.EventHubProducerProperties;
import com.microsoft.azure.eventhub.stream.binder.provisioning.EventHubChannelProvisioner;
import com.microsoft.azure.spring.integration.core.AzureMessageHandler;
import com.microsoft.azure.spring.integration.core.StartPosition;
import com.microsoft.azure.spring.integration.eventhub.EventHubOperation;
import com.microsoft.azure.spring.integration.eventhub.inbound.CheckpointMode;
import com.microsoft.azure.spring.integration.eventhub.inbound.EventHubInboundChannelAdapter;
import com.microsoft.azure.spring.integration.eventhub.inbound.ListenerMode;
import org.springframework.cloud.stream.binder.*;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.expression.FunctionExpression;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * @author Warren Zhu
 */
public class EventHubMessageChannelBinder extends
        AbstractMessageChannelBinder<ExtendedConsumerProperties<EventHubConsumerProperties>,
                ExtendedProducerProperties<EventHubProducerProperties>, EventHubChannelProvisioner>
        implements ExtendedPropertiesBinder<MessageChannel, EventHubConsumerProperties, EventHubProducerProperties> {

    private final EventHubOperation eventHubOperation;

    private final EventHubExtendedBindingProperties bindingProperties = new EventHubExtendedBindingProperties();

    public EventHubMessageChannelBinder(String[] headersToEmbed, EventHubChannelProvisioner provisioningProvider,
            EventHubOperation eventHubOperation) {
        super(headersToEmbed, provisioningProvider);
        this.eventHubOperation = eventHubOperation;
    }

    @Override
    protected MessageHandler createProducerMessageHandler(ProducerDestination destination,
            ExtendedProducerProperties<EventHubProducerProperties> producerProperties, MessageChannel errorChannel) {
        AzureMessageHandler handler = new AzureMessageHandler(destination.getName(), this.eventHubOperation);
        handler.setBeanFactory(getBeanFactory());
        handler.setSync(producerProperties.getExtension().isSync());
        handler.setSendTimeout(producerProperties.getExtension().getSendTimeout());
        if (producerProperties.isPartitioned()) {
            handler.setPartitionKeyExpressionString(
                    "'partitionKey-' + headers['" + BinderHeaders.PARTITION_HEADER + "']");
        } else {
            handler.setPartitionKeyExpression(new FunctionExpression<Message<?>>(m -> m.getPayload().hashCode()));
        }

        return handler;
    }

    @Override
    protected MessageProducer createConsumerEndpoint(ConsumerDestination destination, String group,
            ExtendedConsumerProperties<EventHubConsumerProperties> properties) {
        this.eventHubOperation.setStartPosition(properties.getExtension().getStartPosition());

        boolean anonymous = !StringUtils.hasText(group);
        if (anonymous) {
            group = "anonymous." + UUID.randomUUID().toString();
            this.eventHubOperation.setStartPosition(StartPosition.LATEST);
        }
        EventHubInboundChannelAdapter inboundAdapter =
                new EventHubInboundChannelAdapter(destination.getName(), this.eventHubOperation, group);
        inboundAdapter.setBeanFactory(getBeanFactory());
        return inboundAdapter;
    }

    @Override
    public EventHubConsumerProperties getExtendedConsumerProperties(String channelName) {
        return this.bindingProperties.getExtendedConsumerProperties(channelName);
    }

    @Override
    public EventHubProducerProperties getExtendedProducerProperties(String channelName) {
        return this.bindingProperties.getExtendedProducerProperties(channelName);
    }

}
