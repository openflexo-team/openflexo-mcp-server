package org.openflexo.fmlmcp.tools;

import org.openflexo.foundation.DefaultFlexoEditor;
import org.openflexo.foundation.FlexoEditor;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rt.FMLRTTechnologyAdapter;
import org.openflexo.foundation.fml.rt.FMLRTVirtualModelInstance;
import org.openflexo.foundation.fml.rt.action.CreateBasicVirtualModelInstance;
import org.openflexo.foundation.fml.rt.rm.FMLRTVirtualModelInstanceResource;
import org.openflexo.foundation.resource.DirectoryResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenterService;
import org.openflexo.foundation.resource.RepositoryFolder;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper that creates a transient {@link FMLRTVirtualModelInstance} to act as
 * the container when instantiating FML concepts at runtime.
 *
 * @author Mouad Hayaoui
 */
public class FMLVMIFactory {

    private static final Logger logger = Logger.getLogger(FMLVMIFactory.class.getName());

    private static final java.util.concurrent.ConcurrentHashMap<String, FMLRTVirtualModelInstance> vmiCache
            = new java.util.concurrent.ConcurrentHashMap<>();

    private FMLVMIFactory() {}

    @SuppressWarnings("unchecked")
    public static FMLRTVirtualModelInstance getOrCreateVMI(VirtualModel vm, FMLRuntimeBridge runtime) {

         File tempDir = new File(System.getProperty("java.io.tmpdir"), "fml-mcp-server-rc");
        if (tempDir.exists() && tempDir.listFiles() != null) {
            for (File f : tempDir.listFiles()) {
                if (f.isDirectory() && f.getName().endsWith(".fml.rt")) {
                    deleteRecursively(f);
                    String vmName = f.getName().replace(".fml.rt", "")
                            .replace("_Session", "");
                    vmiCache.remove(vmName);
                }
            }
        }

         FMLRTVirtualModelInstance cached = vmiCache.get(vm.getName());
        if (cached != null) {
            logger.fine("Reusing cached VMI for: " + vm.getName());
            return cached;
        }

        try {
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

             FlexoResourceCenterService rcService = runtime.getServiceManager().getResourceCenterService();
            DirectoryResourceCenter tempRC = null;
            for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
                if (rc instanceof DirectoryResourceCenter) {
                    DirectoryResourceCenter drc = (DirectoryResourceCenter) rc;
                    if (drc.getRootDirectory() != null
                            && drc.getRootDirectory().getAbsolutePath().equals(tempDir.getAbsolutePath())) {
                        tempRC = drc;
                        break;
                    }
                }
            }
            if (tempRC == null) {
                tempRC = DirectoryResourceCenter.instanciateNewDirectoryResourceCenter(tempDir, rcService);
                rcService.addToResourceCenters(tempRC);
                logger.info("Registered temp resource center: " + tempDir.getAbsolutePath());
            }

            FMLRTTechnologyAdapter fmlRTTA = runtime.getServiceManager()
                    .getTechnologyAdapterService()
                    .getTechnologyAdapter(FMLRTTechnologyAdapter.class);

            if (fmlRTTA == null) {
                throw new IllegalStateException("FMLRTTechnologyAdapter not available.");
            }

            RepositoryFolder<FMLRTVirtualModelInstanceResource, File> rootFolder =
                    fmlRTTA.getVirtualModelInstanceRepository(tempRC).getRootFolder();

            FlexoEditor editor = new DefaultFlexoEditor(null, runtime.getServiceManager());

            CreateBasicVirtualModelInstance action =
                    CreateBasicVirtualModelInstance.actionType.makeNewAction(rootFolder, null, editor);
            action.setNewVirtualModelInstanceName(vm.getName() + "_Session");
            action.setNewVirtualModelInstanceTitle(vm.getName() + " Runtime Session");
            action.setVirtualModel(vm);

            action.doAction();

            if (!action.hasActionExecutionSucceeded()) {
                throw new RuntimeException(
                        "Could not create VirtualModelInstance for: " + vm.getName());
            }

            FMLRTVirtualModelInstance vmi = action.getNewVirtualModelInstance();
            if (vmi == null) {
                throw new RuntimeException(
                        "VirtualModelInstance is null after creation for: " + vm.getName());
            }

            vmiCache.put(vm.getName(), vmi);
            logger.info("Created transient VMI: " + vmi.getName());
            return vmi;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create transient VMI for: " + vm.getName(), e);
            throw new RuntimeException("VMI creation failed: " + e.getMessage(), e);
        }
    }

    public static void clearCache() {
        vmiCache.clear();
    }
    /**
     * Explicitly register a VMI in the cache under the given key.
     *
     * Called by {@link CreateInstanceTool} after a VirtualModel-level
     * instantiation so that subsequent calls to {@link #getOrCreateVMI}
     * (from execute_workflow, bind_resource, etc.) return the same
     * correctly-initialised VMI rather than creating a fresh blank one.
     *
     * @param vmName the VirtualModel name (used as cache key)
     * @param vmi    the live VMI to cache
     */
    public static void cacheVMI(String vmName, FMLRTVirtualModelInstance vmi) {
        vmiCache.put(vmName, vmi);
        logger.info("Cached VMI for: " + vmName);
    }
    private static void deleteRecursively(File f) {
        if (f.isDirectory() && f.listFiles() != null) {
            for (File child : f.listFiles()) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }

}