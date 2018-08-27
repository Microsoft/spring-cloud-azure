/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.servicebus.stream.binder;

import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.stream.binder.properties.ServiceBusConsumerProperties;
import com.microsoft.azure.servicebus.stream.binder.properties.ServiceBusProducerProperties;
import org.springframework.cloud.stream.binder.*;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author Warren Zhu
 */

public class ServiceBusTopicTestBinder extends
        AbstractTestBinder<ServiceBusTopicMessageChannelBinder,
                ExtendedConsumerProperties<ServiceBusConsumerProperties>,
                ExtendedProducerProperties<ServiceBusProducerProperties>> {

    public ServiceBusTopicTestBinder() {
        ServiceBusTopicMessageChannelBinder binder = new ServiceBusTopicMessageChannelBinder(BinderHeaders
                .STANDARD_HEADERS,
                new ServiceBusTopicTestChannelProvisioner(null, "namespace"), new ServiceBusTopicTestOperation
                ());
        GenericApplicationContext context = new GenericApplicationContext();
        binder.setApplicationContext(context);
        this.setBinder(binder);
    }

    @Override
    public void cleanup() {
        // No-op
    }

}
