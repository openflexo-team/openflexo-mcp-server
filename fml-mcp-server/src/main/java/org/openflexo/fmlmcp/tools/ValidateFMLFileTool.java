// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\ValidateFMLFileTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\ValidateFMLFileTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FlexoBehaviour;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.foundation.technologyadapter.ModelSlot;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code validate_fml_file}
 *
 * <p>Attempts to load a previously written FML model and reports whether it
 * is syntactically and structurally valid. Unlike {@code load_fml_file}
 * which silently returns 0 concepts on parse failure, this tool always
 * explains what went wrong.
 *
 * <p>On success it returns a full structural summary — model slots, concepts,
 * properties, and behaviours — so Claude can confirm the model structure
 * matches what was intended before proceeding to {@code create_instance}.
 *
 * <p>On failure it returns the error message from the FML parser/loader,
 * which Claude can use to identify and fix the problem in the FML source
 * before calling {@code write_fml_file} again.
 *
 * <p>Input schema:
 * <pre>
 * {
 *   "fmlResourceURI": "http://fmlmcp.test/FML/MyFederation.fml"  // required
 * }
 * </pre>
 *
 * <p>Output (valid):
 * <pre>
 * {
 *   "valid": true,
 *   "name":  "MyFederation",
 *   "uri":   "http://fmlmcp.test/FML/MyFederation.fml",
 *   "modelSlots": [{"name":"data","type":"CSVModelSlot"}],
 *   "concepts": [{"name":"Event","propertyCount":3,"behaviourCount":2}],
 *   "topLevelBehaviours": [{"name":"run","paramCount":0}]
 * }
 * </pre>
 *
 * <p>Output (invalid):
 * <pre>
 * {
 *   "valid": false,
 *   "error": "VirtualModel is null inside CompilationUnit ...",
 *   "hint":  "Check @URI declaration, model slot imports, and syntax."
 * }
 * </pre>
 *
 * @author Mouad Hayaoui
 */
public class ValidateFMLFileTool {

    private static final Logger logger =
            Logger.getLogger(ValidateFMLFileTool.class.getName());

    public static final String TOOL_NAME = "validate_fml_file";

    public static final String TOOL_DESCRIPTION =
            "Validate an FML model that was previously written to disk. "
                    + "Unlike load_fml_file which silently returns 0 concepts on failure, "
                    + "this tool always reports what went wrong with a diagnostic message. "
                    + "On success it returns the full model structure: model slots, concepts, "
                    + "properties, and top-level behaviours. "
                    + "Use this after write_fml_file to confirm the FML is syntactically correct "
                    + "before proceeding to create_instance. "
                    + "If validation fails, fix the FML and call write_fml_file again.";

    private final FMLRuntimeBridge runtime;

