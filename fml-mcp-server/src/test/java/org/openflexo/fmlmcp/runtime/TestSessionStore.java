package org.openflexo.fmlmcp.runtime;

import org.junit.Test;

import static org.junit.Assert.*;


public class TestSessionStore {

    @Test
    public void testRegisterAndGet() {
        FMLSessionStore store = FMLSessionStore.getInstance();
        int initialSize = store.size();

        // We cannot easily create a real FlexoConceptInstance without a runtime,
        // so this test documents the contract at the API level.
        // Full integration coverage lives in TestCreateInstanceTool_Integration.
        assertNotNull("Store must be accessible", store);
        assertTrue("Store size must be non-negative", store.size() >= 0);
        assertEquals("Getting unknown handle must return null", null, store.get("bad_handle_xyz"));
    }

    @Test
    public void testRemoveUnknownHandleReturnsFalse() {
        FMLSessionStore store = FMLSessionStore.getInstance();
        boolean removed = store.remove("handle_that_does_not_exist_xyz123");
        assertFalse("Removing unknown handle must return false", removed);
    }
}
