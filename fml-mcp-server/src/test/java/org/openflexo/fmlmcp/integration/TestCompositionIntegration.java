package org.openflexo.fmlmcp.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.tools.CallBehaviourTool;
import org.openflexo.fmlmcp.tools.CreateInstanceTool;
import org.openflexo.fmlmcp.tools.LoadFMLFileTool;

import static org.junit.Assert.*;

/**
 * Integration test for cross-concept composition.
 *
 * Verifies that a handle string passed as a behaviour argument
 * is correctly resolved to a live FlexoConceptInstance by
 * BehaviourArgumentMapper, enabling one concept to hold a
 * reference to another.
 *
 * @author Mouad Hayaoui
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestCompositionIntegration {

    private static final String RC_PATH =
            "src/test/resources";
    private static final String TEAM_URI =
            "http://fmlmcp.test/FML/TeamModel.fml";

    private static FMLRuntimeBridge   bridge;
    private static FMLSessionStore    store;
    private static LoadFMLFileTool    loadTool;
    private static CreateInstanceTool createTool;
    private static CallBehaviourTool  callTool;

    private static String memberHandle;
    private static String teamHandle;

    @BeforeClass
    public static void setUp() {
        bridge     = FMLRuntimeBridge.getInstance();
        bridge.initialize(RC_PATH);
        store      = FMLSessionStore.getInstance();
        loadTool   = new LoadFMLFileTool(bridge);
        createTool = new CreateInstanceTool(bridge, store);
        callTool   = new CallBehaviourTool(store, bridge);
    }

    @Test
    public void test1_LoadTeamModel() {
        String result = loadTool.execute(TEAM_URI);
        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("load must not return error: " + result, obj.has("error"));
        assertEquals("TeamModel", obj.get("virtualModel").getAsString());
        System.out.println("test1 PASSED " + result);
    }

    @Test
    public void test2_CreateMember() {
        String result = createTool.execute(
                TEAM_URI, "Member",
                "{\"memberName\":\"Alice\",\"memberRole\":\"Engineer\"}");
        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("create Member must not error: " + result, obj.has("error"));
        memberHandle = obj.get("handle").getAsString();
        assertNotNull("handle must be set", memberHandle);
        System.out.println("test2 PASSED — handle=" + memberHandle);
    }

    @Test
    public void test3_CreateTeam() {
        String result = createTool.execute(
                TEAM_URI, "Team",
                "{\"aTeamName\":\"Alpha\"}");
        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("create Team must not error: " + result, obj.has("error"));
        teamHandle = obj.get("handle").getAsString();
        assertNotNull("handle must be set", teamHandle);
        System.out.println("test3 PASSED — handle=" + teamHandle);
    }

    @Test
    public void test4_AssignLeadByHandle() {
        assertNotNull("memberHandle must be set by test2", memberHandle);
        assertNotNull("teamHandle must be set by test3", teamHandle);


        String args = "{\"aMember\":\"" + memberHandle + "\"}";
        String result = callTool.execute(teamHandle, "assignLead", args);
        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("assignLead must not error: " + result, obj.has("error"));
        System.out.println("test4 PASSED — " + result);
    }

    @Test
    public void test5_DescribeTeamWithLead() {
        assertNotNull("teamHandle must be set by test3", teamHandle);

        String result = callTool.execute(teamHandle, "describe", "{}");
        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("describe must not error: " + result, obj.has("error"));

        String description = obj.get("result").getAsString();
        assertTrue("description must mention team name",
                description.contains("Alpha"));
        assertTrue("description must mention lead name",
                description.contains("Alice"));

        System.out.println("test5 PASSED — " + description);
    }

    @Test
    public void test6_MemberIntroduce() {
        assertNotNull("memberHandle must be set by test2", memberHandle);

        String result = callTool.execute(memberHandle, "introduce", "{}");
        JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
        assertFalse("introduce must not error: " + result, obj.has("error"));

        String intro = obj.get("result").getAsString();
        assertTrue("intro must mention Alice", intro.contains("Alice"));
        assertTrue("intro must mention Engineer", intro.contains("Engineer"));

        System.out.println("test6 PASSED — " + intro);
    }
}