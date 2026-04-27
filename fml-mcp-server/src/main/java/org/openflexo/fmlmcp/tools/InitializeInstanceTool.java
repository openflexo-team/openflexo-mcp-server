// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\InitializeInstanceTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\InitializeInstanceTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;
import org.openflexo.foundation.DefaultFlexoEditor;
import org.openflexo.foundation.FlexoEditor;
import org.openflexo.foundation.fml.CreationScheme;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rt.FMLRTVirtualModelInstance;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.foundation.fml.rt.action.CreateFlexoConceptInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code initialize_instance}
 *
 * A clean workaround for the session-state corruption that occurs when
 * {@code create_instance} is called after a {@code destroy_instance} +
 * {@code reload_model} cycle.
 *
 * <p>Strategy (known-good pattern):
 * <ol>
 *   <li>Instantiate the concept using its <em>no-argument</em> CreationScheme
 *       (bypasses parameter-injection which is the failing step).</li>
 *   <li>Apply the supplied {@code properties} map via direct property assignment
 *       (same mechanism as {@code set_property}).</li>
 * </ol>
 *
 * <p>Input schema:
 * <pre>
 * {
 *   "fmlResourceURI": "http://fmlmcp.test/FML/TemperatureConverter.fml",
 *   "conceptName":    "Converter",
 *   "properties": {
 *     "celsius": "100.0"
 *   }
 * }
 * </pre>
 *
 * <p>Output: same as {@code create_instance}:
 * <pre>
 * { "handle": "inst_xxxxxx", "concept": "Converter",
 *   "propertiesSet": ["celsius"], "propertiesFailed": [] }
 * </pre>
 *
 * <p>Notes:
 * <ul>
 *   <li>All property values are passed as strings and coerced to the declared
 *       property type (same coercion logic as {@code SetPropertyTool}).</li>
 *   <li>If the concept has <em>no</em> no-arg CreationScheme the tool falls
 *       back to the first available CreationScheme with no parameters supplied,
 *       which may leave default values in place.</li>
 *   <li>This tool does NOT replace {@code create_instance} for the normal path;
 *       use it only when the normal path is broken after a reload.</li>
 * </ul>
 *
 * @author Mouad Hayaoui
 */
public class InitializeInstanceTool {

    private static final Logger logger =
            Logger.getLogger(InitializeInstanceTool.class.getName());

    public static final String TOOL_NAME = "initialize_instance";
    public static final String TOOL_DESCRIPTION =
            "Create a concept instance using its no-argument CreationScheme and then "
                    + "bulk-set properties via direct assignment. "
                    + "Use this instead of create_instance when that tool fails after a "
                    + "destroy_instance + reload_model cycle (known FML runtime bug). "
                    + "Supply 'properties' as a JSON object of propertyName → stringValue pairs. "
                    + "Returns the session handle plus a report of which properties were set "
                    + "successfully and which failed.";

    private final FMLRuntimeBridge runtime;
    private final FMLSessionStore  sessionStore;

    public InitializeInstanceTool(FMLRuntimeBridge runtime, FMLSessionStore sessionStore) {
        this.runtime      = runtime;
        this.sessionStore = sessionStore;
    }

