/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.cloud.config;

import static com.microsoft.azure.spring.cloud.config.Constants.FEATURE_FLAG_CONTENT_TYPE;
import static com.microsoft.azure.spring.cloud.config.Constants.KEY_VAULT_CONTENT_TYPE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.util.ReflectionUtils;

import com.azure.data.appconfiguration.ConfigurationClient;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.azure.data.appconfiguration.models.SettingSelector;
import com.azure.security.keyvault.secrets.models.Secret;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.spring.cloud.config.feature.management.entity.Feature;
import com.microsoft.azure.spring.cloud.config.feature.management.entity.FeatureManagementItem;
import com.microsoft.azure.spring.cloud.config.feature.management.entity.FeatureSet;
import com.microsoft.azure.spring.cloud.config.stores.ClientStore;
import com.microsoft.azure.spring.cloud.config.stores.KeyVaultClient;

public class AzureConfigPropertySource extends EnumerablePropertySource<ConfigurationClient> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureConfigPropertySource.class);

    private final String context;

    private Map<String, Object> properties = new LinkedHashMap<>();

    private final String storeName;

    private final String label;

    private AzureCloudConfigProperties azureProperties;

    private AppConfigProviderProperties appProperties;

    private static ObjectMapper mapper = new ObjectMapper();

    private static final String FEATURE_MANAGEMENT_KEY = "feature-management.featureManagement";

    private static final String FEATURE_FLAG_PREFIX = ".appconfig.featureflag/";

    private HashMap<String, KeyVaultClient> keyVaultClients;

    private ClientStore clients;

    public AzureConfigPropertySource(String context, String storeName,
            String label, AzureCloudConfigProperties azureProperties, AppConfigProviderProperties appProperties,
            ClientStore clients) {
        // The context alone does not uniquely define a PropertySource, append storeName
        // and label to uniquely
        // define a PropertySource
        super(context + storeName + "/" + label);
        this.context = context;
        this.storeName = storeName;
        this.label = label;
        this.azureProperties = azureProperties;
        this.appProperties = appProperties;
        this.keyVaultClients = new HashMap<String, KeyVaultClient>();
        this.clients = clients;
    }

    @Override
    public String[] getPropertyNames() {
        Set<String> keySet = properties.keySet();
        return keySet.toArray(new String[keySet.size()]);
    }

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * <p>
     * Gets settings from Azure/Cache to set as configurations. Updates the cache.
     * </p>
     * 
     * <p>
     * <b>Note</b>: Doesn't update Feature Management, just stores values in cache. Call
     * {@code initFeatures} to update Feature Management, but make sure its done in the
     * last {@code AzureConfigPropertySource}
     * </p>
     * 
     * @param featureSet The set of Feature Management Flags from various config stores.
     * @throws IOException Thrown when processing key/value failed when reading feature
     * flags
     * @return Updated Feature Set from Property Source
     */
    public FeatureSet initProperties(FeatureSet featureSet) throws IOException {
        Date date = new Date();
        SettingSelector settingSelector = new SettingSelector();
        if (!label.equals("%00")) {
            settingSelector.setLabels(label);
        }

        // * for wildcard match
        settingSelector.setKeys(context + "*");
        List<ConfigurationSetting> settings = clients.listSettings(settingSelector, storeName);
        for (ConfigurationSetting setting : settings) {
            String key = setting.getKey().trim().substring(context.length()).replace('/', '.');
            if (setting.getContentType() != null && setting.getContentType().equals(KEY_VAULT_CONTENT_TYPE)) {
                String entry = getKeyVaultEntry(setting.getValue());

                // Null in the case of failFast is false, will just skip entry.
                if (entry != null) {
                    properties.put(key, entry);
                }
            } else {
                properties.put(key, setting.getValue());
            }

        }

        // Reading In Features
        settingSelector.setKeys(".appconfig*");
        settings = clients.listSettings(settingSelector, storeName);

        return addToFeatureSet(featureSet, settings, date);
    }

    /**
     * Given a Setting's Key Vault Reference stored in the Settings value, it will get its
     * entry in Key Vault.
     * 
     * @param value {"uri":
     * "&lt;your-vault-url&gt;/secret/&lt;secret&gt;/&lt;version&gt;"}
     * @return Key Vault Secret Value
     */
    private String getKeyVaultEntry(String value) {
        String secretValue = null;
        try {
            URI uri = null;

            // Parsing Key Vault Reference for URI
            try {
                JsonNode kvReference = mapper.readTree(value);
                uri = new URI(kvReference.at("/uri").asText());
            } catch (URISyntaxException e) {
                if (azureProperties.isFailFast()) {
                    LOGGER.error("Error Processing Key Vault Entry URI.");
                    ReflectionUtils.rethrowRuntimeException(e);
                } else {
                    LOGGER.error("Error Processing Key Vault Entry URI.", e);
                }
            }

            // If no entry found don't connect to Key Vault
            if (uri == null) {
                if (azureProperties.isFailFast()) {
                    ReflectionUtils.rethrowRuntimeException(
                            new IOException("Invaid URI when parsing Key Vault Reference."));
                } else {
                    return null;
                }
            }

            // Check if we already have a client for this key vault, if not we will make
            // one
            if (!keyVaultClients.containsKey(uri.getHost())) {
                KeyVaultClient client = new KeyVaultClient(uri);
                keyVaultClients.put(uri.getHost(), client);
            }
            Duration keyVaultWaitTime = Duration.ofSeconds(appProperties.getKeyVaultWaitTime());
            Secret secret = keyVaultClients.get(uri.getHost()).getSecret(uri, keyVaultWaitTime);
            if (secret == null) {
                throw new IOException("No Key Vault Secret found for Reference.");
            }
            secretValue = secret.getValue();
        } catch (RuntimeException | IOException e) {
            if (azureProperties.isFailFast()) {
                LOGGER.error("Error Retreiving Key Vault Entry", e);
            } else {
                LOGGER.error("Error Retreiving Key Vault Entry");
                ReflectionUtils.rethrowRuntimeException(e);
            }
        }
        return secretValue;
    }

    /**
     * Initializes Feature Management configurations. Only one
     * {@code AzureConfigPropertySource} can call this, and it needs to be done after the
     * rest have run initProperties.
     * @param featureSet Feature Flag info to be set to this property source.
     */
    public void initFeatures(FeatureSet featureSet) {
        properties.put(FEATURE_MANAGEMENT_KEY, mapper.convertValue(featureSet, LinkedHashMap.class));
    }

    /**
     * Adds items to a {@code FeatureSet} from a list of {@code KeyValueItem}.
     * 
     * @param featureSet The parsed KeyValueItems will be added to this
     * @param items New items read in from Azure
     * @param date Cache timestamp
     * @throws IOException
     */
    private FeatureSet addToFeatureSet(FeatureSet featureSet, List<ConfigurationSetting> settings, Date date) 
            throws IOException {
        // Reading In Features
        for (ConfigurationSetting setting : settings) {
            Object feature = createFeature(setting);
            if (feature != null) {
                featureSet.addFeature(setting.getKey().trim().substring(FEATURE_FLAG_PREFIX.length()), feature);
            }
        }
        return featureSet;
    }

    /**
     * Creates a {@code Feature} from a {@code KeyValueItem}
     * 
     * @param item Used to create Features before being converted to be set into
     * properties.
     * @return Feature created from KeyValueItem
     * @throws IOException
     */
    private Object createFeature(ConfigurationSetting item) throws IOException {
        Feature feature = null;
        if (item.getContentType() != null && item.getContentType().equals(FEATURE_FLAG_CONTENT_TYPE)) {
            try {
                String key = item.getKey().trim().substring(FEATURE_FLAG_PREFIX.length());
                FeatureManagementItem featureItem = mapper.readValue(item.getValue(), FeatureManagementItem.class);
                feature = new Feature(key, featureItem);

                // Setting Enabled For to null, but enabled = true will result in the
                // feature being on. This is the case of a feature is on/off and set to
                // on. This is to tell the difference between conditional/off which looks
                // exactly the same... It should never be the case of Conditional On, and
                // no filters coming from Azure, but it is a valid way from the config
                // file, which should result in false being returned.
                if (feature.getEnabledFor().size() == 0 && featureItem.getEnabled() == true) {
                    return true;
                }
                return feature;

            } catch (IOException e) {
                LOGGER.error("Unabled to parse Feature Management values from Azure.", e);
                if (azureProperties.isFailFast()) {
                    throw e;
                }
            }

        } else {
            String message = String.format("Found Feature Flag %s with invalid Content Type of %s", item.getKey(),
                    item.getContentType());

            if (azureProperties.isFailFast()) {
                throw new IOException(message);
            }
            LOGGER.error(message);
        }
        return feature;
    }
}
