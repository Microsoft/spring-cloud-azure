/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.cloud.autoconfigure.eventhub;

import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.management.eventhub.AuthorizationRule;
import com.microsoft.azure.management.eventhub.EventHubAuthorizationKey;
import com.microsoft.azure.management.eventhub.EventHubNamespace;
import com.microsoft.azure.spring.cloud.autoconfigure.context.AzureContextAutoConfiguration;
import com.microsoft.azure.spring.cloud.autoconfigure.telemetry.TelemetryAutoConfiguration;
import com.microsoft.azure.spring.cloud.autoconfigure.telemetry.TelemetryCollector;
import com.microsoft.azure.spring.cloud.context.core.AzureAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

import javax.annotation.PostConstruct;
import java.util.Arrays;

/**
 * An auto-configuration for Event Hub, which provides {@link KafkaProperties}
 *
 * @author Warren Zhu
 */
@Configuration
@AutoConfigureBefore({KafkaAutoConfiguration.class, TelemetryAutoConfiguration.class})
@AutoConfigureAfter(AzureContextAutoConfiguration.class)
@ConditionalOnClass({EventHubClient.class, KafkaTemplate.class})
@ConditionalOnProperty(value = "spring.cloud.azure.eventhub.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AzureEventHubProperties.class)
public class AzureEventHubKafkaAutoConfiguration {
    private static final String SECURITY_PROTOCOL = "security.protocol";
    private static final String SASL_SSL = "SASL_SSL";
    private static final String SASL_JAAS_CONFIG = "sasl.jaas.config";
    private static final String SASL_CONFIG_VALUE =
            "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$ConnectionString\" " +
                    "password=\"%s\";\n";
    private static final String SASL_MECHANISM = "sasl.mechanism";
    private static final String SASL_MECHANISM_PLAIN = "PLAIN";
    private static final int PORT = 9093;
    private static final String EVENT_HUB_KAFKA = "EventHubKafka";

    @PostConstruct
    public void collectTelemetry() {
        TelemetryCollector.getInstance().addService(EVENT_HUB_KAFKA);
    }

    @ConditionalOnMissingBean
    @Primary
    @Bean
    public KafkaProperties kafkaProperties(AzureAdmin azureAdmin, AzureEventHubProperties eventHubProperties) {
        KafkaProperties kafkaProperties = new KafkaProperties();
        EventHubNamespace namespace = azureAdmin.getOrCreateEventHubNamespace(eventHubProperties.getNamespace());
        String connectionString =
                namespace.listAuthorizationRules().stream().findFirst().map(AuthorizationRule::getKeys)
                         .map(EventHubAuthorizationKey::primaryConnectionString).orElseThrow(() -> new RuntimeException(
                        String.format("Failed to fetch connection string of namespace '%s'", namespace), null));
        String endpoint = namespace.serviceBusEndpoint();
        String endpointHost = endpoint.substring("https://".length(), endpoint.lastIndexOf(':'));
        kafkaProperties.setBootstrapServers(Arrays.asList(endpointHost + ":" + PORT));
        kafkaProperties.getProperties().put(SECURITY_PROTOCOL, SASL_SSL);
        kafkaProperties.getProperties().put(SASL_MECHANISM, SASL_MECHANISM_PLAIN);
        kafkaProperties.getProperties().put(SASL_JAAS_CONFIG, String.format(SASL_CONFIG_VALUE, connectionString));

        return kafkaProperties;
    }
}
