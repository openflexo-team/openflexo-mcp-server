package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.CompilationUnitRepository;
import org.openflexo.foundation.fml.FMLTechnologyAdapter;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.foundation.resource.FlexoResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenterService;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: list_resource_centers
 *
 * Returns all currently registered resource centers with their
 * base URI and the count of FML models they contain.
 *
 * @author Mouad Hayaoui
 */
public class ListResourceCentersTool {

    public static final String TOOL_NAME = "list_resource_centers";
    public static final String TOOL_DESCRIPTION =
            "List all currently registered resource centers with their base URI "
                    + "and the number of FML models they contain. Use this to understand "
                    + "what data sources are available to the FML runtime.";

    private static final Logger logger =
            Logger.getLogger(ListResourceCentersTool.class.getName());

    private final FMLRuntimeBridge runtime;

    public ListResourceCentersTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    public String execute() {
        try {
            FMLTechnologyAdapter fmlTA = runtime.getFMLTechnologyAdapter();
            FlexoResourceCenterService rcService =
                    runtime.getServiceManager().getResourceCenterService();

            JsonArray centers = new JsonArray();

            for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
                JsonObject center = new JsonObject();
                center.addProperty("baseURI", rc.getDefaultBaseURI());
                center.addProperty("type",    rc.getClass().getSimpleName()
                        .replace("$", "."));

                CompilationUnitRepository<?> repo =
                        fmlTA.getVirtualModelRepository(rc);
                int modelCount = repo != null ? repo.getAllResources().size() : 0;
                center.addProperty("fmlModelCount", modelCount);

                // List model URIs inline
                JsonArray modelUris = new JsonArray();
                if (repo != null) {
                    for (CompilationUnitResource r : repo.getAllResources()) {
                        modelUris.add(r.getURI());
                    }
                }
                center.add("models", modelUris);
                centers.add(center);
            }

            JsonObject result = new JsonObject();
            result.addProperty("count", centers.size());
            result.add("resourceCenters", centers);
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "list_resource_centers failed", e);
            return buildError(e.getMessage());
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}