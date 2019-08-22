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
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.io.IOException;
import java.security.*;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Base64.getEncoder;
import static org.elasticsearch.common.Strings.isEmpty;
import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;
import static org.elasticsearch.ingest.ConfigurationUtils.readStringProperty;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnhanceGraviteeAttributionProcessor extends AbstractProcessor {

    static final String TYPE = "gravitee-elasticsearch-ingest-plugin";
    private static final Logger LOGGER = Loggers.getLogger(EnhanceGraviteeAttributionProcessor.class, TYPE);

    private static final String UNKNOWN_APPLICATION = "1";
    private static final String UNKNOWN_APPLICATION_NAME = "Unknown";

    private final String apiField, applicationField;
    private final EndpointConfiguration endpointConfiguration;
    private final ObjectMapper mapper;
    private final CloseableHttpClient httpClient;
    private final Cache<String, String> cache;

    EnhanceGraviteeAttributionProcessor(String tag, EndpointConfiguration endpointConfiguration, String apiField, String applicationField) throws Exception {
        super(tag);
        this.endpointConfiguration = endpointConfiguration;
        this.apiField = apiField;
        this.applicationField = applicationField;
        final CacheBuilder<String, String> cacheBuilder = CacheBuilder.builder();
        if (endpointConfiguration.getCacheTtl() > 0) {
            cacheBuilder.setExpireAfterWrite(timeValueSeconds(endpointConfiguration.getCacheTtl()));
        }
        if (endpointConfiguration.getCacheMaxElement() > 0) {
            cacheBuilder.setMaximumWeight(endpointConfiguration.getCacheMaxElement());
        }
        this.cache = cacheBuilder.build();
        this.mapper = new ObjectMapper();

        final SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        this.httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) {
        if (apiField != null) {
            final String api = ingestDocument.getFieldValue(apiField, String.class, true);
            if (api != null) {
                enhanceDocument(ingestDocument, "/apis/", api, "name", "api-name");
            }
        }
        if (applicationField != null) {
            final String application = ingestDocument.getFieldValue(applicationField, String.class, true);
            if (application != null) {
                enhanceDocument(ingestDocument, "/applications/", application, "name", "application-name");
            }
        }
        return ingestDocument;
    }

    private void enhanceDocument(final IngestDocument ingestDocument, final String path, final String id,
                                 final String attribute, final String enhancedFieldName) {
        String name = cache.get(id);
        if (name == null) {
            if (UNKNOWN_APPLICATION.equals(id)) {
                name = UNKNOWN_APPLICATION_NAME;
            } else {
                final HttpGet apiRequest = new HttpGet(endpointConfiguration.getEndpoint() + path + id);
                apiRequest.setHeader(HttpHeaders.AUTHORIZATION, "Basic " +
                        getEncoder().encodeToString((endpointConfiguration.getUsername() + ':' + endpointConfiguration.getPassword()).getBytes()));
                apiRequest.setHeader(HttpHeaders.ACCEPT, "application/json");
                if (endpointConfiguration.getHeaders() != null && !endpointConfiguration.getHeaders().isEmpty()) {
                    endpointConfiguration.getHeaders().stream()
                            .filter((header) -> header.contains(":"))
                            .forEach((header) -> {
                                final String[] splitHeader = header.split(":");
                                apiRequest.setHeader(splitHeader[0].trim(), splitHeader[1].trim());
                            });
                }
                name = AccessController.doPrivileged((PrivilegedAction<String>) () -> {
                    try {
                        LOGGER.info("Enhancing field '{}' for id '{}'...", enhancedFieldName, id);
                        return httpClient.execute(apiRequest, response -> {
                            int status = response.getStatusLine().getStatusCode();
                            if (status == 200) {
                                return (String) mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class).get(attribute);
                            } else {
                                LOGGER.error(format("Error while trying to enhance gravitee attribute: Status[%s] - %s",
                                        status, EntityUtils.toString(response.getEntity())));
                                return "";
                            }
                        });
                    } catch (IOException e) {
                        LOGGER.error("Error while trying to enhance gravitee attribute", e);
                        return "";
                    }
                });
            }
            cache.put(id, name);
        }
        ingestDocument.setFieldValue(enhancedFieldName, name);
    }

    public static final class Factory implements Processor.Factory {

        private final EndpointConfiguration endpointConfiguration;

        Factory(EndpointConfiguration endpointConfiguration) {
            this.endpointConfiguration = endpointConfiguration;
        }

        @Override
        public EnhanceGraviteeAttributionProcessor create(Map<String, Processor.Factory> factories, String tag, Map<String, Object> config) throws Exception {
            return new EnhanceGraviteeAttributionProcessor(tag, endpointConfiguration,
                    readStringProperty(TYPE, tag, config, "apiField"),
                    readStringProperty(TYPE, tag, config, "applicationField")
            );
        }
    }
}