    public ValidateFMLFileTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    /**
     * @param fmlResourceURI URI of the FML model to validate
     */
    public String execute(String fmlResourceURI) {

        if (fmlResourceURI == null || fmlResourceURI.trim().isEmpty()) {
            return buildError("fmlResourceURI must not be empty.", null);
        }

        try {

            CompilationUnitResource cuResource = (CompilationUnitResource)
                    runtime.getServiceManager()
                            .getResourceManager()
                            .getResource(fmlResourceURI);

            if (cuResource != null && cuResource.isLoaded()) {
                cuResource.unloadResourceData(true);
                FMLVMIFactory.clearCache();
                logger.fine("validate_fml_file: unloaded cached version of "
                        + fmlResourceURI);
            }

             VirtualModel vm;
            try {
                vm = runtime.loadVirtualModel(fmlResourceURI);
            } catch (Exception loadEx) {
                return buildError(loadEx.getMessage(),
                        "Check: @URI declaration matches the file path, "
                                + "all 'use' imports are available on the classpath, "
                                + "model slot names match their TA declarations, "
                                + "and there are no syntax errors in behaviour bodies.");
            }

             if (vm.getFlexoConcepts().isEmpty()
                    && vm.getFlexoBehaviours().isEmpty()
                    && vm.getModelSlots().isEmpty()) {
                return buildError(
                        "Model loaded but appears empty (0 concepts, 0 behaviours, "
                                + "0 model slots). This usually means the FML parser "
                                + "encountered a syntax error but did not throw — check "
                                + "the server logs for parser warnings.",
                        "Common causes: missing semicolon, wrong keyword (use 'public model' "
                                + "not 'model'), Unicode characters in string literals, "
                                + "or a missing closing brace.");
            }

             JsonObject result = new JsonObject();
            result.addProperty("valid", true);
            result.addProperty("name",  vm.getName());
            result.addProperty("uri",   vm.getURI());

             JsonArray slots = new JsonArray();
            for (ModelSlot<?, ?> slot : vm.getModelSlots()) {
                JsonObject s = new JsonObject();
                s.addProperty("name", slot.getName());
                s.addProperty("type", slot.getClass().getSimpleName());
                s.addProperty("required", slot.getIsRequired());
                slots.add(s);
            }
            result.add("modelSlots", slots);

             JsonArray vmProps = new JsonArray();
            for (FlexoProperty<?> prop : vm.getDeclaredProperties()) {
                if (!(prop instanceof ModelSlot)) {
                    JsonObject p = new JsonObject();
                    p.addProperty("name", prop.getName());
                    p.addProperty("type", prop.getResultingType() != null
                            ? prop.getResultingType().getTypeName() : "unknown");
                    vmProps.add(p);
                }
            }
            result.add("vmProperties", vmProps);

             JsonArray topBehaviours = new JsonArray();
            for (FlexoBehaviour beh : vm.getFlexoBehaviours()) {
                JsonObject b = new JsonObject();
                b.addProperty("name",       beh.getName());
                b.addProperty("type",       beh.getClass().getSimpleName());
                b.addProperty("paramCount", beh.getParameters().size());

                JsonArray params = new JsonArray();
                beh.getParameters().forEach(p -> {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("name", p.getName());
                    pObj.addProperty("type", p.getType() != null
                            ? p.getType().getTypeName() : "unknown");
                    params.add(pObj);
                });
                b.add("parameters", params);
                topBehaviours.add(b);
            }
            result.add("topLevelBehaviours", topBehaviours);

             JsonArray concepts = new JsonArray();
            for (FlexoConcept concept : vm.getFlexoConcepts()) {
                JsonObject c = new JsonObject();
                c.addProperty("name",          concept.getName());
                c.addProperty("propertyCount", concept.getDeclaredProperties().size());
                c.addProperty("behaviourCount",concept.getFlexoBehaviours().size());

                JsonArray cProps = new JsonArray();
                concept.getDeclaredProperties().forEach(p -> {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("name", p.getName());
                    pObj.addProperty("type", p.getResultingType() != null
                            ? p.getResultingType().getTypeName() : "unknown");
                    cProps.add(pObj);
                });
                c.add("properties", cProps);

                JsonArray cBehaviours = new JsonArray();
                concept.getFlexoBehaviours().forEach(b -> {
                    JsonObject bObj = new JsonObject();
                    bObj.addProperty("name", b.getName());
                    bObj.addProperty("type", b.getClass().getSimpleName());
                    cBehaviours.add(bObj);
                });
                c.add("behaviours", cBehaviours);

                concepts.add(c);
            }
            result.add("concepts", concepts);

            logger.info("validate_fml_file: '" + vm.getName() + "' is valid — "
                    + vm.getFlexoConcepts().size() + " concepts, "
                    + vm.getFlexoBehaviours().size() + " top-level behaviours, "
                    + vm.getModelSlots().size() + " model slots.");

            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "validate_fml_file unexpected error for: " + fmlResourceURI, e);
            return buildError("Unexpected error: " + e.getMessage(), null);
        }
    }


    private String buildError(String message, String hint) {
        JsonObject err = new JsonObject();
        err.addProperty("valid", false);
        err.addProperty("error", message);
        if (hint != null) {
            err.addProperty("hint", hint);
        }
        return err.toString();
    }
}