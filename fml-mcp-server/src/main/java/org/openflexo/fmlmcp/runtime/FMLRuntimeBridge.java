package org.openflexo.fmlmcp.runtime;

import org.openflexo.foundation.DefaultFlexoServiceManager;
import org.openflexo.foundation.FlexoServiceManager;
import org.openflexo.foundation.fml.FMLCompilationUnit;
import org.openflexo.foundation.fml.FMLTechnologyAdapter;
import org.openflexo.foundation.fml.VirtualModel;
import org.openflexo.foundation.fml.rm.CompilationUnitResource;
import org.openflexo.foundation.resource.DirectoryResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenter;
import org.openflexo.foundation.resource.FlexoResourceCenterService;
import org.openflexo.foundation.technologyadapter.TechnologyAdapterService;
//import org.openflexo.ta.csv.CSVTechnologyAdapter;
//import org.openflexo.ta.mcp.MCPTechnologyAdapter;
//import org.openflexo.technologyadapter.owl.OWLTechnologyAdapter;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton that boots the OpenFlexo {@link FlexoServiceManager} exactly once
 * and keeps it alive for the duration of the server process.
 *
 * @author Mouad Hayaoui
 */
public class FMLRuntimeBridge {

    private static final Logger logger = Logger.getLogger(FMLRuntimeBridge.class.getName());

    private static final FMLRuntimeBridge INSTANCE = new FMLRuntimeBridge();

    public static FMLRuntimeBridge getInstance() { return INSTANCE; }

    private FMLRuntimeBridge() {}

    private volatile FlexoServiceManager serviceManager;
    private volatile FMLTechnologyAdapter fmlTA;
    private volatile boolean initialized = false;


    public synchronized void initialize(String extraResourceCenterPath) {
        if (initialized) {
            logger.fine("FMLRuntimeBridge already initialized  skipping.");
            return;
        }

        logger.info("Initializing OpenFlexo ServiceManager …");
        serviceManager = new DefaultFlexoServiceManager(null, false, true);

        TechnologyAdapterService taService = serviceManager.getTechnologyAdapterService();

        fmlTA = taService.getTechnologyAdapter(FMLTechnologyAdapter.class);
        if (fmlTA == null) {
            throw new IllegalStateException("FMLTechnologyAdapter not found on classpath.");
        }
        taService.activateTechnologyAdapter(fmlTA, true);

     /*   MCPTechnologyAdapter mcpTA = taService.getTechnologyAdapter(MCPTechnologyAdapter.class);
        if (mcpTA != null) {
            taService.activateTechnologyAdapter(mcpTA, true);
            logger.info("MCP technology adapter activated.");
        } else {
            logger.warning("MCPTechnologyAdapter not found  MCP model slots will not work.");
        }*/

       /* CSVTechnologyAdapter csvTA = taService.getTechnologyAdapter(CSVTechnologyAdapter.class);
        if (csvTA != null) {
            taService.activateTechnologyAdapter(csvTA, true);
            logger.info("CSV technology adapter activated.");
        }else {
            logger.warning("CSVTechnologyAdapter not found  CSV model slots will not work.");
        }
        OWLTechnologyAdapter owlTA = taService.getTechnologyAdapter(OWLTechnologyAdapter.class);
        if (owlTA != null) {
            taService.activateTechnologyAdapter(owlTA, true);
            logger.info("OWL technology adapter activated.");
        } else {
            logger.warning("OWLTechnologyAdapter not found  OWL model slots will not work.");
        }*/

        waitForTasks();

        if (extraResourceCenterPath != null && !extraResourceCenterPath.trim().isEmpty()) {
            addResourceCenter(extraResourceCenterPath);
        }

        initialized = true;
        logger.info("OpenFlexo ServiceManager ready.");
    }

