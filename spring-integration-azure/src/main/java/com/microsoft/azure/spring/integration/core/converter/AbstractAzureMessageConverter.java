/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.integration.core.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.io.IOException;
import java.util.Arrays;

/**
 * Abstract class handles common conversion logic between <T> and {@link Message}
 *
 * @author Warren Zhu
 */
public abstract class AbstractAzureMessageConverter<T> implements AzureMessageConverter<T> {
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public T fromMessage(Message<?> message, Class<T> targetClass) {
        T azureMessage = internalFromMessage(message, targetClass);

        setCustomHeaders(message, azureMessage);

        return azureMessage;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> Message<U> toMessage(T azureMessage, MessageHeaders headers, Class<U> targetPayloadClass) {
        return (Message<U>) internalToMessage(azureMessage, headers, targetPayloadClass);
    }

    protected abstract byte[] getPayload(T azureMessage);

    protected abstract T fromString(String payload);

    protected abstract T fromByte(byte[] payload);

    protected void setCustomHeaders(Message<?> message, T azureMessage) {
    }

    private T internalFromMessage(Message<?> message, Class<T> targetClass) {
        Object payload = message.getPayload();

        if (targetClass.isInstance(payload)) {
            return targetClass.cast(payload);
        }

        if (payload instanceof String) {
            return fromString((String) payload);
        }

        if (payload instanceof byte[]) {
            return fromByte((byte[]) payload);
        }

        return fromByte(toPayload(payload));
    }

    private <U> Message<?> internalToMessage(T azureMessage, MessageHeaders headers, Class<U> targetPayloadClass) {
        byte[] payload = getPayload(azureMessage);

        if (targetPayloadClass.isInstance(azureMessage)) {
            return MessageBuilder.withPayload(azureMessage).copyHeaders(headers).build();
        }

        if (targetPayloadClass == String.class) {
            return MessageBuilder.withPayload(new String(payload)).copyHeaders(headers).build();
        }

        if (targetPayloadClass == byte[].class) {
            return MessageBuilder.withPayload(payload).copyHeaders(headers).build();
        }

        return MessageBuilder.withPayload(fromPayload(payload, targetPayloadClass)).copyHeaders(headers).build();
    }

    private static byte[] toPayload(Object object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new ConversionException("Failed to write JSON: " + object, e);
        }
    }

    private static <U> U fromPayload(byte[] payload, Class<U> payloadType) {
        try {
            return objectMapper.readerFor(payloadType).readValue(payload);
        } catch (IOException e) {
            throw new ConversionException("Failed to read JSON: " + Arrays.toString(payload), e);
        }
    }
}
