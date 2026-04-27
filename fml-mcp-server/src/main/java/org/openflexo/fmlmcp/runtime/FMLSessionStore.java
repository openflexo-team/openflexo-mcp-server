package org.openflexo.fmlmcp.runtime;

import org.openflexo.foundation.fml.rt.FlexoConceptInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Store that maps opaque string handles to live
 * {@link FlexoConceptInstance} objects, along with metadata
 * about each session entry.
 *
 * @author Mouad Hayaoui
 */
public class FMLSessionStore {

    private static final Logger logger = Logger.getLogger(FMLSessionStore.class.getName());

    private static final FMLSessionStore INSTANCE = new FMLSessionStore();

    public static FMLSessionStore getInstance() { return INSTANCE; }

    private FMLSessionStore() {}


    public static class SessionEntry {
        public final FlexoConceptInstance instance;
        public final String virtualModelURI;
        public final String conceptName;
        public final long createdAt;

        SessionEntry(FlexoConceptInstance instance,
                     String virtualModelURI,
                     String conceptName) {
            this.instance       = instance;
            this.virtualModelURI = virtualModelURI;
            this.conceptName    = conceptName;
            this.createdAt      = System.currentTimeMillis();
        }
    }


    private final ConcurrentHashMap<String, SessionEntry> sessions =
            new ConcurrentHashMap<>();


    /**
     * Register a new instance and return a unique handle for it.
     *
     * @param instance        the live {@link FlexoConceptInstance} to store
     * @param virtualModelURI the URI of the virtual model this instance belongs to
     * @return an opaque handle string (e.g. {@code inst_3f7a2b})
     */
    public String register(FlexoConceptInstance instance, String virtualModelURI) {
        String handle = "inst_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String conceptName = instance.getFlexoConcept() != null
                ? instance.getFlexoConcept().getName()
                : "Unknown";
        sessions.put(handle, new SessionEntry(instance, virtualModelURI, conceptName));
        logger.fine("Registered handle: " + handle
                + " -> " + conceptName + " [" + virtualModelURI + "]");
        return handle;
    }

    /**
     * Retrieve a stored instance by handle.
     *
     * @param handle the opaque handle returned by {@link #register}
     * @return the {@link FlexoConceptInstance}, or {@code null} if not found
     */
    public FlexoConceptInstance get(String handle) {
        SessionEntry entry = sessions.get(handle);
        return entry != null ? entry.instance : null;
    }

    /**
     * Retrieve the full session entry by handle.
     *
     * @param handle the opaque handle
     * @return the {@link SessionEntry}, or {@code null} if not found
     */
    public SessionEntry getEntry(String handle) {
        return sessions.get(handle);
    }

    /**
     * Return a snapshot of all current entries.
     * The list is a copy — safe to iterate without holding a lock.
     */
    public List<java.util.Map.Entry<String, SessionEntry>> allEntries() {
        return new ArrayList<>(sessions.entrySet());
    }

    /**
     * Remove a stored instance by handle, freeing the reference.
     *
     * @param handle the opaque handle to remove
     * @return {@code true} if the handle was present and has been removed
     */
    public boolean remove(String handle) {
        boolean removed = sessions.remove(handle) != null;
        if (removed) {
            logger.fine("Removed handle: " + handle);
        }
        return removed;
    }

    /** @return the number of live sessions currently held */
    public int size() {
        return sessions.size();
    }
}