    /**
     * @param fmlResourceURI  URI of the virtual model
     * @param conceptName     name of the FML concept to instantiate
     * @param propertiesJson  JSON object of {@code propertyName → value} pairs (may be null)
     */
    public String execute(String fmlResourceURI,
                          String conceptName,
                          String propertiesJson) {
        try {
             VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);

            FlexoConcept concept = vm.getFlexoConcept(conceptName);
            if (concept == null) {
                return buildError("Concept not found: '" + conceptName
                        + "' in virtual model '" + vm.getName() + "'");
            }

             CreationScheme scheme = pickNoArgScheme(concept);
            if (scheme == null) {
                return buildError("Concept '" + conceptName
                        + "' has no CreationScheme available.");
            }

             FMLRTVirtualModelInstance vmi =
                    FMLVMIFactory.getOrCreateVMI(vm, runtime);

             FlexoEditor editor =
                    new DefaultFlexoEditor(null, runtime.getServiceManager());

            CreateFlexoConceptInstance action =
                    CreateFlexoConceptInstance.actionType
                            .makeNewAction(vmi, null, editor);
            action.setFlexoConcept(concept);
            action.setCreationScheme(scheme);
             action.doAction();

            if (!action.hasActionExecutionSucceeded()) {
                return buildError("No-arg CreationScheme execution failed for concept '"
                        + conceptName + "'");
            }

            FlexoConceptInstance fci = action.getNewFlexoConceptInstance();
            if (fci == null) {
                return buildError("CreationScheme returned null instance for concept '"
                        + conceptName + "'");
            }

            String handle = sessionStore.register(fci, fmlResourceURI);
            logger.info("initialize_instance: created handle " + handle
                    + " for concept " + conceptName);

             List<String> propertiesSet    = new ArrayList<>();
            List<String> propertiesFailed = new ArrayList<>();

            if (propertiesJson != null && !propertiesJson.trim().isEmpty()
                    && !propertiesJson.trim().equals("{}")) {

                JsonObject propsMap;
                try {
                    propsMap = JsonParser.parseString(propertiesJson).getAsJsonObject();
                } catch (Exception e) {
                     return buildPartialResult(handle, conceptName,
                            propertiesSet, propertiesFailed,
                            "Could not parse 'properties' as JSON object: " + e.getMessage());
                }

                for (Map.Entry<String, JsonElement> propEntry : propsMap.entrySet()) {
                    String propName  = propEntry.getKey();
                    String propValue = propEntry.getValue().isJsonNull()
                            ? null
                            : propEntry.getValue().getAsString();

                    try {
                        setProperty(fci, propName, propValue);
                        propertiesSet.add(propName);
                        logger.fine("initialize_instance: set " + propName + " = " + propValue);
                    } catch (Exception e) {
                        logger.warning("initialize_instance: failed to set property '"
                                + propName + "': " + e.getMessage());
                        propertiesFailed.add(propName + " (" + e.getMessage() + ")");
                    }
                }
            }

            return buildSuccess(handle, conceptName, propertiesSet, propertiesFailed);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "initialize_instance failed", e);
            return buildError("Internal error: " + e.getMessage());
        }
    }


    /**
     * Prefer a zero-parameter CreationScheme; fall back to the first
     * CreationScheme available (which will be called with no args anyway).
     */
    private CreationScheme pickNoArgScheme(FlexoConcept concept) {
        if (concept.getCreationSchemes() == null
                || concept.getCreationSchemes().isEmpty()) {
            return null;
        }
         for (CreationScheme cs : concept.getCreationSchemes()) {
            if (cs.getParameters() == null || cs.getParameters().isEmpty()) {
                return cs;
            }
        }

        return concept.getCreationSchemes().get(0);
    }

    /**
     * Set a single property by name, coercing the string value to the
     * declared property type.  Mirrors the logic in {@link SetPropertyTool}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setProperty(FlexoConceptInstance fci,
                             String propertyName,
                             String valueStr) throws Exception {

         FlexoProperty prop = fci.getFlexoConcept().getAccessibleProperty(propertyName);

        if (prop == null) {
            throw new IllegalArgumentException("Property '" + propertyName
                    + "' not found on concept '"
                    + fci.getFlexoConcept().getName() + "'");
        }

        if (prop.isReadOnly()) {
            throw new IllegalStateException("Property '" + propertyName + "' is read-only.");
        }

         Object coerced = coerceValue(valueStr, prop.getResultingType());
        fci.setFlexoPropertyValue(prop, coerced);
    }

    /**
     * Best-effort type coercion from a String to the target type.
     * Falls back to the raw string if the type is unknown/unsupported.
     */
    private Object coerceValue(String raw, java.lang.reflect.Type type) {
        if (raw == null) {
            return null;
        }
        if (type == null) {
            return raw;
        }
        String typeName = type.getTypeName();
        try {
            if (typeName.equals("double") || typeName.equals("java.lang.Double")) {
                return Double.parseDouble(raw);
            }
            if (typeName.equals("float") || typeName.equals("java.lang.Float")) {
                return Float.parseFloat(raw);
            }
            if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {
                return Integer.parseInt(raw);
            }
            if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
                return Long.parseLong(raw);
            }
            if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
                return Boolean.parseBoolean(raw);
            }
        } catch (NumberFormatException e) {
            logger.warning("initialize_instance: could not coerce '"
                    + raw + "' to " + typeName + "; using raw string.");
        }
        return raw;
    }


    private String buildSuccess(String handle, String conceptName,
                                List<String> set, List<String> failed) {
        return buildPartialResult(handle, conceptName, set, failed, null);
    }

    private String buildPartialResult(String handle, String conceptName,
                                      List<String> set, List<String> failed,
                                      String warning) {
        JsonObject result = new JsonObject();
        result.addProperty("handle",  handle);
        result.addProperty("concept", conceptName);

        com.google.gson.JsonArray setArr = new com.google.gson.JsonArray();
        set.forEach(setArr::add);
        result.add("propertiesSet", setArr);

        com.google.gson.JsonArray failArr = new com.google.gson.JsonArray();
        failed.forEach(failArr::add);
        result.add("propertiesFailed", failArr);

        if (warning != null) {
            result.addProperty("warning", warning);
        }
        return result.toString();
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}