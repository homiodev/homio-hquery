package org.homio.hquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.util.function.ThrowingBiFunction;
import org.springframework.web.client.RestTemplate;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public final class Curl {

    public static final int ONE_MB = 1000000;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final RestTemplate restTemplate;

    static {
        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }

    public static <T> T get(String url, Class<T> responseType, Object... uriVariables) {
        return restTemplate.getForObject(url, responseType, uriVariables);
    }

    public static <T> T post(String url, Object request, Class<T> responseType,
                             Object... uriVariables) {
        return restTemplate.postForObject(url, request, responseType, uriVariables);
    }

    public static void delete(String url, Object... uriVariables) {
        restTemplate.delete(url, uriVariables);
    }

    @SneakyThrows
    public static void download(String url, Path targetPath) {
        URLConnection connection = getUrlConnection(new URL(url));
        try (InputStream stream = connection.getInputStream()) {
            Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SneakyThrows
    public static HttpRequest createPostRequest(String url, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (body != null) {
            String value = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
            builder.POST(HttpRequest.BodyPublishers.ofString(value));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    @SneakyThrows
    public static HttpRequest createGetRequest(String url) {
        return createGetRequest(url, null);
    }

    @SneakyThrows
    public static HttpRequest createGetRequest(String url, @Nullable Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return builder.GET().build();
    }

    public static <T> void sendAsync(HttpRequest httpRequest, Class<T> responseType, BiConsumer<T, Integer> handler) {
        HTTP_CLIENT.sendAsync(httpRequest, BodyHandlers.ofString()).thenAccept(response -> {
            try {
                handler.accept(objectMapper.readValue(response.body(), responseType), response.statusCode());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SneakyThrows
    public static <T> void sendSync(HttpRequest httpRequest, Class<T> responseType, BiConsumer<T, Integer> handler) {
        HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, BodyHandlers.ofString());
        handler.accept(objectMapper.readValue(response.body(), responseType), response.statusCode());
    }

    @SneakyThrows
    public static <T, R> R sendSync(HttpRequest httpRequest, Class<T> responseType, ThrowingBiFunction<T, Integer, R> handler) {
        HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, BodyHandlers.ofString());
        return handler.apply(objectMapper.readValue(response.body(), responseType), response.statusCode());
    }

    @SneakyThrows
    public static RawResponse download(String path, int maxSize) {
        return download(path, maxSize, null, null);
    }

    /**
     * Download file to byte array. Throw error if downloading exceeded maxSize
     *
     * @param maxSize  - max bytes to read or null
     * @param user     - user or null
     * @param password - password or null
     * @param path     - path not null
     * @return response
     */
    @SneakyThrows
    public static RawResponse download(String path, Integer maxSize, String user, String password) {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(path)).GET().build();
        String name = path.substring(path.lastIndexOf(System.lineSeparator()));
        HttpResponse<InputStream> response;
        if (user == null || password == null) {
            response = HTTP_CLIENT.send(request, BodyHandlers.ofInputStream());
        } else {
            request = HttpRequest.newBuilder().uri(request.uri()).POST(BodyPublishers.noBody()).build();
            response = HttpClient.newBuilder()
                    .authenticator(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(
                                    user,
                                    password.toCharArray());
                        }
                    }).build()
                    .send(request, BodyHandlers.ofInputStream());
            // 401 if wrong user/password
            if (response.statusCode() != 200) {
                String body;
                try (InputStream stream = response.body()) {
                    body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                throw new RuntimeException("Error while download from <" + path + ">. Code: " +
                        response.statusCode() + ". Msg: " + body);
            }
        }

        try (InputStream inputStream = response.body()) {
            InputStream streamToRead = inputStream;
            if (maxSize != null) {
                streamToRead = new LimitedInputStream(inputStream, maxSize);
            }

            return new RawResponse(streamToRead.readAllBytes(),
                    response.headers().firstValue("Content-Type").orElse("text/plain"), Paths.get(path).getFileName().toString());
        }
    }

    public static RawResponse download(String path) {
        return download(path, null, null);
    }

    @SneakyThrows
    public static RawResponse download(String path, String user, String password) {
        return download(path, null, user, password);
    }

    @SneakyThrows
    public static void downloadWithProgress(String urlStr, Path targetPath, ProgressBar progressBar) {
        progressBar.progress(1, "Checking file size...");
        URL url = new URL(urlStr);
        double fileSize = getFileSize(url);
        // download without progress if less than 2 megabytes
        if (fileSize / ONE_MB < 2) { // one mb
            download(urlStr, targetPath);
            return;
        }
        int maxMb = (int) (fileSize / ONE_MB);
        URLConnection connection = getUrlConnection(url);
        try (InputStream fileInputStream = new TransformFilterInputStream(connection.getInputStream(), progressBar, fileSize, maxMb)) {
            Files.copy(fileInputStream, targetPath);
        }
    }

    @SneakyThrows
    public static <T> T getWithTimeout(String url, Class<T> returnType, int timeoutInSec) {
        if (timeoutInSec <= 30) {
            return get(url, returnType);
        }
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(timeoutInSec))
                .setReadTimeout(Duration.ofSeconds(timeoutInSec))
                .build();
        return restTemplate.getForObject(url, returnType);
    }

    @SneakyThrows
    public static int getFileSize(String url) {
        return getFileSize(new URL(url));
    }

    public static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

    private static URLConnection getUrlConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(60000);
        connection.setReadTimeout(60000);
        return connection;
    }

    @Getter
    public static class RawResponse {

        protected String name;
        protected byte[] bytes;
        protected String mimeType;

        public RawResponse(byte[] bytes, String mimeType, String name) {
            if (mimeType.isEmpty()) {
                throw new IllegalArgumentException("mimeType argument must not be blank");
            }
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.name = name;
        }
    }

    private static class TransformFilterInputStream extends FilterInputStream {

        private final Consumer<Integer> progressHandler;
        private int readBytes = 0;

        protected TransformFilterInputStream(InputStream in, ProgressBar progressBar, double fileSize, int maxMb) {
            super(in);
            this.progressHandler = new Consumer<>() {
                int nextStep = 1;

                @Override
                public void accept(Integer num) {
                    readBytes += num;
                    if (readBytes / (double) ONE_MB > nextStep) {
                        nextStep++;
                        double progress = (readBytes / fileSize * 100) * 0.99; // max 99%
                        progressBar.progress(progress, "Downloading " + readBytes / ONE_MB + "Mb. of " + maxMb + " Mb.");
                    }
                }
            };
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = super.read(b, off, len);
            progressHandler.accept(read);
            return read;
        }
    }

    static class LimitedInputStream extends FilterInputStream {

        private final int maxSizeBytes;
        private int bytesRead;

        public LimitedInputStream(InputStream in, int maxSizeBytes) {
            super(in);
            this.maxSizeBytes = maxSizeBytes;
            this.bytesRead = 0;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maxSizeBytes) {
                return -1; // Reached the maximum size, return end of stream
            }
            int data = super.read();
            if (data != -1) {
                bytesRead++;
            }
            return data;
        }
    }
}
