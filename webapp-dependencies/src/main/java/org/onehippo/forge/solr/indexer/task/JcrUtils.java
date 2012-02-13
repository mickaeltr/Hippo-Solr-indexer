package org.onehippo.forge.solr.indexer.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JCR utilities
 * @author Mickaël Tricot
 * @version $Id: JcrUtils.java 129978 2012-02-06 22:08:51Z mtricot $
 */
public final class JcrUtils {

    /** Path to availability */
    private static final String AVAILABILITY_PATH = "hippo:availability";

    /** Logger */
    private static final Logger log = LoggerFactory.getLogger(JcrUtils.class);

    /** Node types cache (including super types) */
    private static final Map<NodeType, Collection<String>> NODE_TYPES = new HashMap<NodeType, Collection<String>>();

    /** Path separator */
    private static final String PATH_SEPARATOR = "/";

    /** Path to UUID */
    private static final String UUID_PATH = "jcr:uuid";

    /**
     * Close session quietly (does not throw exception)
     * @param session Session
     */
    public static void closeQuietly(Session session) {
        if (session != null) {
            try {
                session.logout();
            } catch (Exception e) { // NOSONAR
                //
            }
        }
    }

    /**
     * Get node types (including super types)
     * @param node Node
     * @return Node types (including super types)
     */
    private static Collection<String> getNodeTypes(Node node) {
        try {
            Collection<String> nodeTypes = NODE_TYPES.get(node.getPrimaryNodeType());
            if (nodeTypes == null) {
                nodeTypes = new HashSet<String>();
                nodeTypes.add(node.getPrimaryNodeType().getName());
                for (NodeType superType : node.getPrimaryNodeType().getSupertypes()) {
                    nodeTypes.add(superType.getName());
                }
                NODE_TYPES.put(node.getPrimaryNodeType(), Collections.unmodifiableCollection(nodeTypes));
            }
            return nodeTypes;
        } catch (RepositoryException e) {
            log.error("Failed to retrieve node (super) types at " + getPath(node), e);
            return Collections.emptySet();
        }
    }

    /**
     * Returns the JCR item path, for logging purposes (silent, exception caught)
     * @param item JCR item
     * @return Path
     */
    public static String getPath(Item item) {
        try {
            return item.getPath();
        } catch (RepositoryException e) {
            return null;
        }
    }

    /**
     * Get JCR property (nested property if path contains '/')
     * @param node JCR node
     * @param propertyPath JCR property path
     * @return JCR property (nullable)
     */
    public static Property getProperty(Node node, String propertyPath) {
        String propertyPathStripped = StringUtils.strip(propertyPath, PATH_SEPARATOR);
        try {
            if (StringUtils.contains(propertyPathStripped, PATH_SEPARATOR)) {
                String nodePath = StringUtils.split(propertyPathStripped, PATH_SEPARATOR)[0];
                if (node.hasNode(nodePath)) {
                    return getProperty(node.getNode(nodePath), StringUtils.removeStart(propertyPathStripped, nodePath));
                }
            } else if (node.hasProperty(propertyPathStripped)) {
                return node.getProperty(propertyPathStripped);
            }
        } catch (RepositoryException e) {
            log.error("Failed to retrieve property " + propertyPath + " for node at " + getPath(node), e);
        }
        return null;
    }

    /**
     * Get UUID for a node (handle UUID if the node is a document)
     * @param node Node
     * @return UUID
     */
    public static String getUUID(Node node) {
        if (node == null) {
            return null;
        }
        String uuid = null;
        try {
            // The UUID of a document is changing over the changes, so we try to use the handle UUID instead
            Node parent = node.getParent();
            uuid = isHandle(parent) ? parent.getIdentifier() : node.getIdentifier();
        } catch (RepositoryException e) {
            log.error("Failed to retrieve the handle UUID for node " + getPath(node), e);
        }
        if (uuid == null) {
            try {
                uuid = node.getIdentifier();
            } catch (RepositoryException e) {
                log.error("Failed to retrieve the handle UUID for node " + getPath(node), e);
            }
        }
        return uuid;
    }

