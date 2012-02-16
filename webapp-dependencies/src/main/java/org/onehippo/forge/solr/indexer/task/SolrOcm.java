/*
 *  Copyright 2012 Hippo.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.onehippo.forge.solr.indexer.task;

import java.util.Collection;
import java.util.Map.Entry;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solr Object Content Mapping: from JCR to Solr document
 * @author Mickaël Tricot
 * @version $Id: SolrOcm.java 129982 2012-02-06 22:12:13Z mtricot $
 */
public final class SolrOcm {

    /** JCR path to the documents */
    private static final String DOCUMENTS_PATH = "/content/documents";

    /** Logger */
    private static final Logger log = LoggerFactory.getLogger(SolrOcm.class);

    /** Configuration */
    private final SolrConfiguration configuration;

    /**
     * Constructor
     * @param configuration Configuration
     */
    public SolrOcm(SolrConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Traverses the JCR tree, populate the documents queue, and run a method after each document creation
     * @param documentsQueue Documents queue
     * @param runnable Method to run after a document is created
     * @param i Node iterator
     */
    private void populateDocumentsQueueThenRun(Collection<SolrInputDocument> documentsQueue, Runnable runnable,
            NodeIterator i) {
        while (i.hasNext()) {
            Node node = i.nextNode();
            if (JcrUtils.isFolder(node) || JcrUtils.isHandle(node)) {
                try {
                    populateDocumentsQueueThenRun(documentsQueue, runnable, node.getNodes());
                } catch (RepositoryException e) {
                    log.error("Failed to retrieve (child) nodes at " + JcrUtils.getPath(node), e);
                }
            } else if (JcrUtils.isOfType(node, configuration.getNodes()) && JcrUtils.isLive(node)) {
                SolrInputDocument document = readProperties(node);
                if (document != null) {
                    log.debug("Document added: {}", document);
                    documentsQueue.add(document);
                    runnable.run();
                }
            }
        }
    }

    /**
     * Read document from a JCR node
     * @param node JCR node
     * @return Document (null if not valid)
     */
    private SolrInputDocument readProperties(Node node) {
        log.debug("Create document for node {}", JcrUtils.getPath(node));
        SolrInputDocument document = new SolrInputDocument();
        boolean isEmpty = true;
        for (Entry<String, String> propertyName : configuration.getProperties().entrySet()) {
            Object value = JcrUtils.readPropertyValue(node, propertyName.getValue());
            if (value != null) {
                document.addField(propertyName.getKey(), value);
                isEmpty = false;
            }
        }
        return isEmpty ? null : document;
    }

    /**
     * Traverses the JCR tree, populate the documents queue, and run a method after each document creation
     * @param documentsQueue Documents queue
     * @param runnable Method to run after a document is created
     */
    public void populateDocumentsQueueThenRun(Collection<SolrInputDocument> documentsQueue, Runnable runnable) {
        try {
            populateDocumentsQueueThenRun(documentsQueue, runnable,
                    configuration.getSession().getNode(DOCUMENTS_PATH).getNodes());
        } catch (RepositoryException e) {
            log.error("Failed to retrieve (child) nodes at " + DOCUMENTS_PATH, e);
        }
    }
}