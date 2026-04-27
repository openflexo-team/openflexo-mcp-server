package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

/**
 * MCP tool: list_instances
 *
 * Returns all active session handles with their concept name,
 * virtual model URI, and creation timestamp.
 *
 * @author Mouad Hayaoui
 */
public class ListInstancesTool {

    private static final Logger logger = Logger.getLogger(ListInstancesTool.class.getName());
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private final FMLSessionStore sessionStore;
    public static final String TOOL_NAME        = "list_instances";

    public static final String TOOL_DESCRIPTION =
            "List all active instance handles in the session store, along with their concept name, virtual model URI, and creation timestamp.";

    public ListInstancesTool(FMLSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public String execute() {
        try {
            JsonArray instances = new JsonArray();

            for (Map.Entry<String, SessionEntry> entry : sessionStore.allEntries()) {
                String handle     = entry.getKey();
                SessionEntry sess = entry.getValue();

                JsonObject obj = new JsonObject();
                obj.addProperty("handle",          handle);
                obj.addProperty("concept",         sess.conceptName);
                obj.addProperty("virtualModelURI", sess.virtualModelURI);
                obj.addProperty("createdAt",
                        DATE_FMT.format(new Date(sess.createdAt)));
                instances.add(obj);
            }

            JsonObject result = new JsonObject();
            result.addProperty("count", instances.size());
            result.add("instances", instances);
            return result.toString();

        } catch (Exception e) {
            logger.severe("list_instances failed: " + e.getMessage());
            return buildError(e.getMessage());
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}