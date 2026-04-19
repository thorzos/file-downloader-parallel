package com.thorzos;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

public class App {

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: java App <url> <output-file> [num-chunks]");
            System.exit(1);
        }

        URI uri = parseURI(args[0]);
        File outputFile = parseOutputFile(args[1]);

        FileDownloader downloader = args.length == 3 ? createDownloader(uri, outputFile, args[2]) : new FileDownloader(uri, outputFile);

        try {
            downloader.downloadInParallel();
            System.out.println("Download done: " + outputFile.getAbsolutePath());
        } catch (IOException | InterruptedException | ExecutionException e) {
            cleanup(outputFile);
            System.err.println("Download failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static URI parseURI(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            System.err.println("Invalid url: " + uri);
            System.exit(1);
        }
        return null;
    }

    private static File parseOutputFile(String filePath) {
        File outputFile = new File(filePath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null) {
            if (!parentDir.exists()) {
                System.err.println("Error: output directory does not exist: " + parentDir);
                System.exit(1);
            }
            if (!parentDir.canWrite()) {
                System.err.println("Error: output directory is not writable: " + parentDir);
                System.exit(1);
            }
        }
        return outputFile;
    }

    private static FileDownloader createDownloader(URI uri, File outputFile, String rawChunks) {
        try {
            int maxChunks = Integer.parseInt(rawChunks);
            if (maxChunks < 1) {
                System.err.println("Error: [num-chunks] must be a positive integer.");
                System.exit(1);
            }
            return new FileDownloader(uri, outputFile, maxChunks);
        } catch (NumberFormatException e) {
            System.err.println("Error: [num-chunks] must be an integer.");
            System.exit(1);
            throw new AssertionError("unreachable");
        }
    }

    private static void cleanup(File outputFile) {
        if (outputFile.exists()) {
            boolean delete = outputFile.delete();
            if (!delete) {
                System.err.println("Unable to delete file: " + outputFile.getAbsolutePath());
            }
        }
    }
}