package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: reload_model
 *
 * Forces a reload of a specific FML virtual model from disk.
 * Useful when the .fml file has been edited and you want the
 * runtime to pick up the changes without restarting the server.
 *
 * Note: existing instances created from the old version of the model
 * remain in the session store and may behave unexpectedly after a reload.
 * Call destroy_instance on them before reloading.
 *
 * @author Mouad Hayaoui
 */
public class ReloadModelTool {

    public static final String TOOL_NAME = "reload_model";
    public static final String TOOL_DESCRIPTION =
            "Force a reload of a specific FML virtual model from disk after it "
                    + "has been edited. Existing instances created from the old version "
                    + "should be destroyed before calling this tool.";

    private static final Logger logger =
            Logger.getLogger(ReloadModelTool.class.getName());

    private final FMLRuntimeBridge runtime;

    public ReloadModelTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    public String execute(String fmlResourceURI) {
        if (fmlResourceURI == null || fmlResourceURI.trim().isEmpty()) {
            return buildError("fmlResourceURI must not be empty.");
        }

        try {
             CompilationUnitResource cuResource = (CompilationUnitResource)
                    runtime.getServiceManager()
                            .getResourceManager()
                            .getResource(fmlResourceURI);

            if (cuResource == null) {
                return buildError("No resource found for URI: '" + fmlResourceURI
                        + "'. Make sure the model was loaded at least once first.");
            }

             if (cuResource.isLoaded()) {
                cuResource.unloadResourceData(true);

                FMLVMIFactory.clearCache();
                logger.info("Unloaded resource: " + fmlResourceURI);
            }

             VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);

            logger.info("Reloaded model: " + vm.getName()
                    + " (" + vm.getFlexoConcepts().size() + " concepts)");

            JsonObject result = new JsonObject();
            result.addProperty("reloaded",     true);
            result.addProperty("uri",          fmlResourceURI);
            result.addProperty("name",         vm.getName());
            result.addProperty("conceptCount", vm.getFlexoConcepts().size());
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "reload_model failed for: " + fmlResourceURI, e);
            return buildError(e.getMessage());
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}