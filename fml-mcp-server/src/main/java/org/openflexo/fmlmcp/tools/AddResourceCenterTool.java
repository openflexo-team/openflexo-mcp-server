package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.CompilationUnitRepository;
import org.openflexo.foundation.fml.FMLTechnologyAdapter;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.foundation.resource.FlexoResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenterService;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: add_resource_center
 *
 * Registers a new filesystem directory as an OpenFlexo resource center
 * at runtime without restarting the server. Any FML models, CSV files,
 * or other technology-specific resources inside the directory become
 * immediately accessible to the FML runtime.
 *
 * @author Mouad Hayaoui
 */
public class AddResourceCenterTool {

    public static final String TOOL_NAME = "add_resource_center";
    public static final String TOOL_DESCRIPTION =
            "Register a new filesystem directory as a resource center at runtime. "
                    + "Any FML models or data files inside the directory become immediately "
                    + "accessible without restarting the server. "
                    + "Provide an absolute path to the directory.";

    private static final Logger logger =
            Logger.getLogger(AddResourceCenterTool.class.getName());

    private final FMLRuntimeBridge runtime;

    public AddResourceCenterTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    public String execute(String path) {
        if (path == null || path.trim().isEmpty()) {
            return buildError("path must not be empty.");
        }

        File dir = new File(path.trim());

        if (!dir.exists()) {
            return buildError("Directory does not exist: '" + path + "'.");
        }
        if (!dir.isDirectory()) {
            return buildError("Path is not a directory: '" + path + "'.");
        }

         FlexoResourceCenterService rcService =
                runtime.getServiceManager().getResourceCenterService();
        for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
            if (rc instanceof org.openflexo.foundation.resource.DirectoryResourceCenter) {
                org.openflexo.foundation.resource.DirectoryResourceCenter drc =
                        (org.openflexo.foundation.resource.DirectoryResourceCenter) rc;
                if (drc.getRootDirectory() != null
                        && drc.getRootDirectory().getAbsolutePath()
                        .equals(dir.getAbsolutePath())) {
                    return buildAlreadyRegistered(path, rcService, dir);
                }
            }
        }

        try {
            runtime.addResourceCenter(path);

             FMLTechnologyAdapter fmlTA = runtime.getFMLTechnologyAdapter();
            JsonArray models = new JsonArray();

            for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
                if (rc instanceof org.openflexo.foundation.resource.DirectoryResourceCenter) {
                    org.openflexo.foundation.resource.DirectoryResourceCenter drc =
                            (org.openflexo.foundation.resource.DirectoryResourceCenter) rc;
                    if (drc.getRootDirectory() != null
                            && drc.getRootDirectory().getAbsolutePath()
                            .equals(dir.getAbsolutePath())) {
                        CompilationUnitRepository<?> repo =
                                fmlTA.getVirtualModelRepository(rc);
                        if (repo != null) {
                            for (CompilationUnitResource r : repo.getAllResources()) {
                                models.add(r.getURI());
                            }
                        }
                        break;
                    }
                }
            }

            logger.info("Registered resource center: " + path
                    + " — found " + models.size() + " FML model(s)");

            JsonObject result = new JsonObject();
            result.addProperty("registered",  true);
            result.addProperty("path",        dir.getAbsolutePath());
            result.addProperty("baseURI",     dir.toURI().toString());
            result.addProperty("fmlModels",   models.size());
            result.add("discoveredModels",    models);
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "add_resource_center failed for: " + path, e);
            return buildError(e.getMessage());
        }
    }

    private String buildAlreadyRegistered(String path,
                                          FlexoResourceCenterService rcService,
                                          File dir) {
        FMLTechnologyAdapter fmlTA = runtime.getFMLTechnologyAdapter();
        JsonArray models = new JsonArray();
        for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
            if (rc instanceof org.openflexo.foundation.resource.DirectoryResourceCenter) {
                org.openflexo.foundation.resource.DirectoryResourceCenter drc =
                        (org.openflexo.foundation.resource.DirectoryResourceCenter) rc;
                if (drc.getRootDirectory() != null
                        && drc.getRootDirectory().getAbsolutePath()
                        .equals(dir.getAbsolutePath())) {
                    CompilationUnitRepository<?> repo =
                            fmlTA.getVirtualModelRepository(rc);
                    if (repo != null) {
                        for (CompilationUnitResource r : repo.getAllResources()) {
                            models.add(r.getURI());
                        }
                    }
                    break;
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("registered",       false);
        result.addProperty("alreadyRegistered", true);
        result.addProperty("path",             dir.getAbsolutePath());
        result.addProperty("fmlModels",        models.size());
        result.add("discoveredModels",         models);
        return result.toString();
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}