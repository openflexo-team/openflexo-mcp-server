package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: set_property
 *
 * Directly sets a named property on a live concept instance without
 * calling a behaviour. Useful for mutating state between behaviour calls.
 *
 * @author Mouad Hayaoui
 */
public class SetPropertyTool {

    public static final String TOOL_NAME = "set_property";
    public static final String TOOL_DESCRIPTION =
            "Directly set a named property value on a live concept instance "
                    + "identified by its handle. Use this to mutate state without "
                    + "calling a full behaviour.";

    private static final Logger logger = Logger.getLogger(SetPropertyTool.class.getName());

    private final FMLSessionStore sessionStore;

    public SetPropertyTool(FMLSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public String execute(String handle, String propertyName, String valueJson) {
        if (handle == null || handle.trim().isEmpty()) {
            return buildError("handle must not be empty.");
        }
        if (propertyName == null || propertyName.trim().isEmpty()) {
            return buildError("propertyName must not be empty.");
        }

        SessionEntry entry = sessionStore.getEntry(handle);
        if (entry == null) {
            return buildError("Unknown handle: '" + handle
                    + "'. Did you call create_instance first?");
        }

        try {
            FlexoConceptInstance fci = entry.instance;

            FlexoProperty prop = fci.getFlexoConcept()
                    .getDeclaredProperty(propertyName);
            if (prop == null) {
                return buildError("Property '" + propertyName
                        + "' not found on concept '"
                        + fci.getFlexoConcept().getName() + "'.");
            }

            if (prop.isReadOnly()) {
                return buildError("Property '" + propertyName + "' is read-only.");
            }

             Object value = parseValue(valueJson);
            fci.setFlexoPropertyValue(prop, value);

            logger.info("Set property '" + propertyName
                    + "' on handle '" + handle + "' to: " + value);

            JsonObject result = new JsonObject();
            result.addProperty("handle",       handle);
            result.addProperty("property",     propertyName);
            result.addProperty("value",        valueJson);
            result.addProperty("success",      true);
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "set_property failed", e);
            return buildError(e.getMessage());
        }
    }

    private Object parseValue(String valueJson) {
        if (valueJson == null || valueJson.trim().isEmpty()) {
            return null;
        }
        String trimmed = valueJson.trim();
         if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        // Boolean
        if (trimmed.equals("true"))  return Boolean.TRUE;
        if (trimmed.equals("false")) return Boolean.FALSE;
        // Number
        try { return Long.parseLong(trimmed);   } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(trimmed); } catch (NumberFormatException ignored) {}
        // Fall back to raw string
        return trimmed;
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}