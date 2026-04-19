# Parallel File Downloader

A command-line Java tool that downloads files in parallel chunks using HTTP range requests.
Chunks are fetched in parallel and written directly into the correct position of the output file.

## Requirements

- Java 21+
- A web server that supports `Accept-Ranges: bytes` and returns `Content-Length`

## Usage

```
mvn clean package
java -jar target/file-downloader-parallel-1.0-SNAPSHOT.jar <url> <output-file> [num-chunks]
```

| Argument | Required | Description |
|---|---|---|
| `url` | yes | URL of the file to download |
| `output-file` | yes | Path to write the downloaded file to |
| `num-chunks` | no | Number of parallel chunks (defaults to 8) |

### Example

```bash
java -jar target/file-downloader-parallel-1.0-SNAPSHOT.jar http://localhost:8080/file.zip ./out.zip 7
```

## Running a local test server

You can serve files from a local directory using Docker:

```bash
docker run --rm -p 8080:80 \
  -v /path/to/your/directory:/usr/local/apache2/htdocs/ \
  httpd:latest
```

Files in that directory are then accessible at `http://localhost:8080/<filename>`.

## How it works

1. A `HEAD` request is sent to the URL to retrieve `Content-Length` and verify `Accept-Ranges: bytes` support.
2. The file size is divided into `num-chunks` byte ranges.
3. Each range is downloaded concurrently by a `ChunkDownloader` task running on a virtual thread, using a `Range: bytes=<start>-<end>` header.
4. Each chunk is written directly into its correct offset in the output file via a FileChannel.
5. If the server does not support range requests, or the file is smaller than the requested chunk count, the file is downloaded in a single request instead.

