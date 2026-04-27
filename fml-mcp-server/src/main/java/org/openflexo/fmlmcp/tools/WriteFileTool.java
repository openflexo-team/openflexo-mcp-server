// File: fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\WriteFileTool.java
// Full path: C:\Users\hayaoumo\another_dev\openflexo-fml-mcp-server\fml-mcp-server\src\main\java\org\openflexo\fmlmcp\tools\WriteFileTool.java

package org.openflexo.fmlmcp.tools;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MCP tool: {@code write_file}
 *
 * <p>Writes text content to an absolute file path on disk, creating any
 * missing parent directories automatically.
 *
 * <p>This is the tool Claude uses to place data files (CSV, JSON, OWL, etc.)
 * onto the server's filesystem before registering the containing directory
 * as a resource center. It enables a fully self-contained authoring flow:
 * the user uploads files in the conversation, Claude reads them in context,
 * writes them to disk via this tool, and then proceeds with FML authoring
 * and execution — without the user ever needing to specify file paths
 * or manually copy files.
 *
 * <p>Input schema:
 * <pre>
 * {
 *   "path":    "C:\\Users\\hayaoumo\\dev\\federation\\data\\trace.csv",  // required
 *   "content": "Alice,95\nBob,87\n..."                                   // required
 * }
 * </pre>
 *
 * <p>Output (success):
 * <pre>
 * {
 *   "written":   true,
 *   "path":      "C:\\...\\trace.csv",
 *   "sizeBytes": 42,
 *   "created":   true   // false if file already existed and was overwritten
 * }
 * </pre>
 *
 * <p>Output (failure):
 * <pre>
 * { "error": "Could not create directory: ..." }
 * </pre>
 *
 * @author Mouad Hayaoui
 */
public class WriteFileTool {

    private static final Logger logger =
            Logger.getLogger(WriteFileTool.class.getName());

    public static final String TOOL_NAME = "write_file";

    public static final String TOOL_DESCRIPTION =
            "Write text content to an absolute file path on disk, creating any "
                    + "missing parent directories automatically. "
                    + "Use this to place data files (CSV, JSON, OWL, etc.) onto the "
                    + "filesystem before registering the directory as a resource center. "
                    + "This allows Claude to receive files in the conversation and write "
                    + "them to disk without the user needing to copy them manually. "
                    + "path must be an absolute path including the filename and extension. "
                    + "content is the full text content to write (UTF-8 encoded).";

    public WriteFileTool() {}

    /**
     * @param path    absolute path to the target file (including filename + extension)
     * @param content text content to write (UTF-8)
     */
    public String execute(String path, String content) {

         if (path == null || path.trim().isEmpty()) {
            return buildError("path must not be empty.");
        }
        if (content == null) {
            return buildError("content must not be null.");
        }

        File targetFile = new File(path.trim());

         String lowerName = targetFile.getName().toLowerCase();
        if (lowerName.endsWith(".java") || lowerName.endsWith(".class")
                || lowerName.endsWith(".jar") || lowerName.endsWith(".gradle")) {
            return buildError("Writing to ." + lowerName.substring(lowerName.lastIndexOf('.'))
                    + " files is not allowed for safety reasons.");
        }

        try {
             File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    return buildError("Could not create directory: "
                            + parentDir.getAbsolutePath());
                }
                logger.info("write_file: created directories: "
                        + parentDir.getAbsolutePath());
            }

            boolean existedBefore = targetFile.exists();

             try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                writer.write(content);
            }

            long sizeBytes = targetFile.length();
            logger.info("write_file: wrote " + sizeBytes + " bytes to "
                    + targetFile.getAbsolutePath());

             JsonObject result = new JsonObject();
            result.addProperty("written",   true);
            result.addProperty("path",      targetFile.getAbsolutePath());
            result.addProperty("sizeBytes", sizeBytes);
            result.addProperty("created",   !existedBefore);
            return result.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "write_file: failed to write to " + path, e);
            return buildError("Failed to write file: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        JsonObject err = new JsonObject();
        err.addProperty("error", message);
        return err.toString();
    }
}