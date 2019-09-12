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

import org.elasticsearch.ingest.IngestDocument;

/**
 * An {@link IngestDocument} enhancer, providing convenient method to enhance a document with new information based on
 * this document existing fields. Instances of this interface is used during the Gravitee Ingest plugin execution as a
 * stream of enhancers, each enhancer being able to take advantage of the enhancement from previous enhancers in the
 * stream. Each enhancer must be resilient to previous ones failure, and enforce a default value for such situations.
 */
public interface IngestDocumentEnhancer {

    /**
     * Enhances the specified document with new information. The original document can be updated with new information,
     * but cannot be replaced by a new one.
     *
     * @param ingestDocument the document to enhance.
     */
    void enhanceDocument(final IngestDocument ingestDocument);

}
