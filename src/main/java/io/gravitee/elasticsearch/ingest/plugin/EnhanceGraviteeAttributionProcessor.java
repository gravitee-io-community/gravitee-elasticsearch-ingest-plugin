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

import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;

import java.util.*;

import static org.elasticsearch.ingest.ConfigurationUtils.readStringProperty;

/**
 * A {@link org.elasticsearch.ingest.Processor} implementation that uses a list of {@link IngestDocumentEnhancer}s on
 * each {@link #execute(IngestDocument) execution}. Order of the {@link IngestDocumentEnhancer}s may be important, as
 * some enhancers may depend on the result of the previous ones.
 *
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 * @see IngestDocumentEnhancer
 */
public class EnhanceGraviteeAttributionProcessor extends AbstractProcessor {

    public static final String TYPE = "gravitee-elasticsearch-ingest-plugin";

    private final Collection<IngestDocumentEnhancer> documentEnhancers;

    EnhanceGraviteeAttributionProcessor(String tag, Collection<IngestDocumentEnhancer> documentEnhancers) {
        super(tag);
        if (documentEnhancers == null || documentEnhancers.isEmpty()) {
            throw new IllegalStateException("Cannot initialize processor without document enhancer");
        }
        this.documentEnhancers = Collections.unmodifiableCollection(documentEnhancers);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public IngestDocument execute(IngestDocument ingestDocument) {
        this.documentEnhancers.forEach(documentEnhancer -> documentEnhancer.enhanceDocument(ingestDocument));
        return ingestDocument;
    }

    /**
     * A {@link EnhanceGraviteeAttributionProcessor} factory. By default, it initializes 2 {@link IngestDocumentEnhancer}s
     * to add API and application names in document.
     *
     * This factory is extensible to initialize more {@link IngestDocumentEnhancer}s than those defined by default, or
     * to completely replace the default ones, using {@link #initializeDocumentEnhancers(String, Map)}.
     */
    public static class Factory implements Processor.Factory {

        protected static final String PIPELINE_API_FIELD = "apiField";
        protected static final String PIPELINE_APPLICATION_FIELD = "applicationField";

        private final EndpointConfiguration endpointConfiguration;
        private final boolean reAddPropertyToConfigAfterInit;

        protected Factory(EndpointConfiguration endpointConfiguration) {
            this(endpointConfiguration, false);
        }

        /**
         * Initializes a factory.
         * @param endpointConfiguration the Management API endpoint configuration.
         * @param reAddPropertyToConfigAfterInit indicates whether the properties read from the configuration through
         *                                       {@link #create(Map, String, Map)} must re-add those properties. This
         *                                       allows subclasses to reuse the same properties for their own
         *                                       initialization.
         */
        protected Factory(EndpointConfiguration endpointConfiguration, boolean reAddPropertyToConfigAfterInit) {
            this.endpointConfiguration = endpointConfiguration;
            this.reAddPropertyToConfigAfterInit = reAddPropertyToConfigAfterInit;
        }

        @Override
        public final EnhanceGraviteeAttributionProcessor create(Map<String, Processor.Factory> factories, String tag, Map<String, Object> config) throws Exception {
            return new EnhanceGraviteeAttributionProcessor(tag, initializeDocumentEnhancers(tag, config));
        }

        /**
         * Initializes the {@link IngestDocumentEnhancer}s used by the {@link EnhanceGraviteeAttributionProcessor} during
         * Ingest document enhancement. If the {@link #reAddPropertyToConfigAfterInit} attribute has been set to
         * {@code true} (default to {@code false}), properties read from configuration will be re-added to configuration.
         * @param tag the processor tag.
         * @param config the configuration.
         * @return the list of enhancers to be used by processor.
         * @throws Exception in case of initialization error.
         */
        protected List<IngestDocumentEnhancer> initializeDocumentEnhancers(String tag, Map<String, Object> config) throws Exception {
            List<IngestDocumentEnhancer> enhancers = new ArrayList<>();

            final String apiField = readStringProperty(TYPE, tag, config, PIPELINE_API_FIELD);
            enhancers.add(new ResourceNameIngestDocumentEnhancer(endpointConfiguration, apiField, "api-name", "/apis", "name"));
            if (reAddPropertyToConfigAfterInit) {
                config.put(PIPELINE_API_FIELD, apiField);
            }

            final String applicationField = readStringProperty(TYPE, tag, config, PIPELINE_APPLICATION_FIELD);
            enhancers.add(new ResourceNameIngestDocumentEnhancer(endpointConfiguration, applicationField, "application-name", "/applications", "name"));
            if (reAddPropertyToConfigAfterInit) {
                config.put(PIPELINE_APPLICATION_FIELD, applicationField);
            }
            return enhancers;
        }
    }

}
