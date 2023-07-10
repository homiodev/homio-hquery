package org.homio.hquery;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
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
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RestTemplate;

@Log4j2
@RequiredArgsConstructor
@SuppressWarnings("unused")
public final class Curl {

    public static final int ONE_MB = 1000000;

    public static final RestTemplate restTemplate;

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

    public static void downloadIfSizeNotMatch(Path path, String url) {

    }

    @SneakyThrows
    public static RawResponse download(String path, int maxSize) {
        return download(path, maxSize, null, null);
    }

    /**
     * Download file to byte array. Throw error if downloading exceeded maxSize
     *
     * @param maxSize  -
     * @param password -
     * @param path     -
     * @param user     -
     * @return response
     */
    @SneakyThrows
    public static RawResponse download(String path, Integer maxSize, String user, String password) {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(path)).GET().build();
        String name = path.substring(path.lastIndexOf(System.lineSeparator()));
        HttpResponse<byte[]> response;
        if (user == null || password == null) {
            response = HttpClient.newHttpClient().send(request, BodyHandlers.ofByteArray());
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
                                 .send(request, BodyHandlers.ofByteArray());
            // 401 if wrong user/password
            if (response.statusCode() != 200) {
                throw new RuntimeException("Error while download from <" + path + ">. Code: " +
                    response.statusCode() + ". Msg: " + new String(response.body(), StandardCharsets.UTF_8));
            }
        }

        return new RawResponse(response.body(),
            response.headers().firstValue("Content-Type").orElse("text/plain"), Paths.get(path).getFileName().toString());
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
    public static <T> T getWithTimeout(String path, Class<T> returnType, int timeoutInSec) {
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(path)).timeout(Duration.ofSeconds(timeoutInSec)).GET().build();
        HttpResponse<byte[]> response = HttpClient.newBuilder()
                                                  .connectTimeout(Duration.ofSeconds(timeoutInSec))
                                                  .build().send(request, BodyHandlers.ofByteArray());
        if (returnType.equals(byte[].class)) {
            return (T) response.body();
        } else if (returnType.equals(String.class)) {
            return (T) new String(response.body(), StandardCharsets.UTF_8);
        }
        HttpMessageConverterExtractor<T> responseExtractor =
            new HttpMessageConverterExtractor<>(returnType, restTemplate.getMessageConverters());
        return responseExtractor.extractData(new ClientHttpResponse() {
            @Override
            public HttpStatus getStatusCode() {
                return HttpStatus.resolve(response.statusCode());
            }

            @Override
            public int getRawStatusCode() {
                return response.statusCode();
            }

            @Override
            public String getStatusText() {
                return "";
            }

            @Override
            @SneakyThrows
            public void close() {
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(response.body());
            }

            @Override
            public HttpHeaders getHeaders() {
                MultiValueMap<String, String> headers = new LinkedMultiValueMap<>(response.headers().map().size());
                for (Entry<String, List<String>> entry : response.headers().map().entrySet()) {
                    for (String value : entry.getValue()) {
                        headers.add(entry.getKey(), value);
                    }
                }
                return new HttpHeaders(headers);
            }
        });
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
                        double progress = (readBytes / fileSize * 100) * 0.9;
                        progressBar.progress(progress, // max 90%
                            "Downloading " + readBytes / ONE_MB + "Mb. of " + maxMb + " Mb. - " + String.format("%.2f", progress));
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
}
