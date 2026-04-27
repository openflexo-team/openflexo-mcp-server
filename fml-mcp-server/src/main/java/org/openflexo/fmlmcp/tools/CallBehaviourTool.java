package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.fmlmcp.runtime.BehaviourArgumentMapper;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.ResultSerializer;
import org.openflexo.foundation.DefaultFlexoEditor;
import org.openflexo.foundation.FlexoEditor;
import org.openflexo.foundation.fml.ActionScheme;
import org.openflexo.foundation.fml.FlexoBehaviour;
import org.openflexo.foundation.fml.FlexoBehaviourParameter;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.foundation.fml.rt.action.ActionSchemeAction;
import org.openflexo.foundation.fml.rt.action.ActionSchemeActionFactory;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code call_behaviour}
 *
 * @author Mouad Hayaoui
 */
public class CallBehaviourTool {

    private static final Logger logger = Logger.getLogger(CallBehaviourTool.class.getName());

    public static final String TOOL_NAME        = "call_behaviour";
    public static final String TOOL_DESCRIPTION =
            "Execute a named FML behaviour on a previously created instance. "
                    + "Pass the handle returned by create_instance.";

    private final FMLSessionStore  sessionStore;
    private final FMLRuntimeBridge runtime;

    public CallBehaviourTool(FMLSessionStore sessionStore, FMLRuntimeBridge runtime) {
        this.sessionStore = sessionStore;
        this.runtime      = runtime;
    }

    public String execute(String handle, String behaviourName, String argumentsJson) {
        FlexoConceptInstance fci = sessionStore.get(handle);
        if (fci == null) {
            return buildError("Unknown handle: '" + handle
                    + "'. Did you call create_instance first?");
        }

        FlexoBehaviour behaviour = fci.getFlexoConcept().getFlexoBehaviour(behaviourName);
        if (behaviour == null) {
            return buildError("Behaviour '" + behaviourName
                    + "' not found on concept '"
                    + fci.getFlexoConcept().getName() + "'");
        }


        if (!(behaviour instanceof ActionScheme)) {
            return buildError("Behaviour '" + behaviourName
                    + "' is not an ActionScheme (type: "
                    + behaviour.getClass().getSimpleName() + "). "
                    + "Only ActionScheme is supported by call_behaviour.");
        }
        ActionScheme actionScheme = (ActionScheme) behaviour;

        JsonObject jsonArgs = null;
        if (argumentsJson != null && !argumentsJson.trim().isEmpty()
                && !argumentsJson.trim().equals("{}")) {
            try {
                jsonArgs = JsonParser.parseString(argumentsJson).getAsJsonObject();
            } catch (Exception e) {
                return buildError("Could not parse arguments JSON: " + e.getMessage());
            }
        }
        Map<String, Object> args = BehaviourArgumentMapper.map(actionScheme, jsonArgs);

         try {
            FlexoEditor editor = new DefaultFlexoEditor(null, runtime.getServiceManager());

            ActionSchemeActionFactory factory =
                    new ActionSchemeActionFactory(actionScheme, fci);
            ActionSchemeAction action = factory.makeNewAction(fci, null, editor);

            for (FlexoBehaviourParameter param : actionScheme.getParameters()) {
                Object value = args.get(param.getName());
                if (value != null) {
                    action.setParameterValue(param, value);
                }
            }

            action.doAction();

            if (!action.hasActionExecutionSucceeded()) {
                return buildError("ActionScheme '" + behaviourName + "' execution did not succeed.");
            }

            Object result = action.getReturnedValue();
            logger.info("Behaviour '" + behaviourName + "' on handle '" + handle + "' succeeded.");
            return ResultSerializer.serialize(result);

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Behaviour '" + behaviourName + "' execution failed on handle '" + handle + "'", e);
            return buildError("Execution failed: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}