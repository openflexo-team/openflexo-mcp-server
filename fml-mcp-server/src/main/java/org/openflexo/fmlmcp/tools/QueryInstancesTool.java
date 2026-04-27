// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\QueryInstancesTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\QueryInstancesTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code query_instances}
 *
 * Filters the live session store and returns all handles that match
 * the supplied criteria.  All parameters are optional:
 *
 * <ul>
 *   <li>{@code conceptName}      – keep only instances of this concept (exact name)</li>
 *   <li>{@code fmlResourceURI}   – keep only instances whose virtual model URI matches</li>
 *   <li>{@code filter}           – a JSON object {@code {"propertyName": "expectedValue"}}
 *                                  that is applied after the above two constraints;
 *                                  every key/value pair must match (AND semantics).</li>
 * </ul>
 *
 * If no parameters are supplied the tool behaves like {@code list_instances}.
 *
 * @author Mouad Hayaoui
 */
public class QueryInstancesTool {

    private static final Logger logger =
            Logger.getLogger(QueryInstancesTool.class.getName());

    public static final String TOOL_NAME = "query_instances";
    public static final String TOOL_DESCRIPTION =
            "Filter live concept instances in the session store by concept name, "
                    + "virtual model URI, and/or a property-value predicate. "
                    + "All parameters are optional — omitting all of them is equivalent "
                    + "to list_instances. The 'filter' parameter is a JSON object where "
                    + "each key is a property name and its value is the expected string "
                    + "representation (AND semantics across multiple keys). "
                    + "Returns an array of matching handles with their metadata.";

    private final FMLSessionStore sessionStore;

    public QueryInstancesTool(FMLSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * @param conceptNameFilter  optional concept name constraint (null = no filter)
     * @param fmlResourceURIFilter optional virtual model URI constraint (null = no filter)
     * @param filterJson         optional JSON object of property→expectedValue pairs
     *                           (null / empty / "{}" = no property filter)
     */
    public String execute(String conceptNameFilter,
                          String fmlResourceURIFilter,
                          String filterJson) {
        try {
             JsonObject propertyFilter = null;
            if (filterJson != null && !filterJson.trim().isEmpty()
                    && !filterJson.trim().equals("{}")) {
                try {
                    propertyFilter = JsonParser.parseString(filterJson).getAsJsonObject();
                } catch (Exception e) {
                    return buildError("Could not parse 'filter' as JSON object: " + e.getMessage());
                }
            }

            List<Map.Entry<String, SessionEntry>> allEntries = sessionStore.allEntries();

            JsonArray matches = new JsonArray();
            int total = 0;

            for (Map.Entry<String, SessionEntry> entry : allEntries) {
                String handle = entry.getKey();
                SessionEntry se = entry.getValue();

                 if (conceptNameFilter != null && !conceptNameFilter.trim().isEmpty()) {
                    if (!conceptNameFilter.equals(se.conceptName)) {
                        continue;
                    }
                }

                 if (fmlResourceURIFilter != null && !fmlResourceURIFilter.trim().isEmpty()) {
                    if (!fmlResourceURIFilter.equals(se.virtualModelURI)) {
                        continue;
                    }
                }

                 if (propertyFilter != null && propertyFilter.size() > 0) {
                    if (!matchesPropertyFilter(se.instance, propertyFilter)) {
                        continue;
                    }
                }

                 JsonObject match = new JsonObject();
                match.addProperty("handle", handle);
                match.addProperty("concept", se.conceptName);
                match.addProperty("virtualModelURI", se.virtualModelURI);
                match.addProperty("createdAt",
                        java.time.Instant.ofEpochMilli(se.createdAt).toString());
                matches.add(match);
                total++;
            }

            JsonObject result = new JsonObject();
            result.addProperty("count", total);

             JsonObject appliedFilters = new JsonObject();
            if (conceptNameFilter != null && !conceptNameFilter.trim().isEmpty()) {
                appliedFilters.addProperty("conceptName", conceptNameFilter);
            }
            if (fmlResourceURIFilter != null && !fmlResourceURIFilter.trim().isEmpty()) {
                appliedFilters.addProperty("fmlResourceURI", fmlResourceURIFilter);
            }
            if (propertyFilter != null && propertyFilter.size() > 0) {
                appliedFilters.add("filter", propertyFilter);
            }
            result.add("appliedFilters", appliedFilters);
            result.add("instances", matches);

            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "query_instances failed", e);
            return buildError("Internal error: " + e.getMessage());
        }
    }


    /**
     * Returns true if every key/value pair in {@code filter} matches
     * the corresponding property value on {@code fci}.
     *
     * Comparison is done on the String representation of the property value
     * (same strategy as {@code get_property} / {@code get_instance_state}).
     */
    private boolean matchesPropertyFilter(FlexoConceptInstance fci, JsonObject filter) {
        FlexoConcept concept = fci.getFlexoConcept();
        if (concept == null) {
            return false;
        }

        for (Map.Entry<String, com.google.gson.JsonElement> filterEntry
                : filter.entrySet()) {
            String propName = filterEntry.getKey();
            String expectedValue = filterEntry.getValue().isJsonNull()
                    ? null
                    : filterEntry.getValue().getAsString();

             FlexoProperty<?> prop = findProperty(concept, propName);
            if (prop == null) {
                 logger.fine("query_instances: property '" + propName
                        + "' not found on concept '" + concept.getName() + "'");
                return false;
            }

            try {
                Object value = fci.getFlexoPropertyValue(prop);
                String actualValue = value == null ? null : value.toString();

                if (expectedValue == null && actualValue != null) {
                    return false;
                }
                if (expectedValue != null && !expectedValue.equals(actualValue)) {
                    return false;
                }
            } catch (Exception e) {
                logger.fine("query_instances: could not read property '"
                        + propName + "': " + e.getMessage());
                return false;
            }
        }

        return true;
    }

    /**
     * Finds a property by name using FlexoConcept's own accessible-property
     * lookup, which already covers the full inheritance chain.
     * Falls back to a recursive walk over parent concepts for robustness.
     */
    private FlexoProperty<?> findProperty(FlexoConcept concept, String name) {
         FlexoProperty<?> p = concept.getAccessibleProperty(name);
        if (p != null) {
            return p;
        }
        // Explicit recursive walk as fallback
        for (FlexoConcept parent : concept.getParentFlexoConcepts()) {
            p = findProperty(parent, name);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}