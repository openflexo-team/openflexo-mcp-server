package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.FlexoBehaviour;
import org.openflexo.foundation.fml.FlexoBehaviourParameter;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.FlexoProperty;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: describe_concept
 *
 * Returns the full property and behaviour schema of a named concept,
 * including all inherited members from parent concepts.
 *
 * @author Mouad Hayaoui
 */
public class DescribeConceptTool {

    public static final String TOOL_NAME = "describe_concept";
    public static final String TOOL_DESCRIPTION =
            "Return the full property and behaviour schema of a named concept "
                    + "within a virtual model, including all inherited members. "
                    + "Use this before create_instance to understand what fields "
                    + "and operations the concept exposes.";

    private static final Logger logger =
            Logger.getLogger(DescribeConceptTool.class.getName());

    private final FMLRuntimeBridge runtime;

    public DescribeConceptTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    public String execute(String fmlResourceURI, String conceptName) {
        if (fmlResourceURI == null || fmlResourceURI.trim().isEmpty()) {
            return buildError("fmlResourceURI must not be empty.");
        }
        if (conceptName == null || conceptName.trim().isEmpty()) {
            return buildError("conceptName must not be empty.");
        }

        try {
            VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);

            FlexoConcept concept = vm.getFlexoConcept(conceptName);
            if (concept == null) {
                return buildError("Concept '" + conceptName
                        + "' not found in model '" + vm.getName() + "'.");
            }

            JsonObject result = new JsonObject();
            result.addProperty("concept",         concept.getName());
            result.addProperty("virtualModelURI", fmlResourceURI);

             JsonArray chain = new JsonArray();
            for (FlexoConcept parent : concept.getParentFlexoConcepts()) {
                chain.add(parent.getName());
            }
            result.add("parentConcepts", chain);

            Map<String, JsonObject> propertyMap = new LinkedHashMap<>();
            collectProperties(concept, propertyMap);
            JsonArray properties = new JsonArray();
            for (Map.Entry<String, JsonObject> e : propertyMap.entrySet()) {
                properties.add(e.getValue());
            }
            result.add("properties", properties);

             Map<String, JsonObject> behaviourMap = new LinkedHashMap<>();
            collectBehaviours(concept, behaviourMap);
            JsonArray behaviours = new JsonArray();
            for (Map.Entry<String, JsonObject> e : behaviourMap.entrySet()) {
                behaviours.add(e.getValue());
            }
            result.add("behaviours", behaviours);

            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "describe_concept failed", e);
            return buildError(e.getMessage());
        }
    }

    /**
     * Recursively collect properties walking up the inheritance chain.
     * Parent properties are added first; child properties override by name.
     */
    private void collectProperties(FlexoConcept concept, Map<String, JsonObject> map) {
         for (FlexoProperty<?> prop : concept.getAccessibleProperties()) {
            JsonObject p = new JsonObject();
            p.addProperty("name",       prop.getName());
            p.addProperty("type",       prop.getResultingType() != null
                    ? prop.getResultingType().toString() : "unknown");
            p.addProperty("readOnly",   prop.isReadOnly());
            p.addProperty("declaredOn", prop.getFlexoConcept() != null
                    ? prop.getFlexoConcept().getName() : "unknown");
            map.put(prop.getName(), p);
        }
    }

    private void collectBehaviours(FlexoConcept concept, Map<String, JsonObject> map) {
         for (FlexoBehaviour beh : concept.getAccessibleFlexoBehaviours(false)) {
            JsonObject b = new JsonObject();
            b.addProperty("name",       beh.getName());
            b.addProperty("type",       beh.getClass().getSimpleName());
            b.addProperty("declaredOn", beh.getFlexoConcept() != null
                    ? beh.getFlexoConcept().getName() : "unknown");

            JsonArray params = new JsonArray();
            for (FlexoBehaviourParameter param : beh.getParameters()) {
                JsonObject par = new JsonObject();
                par.addProperty("name", param.getName());
                par.addProperty("type", param.getType() != null
                        ? param.getType().toString() : "unknown");
                params.add(par);
            }
            b.add("parameters", params);
            map.put(beh.getName(), b);
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}