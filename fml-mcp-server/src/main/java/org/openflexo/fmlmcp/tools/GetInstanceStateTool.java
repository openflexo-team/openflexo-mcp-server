package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;

import java.util.logging.Logger;

/**
 * MCP tool: get_instance_state
 *
 * Returns the current property values of a concept instance identified
 * by its session handle, including all inherited properties.
 *
 * @author Mouad Hayaoui
 */
public class GetInstanceStateTool {

    public static final String TOOL_NAME = "get_instance_state";
    public static final String TOOL_DESCRIPTION =
            "Given a session handle, return the current state of the instance "
                    + "including its concept name, virtual model URI, and all property "
                    + "values including inherited ones.";

    private static final Logger logger =
            Logger.getLogger(GetInstanceStateTool.class.getName());

    private final FMLSessionStore sessionStore;

    public GetInstanceStateTool(FMLSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public String execute(String handle) {
        SessionEntry entry = sessionStore.getEntry(handle);
        if (entry == null) {
            return buildError("Unknown handle: '" + handle
                    + "'. Did you call create_instance first?");
        }

        try {
            FlexoConceptInstance fci = entry.instance;
            JsonObject state = new JsonObject();

            state.addProperty("handle",          handle);
            state.addProperty("concept",         entry.conceptName);
            state.addProperty("virtualModelURI", entry.virtualModelURI);

             JsonObject properties = new JsonObject();
            collectProperties(fci.getFlexoConcept(), fci, properties);
            state.add("properties", properties);

            return state.toString();

        } catch (Exception e) {
            logger.severe("get_instance_state failed for handle "
                    + handle + ": " + e.getMessage());
            return buildError(e.getMessage());
        }
    }

    /**
     * Recursively walk the inheritance chain collecting property values.
     * Parent properties are written first; child properties overwrite on
     * name clash so the most-derived value always wins.
     */
    private void collectProperties(FlexoConcept concept,
                                   FlexoConceptInstance fci,
                                   JsonObject target) {
         for (FlexoProperty<?> prop : concept.getAccessibleProperties()) {
            try {
                Object value = fci.getFlexoPropertyValue(prop);
                if (value == null) {
                    target.addProperty(prop.getName(), (String) null);
                } else if (value instanceof Number) {
                    target.addProperty(prop.getName(), (Number) value);
                } else if (value instanceof Boolean) {
                    target.addProperty(prop.getName(), (Boolean) value);
                } else if (value instanceof FlexoConceptInstance) {
                    String nestedHandle = findHandle((FlexoConceptInstance) value);
                    target.addProperty(prop.getName(),
                            nestedHandle != null
                                    ? nestedHandle
                                    : "[" + ((FlexoConceptInstance) value)
                                    .getFlexoConcept().getName() + "]");
                } else {
                    target.addProperty(prop.getName(), value.toString());
                }
            } catch (Exception e) {
                target.addProperty(prop.getName(),
                        "<unreadable: " + e.getMessage() + ">");
            }
        }
    }

    private String findHandle(FlexoConceptInstance fci) {
        for (java.util.Map.Entry<String, FMLSessionStore.SessionEntry> entry
                : sessionStore.allEntries()) {
            if (entry.getValue().instance == fci) {
                return entry.getKey();
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