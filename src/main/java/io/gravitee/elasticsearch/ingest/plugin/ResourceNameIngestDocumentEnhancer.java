/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.elasticsearch.ingest.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.ingest.IngestDocument;

import java.util.Map;
import java.util.Optional;

import static io.gravitee.elasticsearch.ingest.plugin.EnhanceGraviteeAttributionProcessor.TYPE;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;

/**
 * An {@link IngestDocumentEnhancer} implementation that retrieves enhanced field value from the Gravitee Management API.
 *
 * The original field value must be the ID of a Gravitee resource, reachable via the Gravitee Management API, in the form
 * of an URL built as <graviteeBaseUrl>/<resourcePath>/<resourceId>, where the <resourceId> part is the original field value.
 *
 * @see IngestDocumentEnhancer
 * @see ManagementApiClient
 */
class ResourceNameIngestDocumentEnhancer implements IngestDocumentEnhancer {

    private static final Logger LOGGER = Loggers.getLogger(EnhanceGraviteeAttributionProcessor.class, TYPE);

    private static final String UNKNOWN_ID = "1";
    private static final String UNKNOWN_NAME = "Unknown";
    private static final String DEFAULT_VALUE = "";

    private final String fieldName;
    private final String enhancedFieldName;
    private final String resourceBasePath;
    private final String resourceNameAttribute;

    private final ObjectMapper mapper;
    private final ResponseHandler<String> responseHandler;
    private final Cache<String, String> cache;
    private final ManagementApiClient managementApiClient;

    ResourceNameIngestDocumentEnhancer(
            EndpointConfiguration endpointConfiguration,
            String fieldName,
            String enhancedFieldName,
            String resourceBasePath,
            String resourceNameAttribute) throws Exception {
        this.fieldName = fieldName;
        this.enhancedFieldName = enhancedFieldName;
        this.resourceBasePath = resourceBasePath;
        this.resourceNameAttribute = resourceNameAttribute;

        this.mapper = new ObjectMapper();
        this.responseHandler = buildResponseHandler();
        this.cache = initCache(endpointConfiguration);
        this.managementApiClient = new ManagementApiClient(endpointConfiguration);
    }

    @Override
    public void enhanceDocument(IngestDocument ingestDocument) {
        final String fieldValue = ingestDocument.getFieldValue(fieldName, String.class, true);
        final String enhancedFieldValue = fieldValue != null ? getCachedEnhancedFieldValue(fieldValue) : DEFAULT_VALUE;
        ingestDocument.setFieldValue(enhancedFieldName, enhancedFieldValue);
    }

    private String getCachedEnhancedFieldValue(final String fieldValue) {
        return Optional.ofNullable(cache.get(fieldValue)).orElseGet(() -> initCachedEnhancedFieldValue(fieldValue));
    }

    private String initCachedEnhancedFieldValue(final String fieldValue) {
        String name = getEnhancedFieldValue(fieldValue);
        cache.put(fieldValue, name);
        return name;
    }

    private String getEnhancedFieldValue(final String fieldValue) {
        if (UNKNOWN_ID.equals(fieldValue)) {
            return UNKNOWN_NAME;
        }
        LOGGER.info("Enhancing field '{}' for id '{}'...", enhancedFieldName, fieldValue);
        return managementApiClient.requestForValue(resourceBasePath, fieldValue, responseHandler, DEFAULT_VALUE);
    }

    private Cache<String, String> initCache(EndpointConfiguration endpointConfiguration) {
        final CacheBuilder<String, String> cacheBuilder = CacheBuilder.builder();
        if (endpointConfiguration.getCacheTtl() > 0) {
            cacheBuilder.setExpireAfterWrite(timeValueSeconds(endpointConfiguration.getCacheTtl()));
        }
        if (endpointConfiguration.getCacheMaxElement() > 0) {
            cacheBuilder.setMaximumWeight(endpointConfiguration.getCacheMaxElement());
        }
        return cacheBuilder.build();
    }

    private ResponseHandler<String> buildResponseHandler() {
        return response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                return (String) mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class).get(resourceNameAttribute);
            }
            LOGGER.error("Error while trying to enhance gravitee '{}' attribute: Status[{}] - {}",
                    enhancedFieldName, status, EntityUtils.toString(response.getEntity()));
            return DEFAULT_VALUE;
        };
    }

}
