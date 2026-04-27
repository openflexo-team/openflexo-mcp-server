package org.openflexo.fmlmcp.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Test;
import org.openflexo.fmlmcp.ServerConfig;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;

import java.lang.reflect.Method;

import static org.junit.Assert.*;


public class TestFMLMCPServer {

    private FMLMCPServer server;
    private Method handleLine;

    @Before
    public void setUp() throws Exception {
        FMLRuntimeBridge bridge = FMLRuntimeBridge.getInstance();
        bridge.initialize(null);

        ServerConfig config = ServerConfig.parse(new String[]{"--transport", "stdio"});
        server = new FMLMCPServer(bridge, config);

        handleLine = FMLMCPServer.class.getDeclaredMethod("handleJsonRpcLine", String.class);
        handleLine.setAccessible(true);
    }

    @Test
    public void testInitializeHandshake() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\","
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

        String response = (String) handleLine.invoke(server, request);
        assertNotNull(response);

        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        assertEquals("2.0", obj.get("jsonrpc").getAsString());
        assertTrue("Response must contain 'result'", obj.has("result"));

        JsonObject result = obj.getAsJsonObject("result");
        assertTrue("Result must contain serverInfo", result.has("serverInfo"));
    }

    @Test
    public void testToolsList() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        String response = (String) handleLine.invoke(server, request);
        System.out.println("tools/list response: " + response);
        assertNotNull(response);

        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(obj.has("result"));

        JsonObject result = obj.getAsJsonObject("result");
        assertTrue("tools/list result must contain 'tools'", result.has("tools"));
        assertEquals("Should expose exactly 22 tools",
                22, result.getAsJsonArray("tools").size());
    }

    @Test
    public void testUnknownMethodReturnsError() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"unknown/method\"}";
        String response = (String) handleLine.invoke(server, request);
        assertNotNull(response);

        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        assertTrue("Unknown method must return error", obj.has("error"));
        assertEquals(-32601, obj.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    public void testCallBehaviourWithBadHandleReturnsError() throws Exception {
        String request = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"call_behaviour\","
                + "\"arguments\":{\"handle\":\"inst_bad\","
                + "\"behaviourName\":\"doSomething\","
                + "\"arguments\":{}}}}";

        String response = (String) handleLine.invoke(server, request);
        assertNotNull(response);

        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(obj.has("result"));
         String contentText = obj.getAsJsonObject("result")
                .getAsJsonArray("content").get(0)
                .getAsJsonObject().get("text").getAsString();
        assertTrue("Bad handle must return error JSON",
                contentText.contains("error") || contentText.contains("Unknown handle"));
    }
}
