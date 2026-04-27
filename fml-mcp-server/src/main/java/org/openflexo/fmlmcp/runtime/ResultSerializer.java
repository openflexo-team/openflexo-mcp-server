package org.openflexo.fmlmcp.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serializes FML execution results to JSON strings suitable for return
 * over the MCP protocol.
 *
 * Nested FlexoConceptInstance values are resolved to their session handle
 * if one exists in the FMLSessionStore, otherwise serialized recursively.
 *
 * @author Mouad Hayaoui
 */
public class ResultSerializer {

    private static final Logger logger = Logger.getLogger(ResultSerializer.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ResultSerializer() {}

    public static String serialize(Object result) {
        if (result == null) {
            return "{\"result\": null}";
        }
        if (result instanceof String) {
            JsonObject obj = new JsonObject();
            obj.addProperty("result", (String) result);
            return GSON.toJson(obj);
        }
        if (result instanceof Number) {
            JsonObject obj = new JsonObject();
            obj.addProperty("result", (Number) result);
            return GSON.toJson(obj);
        }
        if (result instanceof Boolean) {
            JsonObject obj = new JsonObject();
            obj.addProperty("result", (Boolean) result);
            return GSON.toJson(obj);
        }
        if (result instanceof FlexoConceptInstance) {
            return serializeConceptInstance((FlexoConceptInstance) result, 0);
        }
        if (result instanceof JsonElement) {
            return GSON.toJson((JsonElement) result);
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("result", result.toString());
        return GSON.toJson(obj);
    }

    /**
     * Serialize a FlexoConceptInstance to a JSON object.
     *
     * If the instance is already registered in FMLSessionStore, its handle
     * is included so the caller can reference it in future tool calls.
     * Nested instances are resolved recursively up to a depth of 3 to
     * avoid infinite loops in circular object graphs.
     */
    private static String serializeConceptInstance(FlexoConceptInstance fci, int depth) {
        JsonObject obj = new JsonObject();
        FlexoConcept concept = fci.getFlexoConcept();
        obj.addProperty("_concept", concept.getName());

         String handle = findHandle(fci);
        if (handle != null) {
            obj.addProperty("_handle", handle);
        }

         for (FlexoProperty<?> property : getAllProperties(concept)) {
            String name = property.getName();
            try {
                Object value = fci.getFlexoPropertyValue(property);
                if (value == null) {
                    obj.add(name, com.google.gson.JsonNull.INSTANCE);
                } else if (value instanceof String) {
                    obj.addProperty(name, (String) value);
                } else if (value instanceof Number) {
                    obj.addProperty(name, (Number) value);
                } else if (value instanceof Boolean) {
                    obj.addProperty(name, (Boolean) value);
                } else if (value instanceof FlexoConceptInstance) {
                    FlexoConceptInstance nested = (FlexoConceptInstance) value;
                    // Try to resolve to a known handle first
                    String nestedHandle = findHandle(nested);
                    if (nestedHandle != null) {
                        obj.addProperty(name, nestedHandle);
                    } else if (depth < 3) {
                        // Recurse for unknown nested instances
                        obj.add(name, GSON.fromJson(
                                serializeConceptInstance(nested, depth + 1),
                                JsonObject.class));
                    } else {
                        obj.addProperty(name, "[" + nested.getFlexoConcept().getName() + "]");
                    }
                } else {
                    obj.addProperty(name, value.toString());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not read property '" + name + "'", e);
                obj.addProperty(name, "<error reading property>");
            }
        }

        return GSON.toJson(obj);
    }

    /**
     * Walk the full inheritance chain and collect all properties,
     * parent properties first, child properties last.
     */
    private static java.util.List<FlexoProperty<?>> getAllProperties(FlexoConcept concept) {
         return concept.getAccessibleProperties();
    }

    /**
     * Look up the session store to find if this instance already has a handle.
     * Returns null if not registered.
     */
    private static String findHandle(FlexoConceptInstance fci) {
        FMLSessionStore store = FMLSessionStore.getInstance();
        for (java.util.Map.Entry<String, FMLSessionStore.SessionEntry> entry
                : store.allEntries()) {
            if (entry.getValue().instance == fci) {
                return entry.getKey();
            }
        }
        return null;
    }
}