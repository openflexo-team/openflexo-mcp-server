package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.foundation.fml.FlexoBehaviour;
import org.openflexo.foundation.fml.FlexoBehaviourParameter;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.VirtualModel;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code load_fml_file}
 *
 * <p>Loads a {@code .fml} virtual model resource into the OpenFlexo runtime
 * and returns a description of its concepts and behaviours
 * <p>Input schema:
 * <pre>
 * {
 *   "fmlResourceURI": "http://my.rc/FML/HandMeasurementProfessional.fml"
 * }
 * </pre>
 *
 * <p>Output (JSON string):
 * <pre>
 * {
 *   "virtualModel": "HandMeasurementProfessional",
 *   "concepts": [ { "name": "...", "behaviours": [ ... ] } ],
 *   "topLevelBehaviours": [ { "name": "...", "parameters": [ ... ] } ]
 * }
 * </pre>
 *
 * @author Mouad Hayaoui
 */
public class LoadFMLFileTool {

    private static final Logger logger = Logger.getLogger(LoadFMLFileTool.class.getName());

    public static final String TOOL_NAME        = "load_fml_file";
    public static final String TOOL_DESCRIPTION =
            "Load a .fml VirtualModel resource into the FML runtime. "
            + "Returns the list of concepts and callable behaviours so you know "
            + "what to create/call next.";

    private final FMLRuntimeBridge runtime;

    public LoadFMLFileTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }


    public String execute(String fmlResourceURI) {
        try {
            VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);
            return buildSuccessResponse(vm);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load FML file: " + fmlResourceURI, e);
            return buildErrorResponse(fmlResourceURI, e);
        }
    }



    private String buildSuccessResponse(VirtualModel vm) {
        JsonObject root = new JsonObject();
        root.addProperty("virtualModel", vm.getName());
        root.addProperty("uri", vm.getURI());

        JsonArray topBehaviours = new JsonArray();
        for (FlexoBehaviour behaviour : vm.getFlexoBehaviours()) {
            topBehaviours.add(describeBehaviour(behaviour));
        }
        root.add("topLevelBehaviours", topBehaviours);

        JsonArray concepts = new JsonArray();
        for (FlexoConcept concept : vm.getFlexoConcepts()) {
            JsonObject cObj = new JsonObject();
            cObj.addProperty("name", concept.getName());

            JsonArray cBehaviours = new JsonArray();
            for (FlexoBehaviour b : concept.getFlexoBehaviours()) {
                cBehaviours.add(describeBehaviour(b));
            }
            cObj.add("behaviours", cBehaviours);
            concepts.add(cObj);
        }
        root.add("concepts", concepts);

        return root.toString();
    }

    private JsonObject describeBehaviour(FlexoBehaviour behaviour) {
        JsonObject bObj = new JsonObject();
        bObj.addProperty("name", behaviour.getName());
        bObj.addProperty("type", behaviour.getClass().getSimpleName());

        JsonArray params = new JsonArray();
        for (FlexoBehaviourParameter p : behaviour.getParameters()) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", p.getName());
            pObj.addProperty("type", p.getType() != null ? p.getType().toString() : "unknown");
            params.add(pObj);
        }
        bObj.add("parameters", params);
        return bObj;
    }

    private String buildErrorResponse(String uri, Exception e) {
        JsonObject err = new JsonObject();
        err.addProperty("error", "Failed to load FML file");
        err.addProperty("uri", uri);
        err.addProperty("message", e.getMessage());
        return err.toString();
    }
}
