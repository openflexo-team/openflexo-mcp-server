package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

/**
 * MCP tool: ping
 *
 * Liveness check. Returns server version, runtime status,
 * active session count, and current timestamp.
 *
 * @author Mouad Hayaoui
 */
public class PingTool {

    public static final String TOOL_NAME = "ping";
    public static final String TOOL_DESCRIPTION =
            "Liveness check. Returns server version, OpenFlexo runtime status, "
                    + "active session count, and current server timestamp.";

    private static final Logger logger = Logger.getLogger(PingTool.class.getName());
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private final FMLRuntimeBridge runtime;
    private final FMLSessionStore  sessionStore;
    private final String           serverVersion;

    public PingTool(FMLRuntimeBridge runtime,
                    FMLSessionStore sessionStore,
                    String serverVersion) {
        this.runtime       = runtime;
        this.sessionStore  = sessionStore;
        this.serverVersion = serverVersion;
    }

    public String execute() {
        try {
            JsonObject result = new JsonObject();
            result.addProperty("status",        "ok");
            result.addProperty("serverVersion", serverVersion);
            result.addProperty("timestamp",
                    DATE_FMT.format(new Date()));
            result.addProperty("activeSessions", sessionStore.size());

            // Runtime status
            boolean runtimeReady = runtime.getServiceManager() != null;
            result.addProperty("runtimeReady", runtimeReady);

            if (runtimeReady) {
                int rcCount = runtime.getServiceManager()
                        .getResourceCenterService()
                        .getResourceCenters().size();
                result.addProperty("resourceCenterCount", rcCount);
            }

            return result.toString();

        } catch (Exception e) {
            logger.severe("ping failed: " + e.getMessage());
            JsonObject err = new JsonObject();
            err.addProperty("status", "error");
            err.addProperty("error",  e.getMessage());
            return err.toString();
        }
    }
}