    /**
     * Check if a node is a folder
     * @param node Node
     * @return TRUE if it is a folder
     */
    public static boolean isFolder(Node node) {
        return node != null && getNodeTypes(node).contains("hippostd:folder");
    }

    /**
     * Check if a node is a handle
     * @param node Node
     * @return TRUE if it is a handle
     */
    public static boolean isHandle(Node node) {
        return node != null && getNodeTypes(node).contains("hippo:handle");
    }

    /**
     * Check if a node is live
     * @param node Node
     * @return TRUE if the node is live
     */
    public static boolean isLive(Node node) {
        boolean isLive = false;
        try {
            if (!node.hasProperty(AVAILABILITY_PATH)) {
                return true;
            }
            Value[] values = node.getProperty(AVAILABILITY_PATH).getValues();
            int i = 0;
            while (!isLive && i < values.length) {
                isLive = "live".equals(values[i].getString());
                ++i;
            }
        } catch (RepositoryException e) {
            log.error("Failed to retrieve property " + AVAILABILITY_PATH + " for node at " + getPath(node), e);
        }
        return isLive;
    }

    /**
     * Check if a node is of a certain type
     * @param node Node
     * @param types Types
     * @return TRUE if the node matches with one of the types
     */
    public static boolean isOfType(Node node, Collection<String> types) {
        if (node == null || types == null || types.isEmpty()) {
            return false;
        }
        Collection<String> intersect = new ArrayList<String>(types);
        intersect.retainAll(getNodeTypes(node));
        return !intersect.isEmpty();
    }

    /**
     * Read property value
     * @param node Node
     * @param propertyPath Property path
     * @return Property value (array if multiple property)
     */
    public static Object readPropertyValue(Node node, String propertyPath) {
        if (node == null || StringUtils.isBlank(propertyPath)) {
            return null;
        }
        Object value = null;
        if (UUID_PATH.equals(propertyPath)) {
            value = getUUID(node);
        }
        if (value == null) {
            Property property = getProperty(node, propertyPath);
            if (property != null) {
                try {
                    if (property.isMultiple()) {
                        Collection<Object> multiPropertyValue = new ArrayList<Object>();
                        for (Value propertyValue : property.getValues()) {
                            Object o = toObject(propertyValue);
                            if (o != null) {
                                multiPropertyValue.add(o);
                            }
                        }
                        value = multiPropertyValue.isEmpty() ? null : multiPropertyValue;
                    } else {
                        value = toObject(property.getValue());
                    }
                } catch (RepositoryException e) {
                    log.error("Failed to retrieve property " + getPath(property), e);
                }
            }
        }
        return value;
    }

    /**
     * Convert a JCR value into a Java object
     * @param value JCR value
     * @return Java object (nullable)
     */
    private static Object toObject(Value value) {
        if (value == null) {
            return null;
        }
        Object object = null;
        try {
            switch (value.getType()) {
                case PropertyType.BOOLEAN:
                    object = value.getBoolean();
                    break;
                case PropertyType.DATE:
                    object = value.getDate();
                    break;
                case PropertyType.DECIMAL:
                    object = value.getDecimal();
                    break;
                case PropertyType.DOUBLE:
                    object = value.getDouble();
                    break;
                case PropertyType.LONG:
                    object = value.getLong();
                    break;
                case PropertyType.NAME:
                    object = value.getString();
                    break;
                case PropertyType.STRING:
                    object = StringUtils.trimToNull(value.getString());
                    break;
                default:
                    log.warn("Unhandled JCR type {}", PropertyType.nameFromValue(value.getType()));
            }
        } catch (RepositoryException e) {
            log.warn("Error while reading property value: " + value, e);
        }
        return object;
    }

    /** Constructor (prevents instantiation) */
    private JcrUtils() {
    }
}