    public void addResourceCenter(String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warning("Resource center path does not exist or is not a directory: " + path);
            return;
        }
        try {
            FlexoResourceCenterService rcService = serviceManager.getResourceCenterService();
            DirectoryResourceCenter rc =
                    DirectoryResourceCenter.instanciateNewDirectoryResourceCenter(dir, rcService);

            fmlTA.getVirtualModelRepository(rc);

            rcService.addToResourceCenters(rc);
            waitForTasks();

            logger.info("Registered resource center: " + dir.getAbsolutePath());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to register resource center: " + path, e);
        }
    }

    /**
     * Load a {@link VirtualModel} from a CompilationUnit resource URI.
     * Falls back to searching all RC repositories if URI lookup fails.
     */
    public VirtualModel loadVirtualModel(String fmlResourceURI) throws Exception {
        assertInitialized();

        if (fmlResourceURI == null || fmlResourceURI.trim().isEmpty()) {
            throw new IllegalArgumentException("fmlResourceURI must not be null or empty.");
        }

        logger.info("Loading virtual model: " + fmlResourceURI);

        CompilationUnitResource cuResource = (CompilationUnitResource)
                serviceManager.getResourceManager().getResource(fmlResourceURI);

        if (cuResource == null) {
            logger.info("URI lookup failed searching resource centers for: " + fmlResourceURI);
            cuResource = findCompilationUnitResourceInRCs(fmlResourceURI);
        }

        if (cuResource == null) {
            throw new IllegalArgumentException(
                    "No CompilationUnitResource found for URI: " + fmlResourceURI
                            + ". Make sure the containing resource center is registered.");
        }

        FMLCompilationUnit cu = cuResource.getResourceData();
        if (cu == null) {
            throw new IllegalStateException(
                    "CompilationUnit loaded as null for URI: " + fmlResourceURI);
        }

        VirtualModel vm = cu.getVirtualModel();
        if (vm == null) {
            throw new IllegalStateException(
                    "VirtualModel is null inside CompilationUnit for URI: " + fmlResourceURI);
        }

        logger.info("Virtual model loaded: " + vm.getName()
                + " (" + vm.getFlexoConcepts().size() + " concepts)");

        return vm;
    }

    /**
     * Search all registered resource centers for a CompilationUnitResource
     * matching the supplied URI. Falls back to filename match if exact URI
     * match fails.
     */
    private CompilationUnitResource findCompilationUnitResourceInRCs(String fmlResourceURI) {
        String fileName = fmlResourceURI;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        FlexoResourceCenterService rcService = serviceManager.getResourceCenterService();
        for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
            org.openflexo.foundation.fml.CompilationUnitRepository<?> repo =
                    fmlTA.getVirtualModelRepository(rc);

            if (repo == null) continue;

            for (CompilationUnitResource r : repo.getAllResources()) {
                if (fmlResourceURI.equals(r.getURI())) {
                    logger.info("Found by URI match in RC: " + rc.getDefaultBaseURI());
                    return r;
                }
                if (r.getURI() != null && r.getURI().endsWith(fileName)) {
                    logger.info("Found by filename match: " + r.getURI()
                            + " in RC: " + rc.getDefaultBaseURI());
                    return r;
                }
            }
        }
        return null;
    }


    public void logRegisteredResources() {
        logger.info("=== Registered resources ===");
        for (org.openflexo.foundation.resource.FlexoResource<?> r :
                serviceManager.getResourceManager().getRegisteredResources()) {
            logger.info("  " + r.getURI() + " [" + r.getClass().getSimpleName() + "]");
        }
        logger.info("============================");
    }


    public FlexoServiceManager getServiceManager() {
        assertInitialized();
        return serviceManager;
    }

    public FMLTechnologyAdapter getFMLTechnologyAdapter() {
        assertInitialized();
        return fmlTA;
    }


    private void waitForTasks() {
        if (serviceManager == null || serviceManager.getTaskManager() == null) {
            return;
        }
        int maxWaitMs = 10000;
        int waited    = 0;
        int sleepMs   = 200;
        while (!serviceManager.getTaskManager().getScheduledTasks().isEmpty()
                && waited < maxWaitMs) {
            try {
                Thread.sleep(sleepMs);
                waited += sleepMs;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (waited >= maxWaitMs) {
            logger.warning("Timed out waiting for task manager  proceeding anyway.");
        } else if (waited > 0) {
            logger.fine("Task manager idle after " + waited + "ms.");
        }
    }

    private void assertInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "FMLRuntimeBridge has not been initialized. Call initialize() first.");
        }
    }
    public void logFullDiagnostic() {
        logger.info("========== FULL DIAGNOSTIC ==========");

        logger.info("--- Resource Centers ---");
        FlexoResourceCenterService rcService = serviceManager.getResourceCenterService();
        for (FlexoResourceCenter<?> rc : rcService.getResourceCenters()) {
            logger.info("  RC: " + rc.getDefaultBaseURI() + " [" + rc.getClass().getSimpleName() + "]");

            org.openflexo.foundation.fml.CompilationUnitRepository<?> repo =
                    fmlTA.getVirtualModelRepository(rc);
            if (repo == null) {
                logger.info("    FML repo: NULL");
            } else {
                logger.info("    FML repo: " + repo.getClass().getSimpleName()
                        + "  resources: " + repo.getAllResources().size());
                for (CompilationUnitResource r : repo.getAllResources()) {
                    logger.info("      -> " + r.getURI()
                            + " | name=" + r.getName()
                            + " | file=" + r.getIODelegate());
                }
            }
        }

        logger.info("--- ResourceManager resources ---");
        for (org.openflexo.foundation.resource.FlexoResource<?> r :
                serviceManager.getResourceManager().getRegisteredResources()) {
            logger.info("  " + r.getURI() + " [" + r.getClass().getSimpleName() + "]");
        }

        logger.info("--- FML Virtual Model Repositories ---");
        for (org.openflexo.foundation.fml.CompilationUnitRepository<?> repo :
                fmlTA.getVirtualModelRepositories()) {
            logger.info("  Repo base: " + repo.getDefaultBaseURI()
                    + "  resources: " + repo.getAllResources().size());
            for (CompilationUnitResource r : repo.getAllResources()) {
                logger.info("    -> " + r.getURI());
            }
        }

        logger.info("=====================================");
    }
}