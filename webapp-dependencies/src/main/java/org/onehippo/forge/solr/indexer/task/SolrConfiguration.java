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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * Solr configuration
 * @author Mickaël Tricot
 * @version $Id: SolrConfiguration.java 130341 2012-02-09 16:47:56Z mtricot $
 */
public final class SolrConfiguration {

    /** Logger */
    private static final Logger log = LoggerFactory.getLogger(SolrConfiguration.class);

    /** Query for reading the configuration nodes */
    private static final String QUERY =
            "SELECT * FROM " + Namespace.NODE + " WHERE jcr:path LIKE '/content/%' AND (" + Namespace.PROPERTY_NODE +
                    " <> NULL OR " + Namespace.PROPERTY_PROPERTY + " <> NULL)";

    /** Wrong character pattern for Solr ID */
    private static final Pattern WRONG_CHARACTER_PATTERN_FOR_SOLR_ID = Pattern.compile("[^a-zA-Z0-9_]");

    /**
     * Validate solr filter properties (skip invalid properties)
     * @param solrFilterProperties Solr filter properties
     * @return Solr filter properties
     */
    public static Map<String, String> validateSolrFilterProperties(Map<String, String> solrFilterProperties) {
        Map<String, String> solrFilterPropertiesValid = new HashMap<String, String>();
        for (Entry<String, String> property : solrFilterProperties.entrySet()) {
            String key = StringUtils.trimToNull(property.getKey());
            String value = StringUtils.trimToNull(property.getValue());
            if (key == null || value == null || WRONG_CHARACTER_PATTERN_FOR_SOLR_ID.matcher(key).find()) {
                log.warn("Skip invalid Solr filter property: {}", property);
            } else {
                solrFilterPropertiesValid.put(key, value);
            }
        }
        return Collections.unmodifiableMap(solrFilterPropertiesValid);
    }

    /** Nodes to index */
    private final Collection<String> nodes;

    /** Properties to index */
    private final Map<String, String> properties;

    /** JCR session */
    private final Session session;

    /**
     * Constructor
     * @param session JCR session
     * @param solrFilterProperties Solr filter properties
     */
    public SolrConfiguration(Session session, Map<String, String> solrFilterProperties) {
        Assert.notNull(session, "session must not be null");

        Collection<String> n = new HashSet<String>();
        Map<String, String> p = new HashMap<String, String>(solrFilterProperties);

        try {
            // Read configuration nodes
            NodeIterator ni =
                    session.getWorkspace().getQueryManager().createQuery(QUERY, Query.SQL).execute().getNodes();
            while (ni.hasNext()) {

                Node node = ni.nextNode();
                log.info("Loading Solr configuration from node {}", JcrUtils.getPath(node));

                // Read nodes to index from configuration
                if (node.hasProperty(Namespace.PROPERTY_NODE)) {
                    for (Value value : node.getProperty(Namespace.PROPERTY_NODE).getValues()) {
                        String nodeName = StringUtils.trimToNull(value.getString());
                        if (nodeName != null) {
                            n.add(nodeName);
                        }
                    }
                }

                // Read properties to index from configuration
                if (node.hasProperty(Namespace.PROPERTY_PROPERTY)) {
                    for (Value value : node.getProperty(Namespace.PROPERTY_PROPERTY).getValues()) {
                        String propertyName = StringUtils.trimToNull(value.getString());
                        if (propertyName != null) {
                            p.put("dynamic_" +
                                    WRONG_CHARACTER_PATTERN_FOR_SOLR_ID.matcher(propertyName).replaceAll("_"),
                                    propertyName);
                        }
                    }
                }
            }

        } catch (RepositoryException e) {
            log.error("An error occurred while loading the Solr configuration", e);
        }

        nodes = Collections.unmodifiableCollection(n);
        properties = Collections.unmodifiableMap(p);
        this.session = session;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[nodes = " + getNodes() + ", properties = " + getProperties() + ']';
    }

    /** @return Nodes to index */
    public Collection<String> getNodes() {
        return nodes;
    }

    /** @return Properties to index */
    public Map<String, String> getProperties() {
        return properties;
    }

    /** @return JCR session */
    public Session getSession() {
        return session;
    }

    /**
     * Check configuration
     * @return TRUE if the configuration is valid
     */
    public boolean isNotValid() {
        return session == null || getNodes().isEmpty() || getProperties().isEmpty() || !session.isLive();
    }

    /** Solr configuration namespace */
    private interface Namespace {
        /** Configuration node */
        String NODE = "solr:configuration";

        /** Configuration property for nodes */
        String PROPERTY_NODE = "solr:node";

        /** Configuration property for properties */
        String PROPERTY_PROPERTY = "solr:property";
    }
}
