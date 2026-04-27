// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\BatchCallBehaviourTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\BatchCallBehaviourTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.fmlmcp.runtime.BehaviourArgumentMapper;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.runtime.FMLSessionStore.SessionEntry;
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
 * MCP tool: {@code batch_call_behaviour}
 *
 * Calls the same named behaviour on a list of handles in a single
 * MCP round-trip, returning per-handle results.  This is useful when
 * iterating over a collection of instances (e.g. convert all Converters,
 * greet all Greeters) without spending N separate tool-call exchanges.
 *
 * Input schema:
 * <pre>
 * {
 *   "handles":       ["inst_aaa", "inst_bbb", ...],   // required, 1–N handles
 *   "behaviourName": "toFahrenheit",                   // required
 *   "arguments":     {}                                // optional, same args applied to all
 * }
 * </pre>
 *
 * Output schema:
 * <pre>
 * {
 *   "behaviourName": "toFahrenheit",
 *   "total":    3,
 *   "succeeded": 2,
 *   "failed":   1,
 *   "results": [
 *     { "handle": "inst_aaa", "concept": "Converter", "result": "Fahrenheit: 212.0" },
 *     { "handle": "inst_bbb", "concept": "Converter", "result": "Fahrenheit: 98.6"  },
 *     { "handle": "inst_ccc", "error":  "Behaviour 'toFahrenheit' not found ..." }
 *   ]
 * }
 * </pre>
 *
 * Execution is sequential (no concurrent FML access).
 * A failure on one handle does not abort the remaining ones.
 *
 * @author Mouad Hayaoui
 */
public class BatchCallBehaviourTool {

    private static final Logger logger =
            Logger.getLogger(BatchCallBehaviourTool.class.getName());

    public static final String TOOL_NAME = "batch_call_behaviour";
    public static final String TOOL_DESCRIPTION =
            "Execute the same named FML behaviour on a list of instance handles "
                    + "in a single call. Accepts an array of handles, a behaviour name, "
                    + "and an optional arguments object applied identically to every handle. "
                    + "Returns per-handle results; a failure on one handle does not abort "
                    + "the remaining ones.";

    private final FMLSessionStore  sessionStore;
    private final FMLRuntimeBridge runtime;

    public BatchCallBehaviourTool(FMLSessionStore sessionStore, FMLRuntimeBridge runtime) {
        this.sessionStore = sessionStore;
        this.runtime      = runtime;
    }

    /**
     * @param handlesJson   JSON array of handle strings, e.g. {@code ["inst_aaa","inst_bbb"]}
     * @param behaviourName name of the ActionScheme to invoke
     * @param argumentsJson shared arguments JSON object (may be null / empty / "{}")
     */
    public String execute(String handlesJson, String behaviourName, String argumentsJson) {

         if (behaviourName == null || behaviourName.trim().isEmpty()) {
            return buildError("behaviourName must not be empty.");
        }

        if (handlesJson == null || handlesJson.trim().isEmpty()) {
            return buildError("handles must be a non-empty JSON array of handle strings.");
        }

        JsonArray handlesArray;
        try {
            JsonElement el = JsonParser.parseString(handlesJson);
            if (!el.isJsonArray()) {
                return buildError("handles must be a JSON array, got: " + handlesJson);
            }
            handlesArray = el.getAsJsonArray();
        } catch (Exception e) {
            return buildError("Could not parse handles as JSON array: " + e.getMessage());
        }

        if (handlesArray.size() == 0) {
            return buildError("handles array must not be empty.");
        }

         JsonObject sharedJsonArgs = null;
        if (argumentsJson != null && !argumentsJson.trim().isEmpty()
                && !argumentsJson.trim().equals("{}")) {
            try {
                sharedJsonArgs = JsonParser.parseString(argumentsJson).getAsJsonObject();
            } catch (Exception e) {
                return buildError("Could not parse arguments as JSON object: " + e.getMessage());
            }
        }

         JsonArray results = new JsonArray();
        int succeeded = 0;
        int failed    = 0;

        for (JsonElement handleEl : handlesArray) {
            String handle = handleEl.isJsonNull() ? null : handleEl.getAsString();
            JsonObject itemResult = new JsonObject();
            itemResult.addProperty("handle", handle == null ? "<null>" : handle);

            if (handle == null || handle.trim().isEmpty()) {
                itemResult.addProperty("error", "handle must not be null or empty.");
                results.add(itemResult);
                failed++;
                continue;
            }

            SessionEntry entry = sessionStore.getEntry(handle);
            if (entry == null) {
                itemResult.addProperty("error",
                        "Unknown handle: '" + handle + "'. Did you call create_instance first?");
                results.add(itemResult);
                failed++;
                continue;
            }

            itemResult.addProperty("concept", entry.conceptName);

            FlexoConceptInstance fci = entry.instance;
            FlexoBehaviour behaviour = fci.getFlexoConcept().getFlexoBehaviour(behaviourName);

            if (behaviour == null) {
                itemResult.addProperty("error",
                        "Behaviour '" + behaviourName + "' not found on concept '"
                                + fci.getFlexoConcept().getName() + "'");
                results.add(itemResult);
                failed++;
                continue;
            }

            if (!(behaviour instanceof ActionScheme)) {
                itemResult.addProperty("error",
                        "Behaviour '" + behaviourName
                                + "' is not an ActionScheme (type: "
                                + behaviour.getClass().getSimpleName() + ").");
                results.add(itemResult);
                failed++;
                continue;
            }

            ActionScheme actionScheme = (ActionScheme) behaviour;

            // Re-map arguments for each instance (same JSON object, fresh map)
            Map<String, Object> args = BehaviourArgumentMapper.map(actionScheme, sharedJsonArgs);

            try {
                FlexoEditor editor =
                        new DefaultFlexoEditor(null, runtime.getServiceManager());

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
                    itemResult.addProperty("error",
                            "ActionScheme '" + behaviourName + "' execution did not succeed.");
                    results.add(itemResult);
                    failed++;
                    continue;
                }

                Object returnValue = action.getReturnedValue();
                String serialized  = ResultSerializer.serialize(returnValue);


                try {
                    JsonObject parsed = JsonParser.parseString(serialized).getAsJsonObject();
                    if (parsed.has("result")) {
                        itemResult.add("result", parsed.get("result"));
                    } else {
                        itemResult.addProperty("result", serialized);
                    }
                } catch (Exception e) {
                    itemResult.addProperty("result", serialized);
                }

                logger.info("batch_call_behaviour: '" + behaviourName
                        + "' on handle '" + handle + "' succeeded.");
                succeeded++;

            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "batch_call_behaviour: '" + behaviourName
                                + "' on handle '" + handle + "' threw exception", e);
                itemResult.addProperty("error", "Execution failed: " + e.getMessage());
                failed++;
            }

            results.add(itemResult);
        }

         JsonObject response = new JsonObject();
        response.addProperty("behaviourName", behaviourName);
        response.addProperty("total",     handlesArray.size());
        response.addProperty("succeeded", succeeded);
        response.addProperty("failed",    failed);
        response.add("results", results);

        return response.toString();
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}