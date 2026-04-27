package org.openflexo.fmlmcp.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.tools.*;

import static org.junit.Assert.*;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestHelloFMLIntegration {

    private static final String FML_URI     = "http://fmlmcp.test/FML/HelloFML.fml";
    private static final String CONCEPT     = "Greeter";
    private static final String RC_PATH     = "src/test/resources";

    private static LoadFMLFileTool   loadTool;
    private static CreateInstanceTool createTool;
    private static CallBehaviourTool  callTool;
    private static ListInstancesTool  listTool;

     private static String handle;

    @BeforeClass
    public static void setUp() {
        FMLRuntimeBridge bridge = FMLRuntimeBridge.getInstance();
 
        bridge.initialize(RC_PATH);

        bridge.logFullDiagnostic();
        FMLSessionStore store = FMLSessionStore.getInstance();
        FMLVMIFactory.clearCache();

        loadTool   = new LoadFMLFileTool(bridge);
        createTool = new CreateInstanceTool(bridge, store);
        callTool   = new CallBehaviourTool(store, bridge);
        listTool   = new ListInstancesTool(store);
    }

 
    @Test
    public void test1_LoadFMLFile() {
        String result = loadTool.execute(FML_URI);
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("load_fml_file must not return an error: " + result, obj.has("error"));
        assertEquals("HelloFML", obj.get("virtualModel").getAsString());

         boolean foundGreeter = false;
        for (com.google.gson.JsonElement el : obj.getAsJsonArray("concepts")) {
            if ("Greeter".equals(el.getAsJsonObject().get("name").getAsString())) {
                foundGreeter = true;
                break;
            }
        }
        assertTrue("Greeter concept must be present in loaded model", foundGreeter);

        System.out.println("test1_LoadFMLFile PASSED  " + result);
    }

 
    @Test
    public void test2_CreateInstance() {
        String arguments = "{\"aName\": \"World\"}";
        String result = createTool.execute(FML_URI, CONCEPT, arguments);
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("create_instance must not return an error: " + result, obj.has("error"));
        assertTrue("Result must contain a handle", obj.has("handle"));
        assertEquals(CONCEPT, obj.get("concept").getAsString());

        handle = obj.get("handle").getAsString();
        assertNotNull("Handle must not be null", handle);
        assertTrue("Handle must start with inst_", handle.startsWith("inst_"));

        System.out.println("test2_CreateInstance PASSED  handle=" + handle);
    }

 
    @Test
    public void test3_CallGreet() {
         assertNotNull("Handle must be set by test2_CreateInstance", handle);

        String result = callTool.execute(handle, "greet", "{}");
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("call_behaviour must not return an error: " + result, obj.has("error"));
        assertTrue("Result must contain 'result' field", obj.has("result"));

        String greeting = obj.get("result").getAsString();
        assertTrue("Greeting must contain 'World'", greeting.contains("World"));
        assertTrue("Greeting must contain 'Hello'", greeting.contains("Hello"));

        System.out.println("test3_CallGreet PASSED  result=" + greeting);
    }



 
    @Test
    public void test4_CallGreetFormal() {
        assertNotNull("Handle must be set by test2_CreateInstance", handle);

        String arguments = "{\"title\": \"Dr.\"}";
        String result = callTool.execute(handle, "greetFormal", arguments);
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("call_behaviour must not return an error: " + result, obj.has("error"));
        assertTrue("Result must contain 'result' field", obj.has("result"));
        assertFalse("Result must not be null  check greetFormal binding in HelloFML.fml",
                obj.get("result").isJsonNull());

        String greeting = obj.get("result").getAsString();
        assertTrue("Formal greeting must contain 'Dr.'",  greeting.contains("Dr."));
        assertTrue("Formal greeting must contain 'World'", greeting.contains("World"));

        System.out.println("test4_CallGreetFormal PASSED  result=" + greeting);
    }

 
    @Test
    public void test5_BadHandleReturnsError() {
        String result = callTool.execute("inst_bad", "greet", "{}");
        assertNotNull(result);

        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertTrue("Bad handle must return error", obj.has("error"));

        System.out.println("test5_BadHandleReturnsError PASSED");
    }


    @Test
    public void test6_GetListInstances() {
        String result = listTool.execute();
        assertNotNull(result);
        System.out.println("test6_GetListInstances result=" + result);

    }
}