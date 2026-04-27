package org.openflexo.fmlmcp;

import org.openflexo.fmlmcp.runtime.FMLRuntimeBridge;
import org.openflexo.fmlmcp.server.FMLMCPServer;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 * Entry point for the FML-MCP Server.
 *
 * @author Mouad Hayaoui
 */
public class FMLMCPServerMain {

    private static final Logger logger = Logger.getLogger(FMLMCPServerMain.class.getName());

    public static void main(String[] args) throws Exception {

        // Redirect ALL logging to stderr so stdout stays clean for JSON-RPC.
        // Claude Desktop reads stdout and expects pure JSON lines only.
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }
        StreamHandler stderrHandler = new StreamHandler(System.err, new SimpleFormatter());
        stderrHandler.setLevel(Level.ALL);
        rootLogger.addHandler(stderrHandler);
        rootLogger.setLevel(Level.INFO);

        // Also force System.out to stderr so any rogue printlns from
        // OpenFlexo internals (e.g. "Trying to install default packaged resource center")
        // do not corrupt the JSON-RPC stream.
        System.setOut(new java.io.PrintStream(System.err, true));

        ServerConfig config = ServerConfig.parse(args);

        logger.info("Booting OpenFlexo runtime...");
        FMLRuntimeBridge runtime = FMLRuntimeBridge.getInstance();
        runtime.initialize(config.getResourceCenterPath());
        logger.info("OpenFlexo runtime ready.");

        FMLMCPServer server = new FMLMCPServer(runtime, config);
        logger.info("Starting FML-MCP server [transport=" + config.getTransport() + "]...");
        server.start();
    }
}