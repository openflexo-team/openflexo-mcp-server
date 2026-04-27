package org.openflexo.fmlmcp;

/**
 * Parsed server configuration from CLI arguments.
 *
 * @author Mouad Hayaoui
 */
public class ServerConfig {

    public enum Transport { STDIO, HTTP }

    private Transport transport = Transport.STDIO;
    private int port = 8080;
    private String resourceCenterPath = null;

    private ServerConfig() {}

    /**
     * Parse CLI arguments into a {@link ServerConfig}.
     *
     * <p>Supported flags:
     * <ul>
     *   <li>{@code --transport stdio|http}</li>
     *   <li>{@code --port <n>}</li>
     *   <li>{@code --rc <path>}  path to a filesystem resource center</li>
     * </ul>
     */
    public static ServerConfig parse(String[] args) {
        ServerConfig cfg = new ServerConfig();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--transport":
                    cfg.transport = "http".equalsIgnoreCase(args[++i]) ? Transport.HTTP : Transport.STDIO;
                    break;
                case "--port":
                    cfg.port = Integer.parseInt(args[++i]);
                    break;
                case "--rc":
                    cfg.resourceCenterPath = args[++i];
                    break;
                default:

            }
        }
        return cfg;
    }

    public Transport getTransport()       { return transport; }
    public int       getPort()            { return port; }
    public String    getResourceCenterPath() { return resourceCenterPath; }
}
