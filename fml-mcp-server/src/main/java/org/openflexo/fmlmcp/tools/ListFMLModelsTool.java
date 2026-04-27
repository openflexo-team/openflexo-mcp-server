package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FMLTechnologyAdapter;
import org.openflexo.foundation.fml.CompilationUnitRepository;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.foundation.resource.FlexoResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenterService;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.util.logging.Logger;

/**
 * MCP tool: list_fml_models
 *
 * Scans all registered resource centers and returns the URI,
 * name, and concept count of every available FML virtual model.
 * This lets the client discover what models are available without
 * needing to know URIs upfront.
 *
 * @author Mouad Hayaoui
 */
public class ListFMLModelsTool {

    private static final Logger logger = Logger.getLogger(ListFMLModelsTool.class.getName());

    private final FMLRuntimeBridge runtime;
    public static final String TOOL_NAME        = "list_fml_models";

    public static final String TOOL_DESCRIPTION =
            "Scan all registered resource centers and return the URI, name, and concept count of every available FML virtual model. "
                    + "This lets the connected client discover what models are available without needing to know URIs upfront.";

    public ListFMLModelsTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    public String execute() {
        try {
            FMLTechnologyAdapter fmlTA = runtime.getFMLTechnologyAdapter();
            FlexoResourceCenterService rcService =
                    runtime.getServiceManager().getResourceCenterService();

            JsonArray models = new JsonArray();

            for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
                CompilationUnitRepository<?> repo =
                        fmlTA.getVirtualModelRepository(rc);

                if (repo == null) continue;

                for (CompilationUnitResource resource : repo.getAllResources()) {
                    JsonObject model = new JsonObject();
                    model.addProperty("uri",  resource.getURI());
                    model.addProperty("name", resource.getName());
                    model.addProperty("resourceCenter",
                            rc.getDefaultBaseURI());


                    try {
                        if (resource.isLoaded()) {
                            int conceptCount = resource.getLoadedResourceData()
                                    .getVirtualModel()
                                    .getFlexoConcepts()
                                    .size();
                            model.addProperty("conceptCount", conceptCount);
                        } else {
                            model.addProperty("conceptCount", -1);
                            model.addProperty("note",
                                    "not yet loaded — call load_fml_file to inspect");
                        }
                    } catch (Exception e) {
                        model.addProperty("conceptCount", -1);
                    }

                    models.add(model);
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("count", models.size());
            result.add("models", models);
            return result.toString();

        } catch (Exception e) {
            logger.severe("list_fml_models failed: " + e.getMessage());
            JsonObject err = new JsonObject();
            err.addProperty("error", e.getMessage());
            return err.toString();
        }
    }
}