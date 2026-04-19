package com.thorzos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.Callable;

public class ChunkDownloader implements Callable<Void> {

    private final HttpClient client;
    private final URI uri;
    private final FileChannel fileChannel;
    private final long rangeStart;
    private final long rangeEnd;

    public ChunkDownloader(HttpClient client, URI uri, FileChannel outputChannel, long rangeStart, long rangeEnd) {
        this.client = client;
        this.uri = uri;
        this.fileChannel = outputChannel;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    @Override
    public Void call() throws IOException, InterruptedException {
        var getRequest = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .header("Range", "bytes=" + rangeStart + "-" + rangeEnd)
                .build();

        HttpResponse<byte[]> response = client.send(getRequest, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200 && response.statusCode() != 206) {
            throw new IOException("Unexpected status code: " + response.statusCode());
        }

        byte[] responseBytes = response.body();
        fileChannel.write(ByteBuffer.wrap(responseBytes), rangeStart);
        return null;
    }

}
