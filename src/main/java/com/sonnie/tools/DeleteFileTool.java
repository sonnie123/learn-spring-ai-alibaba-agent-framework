package com.sonnie.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiFunction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Tool for deleting a file from the filesystem.
 */
public class DeleteFileTool implements BiFunction<DeleteFileTool.DeleteFileRequest, ToolContext, String> {

    public static final String DESCRIPTION = """
            Deletes a file from the filesystem.
            
            Usage:
            - The file_path parameter must be an absolute path, not a relative path
            - Only regular files can be deleted, directories are not supported
            - If the file does not exist, an error message will be returned
            """;

    public DeleteFileTool() {
    }

    @Override
    public String apply(DeleteFileRequest request, ToolContext toolContext) {
        try {
            Path path = Paths.get(request.filePath);
            return deleteFile(path);
        } catch (Exception e) {
            return "Error deleting file: " + e.getMessage();
        }
    }

    /**
     * Core logic for deleting a file.
     * This method can be reused by other classes like FileSystemTools.
     *
     * @param filePath The path to the file to delete
     * @return Success message or error message
     */
    public static String deleteFile(Path filePath) {
        try {
            // Check if file does not exist
            if (!Files.exists(filePath)) {
                return "Error: File " + filePath + " does not exist.";
            }

            // Check if path is a directory
            if (Files.isDirectory(filePath)) {
                return "Error: " + filePath + " is a directory, only files can be deleted.";
            }

            // Delete the file
            Files.delete(filePath);

            return "Successfully deleted file: " + filePath;
        } catch (IOException e) {
            return "Error deleting file '" + filePath + "': " + e.getMessage();
        }
    }

    public static ToolCallback createDeleteFileToolCallback(String description) {
        return FunctionToolCallback.builder("delete_file", new DeleteFileTool())
                .description(description)
                .inputType(DeleteFileRequest.class)
                .build();
    }

    /**
     * Request structure for deleting a file.
     */
    public static class DeleteFileRequest {

        @JsonProperty(required = true, value = "file_path")
        @JsonPropertyDescription("The absolute path of the file to delete")
        public String filePath;

        public DeleteFileRequest() {
        }

        public DeleteFileRequest(String filePath) {
            this.filePath = filePath;
        }
    }
}