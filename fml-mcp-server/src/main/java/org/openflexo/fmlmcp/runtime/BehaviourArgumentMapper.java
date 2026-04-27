// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\runtime\BehaviourArgumentMapper.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\runtime\BehaviourArgumentMapper.java

package org.openflexo.fmlmcp.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FlexoBehaviour;
import org.openflexo.foundation.fml.FlexoBehaviourParameter;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.foundation.resource.FlexoResource;
import org.openflexo.foundation.resource.ResourceManager;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Maps a JSON arguments object from the MCP wire format to a
 * Map&lt;String, Object&gt; that can be fed into an FML behaviour execution.
 *
 * <p>Three kinds of resolution are performed on string values:
 * <ol>
 *   <li><b>Handle resolution</b> — strings matching {@code inst_xxxxxx} are
 *       resolved to live {@link FlexoConceptInstance} objects from the
 *       {@link FMLSessionStore}.</li>
 *   <li><b>Resource resolution</b> — when the declared parameter type is
 *       {@code Resource<X>} (i.e. a {@link FlexoResource}), the string value
 *       is treated as a resource URI and looked up in the
 *       {@link ResourceManager}.  This is what makes CreationSchemes that
 *       accept {@code required Resource<CSVDocument> resource} work from
 *       the MCP layer.</li>
 *   <li><b>Primitive coercion</b> — booleans and numbers are coerced to
 *       their Java equivalents; everything else is passed as a plain String.</li>
 * </ol>
 *
 * @author Mouad Hayaoui
 */
public class BehaviourArgumentMapper {

    private static final Logger logger =
            Logger.getLogger(BehaviourArgumentMapper.class.getName());

    // Handle strings follow the pattern inst_xxxxxx (6 hex chars)
    private static final java.util.regex.Pattern HANDLE_PATTERN =
            java.util.regex.Pattern.compile("^inst_[0-9a-f]{6}$");

    private BehaviourArgumentMapper() {}

    /**
     * Convert a JSON arguments object to a typed parameter map.
     *
     * @param behaviour the target FML behaviour (used to inspect declared parameter types)
     * @param jsonArgs  the JSON object carrying argument values (may be null)
     * @return a Map from parameter name to coerced Java value
     */
    public static Map<String, Object> map(FlexoBehaviour behaviour, JsonObject jsonArgs) {
        Map<String, Object> result = new HashMap<>();

        if (jsonArgs == null || jsonArgs.size() == 0) {
            return result;
        }

        for (Map.Entry<String, JsonElement> entry : jsonArgs.entrySet()) {
            String paramName  = entry.getKey();
            JsonElement value = entry.getValue();

            FlexoBehaviourParameter declared = behaviour.getParameter(paramName);
            if (declared == null) {
                logger.warning("Behaviour '" + behaviour.getName()
                        + "' has no parameter named '" + paramName
                        + "'- passing value anyway.");
            }

            // Pass the declared parameter type so coerce() can do resource resolution
            Type declaredType = (declared != null) ? declared.getType() : null;
            result.put(paramName, coerce(value, declaredType));
        }

        return result;
    }


    private static Object coerce(JsonElement el, Type declaredType) {
        if (el == null || el.isJsonNull()) {
            return null;
        }

        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isBoolean()) {
                return el.getAsBoolean();
            }
            if (el.getAsJsonPrimitive().isNumber()) {
                double d = el.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return (long) d;
                }
                return d;
            }

             String str = el.getAsString();

             if (HANDLE_PATTERN.matcher(str).matches()) {
                FlexoConceptInstance resolved = resolveHandle(str);
                if (resolved != null) {
                    logger.info("Resolved handle '" + str
                            + "' to FlexoConceptInstance: "
                            + resolved.getFlexoConcept().getName());
                    return resolved;
                }
                logger.warning("Handle '" + str
                        + "' not found in session store — passing as plain string.");
            }


            if (isResourceType(declaredType) || looksLikeResourceURI(str)) {
                FlexoResource<?> resource = resolveResource(str);
                if (resource != null) {
                    logger.info("Resolved resource URI '" + str
                            + "' to FlexoResource: " + resource.getURI());
                    return resource;
                }
                // If resolution failed log a warning but fall through to string
                if (isResourceType(declaredType)) {
                    logger.warning("Resource not found for URI '" + str
                            + "' — the resource center containing this file may not be registered. "
                            + "Call add_resource_center first.");
                }
            }

             return str;
        }

         return el.toString();
    }


    /**
     * Returns true if the declared parameter type is {@code FlexoResource}
     * or a parameterized form such as {@code Resource<CSVDocument>}.
     */
    private static boolean isResourceType(Type declaredType) {
        if (declaredType == null) {
            return false;
        }
        // Raw FlexoResource
        if (declaredType instanceof Class) {
            return FlexoResource.class.isAssignableFrom((Class<?>) declaredType);
        }
        // Parameterized: Resource<CSVDocument>, Resource<MCPServer>, etc.
        if (declaredType instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) declaredType).getRawType();
            if (raw instanceof Class) {
                return FlexoResource.class.isAssignableFrom((Class<?>) raw);
            }
        }
         String typeName = declaredType.getTypeName();
        return typeName.contains("FlexoResource")
                || typeName.contains("Resource<")
                || typeName.contains("CSVResource")
                || typeName.contains("MCPServerResource");
    }

    /**
     * Heuristic: a string looks like a resource URI if it starts with
     * "http" or ends with a common data file extension.
     * This catches cases where the declared type is unknown (null) but the
     * intent is clearly to pass a resource path.
     */
    private static boolean looksLikeResourceURI(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        String lower = str.toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("file://")
                || lower.endsWith(".csv")
                || lower.endsWith(".json")
                || lower.endsWith(".xml")
                || lower.endsWith(".owl")
                || lower.endsWith(".fml");
    }


    /**
     * Look up a resource by URI in the FMLRuntimeBridge's resource manager.
     * Returns null if not found or if the runtime is not initialised.
     */
    @SuppressWarnings("rawtypes")
    private static FlexoResource<?> resolveResource(String uri) {
        try {
            FMLRuntimeBridge bridge = FMLRuntimeBridge.getInstance();
            ResourceManager rm = bridge.getServiceManager().getResourceManager();
            FlexoResource resource = rm.getResource(uri);
            if (resource == null) {
                // Try a filename-based fallback: scan all registered resources
                // for a URI suffix match (same strategy as FMLRuntimeBridge.loadVirtualModel)
                String fileName = uri.contains("/")
                        ? uri.substring(uri.lastIndexOf('/') + 1)
                        : uri;
                for (FlexoResource<?> r : rm.getRegisteredResources()) {
                    if (r.getURI() != null && r.getURI().endsWith(fileName)) {
                        logger.info("Resource found by filename match: " + r.getURI());
                        return r;
                    }
                }
            }
            return resource;
        } catch (Exception e) {
            logger.warning("Resource lookup failed for URI '" + uri + "': " + e.getMessage());
            return null;
        }
    }


    private static FlexoConceptInstance resolveHandle(String handle) {
        return FMLSessionStore.getInstance().get(handle);
    }
}