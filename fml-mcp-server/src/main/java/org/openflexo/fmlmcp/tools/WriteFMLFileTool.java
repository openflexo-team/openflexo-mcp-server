// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\WriteFMLFileTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\WriteFMLFileTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.foundation.resource.FlexoResourceCenterService;

import java.io.File;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code write_fml_file}
 *
 * <p>Writes an FML source string to disk in the directory structure that
 * OpenFlexo requires, then immediately registers the containing directory
 * as a resource center and attempts to load the model to validate it.
 *
 * <p>OpenFlexo requires the following layout for every FML model:
 * <pre>
 *   &lt;rcPath&gt;/
 *     FML/
 *       &lt;modelName&gt;.fml/        ← directory
 *         &lt;modelName&gt;.fml       ← file with same name inside
 * </pre>
 *
 * <p>This tool creates that structure automatically from the supplied
 * {@code rcPath} and {@code modelName}.
 *
 * <p>Input schema:
 * <pre>
 * {
 *   "rcPath":    "C:\\Users\\hayaoumo\\dev\\my-federation",  // required
 *   "modelName": "MyFederation",                             // required
 *   "content":   "use org.openflexo.ta.csv... \n public model MyFederation { ... }"
 * }
 * </pre>
 *
 * <p>Output (success):
 * <pre>
 * {
 *   "written":      true,
 *   "filePath":     "C:\\...\\FML\\MyFederation.fml\\MyFederation.fml",
 *   "fmlResourceURI": "http://...",
 *   "valid":        true,
 *   "conceptCount": 2,
 *   "topLevelBehaviourCount": 1
 * }
 * </pre>
 *
 * <p>Output (written but invalid FML):
 * <pre>
 * {
 *   "written": true,
 *   "filePath": "...",
 *   "valid":   false,
 *   "error":   "VirtualModel is null inside CompilationUnit ..."
 * }
 * </pre>
 *
 * @author Mouad Hayaoui
 */
public class WriteFMLFileTool {

    private static final Logger logger =
            Logger.getLogger(WriteFMLFileTool.class.getName());

    public static final String TOOL_NAME = "write_fml_file";

    public static final String TOOL_DESCRIPTION =
            "Write an FML source string to disk in the correct OpenFlexo directory "
                    + "structure (rcPath/FML/ModelName.fml/ModelName.fml), then register "
                    + "the directory as a resource center and validate the model by loading it. "
                    + "Returns whether the FML was syntactically valid and how many concepts "
                    + "and top-level behaviours were found. If invalid, returns the parse error "
                    + "so you can fix and rewrite. "
                    + "rcPath is the root directory that will become the resource center. "
                    + "modelName is the name of the VirtualModel (no .fml extension). "
                    + "content is the full FML source text.";

    private final FMLRuntimeBridge runtime;

    public WriteFMLFileTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    /**
     * @param rcPath    absolute path to the resource center root directory
     * @param modelName name of the VirtualModel (no .fml extension)
     * @param content   full FML source text
     */
    public String execute(String rcPath, String modelName, String content) {

         if (rcPath == null || rcPath.trim().isEmpty()) {
            return buildError("rcPath must not be empty.");
        }
        if (modelName == null || modelName.trim().isEmpty()) {
            return buildError("modelName must not be empty.");
        }
        if (content == null || content.trim().isEmpty()) {
            return buildError("content must not be empty.");
        }

         modelName = modelName.trim().replace(" ", "_").replace("/", "_").replace("\\", "_");

        try {

            File rcDir      = new File(rcPath.trim());
            File fmlDir     = new File(rcDir, "FML");
            File modelDir   = new File(fmlDir, modelName + ".fml");
            File modelFile  = new File(modelDir, modelName + ".fml");


            if (!modelDir.exists() && !modelDir.mkdirs()) {
                return buildError("Could not create directory: " + modelDir.getAbsolutePath());
            }


            try (java.io.OutputStreamWriter fw = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(modelFile), StandardCharsets.UTF_8)) {
                fw.write(content);
            }
            logger.info("Wrote FML file: " + modelFile.getAbsolutePath());

             runtime.addResourceCenter(rcDir.getAbsolutePath());


            Thread.sleep(300);

            JsonObject result = new JsonObject();
            result.addProperty("written",  true);
            result.addProperty("filePath", modelFile.getAbsolutePath());

            try {
                 VirtualModel vm = loadByNameFallback(modelName);

                result.addProperty("valid",        true);
                result.addProperty("fmlResourceURI", vm.getURI());
                result.addProperty("conceptCount", vm.getFlexoConcepts().size());
                result.addProperty("topLevelBehaviourCount",
                        vm.getFlexoBehaviours().size());

                 JsonArray concepts = new JsonArray();
                vm.getFlexoConcepts().forEach(c -> concepts.add(c.getName()));
                result.add("concepts", concepts);

                JsonArray behaviours = new JsonArray();
                vm.getFlexoBehaviours().forEach(b -> behaviours.add(b.getName()));
                result.add("topLevelBehaviours", behaviours);

                logger.info("write_fml_file: model '" + modelName
                        + "' written and validated successfully.");

            } catch (Exception loadEx) {
                 result.addProperty("valid", false);
                result.addProperty("error", loadEx.getMessage());
                logger.warning("write_fml_file: model written but failed to load: "
                        + loadEx.getMessage());
            }

            return result.toString();

        } catch (IOException e) {
            logger.log(Level.SEVERE, "write_fml_file: IO error", e);
            return buildError("IO error writing file: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "write_fml_file: unexpected error", e);
            return buildError("Unexpected error: " + e.getMessage());
        }
    }


    /**
     * Try to load the model by searching all registered RCs for a
     * CompilationUnitResource whose URI ends with the model name.
     * This is needed because the URI in the file may differ from what
     * we'd construct manually.
     */
    private VirtualModel loadByNameFallback(String modelName) throws Exception {
        // Scan all RCs for a resource matching the model name
        FlexoResourceCenterService rcService = runtime.getServiceManager().getResourceCenterService();
        org.openflexo.foundation.fml.FMLTechnologyAdapter fmlTA = runtime.getFMLTechnologyAdapter();

        for (org.openflexo.foundation.resource.FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
            org.openflexo.foundation.fml.CompilationUnitRepository<?> repo = fmlTA.getVirtualModelRepository(rc);
            if (repo == null) continue;
            for (CompilationUnitResource r : repo.getAllResources()) {
                if (r.getURI() != null && r.getURI().endsWith(modelName + ".fml")) {
                    org.openflexo.foundation.fml.FMLCompilationUnit cu = r.getResourceData();
                    if (cu != null && cu.getVirtualModel() != null) {
                        return cu.getVirtualModel();
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Model '" + modelName + "' not found in any registered resource center "
                        + "after writing. The FML may have parse errors.");
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}