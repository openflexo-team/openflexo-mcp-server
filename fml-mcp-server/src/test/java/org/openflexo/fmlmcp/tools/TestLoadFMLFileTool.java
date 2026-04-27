package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;

import static org.junit.Assert.*;


public class TestLoadFMLFileTool {

    private static LoadFMLFileTool tool;

    @BeforeClass
    public static void setUp() {
        FMLRuntimeBridge bridge = FMLRuntimeBridge.getInstance();
        bridge.initialize(null);
        tool = new LoadFMLFileTool(bridge);
    }

    @Test
    public void testBadURIReturnsErrorEnvelope() {
        String result = tool.execute("http://does.not.exist/FML/Missing.fml");
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertTrue("Bad URI must produce an error field", obj.has("error"));
        assertTrue("Error must include URI", obj.get("uri").getAsString()
                .contains("Missing.fml"));
    }

    @Test
    public void testNullURIReturnsError() {
        String result = tool.execute(null);
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertTrue("Null URI must produce an error field", obj.has("error"));
    }

    @Test
    public void testEmptyURIReturnsError() {
        String result = tool.execute("");
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertTrue("Empty URI must produce an error field", obj.has("error"));
    }
}
