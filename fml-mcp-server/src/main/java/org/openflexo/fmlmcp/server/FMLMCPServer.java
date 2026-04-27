package org.openflexo.fmlmcp.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.openflexo.fmlmcp.ServerConfig;
import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.runtime.FMLSessionStore;
import org.openflexo.fmlmcp.tools.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP protocol server.
 *
 * <p>Supports two transports:
 * <ul>
 *   <li><b>stdio</b> : reads JSON-RPC lines from stdin, writes to stdout.</li>
 *   <li><b>http</b> :  listens on a port and accepts POST /mcp requests.</li>
 * </ul>
 *
 * @author Mouad Hayaoui
 */
public class FMLMCPServer {

    private static final Logger logger = Logger.getLogger(FMLMCPServer.class.getName());

    private final ServerConfig             config;
    private final LoadFMLFileTool          loadTool;
    private final CreateInstanceTool       createTool;
    private final CallBehaviourTool        callTool;
    private final ListInstancesTool        listInstancesTool;
    private final GetInstanceStateTool     getInstanceStateTool;
    private final DestroyInstanceTool      destroyInstanceTool;
    private final ListFMLModelsTool        listFMLModelsTool;
    private final DescribeConceptTool      describeConceptTool;
    private final SetPropertyTool          setPropertyTool;
    private final GetPropertyTool          getPropertyTool;
    private final ListResourceCentersTool  listResourceCentersTool;
    private final PingTool                 pingTool;
    private final ReloadModelTool          reloadModelTool;
    private final AddResourceCenterTool    addResourceCenterTool;
    private final QueryInstancesTool       queryInstancesTool;
    private final InitializeInstanceTool   initializeInstanceTool;
    private final BatchCallBehaviourTool   batchCallBehaviourTool;
    private final ExecuteWorkflowTool executeWorkflowTool;
    private final BindResourceTool bindResourceTool;
    private final WriteFileTool writeFileTool;
    private final WriteFMLFileTool writeFMLFileTool;
    private ValidateFMLFileTool validateFMLFileTool;


    public FMLMCPServer(FMLRuntimeBridge runtime, ServerConfig config) {
        this.config     = config;
        FMLSessionStore store = FMLSessionStore.getInstance();
        this.loadTool                = new LoadFMLFileTool(runtime);
        this.createTool              = new CreateInstanceTool(runtime, store);
        this.callTool                = new CallBehaviourTool(store, runtime);
        this.listInstancesTool       = new ListInstancesTool(store);
        this.getInstanceStateTool    = new GetInstanceStateTool(store);
        this.destroyInstanceTool     = new DestroyInstanceTool(store);
        this.listFMLModelsTool       = new ListFMLModelsTool(runtime);
        this.describeConceptTool     = new DescribeConceptTool(runtime);
        this.setPropertyTool         = new SetPropertyTool(store);
        this.getPropertyTool         = new GetPropertyTool(store);
        this.listResourceCentersTool = new ListResourceCentersTool(runtime);
        this.pingTool                = new PingTool(runtime, store, "1.0.0");
        this.reloadModelTool         = new ReloadModelTool(runtime);
        this.addResourceCenterTool   = new AddResourceCenterTool(runtime);
        this.queryInstancesTool      = new QueryInstancesTool(store);
        this.initializeInstanceTool  = new InitializeInstanceTool(runtime, store);
        this.batchCallBehaviourTool  = new BatchCallBehaviourTool(store, runtime);
        this.executeWorkflowTool = new ExecuteWorkflowTool(runtime);
        this.bindResourceTool = new BindResourceTool(runtime);
        this.writeFileTool = new WriteFileTool();
        this.writeFMLFileTool = new WriteFMLFileTool(runtime);
        this.validateFMLFileTool = new ValidateFMLFileTool(runtime);


    }


    public void start() throws Exception {
        if (config.getTransport() == ServerConfig.Transport.HTTP) {
            startHttp();
        } else {
            startStdio();
        }
    }


