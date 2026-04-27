// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\CreateInstanceTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\CreateInstanceTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.openflexo.fmlmcp.runtime.BehaviourArgumentMapper;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.foundation.DefaultFlexoEditor;
import org.openflexo.foundation.FlexoEditor;
import org.openflexo.foundation.fml.CreationScheme;
import org.openflexo.foundation.fml.FlexoBehaviourParameter;
import org.openflexo.foundation.fml.FlexoConcept;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rt.FMLRTTechnologyAdapter;
import org.openflexo.foundation.fml.rt.FMLRTVirtualModelInstance;
import org.openflexo.foundation.fml.rt.FlexoConceptInstance;
import org.openflexo.foundation.fml.rt.ModelSlotInstance;
import org.openflexo.foundation.fml.rt.action.CreateBasicVirtualModelInstance;
import org.openflexo.foundation.fml.rt.action.CreateFlexoConceptInstance;
import org.openflexo.foundation.fml.rt.rm.FMLRTVirtualModelInstanceResource;
import org.openflexo.foundation.resource.DirectoryResourceCenter;
import org.openflexo.foundation.resource.FlexoResource;
import org.openflexo.foundation.resource.FlexoResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenterService;
import org.openflexo.foundation.resource.RepositoryFolder;
import org.openflexo.foundation.resource.ResourceData;
import org.openflexo.foundation.technologyadapter.ModelSlot;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: create_instance
 *
 * Instantiates a FML concept via its CreationScheme, or instantiates the
 * VirtualModel itself when conceptName matches the VirtualModel name.
 *
 * <p>Two execution paths:
 * <ol>
 *   <li><b>Concept path</b> (normal): conceptName identifies a nested
 *       {@link FlexoConcept} inside the VirtualModel. A transient VMI is
 *       used as the container and the concept instance is created inside it.</li>
 *   <li><b>VirtualModel path</b> (new): conceptName matches the VirtualModel
 *       name itself. The VirtualModel's own CreationScheme is called, which
 *       may include {@code connect} statements that bind model slots. The
 *       resulting {@link FMLRTVirtualModelInstance} is registered as the
 *       session handle and cached in {@link FMLVMIFactory} so subsequent
 *       {@code bind_resource} and {@code execute_workflow} calls see the
 *       correctly initialised VMI.</li>
 * </ol>
 *
 * @author Mouad Hayaoui
 */
public class CreateInstanceTool {

    private static final Logger logger =
            Logger.getLogger(CreateInstanceTool.class.getName());

    public static final String TOOL_NAME = "create_instance";
    public static final String TOOL_DESCRIPTION =
            "Instantiate a FML concept via its creation scheme. "
                    + "If conceptName matches the VirtualModel name, the VirtualModel itself "
                    + "is instantiated (running its own CreationScheme, which may connect model slots). "
                    + "For nested concepts, returns a session handle to use with call_behaviour. "
                    + "Optionally provide resourceBindings as a JSON object mapping "
                    + "model slot names to resource URIs to bind data sources to the instance.";

    private final FMLRuntimeBridge runtime;
    private final FMLSessionStore  sessionStore;

    public CreateInstanceTool(FMLRuntimeBridge runtime, FMLSessionStore sessionStore) {
        this.runtime      = runtime;
        this.sessionStore = sessionStore;
    }

    public String execute(String fmlResourceURI,
                          String conceptName,
                          String argumentsJson) {
        return execute(fmlResourceURI, conceptName, argumentsJson, null);
    }

