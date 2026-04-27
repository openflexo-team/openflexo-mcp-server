package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: get_property
 *
 * Reads a single named property from a live concept instance.
 * Lighter than get_instance_state when only one field is needed.
 *
 * @author Mouad Hayaoui
 */
public class GetPropertyTool {

    public static final String TOOL_NAME = "get_property";
    public static final String TOOL_DESCRIPTION =
            "Read a single named property from a live concept instance identified "
                    + "by its handle. Lighter than get_instance_state when only one "
                    + "field is needed.";

    private static final Logger logger = Logger.getLogger(GetPropertyTool.class.getName());

    private final FMLSessionStore sessionStore;

    public GetPropertyTool(FMLSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public String execute(String handle, String propertyName) {
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

            Object value = fci.getFlexoPropertyValue(prop);

            JsonObject result = new JsonObject();
            result.addProperty("handle",   handle);
            result.addProperty("property", propertyName);

            if (value == null) {
                result.addProperty("value", (String) null);
            } else if (value instanceof Number) {
                result.addProperty("value", (Number) value);
            } else if (value instanceof Boolean) {
                result.addProperty("value", (Boolean) value);
            } else {
                result.addProperty("value", value.toString());
            }

            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "get_property failed", e);
            return buildError(e.getMessage());
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}