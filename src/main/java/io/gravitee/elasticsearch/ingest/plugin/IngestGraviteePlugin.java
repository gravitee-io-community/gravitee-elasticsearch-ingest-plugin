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

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.plugins.IngestPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.elasticsearch.common.settings.Setting.*;
import static org.elasticsearch.common.settings.Setting.Property.NodeScope;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IngestGraviteePlugin extends Plugin implements IngestPlugin {

    private static final Setting<String> ENDPOINT =
            simpleString("ingest.gravitee.endpoint", "http://localhost:8083/management", NodeScope);
    private static final Setting<String> USERNAME =
            simpleString("ingest.gravitee.username", "admin", NodeScope);
    private static final Setting<String> PASSWORD =
            simpleString("ingest.gravitee.password", "admin", NodeScope);
    private static final Setting<Integer> CACHE_MAX_ELEMENT =
            intSetting("ingest.gravitee.cache.maxElement", 1000, 0, NodeScope);
    private static final Setting<Long> CACHE_TTL =
            longSetting("ingest.gravitee.cache.ttl", 3600, 0, NodeScope);
    private static final Setting<List<String>> HEADERS_KEY = listSetting("ingest.gravitee.headers", emptyList(), s -> s, NodeScope);

    @Override
    public List<Setting<?>> getSettings() {
        final List<Setting<?>> settings = new ArrayList<>();
        settings.add(ENDPOINT);
        settings.add(USERNAME);
        settings.add(PASSWORD);
        settings.add(CACHE_MAX_ELEMENT);
        settings.add(CACHE_TTL);
        settings.add(HEADERS_KEY);
        return settings;
    }

    @Override
    public Map<String, Processor.Factory> getProcessors(Processor.Parameters parameters) {
        final EndpointConfiguration endpointConfiguration =
                new EndpointConfiguration.Builder(ENDPOINT.get(parameters.env.settings()))
                        .username(USERNAME.get(parameters.env.settings()))
                        .password(PASSWORD.get(parameters.env.settings()))
                        .cacheMaxElement(CACHE_MAX_ELEMENT.get(parameters.env.settings()))
                        .cacheTtl(CACHE_TTL.get(parameters.env.settings()))
                        .headers(HEADERS_KEY.get(parameters.env.settings()))
                        .build();
        return singletonMap(EnhanceGraviteeAttributionProcessor.TYPE,
                new EnhanceGraviteeAttributionProcessor.Factory(endpointConfiguration));
    }
}
