// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\ExecuteWorkflowTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\ExecuteWorkflowTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.fmlmcp.runtime.BehaviourArgumentMapper;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.ResultSerializer;
import org.openflexo.foundation.DefaultFlexoEditor;
import org.openflexo.foundation.FlexoEditor;
import org.openflexo.foundation.fml.ActionScheme;
import org.openflexo.foundation.fml.FlexoBehaviour;
import org.openflexo.foundation.fml.FlexoBehaviourParameter;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rt.FMLRTVirtualModelInstance;
import org.openflexo.foundation.fml.rt.action.ActionSchemeAction;
import org.openflexo.foundation.fml.rt.action.ActionSchemeActionFactory;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code execute_workflow}
 *
 * <p>Executes a top-level {@link ActionScheme} behaviour declared directly
 * on a {@link VirtualModel}, as opposed to {@code call_behaviour} which
 * targets behaviours on a {@link org.openflexo.foundation.fml.FlexoConcept}
 * instance.
 *
 * <p>The distinction matters because FML allows behaviours to be owned by
 * the VirtualModel itself (not by any concept inside it).  These top-level
 * behaviours act on the VirtualModel instance as a whole — they can iterate
 * over all concept instances, orchestrate multi-step operations, etc.
 *
 * <p>Internally the tool:
 * <ol>
 *   <li>Loads the VirtualModel identified by {@code fmlResourceURI}.</li>
 *   <li>Gets or creates a transient {@link FMLRTVirtualModelInstance} (VMI)
 *       for that model via {@link FMLVMIFactory}.  The VMI is the runtime
 *       receiver for top-level behaviours.</li>
 *   <li>Looks up the named {@link ActionScheme} on the VirtualModel.</li>
 *   <li>Executes it via {@link ActionSchemeAction}, passing any supplied
 *       arguments.</li>
 * </ol>
 *
 * <p>The VMI handle is returned in the response so the caller can later call
 * {@code call_behaviour} on concept instances that were created inside the
 * workflow, or pass the VMI handle to other tools.
 *
 * <p>Input schema:
 * <pre>
 * {
 *   "fmlResourceURI": "http://fmlmcp.test/FML/MyModel.fml",  // required
 *   "behaviourName":  "myWorkflow",                           // required
 *   "arguments":      { "param1": "value1" }                  // optional
 * }
 * </pre>
 *
 * <p>Output schema (success):
 * <pre>
 * {
 *   "behaviourName":    "myWorkflow",
 *   "virtualModelURI":  "http://fmlmcp.test/FML/MyModel.fml",
 *   "virtualModelName": "MyModel",
 *   "result":           &lt;serialized return value, or null&gt;
 * }
 * </pre>
 *
 * @author Mouad Hayaoui
 */
public class ExecuteWorkflowTool {

    private static final Logger logger =
            Logger.getLogger(ExecuteWorkflowTool.class.getName());

    public static final String TOOL_NAME = "execute_workflow";

    public static final String TOOL_DESCRIPTION =
            "Execute a top-level ActionScheme behaviour declared directly on a "
                    + "VirtualModel (not on a concept inside it). "
                    + "Use this when the FML model exposes orchestration methods at the "
                    + "model level rather than on individual concept instances. "
                    + "The behaviour is executed against the VirtualModel instance as a whole. "
                    + "Supply 'arguments' as a JSON object if the behaviour has parameters. "
                    + "Returns the serialized return value of the behaviour, or null for void behaviours.";

    private final FMLRuntimeBridge runtime;

    public ExecuteWorkflowTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    /**
     * @param fmlResourceURI URI of the VirtualModel that owns the behaviour
     * @param behaviourName  name of the top-level ActionScheme to invoke
     * @param argumentsJson  optional JSON object of argument values
     */
    public String execute(String fmlResourceURI,
                          String behaviourName,
                          String argumentsJson) {
         if (fmlResourceURI == null || fmlResourceURI.trim().isEmpty()) {
            return buildError("fmlResourceURI must not be empty.");
        }
        if (behaviourName == null || behaviourName.trim().isEmpty()) {
            return buildError("behaviourName must not be empty.");
        }

        try {
             VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);

            @SuppressWarnings("deprecation")
            FlexoBehaviour behaviour = vm.getFlexoBehaviour(behaviourName);

            if (behaviour == null) {
                return buildError("Behaviour '" + behaviourName
                        + "' not found as a top-level behaviour on VirtualModel '"
                        + vm.getName() + "'. "
                        + "Top-level behaviours available: "
                        + listTopLevelActionSchemeNames(vm));
            }

            if (!(behaviour instanceof ActionScheme)) {
                return buildError("Behaviour '" + behaviourName
                        + "' is not an ActionScheme (type: "
                        + behaviour.getClass().getSimpleName() + "). "
                        + "Only ActionScheme is supported by execute_workflow.");
            }

            ActionScheme actionScheme = (ActionScheme) behaviour;

             JsonObject jsonArgs = null;
            if (argumentsJson != null && !argumentsJson.trim().isEmpty()
                    && !argumentsJson.trim().equals("{}")) {
                try {
                    jsonArgs = JsonParser.parseString(argumentsJson).getAsJsonObject();
                } catch (Exception e) {
                    return buildError("Could not parse arguments as JSON object: "
                            + e.getMessage());
                }
            }

            Map<String, Object> args = BehaviourArgumentMapper.map(actionScheme, jsonArgs);


            FMLRTVirtualModelInstance vmi = FMLVMIFactory.getOrCreateVMI(vm, runtime);

             FlexoEditor editor =
                    new DefaultFlexoEditor(null, runtime.getServiceManager());

            ActionSchemeActionFactory factory =
                    new ActionSchemeActionFactory(actionScheme, vmi);
            ActionSchemeAction action = factory.makeNewAction(vmi, null, editor);

            for (FlexoBehaviourParameter param : actionScheme.getParameters()) {
                Object value = args.get(param.getName());
                if (value != null) {
                    action.setParameterValue(param, value);
                }
            }

            action.doAction();

            if (!action.hasActionExecutionSucceeded()) {
                return buildError("Behaviour '" + behaviourName
                        + "' execution did not succeed on VirtualModel '"
                        + vm.getName() + "'.");
            }

            Object returnValue = action.getReturnedValue();
            logger.info("execute_workflow: '" + behaviourName
                    + "' on VirtualModel '" + vm.getName() + "' succeeded.");


            String serialized = ResultSerializer.serialize(returnValue);
            JsonObject response = new JsonObject();
            response.addProperty("behaviourName",    behaviourName);
            response.addProperty("virtualModelURI",  fmlResourceURI);
            response.addProperty("virtualModelName", vm.getName());

            try {
                JsonObject parsed = JsonParser.parseString(serialized).getAsJsonObject();
                if (parsed.has("result")) {
                    response.add("result", parsed.get("result"));
                } else {
                    response.addProperty("result", serialized);
                }
            } catch (Exception e) {
                // ResultSerializer didn't return a JSON object — store raw string
                response.addProperty("result", serialized);
            }

            return response.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "execute_workflow failed for behaviour '" + behaviourName
                            + "' on '" + fmlResourceURI + "'", e);
            return buildError("Internal error: " + e.getMessage());
        }
    }


    /**
     * Returns a comma-separated list of ActionScheme names declared directly
     * on the VirtualModel, to help the caller when a behaviour is not found.
     */
    private String listTopLevelActionSchemeNames(VirtualModel vm) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (FlexoBehaviour b : vm.getFlexoBehaviours()) {
            if (b instanceof ActionScheme) {
                if (!first) sb.append(", ");
                sb.append(b.getName());
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}
