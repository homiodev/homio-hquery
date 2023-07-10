package org.homio.hquery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StreamReader implements Runnable {

    private final String name;
    private final InputStream inputStream;
    private final Consumer<String> lineConsumer;

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
            }
        } catch (IOException ex) {
            lineConsumer.accept("Thread reader <" + name + "> got error: <" + ex.getMessage() + ">");
            throw new RuntimeException(ex);
        }
    }
}
