package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;

import java.util.logging.Logger;

/**
 * MCP tool: destroy_instance
 *
 * Removes a session handle from the store and frees the reference
 * to the underlying {@link org.openflexo.foundation.fml.rt.FlexoConceptInstance}.
 *
 * @author Mouad Hayaoui
 */
public class DestroyInstanceTool {

    private static final Logger logger = Logger.getLogger(DestroyInstanceTool.class.getName());

    private final FMLSessionStore sessionStore;
    public static final String TOOL_NAME        = "destroy_instance";

    public static final String TOOL_DESCRIPTION =
            "Given a session handle, destroy the instance and remove the handle from the session store. "
                    + "This will free the reference to the underlying FlexoConceptInstance and allow it to be garbage collected.";
    public DestroyInstanceTool(FMLSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public String execute(String handle) {
        SessionEntry entry = sessionStore.getEntry(handle);
        if (entry == null) {
            return buildError("Unknown handle: '" + handle + "'. Nothing to destroy.");
        }

        String conceptName = entry.conceptName;
        boolean removed    = sessionStore.remove(handle);

        if (!removed) {
            return buildError("Failed to remove handle: '" + handle + "'.");
        }

        logger.info("Destroyed instance handle: " + handle
                + " (concept: " + conceptName + ")");

        JsonObject result = new JsonObject();
        result.addProperty("destroyed", true);
        result.addProperty("handle",    handle);
        result.addProperty("concept",   conceptName);
        return result.toString();
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}