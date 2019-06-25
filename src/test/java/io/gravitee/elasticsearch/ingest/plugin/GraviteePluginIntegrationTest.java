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

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.RandomDocumentPicks;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.test.ESIntegTestCase;
import org.junit.Before;
import org.junit.ClassRule;

import java.util.*;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomAsciiAlphanumOfLength;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.singleton;
import static org.hamcrest.Matchers.*;

/**
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteePluginIntegrationTest extends ESIntegTestCase {

    @ClassRule
    public static final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return singleton(IngestGraviteePlugin.class);
    }

    @Before
    public void init() {
        resetAllRequests();
    }

    public void testPluginIsLoaded() {
        NodesInfoResponse response = client().admin().cluster().prepareNodesInfo().setPlugins(true).get();
        for (NodeInfo nodeInfo : response.getNodes()) {
            boolean pluginFound = false;
            for (PluginInfo pluginInfo : nodeInfo.getPlugins().getPluginInfos()) {
                if (pluginInfo.getName().equals(IngestGraviteePlugin.class.getName())) {
                    pluginFound = true;
                    break;
                }
            }
            assertThat(pluginFound, is(true));
        }
    }

    public void testThatProcessorNotWorks() throws Exception {
        stubFor(WireMock.get(urlEqualTo("/apis/123"))
                .willReturn(aResponse()
                        .withStatus(404)));

        final Map<String, Object> document = new HashMap<>();
        document.put("api", "123");
        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);
        EnhanceGraviteeAttributionProcessor processor = new EnhanceGraviteeAttributionProcessor(randomAsciiAlphanumOfLength(10),
                new EndpointConfiguration.Builder(wireMockRule.baseUrl()).build(), "api", "application");
        processor.execute(ingestDocument);
        Map<String, Object> data = ingestDocument.getSourceAndMetadata();
        assertThat(data, hasKey("api-name"));
        assertThat(data.get("api-name"), nullValue());
    }

    public void testThatProcessorWorks() throws Exception {
        stubFor(WireMock.get(urlEqualTo("/apis/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"name\": \"My API name\"}")));
        stubFor(WireMock.get(urlEqualTo("/applications/321"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"name\": \"My app name\"}")));

        final Map<String, Object> document = new HashMap<>();
        document.put("api", "123");
        document.put("application", "321");
        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);
        EnhanceGraviteeAttributionProcessor processor = new EnhanceGraviteeAttributionProcessor(randomAsciiAlphanumOfLength(10),
                new EndpointConfiguration.Builder(wireMockRule.baseUrl()).build(), "api", "application");
        processor.execute(ingestDocument);
        Map<String, Object> data = ingestDocument.getSourceAndMetadata();
        assertThat(data, hasKey("api-name"));
        assertThat(data.get("api-name"), is("My API name"));

        assertThat(data, hasKey("application-name"));
        assertThat(data.get("application-name"), is("My app name"));

        verify(1, getRequestedFor(urlEqualTo("/apis/123")));
        verify(1, getRequestedFor(urlEqualTo("/applications/321")));
    }

    public void testThatProcessorCachingWorks() throws Exception {
        stubFor(WireMock.get(urlEqualTo("/apis/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\"name\": \"My API name\"}")));

        final Map<String, Object> document = new HashMap<>();
        document.put("api", "123");
        IngestDocument ingestDocument = RandomDocumentPicks.randomIngestDocument(random(), document);
        EnhanceGraviteeAttributionProcessor processor = new EnhanceGraviteeAttributionProcessor(randomAsciiAlphanumOfLength(10),
                new EndpointConfiguration.Builder(wireMockRule.baseUrl()).build(), "api", "application");
        processor.execute(ingestDocument);
        Map<String, Object> data = ingestDocument.getSourceAndMetadata();
        assertThat(data, hasKey("api-name"));
        assertThat(data.get("api-name"), is("My API name"));
        processor.execute(ingestDocument);
        data = ingestDocument.getSourceAndMetadata();
        assertThat(data, hasKey("api-name"));
        assertThat(data.get("api-name"), is("My API name"));

        verify(1, getRequestedFor(urlEqualTo("/apis/123")));
        verify(0, getRequestedFor(urlEqualTo("/applications/321")));
    }
}
