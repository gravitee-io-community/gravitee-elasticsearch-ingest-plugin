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

import java.util.List;
import java.util.Map;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EndpointConfiguration {

    private String endpoint, username, password;
    private int cacheMaxElement;
    private long cacheTtl;
    private List<String> headers;

    private EndpointConfiguration() {
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getCacheMaxElement() {
        return cacheMaxElement;
    }

    public void setCacheMaxElement(int cacheMaxElement) {
        this.cacheMaxElement = cacheMaxElement;
    }

    public long getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public static class Builder {
        private String endpoint, username, password;
        private int cacheMaxElement;
        private long cacheTtl;
        private List<String> headers;

        Builder(String endpoint) {
            this.endpoint = endpoint;
        }

        public EndpointConfiguration.Builder username(String username) {
            this.username = username;
            return this;
        }

        public EndpointConfiguration.Builder password(String password) {
            this.password = password;
            return this;
        }

        public EndpointConfiguration.Builder cacheMaxElement(int cacheMaxElement) {
            this.cacheMaxElement = cacheMaxElement;
            return this;
        }

        public EndpointConfiguration.Builder cacheTtl(long cacheTtl) {
            this.cacheTtl = cacheTtl;
            return this;
        }

        public EndpointConfiguration.Builder headers(List<String> headers) {
            this.headers = headers;
            return this;
        }

        public EndpointConfiguration build() {
            final EndpointConfiguration endpointConfiguration = new EndpointConfiguration();
            endpointConfiguration.setEndpoint(endpoint);
            endpointConfiguration.setUsername(username);
            endpointConfiguration.setPassword(password);
            endpointConfiguration.setCacheMaxElement(cacheMaxElement);
            endpointConfiguration.setCacheTtl(cacheTtl);
            endpointConfiguration.setHeaders(headers);
            return endpointConfiguration;
        }
    }
}
