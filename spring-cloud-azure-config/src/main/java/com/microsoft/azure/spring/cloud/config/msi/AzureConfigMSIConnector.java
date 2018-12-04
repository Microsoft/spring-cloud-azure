/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.cloud.config.msi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Optional;

import static com.microsoft.azure.spring.cloud.config.msi.ConfigAccessKeyResource.ARM_ENDPONT;

public class AzureConfigMSIConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureConfigMSIConnector.class);
    private static final String CONN_STRING = "Endpoint=https://%s.azconfig.io;Id=%s;Secret=%s";

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
    private final ConfigMSICredentials msiCredentials;
    private final ConfigAccessKeyResource keyResource;

    public AzureConfigMSIConnector(ConfigMSICredentials credentials, ConfigAccessKeyResource keyResource) {
        this.msiCredentials = credentials;
        this.keyResource = keyResource;
    }

    public String getConnection() {
        String msiToken = msiCredentials.getToken(ARM_ENDPONT);
        String resourceId = keyResource.getResourceIdUrl();

        HttpPost post = new HttpPost(resourceId);
        post.setHeader("Authorization", "Bearer " + msiToken);

        String configStoreName = keyResource.getConfigStoreName();

        LOGGER.debug("Acquiring connection string from endpoint {}.", post.getURI());
        try (CloseableHttpResponse response = HTTP_CLIENT.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (!(statusCode >= 200 && statusCode <= 299)) {
                switch (statusCode) {
                    case HttpStatus.SC_NOT_FOUND:
                        throw new IllegalStateException(String.format("The configuration store with name %s " +
                                "and id %s could not be found.", configStoreName, resourceId));
                    case HttpStatus.SC_UNAUTHORIZED:
                    case HttpStatus.SC_FORBIDDEN:
                        throw new IllegalStateException(String.format("No permission to access configuration store %s",
                                configStoreName));
                    default:
                        throw new IllegalStateException(String.format("Failed to retrieve API key and secret " +
                                "for configuration store %s.", configStoreName));
                }
            }

            ConfigAccessKeys result = mapper.readValue(response.getEntity().getContent(), ConfigAccessKeys.class);
            return buildConnectionString(configStoreName, result);
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Failed to retrieve API key and secret " +
                    "for configuration store %s.", configStoreName), e);
        }
    }

    private static String buildConnectionString(String configStoreName, ConfigAccessKeys result) {
        Optional<ConfigAccessKey> keyOptional = result.getAccessKeyList().stream().findFirst();
        Assert.isTrue(keyOptional.isPresent(), String.format("API key should exist for configuration store %s",
                configStoreName));

        ConfigAccessKey key = keyOptional.get();
        validateAccessKey(key, configStoreName);

        return String.format(CONN_STRING, configStoreName, key.getId(), key.getValue());
    }

    private static void validateAccessKey(ConfigAccessKey key, String configStoreName) {
        Assert.hasText(key.getId(), String.format("API key should have non empty id for config store %s.",
                configStoreName));
        Assert.hasText(key.getValue(), String.format("API key should have non empty secret value for config store %s.",
                configStoreName));
    }
}
