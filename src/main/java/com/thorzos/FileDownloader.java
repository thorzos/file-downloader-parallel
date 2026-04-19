package com.thorzos;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FileDownloader {

    private final URI uri;
    private final File outputFile;
    private final int maxChunks;

    private static final int DEFAULT_THREAD_COUNT = 8;

    public FileDownloader(URI uri, File outputFile) {
        this(uri, outputFile, DEFAULT_THREAD_COUNT);
    }

    public FileDownloader(URI uri, File outputFile, int maxChunks) {
        this.uri = uri;
        this.outputFile = outputFile;
        this.maxChunks = maxChunks;
    }

    public void downloadInParallel() throws IOException, InterruptedException, ExecutionException {
        try (HttpClient client = HttpClient.newHttpClient()){

            FileInfo fileInfo = fetchFileInfo(client);
            int numOfChunks = determineChunkNumber(fileInfo);

            try (RandomAccessFile multiWriteFile = new RandomAccessFile(outputFile, "rw");
                 FileChannel channel = multiWriteFile.getChannel())
            {
                downloadInChunks(client, channel, fileInfo.fileSize(), numOfChunks);
            }

        }
    }

    private void downloadInChunks(HttpClient client, FileChannel channel, long fileSize, int numOfChunks) throws ExecutionException,
            InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futureList = new ArrayList<>(numOfChunks);

            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Range
            // "zero-indexed & inclusive"
            for (int i = 0; i < numOfChunks; i++) {
                long rangeStart =  i * (fileSize / numOfChunks);
                long rangeEnd = (i == numOfChunks - 1)
                        ? fileSize - 1
                        : (i + 1) * (fileSize / numOfChunks) - 1;

                var future = executor.submit(new ChunkDownloader(client, uri, channel, rangeStart, rangeEnd));
                futureList.add(future);
            }

            try {
                for (var future : futureList) {
                    future.get();
                }
            } catch (ExecutionException | InterruptedException e) {
                executor.shutdownNow();
                throw e;
            }
        }
    }

    private FileInfo fetchFileInfo(HttpClient client) throws IOException, InterruptedException {
        HttpRequest headRequest = HttpRequest.newBuilder()
                .uri(uri)
                .HEAD()
                .build();

        HttpResponse<Void> response = client.send(headRequest, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Server returned unexpected status: " + response.statusCode());
        }

        long fileSize = response.headers().firstValueAsLong("content-length")
                .orElseThrow(() -> new IOException("Server did not return a Content-Length header"));

        boolean acceptsRanges = response.headers()
                .firstValue("accept-ranges")
                .map("bytes"::equals)
                .orElse(false);

        return new FileInfo(acceptsRanges, fileSize);
    }

    private int determineChunkNumber(FileInfo fileInfo) {
        if (!fileInfo.acceptsRanges() || fileInfo.fileSize() <= maxChunks) {
            return Math.min(DEFAULT_THREAD_COUNT, (int) fileInfo.fileSize());
        }
        return maxChunks;
    }

    private record FileInfo(boolean acceptsRanges, long fileSize) {}
}