    public String execute(String fmlResourceURI,
                          String conceptName,
                          String argumentsJson,
                          String resourceBindingsJson) {
        try {
            VirtualModel vm = runtime.loadVirtualModel(fmlResourceURI);


            if (vm.getName().equals(conceptName)) {
                return executeVirtualModelInstantiation(
                        vm, fmlResourceURI, argumentsJson, resourceBindingsJson);
            }

             FlexoConcept concept = vm.getFlexoConcept(conceptName);
            if (concept == null) {
                return buildError("Concept not found: '" + conceptName
                        + "' in virtual model '" + vm.getName() + "'. "
                        + "If you want to instantiate the VirtualModel itself, "
                        + "use conceptName = '" + vm.getName() + "'.");
            }

            CreationScheme scheme = concept.getCreationSchemes().isEmpty()
                    ? null
                    : concept.getCreationSchemes().get(0);

            if (scheme == null) {
                return buildError("Concept '" + conceptName
                        + "' has no CreationScheme.");
            }

            JsonObject jsonArgs = null;
            if (argumentsJson != null && !argumentsJson.trim().isEmpty()
                    && !argumentsJson.trim().equals("{}")) {
                jsonArgs = JsonParser.parseString(argumentsJson).getAsJsonObject();
            }
            Map<String, Object> args = BehaviourArgumentMapper.map(scheme, jsonArgs);

             FMLRTVirtualModelInstance vmi =
                    FMLVMIFactory.getOrCreateVMI(vm, runtime);

             if (resourceBindingsJson != null
                    && !resourceBindingsJson.trim().isEmpty()
                    && !resourceBindingsJson.trim().equals("{}")) {
                JsonObject bindings =
                        JsonParser.parseString(resourceBindingsJson).getAsJsonObject();
                applyResourceBindings(vmi, vm, bindings);
            }

            FlexoEditor editor =
                    new DefaultFlexoEditor(null, runtime.getServiceManager());

            CreateFlexoConceptInstance action =
                    CreateFlexoConceptInstance.actionType
                            .makeNewAction(vmi, null, editor);
            action.setFlexoConcept(concept);
            action.setCreationScheme(scheme);

            for (FlexoBehaviourParameter param : scheme.getParameters()) {
                Object value = args.get(param.getName());
                if (value != null) {
                    action.setParameterValue(param, value);
                }
            }

            action.doAction();

            if (!action.hasActionExecutionSucceeded()) {
                return buildError("CreationScheme execution failed for concept '"
                        + conceptName + "'");
            }

            FlexoConceptInstance fci = action.getNewFlexoConceptInstance();
            if (fci == null) {
                return buildError("CreationScheme returned null instance for concept '"
                        + conceptName + "'");
            }

            String handle = sessionStore.register(fci, fmlResourceURI);
            logger.info("Created instance handle: " + handle
                    + " for concept: " + conceptName);

            JsonObject result = new JsonObject();
            result.addProperty("handle",  handle);
            result.addProperty("concept", conceptName);
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "create_instance failed", e);
            return buildError(e.getMessage());
        }
    }


    @SuppressWarnings("unchecked")
    private String executeVirtualModelInstantiation(VirtualModel vm,
                                                    String fmlResourceURI,
                                                    String argumentsJson,
                                                    String resourceBindingsJson) {
        try {

            CreationScheme scheme = pickBestCreationScheme(vm, argumentsJson);
            if (scheme == null) {
                return buildError("VirtualModel '" + vm.getName()
                        + "' has no CreationScheme.");
            }

            JsonObject jsonArgs = null;
            if (argumentsJson != null && !argumentsJson.trim().isEmpty()
                    && !argumentsJson.trim().equals("{}")) {
                jsonArgs = JsonParser.parseString(argumentsJson).getAsJsonObject();
            }
            Map<String, Object> args = BehaviourArgumentMapper.map(scheme, jsonArgs);

             File tempDir = new File(System.getProperty("java.io.tmpdir"), "fml-mcp-server-rc");
            if (!tempDir.exists()) tempDir.mkdirs();

            FlexoResourceCenterService rcService =
                    runtime.getServiceManager().getResourceCenterService();
            DirectoryResourceCenter tempRC = null;
            for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
                if (rc instanceof DirectoryResourceCenter) {
                    DirectoryResourceCenter drc = (DirectoryResourceCenter) rc;
                    if (drc.getRootDirectory() != null
                            && drc.getRootDirectory().getAbsolutePath()
                            .equals(tempDir.getAbsolutePath())) {
                        tempRC = drc;
                        break;
                    }
                }
            }
            if (tempRC == null) {
                tempRC = DirectoryResourceCenter
                        .instanciateNewDirectoryResourceCenter(tempDir, rcService);
                rcService.addToResourceCenters(tempRC);
            }

            FMLRTTechnologyAdapter fmlRTTA = runtime.getServiceManager()
                    .getTechnologyAdapterService()
                    .getTechnologyAdapter(FMLRTTechnologyAdapter.class);

            if (fmlRTTA == null) {
                return buildError("FMLRTTechnologyAdapter not available.");
            }

            RepositoryFolder<FMLRTVirtualModelInstanceResource, File> rootFolder =
                    fmlRTTA.getVirtualModelInstanceRepository(tempRC).getRootFolder();

            FlexoEditor editor =
                    new DefaultFlexoEditor(null, runtime.getServiceManager());

            CreateBasicVirtualModelInstance action =
                    CreateBasicVirtualModelInstance.actionType
                            .makeNewAction(rootFolder, null, editor);
            action.setNewVirtualModelInstanceName(vm.getName() + "_Session");
            action.setNewVirtualModelInstanceTitle(vm.getName() + " Runtime Session");
            action.setVirtualModel(vm);
            action.setCreationScheme(scheme);

             for (FlexoBehaviourParameter param : scheme.getParameters()) {
                Object value = args.get(param.getName());
                if (value != null) {
                    action.setParameterValue(param, value);
                }
            }

            action.doAction();

            if (!action.hasActionExecutionSucceeded()) {
                return buildError("VirtualModel CreationScheme execution failed for '"
                        + vm.getName() + "'.");
            }

            FMLRTVirtualModelInstance vmi = action.getNewVirtualModelInstance();
            if (vmi == null) {
                return buildError("VirtualModel instantiation returned null VMI for '"
                        + vm.getName() + "'.");
            }

             if (resourceBindingsJson != null
                    && !resourceBindingsJson.trim().isEmpty()
                    && !resourceBindingsJson.trim().equals("{}")) {
                JsonObject bindings =
                        JsonParser.parseString(resourceBindingsJson).getAsJsonObject();
                applyResourceBindings(vmi, vm, bindings);
            }

             FMLVMIFactory.cacheVMI(vm.getName(), vmi);

             String handle = sessionStore.register(vmi, fmlResourceURI);
            logger.info("Created VMI-level instance handle: " + handle
                    + " for VirtualModel: " + vm.getName());

            JsonObject result = new JsonObject();
            result.addProperty("handle",       handle);
            result.addProperty("concept",      vm.getName());
            result.addProperty("type",         "VirtualModelInstance");
            result.addProperty("slotsConnected", scheme.getParameters().size() > 0);
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "VirtualModel instantiation failed for: " + vm.getName(), e);
            return buildError("VirtualModel instantiation failed: " + e.getMessage());
        }
    }

    /**
     * Pick the best CreationScheme for the given arguments JSON.
     * Prefers a scheme whose parameter count matches the supplied argument count.
     * Falls back to the first available scheme.
     */
    private CreationScheme pickBestCreationScheme(VirtualModel vm, String argumentsJson) {
        if (vm.getCreationSchemes().isEmpty()) {
            return null;
        }
        if (argumentsJson == null || argumentsJson.trim().isEmpty()
                || argumentsJson.trim().equals("{}")) {
             for (CreationScheme cs : vm.getCreationSchemes()) {
                if (cs.getParameters().isEmpty()) {
                    return cs;
                }
            }
             return vm.getCreationSchemes().get(0);
        }
         try {
            JsonObject jsonArgs = JsonParser.parseString(argumentsJson).getAsJsonObject();
            int argCount = jsonArgs.size();
            for (CreationScheme cs : vm.getCreationSchemes()) {
                if (cs.getParameters().size() == argCount) {
                    return cs;
                }
            }
        } catch (Exception e) {
            // ignore parse error here  will be caught later
        }
        return vm.getCreationSchemes().get(0);
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyResourceBindings(FMLRTVirtualModelInstance vmi,
                                       VirtualModel vm,
                                       JsonObject bindings) {
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : bindings.entrySet()) {
            String slotName    = entry.getKey();
            String resourceURI = entry.getValue().getAsString();

            try {
                ModelSlot<?, ?> modelSlot = vm.getModelSlot(slotName);
                if (modelSlot == null) {
                    logger.warning("Model slot '" + slotName
                            + "' not found on virtual model '"
                            + vm.getName() + "' — skipping binding.");
                    continue;
                }

                FlexoResource resource = runtime.getServiceManager()
                        .getResourceManager()
                        .getResource(resourceURI);

                if (resource == null) {
                    logger.warning("Resource not found for URI: '"
                            + resourceURI + "' — skipping binding for slot '"
                            + slotName + "'.");
                    continue;
                }

                Object resourceData = resource.getResourceData();
                if (resourceData == null) {
                    logger.warning("Resource data is null for URI: '"
                            + resourceURI + "' — skipping binding for slot '"
                            + slotName + "'.");
                    continue;
                }

                ModelSlotInstance msi = vmi.getModelSlotInstance(slotName);
                if (msi == null) {
                    logger.warning("Could not get ModelSlotInstance for slot '"
                            + slotName + "' on VMI — skipping.");
                    continue;
                }

                msi.setAccessedResourceData((ResourceData) resourceData);
                logger.info("Bound resource '" + resourceURI
                        + "' to model slot '" + slotName + "'");

            } catch (Exception e) {
                logger.log(Level.WARNING,
                        "Failed to bind resource for slot '" + slotName + "'", e);
            }
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}