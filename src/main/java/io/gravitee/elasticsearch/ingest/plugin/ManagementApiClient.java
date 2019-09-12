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

import org.apache.http.HttpHeaders;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static io.gravitee.elasticsearch.ingest.plugin.EnhanceGraviteeAttributionProcessor.TYPE;
import static java.lang.String.format;
import static java.util.Base64.getEncoder;

/**
 * An HTTP client responsible for accessing to the Gravitee Management API to retrieve some information from resources.
 * This client can be used by {@link IngestDocumentEnhancer}s. It manages Management API authentication as specified
 * during construction through {@link EndpointConfiguration}. This implementation is thread-safe, thus initialize only
 * one instance per enhancer.
 *
 * @see EndpointConfiguration
 */
public class ManagementApiClient {

    private static final Logger LOGGER = Loggers.getLogger(EnhanceGraviteeAttributionProcessor.class, TYPE);

    private static final String MANAGEMENT_API_ACCEPT_MEDIA_TYPE = "application/json";
    private static final String CUSTOM_HEADER_SEPARATOR = ":";

    private final EndpointConfiguration endpointConfiguration;
    private final CloseableHttpClient httpClient;

    public ManagementApiClient(final EndpointConfiguration endpointConfiguration) throws Exception {
        this.endpointConfiguration = endpointConfiguration;
        this.httpClient = buildHttpClient();
    }

    public <T> T requestForValue(final String resourceBasePath, final String resourceId, final ResponseHandler<? extends T> responseHandler, final T defaultValue) {
        HttpGet apiRequest = prepareApiRequest(resourceBasePath, resourceId);
        return requestForEnhancedFieldValue(apiRequest, responseHandler, defaultValue);
    }

    private HttpGet prepareApiRequest(final String resourceBasePath, final String resourceId) {
        final HttpGet apiRequest = new HttpGet(format("%s%s/%s", endpointConfiguration.getEndpoint(), resourceBasePath, resourceId));
        apiRequest.setHeader(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue());
        apiRequest.setHeader(HttpHeaders.ACCEPT, MANAGEMENT_API_ACCEPT_MEDIA_TYPE);
        endpointConfiguration.getHeaders().stream()
                .filter(header -> header.contains(CUSTOM_HEADER_SEPARATOR))
                .map(header -> header.split(CUSTOM_HEADER_SEPARATOR))
                .filter(headerSplit -> headerSplit.length == 2)
                .forEach(headerSplit -> apiRequest.setHeader(headerSplit[0].trim(), headerSplit[1].trim()));
        return apiRequest;
    }

    private String getAuthorizationHeaderValue() {
        return format("Basic %s", getEncoder().encodeToString(
                format("%s:%s", endpointConfiguration.getUsername(), endpointConfiguration.getPassword()).getBytes()));
    }

    private <T> T requestForEnhancedFieldValue(final HttpGet apiRequest, final ResponseHandler<? extends T> responseHandler, final T defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<T>) () -> {
            try {
                return httpClient.execute(apiRequest, responseHandler);
            } catch (IOException e) {
                LOGGER.error("Error while trying to enhance gravitee attribute", e);
                return defaultValue;
            }
        });
    }

    private CloseableHttpClient buildHttpClient() throws Exception {
        final SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        return HttpClients.custom().setSSLSocketFactory(sslsf).build();
    }

}