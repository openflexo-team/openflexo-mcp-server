// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\BindResourceTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\BindResourceTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rt.FMLRTVirtualModelInstance;
import org.openflexo.foundation.fml.rt.ModelSlotInstance;
import org.openflexo.foundation.resource.FlexoResource;
import org.openflexo.foundation.resource.ResourceData;
import org.openflexo.foundation.resource.ResourceManager;
import org.openflexo.foundation.technologyadapter.ModelSlot;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code bind_resource}
 *
 * <p>Connects a data resource (CSV file, MCP server configuration, etc.)
 * to a named model slot on the VirtualModel instance (VMI).
 *
 * <p>This is the standalone counterpart to the {@code resourceBindings} option
 * available at {@code create_instance} time.  Use this tool when:
 * <ul>
 *   <li>The VMI was already created and you need to attach a resource after
 *       the fact.</li>
 *   <li>You want to swap the resource attached to a slot (e.g. change which
 *       CSV file is being processed) without destroying and recreating all
 *       concept instances.</li>
 *   <li>You need to bind a resource before calling {@code execute_workflow}
 *       on a model whose top-level behaviour reads from a model slot.</li>
 * </ul>
 *
 * <p>Input schema:
 * <pre>
 * {
 *   "fmlResourceURI": "http://fmlmcp.test/FML/MyModel.fml",  // required
 *   "slotName":       "traceData",                            // required
 *   "resourceURI":    "http://fmlmcp.test/data/trace.csv"     // required
 * }
 * </pre>
 *
 * <p>Output schema (success):
 * <pre>
 * {
 *   "bound":          true,
 *   "slotName":       "traceData",
 *   "resourceURI":    "http://fmlmcp.test/data/trace.csv",
 *   "resourceClass":  "CSVDocument",
 *   "virtualModel":   "MyModel"
 * }
 * </pre>
 *
 * <p>On failure an {@code error} field is returned describing what went wrong
 * (slot not found, resource not found, resource center not registered, etc.).
 *
 * @author Mouad Hayaoui
 */
public class BindResourceTool {

    private static final Logger logger =
            Logger.getLogger(BindResourceTool.class.getName());

    public static final String TOOL_NAME = "bind_resource";

    public static final String TOOL_DESCRIPTION =
            "Connect a data resource (CSV file, MCP server, etc.) to a named model slot "
                    + "on the VirtualModel instance. "
                    + "Use this before execute_workflow when the model reads from a model slot, "
                    + "or to swap the resource on an already-live model without recreating instances. "
                    + "The resource must already be discoverable — call add_resource_center first "
                    + "if the file lives in a directory not yet registered. "
                    + "Provide the fmlResourceURI of the model, the slotName as declared in FML, "
                    + "and the resourceURI of the data file.";

    private final FMLRuntimeBridge runtime;

    public BindResourceTool(FMLRuntimeBridge runtime) {
        this.runtime = runtime;
    }

    /**
     * @param fmlResourceURI URI of the VirtualModel that declares the model slot
     * @param slotName       name of the model slot as declared in FML
     * @param resourceURI    URI of the resource to bind (e.g. a CSV file URI)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String execute(String fmlResourceURI, String slotName, String resourceURI) {

         if (fmlResourceURI == null || fmlResourceURI.trim().isEmpty()) {
            return buildError("fmlResourceURI must not be empty.");
        }
        if (slotName == null || slotName.trim().isEmpty()) {
            return buildError("slotName must not be empty.");
        }
        if (resourceURI == null || resourceURI.trim().isEmpty()) {
            return buildError("resourceURI must not be empty.");
        }

        try {
             VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);

             ModelSlot<?, ?> modelSlot = vm.getModelSlot(slotName);
            if (modelSlot == null) {
                return buildErrorWithSlots(
                        "Model slot '" + slotName + "' not found on VirtualModel '"
                                + vm.getName() + "'.",
                        vm);
            }

             ResourceManager rm = runtime.getServiceManager().getResourceManager();
            FlexoResource<?> resource = rm.getResource(resourceURI);

            if (resource == null) {
                 String fileName = resourceURI.contains("/")
                        ? resourceURI.substring(resourceURI.lastIndexOf('/') + 1)
                        : resourceURI;
                for (FlexoResource<?> r : rm.getRegisteredResources()) {
                    if (r.getURI() != null && r.getURI().endsWith(fileName)) {
                        resource = r;
                        logger.info("bind_resource: found resource by filename match: "
                                + r.getURI());
                        break;
                    }
                }
            }

            if (resource == null) {
                return buildError("Resource not found for URI: '" + resourceURI + "'. "
                        + "Make sure the directory containing this file has been registered "
                        + "with add_resource_center.");
            }

             Object resourceData;
            try {
                resourceData = resource.getResourceData();
            } catch (Exception e) {
                return buildError("Failed to load resource data for '" + resourceURI
                        + "': " + e.getMessage());
            }

            if (resourceData == null) {
                return buildError("Resource data is null for URI: '" + resourceURI
                        + "'. The file may be empty or unreadable.");
            }

             FMLRTVirtualModelInstance vmi = FMLVMIFactory.getOrCreateVMI(vm, runtime);

             ModelSlotInstance msi = vmi.getModelSlotInstance(slotName);
            if (msi == null) {
                return buildError("Could not get ModelSlotInstance for slot '"
                        + slotName + "' on VMI for model '" + vm.getName() + "'. "
                        + "The VMI may not have been initialised correctly.");
            }

            msi.setAccessedResourceData((ResourceData) resourceData);

            String resourceDataClassName = resourceData.getClass().getSimpleName();
            logger.info("bind_resource: bound '" + resourceURI
                    + "' (" + resourceDataClassName + ") to slot '"
                    + slotName + "' on model '" + vm.getName() + "'");

             JsonObject result = new JsonObject();
            result.addProperty("bound",         true);
            result.addProperty("slotName",      slotName);
            result.addProperty("resourceURI",   resource.getURI());
            result.addProperty("resourceClass", resourceDataClassName);
            result.addProperty("virtualModel",  vm.getName());
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "bind_resource failed for slot '" + slotName
                            + "' on '" + fmlResourceURI + "'", e);
            return buildError("Internal error: " + e.getMessage());
        }
    }


    /**
     * Builds an error response that also lists the available model slots on
     * the VirtualModel, so the caller knows what names are valid.
     */
    private String buildErrorWithSlots(String message, VirtualModel vm) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);

        JsonArray slots = new JsonArray();
        for (ModelSlot<?, ?> slot : vm.getModelSlots()) {
            JsonObject s = new JsonObject();
            s.addProperty("name", slot.getName());
            s.addProperty("type", slot.getClass().getSimpleName());
            slots.add(s);
        }
        err.add("availableSlots", slots);
        return err.toString();
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}