    private void startStdio() throws IOException {

        PrintStream jsonOut = new PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out),
                true,
                StandardCharsets.UTF_8.name());

        logger.info("FML-MCP server running in stdio mode. Waiting for JSON-RPC requests...");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String response = handleJsonRpcLine(line);
            // Notifications return null — must not send any response
            if (response != null) {
                jsonOut.println(response);
                jsonOut.flush();
            }
        }
        logger.info("stdin closed - FML-MCP server exiting.");
    }


    private void startHttp() throws IOException, InterruptedException {
        HttpServer httpServer = HttpServer.create(
                new InetSocketAddress(config.getPort()), 0);
        httpServer.createContext("/mcp", new MCPHttpHandler());
        httpServer.createContext("/health", exchange -> {
            byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        logger.info("FML-MCP HTTP server listening on port " + config.getPort()
                + " - POST /mcp with JSON-RPC body.");
        Thread.currentThread().join();
    }

    private class MCPHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            try {
                String requestBody = readInputStream(exchange.getRequestBody());
                String responseBody = handleJsonRpcLine(requestBody.trim());
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "HTTP handler error", e);
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
    }


    private String handleJsonRpcLine(String line) {
        String id = "null";
        try {
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            id = request.has("id") ? request.get("id").toString() : "null";

            String method = request.has("method") ? request.get("method").getAsString() : "";

            // Notifications must receive no response at all
            if (method.startsWith("notifications/")) {
                return null;
            }

            if ("initialize".equals(method)) {
                return buildRpcResult(id, buildServerInfo());
            }

            if ("tools/list".equals(method)) {
                return buildRpcResult(id, buildToolList());
            }

            if ("tools/call".equals(method)) {
                JsonObject params   = request.getAsJsonObject("params");
                String toolName     = params.get("name").getAsString();
                JsonObject toolArgs = params.has("arguments")
                        ? params.getAsJsonObject("arguments")
                        : new JsonObject();

                String toolResult = dispatchTool(toolName, toolArgs);
                return buildRpcResult(id, wrapContent(toolResult));
            }

            return buildRpcError(id, -32601, "Method not found: " + method);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling JSON-RPC line", e);
            return buildRpcError(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private String dispatchTool(String toolName, JsonObject args) {
        switch (toolName) {

            case LoadFMLFileTool.TOOL_NAME:
                return loadTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "");

            case CreateInstanceTool.TOOL_NAME:
                return createTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "",
                        args.has("conceptName")    ? args.get("conceptName").getAsString()    : "",
                        args.has("arguments")      ? args.get("arguments").toString()          : "{}");

            case CallBehaviourTool.TOOL_NAME:
                return callTool.execute(
                        args.has("handle")        ? args.get("handle").getAsString()        : "",
                        args.has("behaviourName") ? args.get("behaviourName").getAsString() : "",
                        args.has("arguments")     ? args.get("arguments").toString()         : "{}");

            case ListInstancesTool.TOOL_NAME:
                return listInstancesTool.execute();

            case GetInstanceStateTool.TOOL_NAME:
                return getInstanceStateTool.execute(
                        args.has("handle") ? args.get("handle").getAsString() : "");

            case DestroyInstanceTool.TOOL_NAME:
                return destroyInstanceTool.execute(
                        args.has("handle") ? args.get("handle").getAsString() : "");

            case ListFMLModelsTool.TOOL_NAME:
                return listFMLModelsTool.execute();

            case DescribeConceptTool.TOOL_NAME:
                return describeConceptTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "",
                        args.has("conceptName")    ? args.get("conceptName").getAsString()    : "");

            case SetPropertyTool.TOOL_NAME:
                return setPropertyTool.execute(
                        args.has("handle")       ? args.get("handle").getAsString()       : "",
                        args.has("propertyName") ? args.get("propertyName").getAsString() : "",
                        args.has("value")        ? args.get("value").toString()            : "null");

            case GetPropertyTool.TOOL_NAME:
                return getPropertyTool.execute(
                        args.has("handle")       ? args.get("handle").getAsString()       : "",
                        args.has("propertyName") ? args.get("propertyName").getAsString() : "");

            case ListResourceCentersTool.TOOL_NAME:
                return listResourceCentersTool.execute();

            case PingTool.TOOL_NAME:
                return pingTool.execute();

            case ReloadModelTool.TOOL_NAME:
                return reloadModelTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "");

            case AddResourceCenterTool.TOOL_NAME:
                return addResourceCenterTool.execute(
                        args.has("path") ? args.get("path").getAsString() : "");

            case QueryInstancesTool.TOOL_NAME:
                return queryInstancesTool.execute(
                        args.has("conceptName")    ? args.get("conceptName").getAsString()    : null,
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : null,
                        args.has("filter")         ? args.get("filter").toString()             : null);

            case BatchCallBehaviourTool.TOOL_NAME:
                return batchCallBehaviourTool.execute(
                        args.has("handles")       ? args.get("handles").toString()          : "[]",
                        args.has("behaviourName") ? args.get("behaviourName").getAsString() : "",
                        args.has("arguments")     ? args.get("arguments").toString()        : "{}");

            case InitializeInstanceTool.TOOL_NAME:
                return initializeInstanceTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "",
                        args.has("conceptName")    ? args.get("conceptName").getAsString()    : "",
                        args.has("properties")     ? args.get("properties").toString()        : "{}");
            case ExecuteWorkflowTool.TOOL_NAME:
                return executeWorkflowTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "",
                        args.has("behaviourName")  ? args.get("behaviourName").getAsString()  : "",
                        args.has("arguments")      ? args.get("arguments").toString()          : "{}");
            case BindResourceTool.TOOL_NAME:
                return bindResourceTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "",
                        args.has("slotName")       ? args.get("slotName").getAsString()       : "",
                        args.has("resourceURI")    ? args.get("resourceURI").getAsString()    : "");
            case WriteFileTool.TOOL_NAME:
                return writeFileTool.execute(
                        args.has("path")    ? args.get("path").getAsString()    : "",
                        args.has("content") ? args.get("content").getAsString() : "");
            case WriteFMLFileTool.TOOL_NAME:
                return writeFMLFileTool.execute(
                        args.has("rcPath")    ? args.get("rcPath").getAsString()    : "",
                        args.has("modelName") ? args.get("modelName").getAsString() : "",
                        args.has("content")   ? args.get("content").getAsString() : "");
            case ValidateFMLFileTool.TOOL_NAME:
                return validateFMLFileTool.execute(
                        args.has("fmlResourceURI") ? args.get("fmlResourceURI").getAsString() : "");
            default:
                JsonObject err = new JsonObject();
                err.addProperty("error", "Unknown tool: " + toolName);
                return err.toString();
        }
    }

    private String readInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    private String buildRpcResult(String id, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + resultJson + "}";
    }

    private String buildRpcError(String id, int code, String message) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"error\":{\"code\":" + code + ",\"message\":\""
                + message.replace("\"", "\\\"") + "\"}}";
    }

    private String wrapContent(String jsonPayload) {
        return "{\"content\":[{\"type\":\"text\",\"text\":"
                + escapeForJson(jsonPayload) + "}]}";
    }

    private String escapeForJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String buildServerInfo() {
        return "{\"protocolVersion\":\"2025-11-25\","
                + "\"capabilities\":{\"tools\":{}},"
                + "\"serverInfo\":{\"name\":\"fml-mcp-server\",\"version\":\"1.0.0\"}}";
    }

    private String buildToolList() {
        return "{\"tools\":["

                + "{\"name\":\"" + LoadFMLFileTool.TOOL_NAME + "\","
                + "\"description\":\"" + LoadFMLFileTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"}},"
                + "\"required\":[\"fmlResourceURI\"]}},"

                + "{\"name\":\"" + CreateInstanceTool.TOOL_NAME + "\","
                + "\"description\":\"" + CreateInstanceTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"},"
                + "\"conceptName\":{\"type\":\"string\"},"
                + "\"arguments\":{\"type\":\"object\"}},"
                + "\"required\":[\"fmlResourceURI\",\"conceptName\"]}},"

                + "{\"name\":\"" + CallBehaviourTool.TOOL_NAME + "\","
                + "\"description\":\"" + CallBehaviourTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"handle\":{\"type\":\"string\"},"
                + "\"behaviourName\":{\"type\":\"string\"},"
                + "\"arguments\":{\"type\":\"object\"}},"
                + "\"required\":[\"handle\",\"behaviourName\"]}},"

                + "{\"name\":\"" + ListInstancesTool.TOOL_NAME + "\","
                + "\"description\":\"" + ListInstancesTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"

                + "{\"name\":\"" + GetInstanceStateTool.TOOL_NAME + "\","
                + "\"description\":\"" + GetInstanceStateTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"handle\":{\"type\":\"string\"}},"
                + "\"required\":[\"handle\"]}},"

                + "{\"name\":\"" + DestroyInstanceTool.TOOL_NAME + "\","
                + "\"description\":\"" + DestroyInstanceTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"handle\":{\"type\":\"string\"}},"
                + "\"required\":[\"handle\"]}},"

                + "{\"name\":\"" + ListFMLModelsTool.TOOL_NAME + "\","
                + "\"description\":\"" + ListFMLModelsTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"

                + "{\"name\":\"" + DescribeConceptTool.TOOL_NAME + "\","
                + "\"description\":\"" + DescribeConceptTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"},"
                + "\"conceptName\":{\"type\":\"string\"}},"
                + "\"required\":[\"fmlResourceURI\",\"conceptName\"]}},"

                + "{\"name\":\"" + SetPropertyTool.TOOL_NAME + "\","
                + "\"description\":\"" + SetPropertyTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"handle\":{\"type\":\"string\"},"
                + "\"propertyName\":{\"type\":\"string\"},"
                + "\"value\":{\"type\":\"string\"}},"
                + "\"required\":[\"handle\",\"propertyName\",\"value\"]}},"

                + "{\"name\":\"" + GetPropertyTool.TOOL_NAME + "\","
                + "\"description\":\"" + GetPropertyTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"handle\":{\"type\":\"string\"},"
                + "\"propertyName\":{\"type\":\"string\"}},"
                + "\"required\":[\"handle\",\"propertyName\"]}},"

                + "{\"name\":\"" + ListResourceCentersTool.TOOL_NAME + "\","
                + "\"description\":\"" + ListResourceCentersTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"

                + "{\"name\":\"" + PingTool.TOOL_NAME + "\","
                + "\"description\":\"" + PingTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"

                + "{\"name\":\"" + ReloadModelTool.TOOL_NAME + "\","
                + "\"description\":\"" + ReloadModelTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"}},"
                + "\"required\":[\"fmlResourceURI\"]}},"

                + "{\"name\":\"" + AddResourceCenterTool.TOOL_NAME + "\","
                + "\"description\":\"" + AddResourceCenterTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"path\":{\"type\":\"string\"}},"
                + "\"required\":[\"path\"]}},"

                + "{\"name\":\"" + QueryInstancesTool.TOOL_NAME + "\","
                + "\"description\":\"" + QueryInstancesTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"conceptName\":{\"type\":\"string\"},"
                + "\"fmlResourceURI\":{\"type\":\"string\"},"
                + "\"filter\":{\"type\":\"object\"}}}},"

                + "{\"name\":\"" + BatchCallBehaviourTool.TOOL_NAME + "\","
                + "\"description\":\"" + BatchCallBehaviourTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"handles\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"behaviourName\":{\"type\":\"string\"},"
                + "\"arguments\":{\"type\":\"object\"}},"
                + "\"required\":[\"handles\",\"behaviourName\"]}},"

                + "{\"name\":\"" + InitializeInstanceTool.TOOL_NAME + "\","
                + "\"description\":\"" + InitializeInstanceTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"},"
                + "\"conceptName\":{\"type\":\"string\"},"
                + "\"properties\":{\"type\":\"object\"}},"
                + "\"required\":[\"fmlResourceURI\",\"conceptName\"]}},"

                + "{\"name\":\"" + ExecuteWorkflowTool.TOOL_NAME + "\","
                + "\"description\":\"" + ExecuteWorkflowTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"},"
                + "\"behaviourName\":{\"type\":\"string\"},"
                + "\"arguments\":{\"type\":\"object\"}},"
                + "\"required\":[\"fmlResourceURI\",\"behaviourName\"]}},"

                + "{\"name\":\"" + BindResourceTool.TOOL_NAME + "\","
                + "\"description\":\"" + BindResourceTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"},"
                + "\"slotName\":{\"type\":\"string\"},"
                + "\"resourceURI\":{\"type\":\"string\"}},"
                + "\"required\":[\"fmlResourceURI\",\"slotName\",\"resourceURI\"]}},"

                + "{\"name\":\"" + WriteFileTool.TOOL_NAME + "\","
                + "\"description\":\"" + WriteFileTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"path\":{\"type\":\"string\"},"
                + "\"content\":{\"type\":\"string\"}},"
                + "\"required\":[\"path\",\"content\"]}},"

                + "{\"name\":\"" + WriteFMLFileTool.TOOL_NAME + "\","
                + "\"description\":\"" + WriteFMLFileTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"rcPath\":{\"type\":\"string\"},"
                + "\"modelName\":{\"type\":\"string\"},"
                + "\"content\":{\"type\":\"string\"}},"
                + "\"required\":[\"rcPath\",\"modelName\",\"content\"]}},"

                + "{\"name\":\"" + ValidateFMLFileTool.TOOL_NAME + "\","
                + "\"description\":\"" + ValidateFMLFileTool.TOOL_DESCRIPTION + "\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":"
                + "{\"fmlResourceURI\":{\"type\":\"string\"}},"
                + "\"required\":[\"fmlResourceURI\"]}}"


                + "]}";
    }
}