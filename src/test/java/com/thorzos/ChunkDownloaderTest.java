package com.thorzos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkDownloaderTest {

    @Mock
    private HttpClient httpClient;

    private HttpResponse<byte[]> httpResponse;

    private URI uri;
    private Path tempFile;
    private FileChannel fileChannel;

    @BeforeEach
    void setUp() throws Exception {
        httpResponse = mock(HttpResponse.class);
        uri = URI.create("http://test.com/iWantToDownload");
        tempFile = Files.createTempFile("chunk-test", ".tmp");
        fileChannel = FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    @AfterEach
    void tearDown() throws Exception {
        fileChannel.close();
        Files.deleteIfExists(tempFile);
    }

    @Test
    void call_returns_null_on_success() throws Exception {
        stubSuccessResponse(206, "data".getBytes());
        ChunkDownloader downloader = new ChunkDownloader(httpClient, uri, fileChannel, 0, 3);

        assertNull(downloader.call(), "Callable<Void> must return null");
    }

    @Test
    void call_writes_bytes_at_offset_zero() throws Exception {
        byte[] data = "Hello".getBytes();
        stubSuccessResponse(206, data);

        new ChunkDownloader(httpClient, uri, fileChannel, 0, data.length - 1).call();

        assertBytesAtOffset(data, 0);
    }

    @Test
    void call_writes_bytes_at_non_zero_offset() throws Exception {
        byte[] data = "World".getBytes();
        long offset = 100;
        stubSuccessResponse(206, data);

        new ChunkDownloader(httpClient, uri, fileChannel, offset, offset + data.length - 1).call();

        assertBytesAtOffset(data, offset);
    }

    @Test
    void call_accepts_200_status_as_valid() throws Exception {
        stubSuccessResponse(200, "ok".getBytes());
        ChunkDownloader downloader = new ChunkDownloader(httpClient, uri, fileChannel, 0, 1);

        assertDoesNotThrow(downloader::call);
    }

    @Test
    void call_throws_IOException_on_error() throws Exception {
        stubErrorResponse(500);
        ChunkDownloader downloader = new ChunkDownloader(httpClient, uri, fileChannel, 0, 99);

        assertThrows(IOException.class, downloader::call);
    }

    @Test
    void call_sends_correct_Range_header() throws Exception {
        stubSuccessResponse(206, new byte[10]);

        new ChunkDownloader(httpClient, uri, fileChannel, 100, 199).call();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        String rangeHeader = requestCaptor.getValue()
                .headers()
                .firstValue("Range")
                .orElse("");

        assertEquals("bytes=100-199", rangeHeader);
    }

    @Test
    void call_sends_GET_to_correct_uri() throws Exception {
        stubSuccessResponse(206, new byte[5]);

        new ChunkDownloader(httpClient, uri, fileChannel, 0, 4).call();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        assertEquals(uri, requestCaptor.getValue().uri());
        assertEquals("GET", requestCaptor.getValue().method());
    }


    private void stubSuccessResponse(int status, byte[] body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        when(httpResponse.body()).thenReturn(body);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
    }

    private void stubErrorResponse(int status) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
    }

    private void assertBytesAtOffset(byte[] expected, long offset) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(expected.length);
        fileChannel.read(buffer, offset);
        assertArrayEquals(expected, buffer.array());
    }
}