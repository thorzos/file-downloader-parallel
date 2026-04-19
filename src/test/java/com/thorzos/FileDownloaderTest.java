package com.thorzos;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
class FileDownloaderTest {

    private static final String FILE_PATH = "/testfile";

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("downloader-test", ".tmp");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void downloadInParallel_downloads_single_byte_file_correctly(WireMockRuntimeInfo wm)
            throws Exception {
        byte[] content = {100};

        stubHeadWithSizeAndRanges(1, true);
        stubFor(get(urlEqualTo(FILE_PATH))
                .willReturn(aResponse().withStatus(206).withBody(content)));

        download(wm, 8);

        assertArrayEquals(content, Files.readAllBytes(tempFile));
    }

    @Test
    void downloadInParallel_downloads_in_chunks(WireMockRuntimeInfo wm) throws Exception {

        int maxChunks = 4;
        stubHeadWithSizeAndRanges(maxChunks, true);
        stubFor(get(urlEqualTo(FILE_PATH))
                .willReturn(aResponse().withStatus(206).withBody(new byte[1])));

        download(wm, maxChunks);

        verify(maxChunks, getRequestedFor(urlEqualTo(FILE_PATH)));
    }

    @Test
    void downloadInParallel_still_works_when_server_does_not_accept_ranges(WireMockRuntimeInfo wm) throws Exception {
        stubHeadWithSizeAndRanges(1, false);
        stubFor(get(urlEqualTo(FILE_PATH))
                .willReturn(aResponse().withStatus(206).withBody(new byte[]{7})));

        assertDoesNotThrow(() -> download(wm, 8));

        verify(1, getRequestedFor(urlEqualTo(FILE_PATH)));
    }

    @Test
    void downloadInParallel_throws_IOException_when_HEAD_returns_error(WireMockRuntimeInfo wm) {
        stubFor(head(urlEqualTo(FILE_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(IOException.class, () -> download(wm, 8));
    }

    @Test
    void downloadInParallel_throws_IOException_when_Content_Length_header_is_missing(
            WireMockRuntimeInfo wm) {
        stubFor(head(urlEqualTo(FILE_PATH))
                .willReturn(ok().withHeader("Accept-Ranges", "bytes")));

        assertThrows(IOException.class, () -> download(wm, 8));
    }

    @Test
    void downloadInParallel_throws_when_chunk_GET_fails(WireMockRuntimeInfo wm) {
        stubHeadWithSizeAndRanges(1, true);
        stubFor(get(urlEqualTo(FILE_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(Exception.class, () -> download(wm, 8));
    }

    /** Stubs HEAD with a given file-size (in bytes) and optional Accept-Ranges. */
    private static void stubHeadWithSizeAndRanges(long fileSize, boolean acceptsRanges) {
        var response = ok().withHeader("Content-Length", String.valueOf(fileSize));
        if (acceptsRanges) {
            response = response.withHeader("Accept-Ranges", "bytes");
        }
        stubFor(head(urlEqualTo(FILE_PATH)).willReturn(response));
    }

    private void download(WireMockRuntimeInfo wm, int maxChunks) throws Exception {
        URI uri = URI.create(wm.getHttpBaseUrl() + FILE_PATH);
        new FileDownloader(uri, tempFile.toFile(), maxChunks).downloadInParallel();
    }
}